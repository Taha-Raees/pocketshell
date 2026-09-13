/*
 * pocketshell-notif-bridge — org.freedesktop.Notifications D-Bus service
 *
 * A minimal implementation of the Desktop Notifications Specification 1.2
 * that receives notifications from ANY guest program (notify-send, agents,
 * custom tools) and writes them as JSONL records to a bridge file.
 *
 * The Android-side notification system reads the bridge file (it's inside
 * the app-owned rootfs, same as AgentLaunchRecords) and translates them
 * into real Android notifications through the existing NotificationCoordinator.
 *
 * Architecture:
 *   Guest programs → libnotify/notify-send → D-Bus session bus
 *     → THIS SERVICE (org.freedesktop.Notifications)
 *       → JSONL bridge file → Android NotificationCoordinator
 *
 * Build:
 *   gcc -O2 -o pocketshell-notif-bridge pocketshell-notif-bridge.c \
 *       $(pkg-config --cflags --libs gio-2.0)
 *
 * Run:
 *   Requires DBUS_SESSION_BUS_ADDRESS set (the session bus socket).
 *   The session bus is started per-session by the PocketShell session
 *   bootstrap if the bridge is installed.
 *
 * Performance: event-driven (GLib main loop), zero polling, < 1 MB RSS,
 * no threads, no timers. Sleeps on epoll when idle.
 *
 * License: Same as PocketShell (the project's license).
 */

#include <gio/gio.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <signal.h>

/* The bridge file: JSONL records, one per notification. */
#define BRIDGE_DIR   "/var/lib/pocketshell-agent"
#define BRIDGE_FILE  BRIDGE_DIR "/notifications.jsonl"

/* Next notification id (monotonic, process-scoped). */
static guint32 next_id = 1;
static GMainLoop *loop = NULL;

/* D-Bus introspection XML — the standard Desktop Notifications 1.2 interface. */
static const gchar introspection_xml[] =
    "<node>"
    "  <interface name='org.freedesktop.Notifications'>"
    "    <method name='Notify'>"
    "      <arg direction='in'  type='s'  name='app_name'/>"
    "      <arg direction='in'  type='u'  name='replaces_id'/>"
    "      <arg direction='in'  type='s'  name='app_icon'/>"
    "      <arg direction='in'  type='s'  name='summary'/>"
    "      <arg direction='in'  type='s'  name='body'/>"
    "      <arg direction='in'  type='as' name='actions'/>"
    "      <arg direction='in'  type='a{sv}' name='hints'/>"
    "      <arg direction='in'  type='i'  name='expire_timeout'/>"
    "      <arg direction='out' type='u'  name='id'/>"
    "    </method>"
    "    <method name='CloseNotification'>"
    "      <arg direction='in' type='u' name='id'/>"
    "    </method>"
    "    <method name='GetCapabilities'>"
    "      <arg direction='out' type='as' name='capabilities'/>"
    "    </method>"
    "    <method name='GetServerInformation'>"
    "      <arg direction='out' type='s' name='name'/>"
    "      <arg direction='out' type='s' name='vendor'/>"
    "      <arg direction='out' type='s' name='version'/>"
    "      <arg direction='out' type='s' name='spec_version'/>"
    "    </method>"
    "    <signal name='NotificationClosed'>"
    "      <arg type='u' name='id'/>"
    "      <arg type='u' name='reason'/>"
    "    </signal>"
    "    <signal name='ActionInvoked'>"
    "      <arg type='u' name='id'/>"
    "      <arg type='s' name='action_key'/>"
    "    </signal>"
    "  </interface>"
    "</node>";

/*
 * Escape a string for JSON output (minimal: backslash, quote, control chars).
 * Caller frees the result.
 */
static gchar *json_escape(const gchar *s) {
    if (!s) return g_strdup("");
    GString *out = g_string_new(NULL);
    for (const gchar *p = s; *p; p++) {
        switch (*p) {
            case '"':  g_string_append(out, "\\\""); break;
            case '\\': g_string_append(out, "\\\\"); break;
            case '\n': g_string_append(out, "\\n");  break;
            case '\r': g_string_append(out, "\\r");  break;
            case '\t': g_string_append(out, "\\t");  break;
            default:
                if ((unsigned char)*p < 0x20)
                    g_string_append_printf(out, "\\u%04x", (unsigned char)*p);
                else
                    g_string_append_c(out, *p);
                break;
        }
    }
    return g_string_free(out, FALSE);
}

/*
 * Write a notification record to the bridge file (JSONL, append).
 * Fields: id, app, summary, body, timeout, ts (epoch seconds).
 */
static void write_bridge_record(guint32 id, const gchar *app,
                                 const gchar *summary, const gchar *body,
                                 gint32 timeout) {
    /* Ensure directory exists (idempotent). */
    g_mkdir_with_parents(BRIDGE_DIR, 0755);

    FILE *f = fopen(BRIDGE_FILE, "a");
    if (!f) {
        g_printerr("pocketshell-notif-bridge: cannot open %s: %m\n", BRIDGE_FILE);
        return;
    }

    gchar *e_app = json_escape(app);
    gchar *e_sum = json_escape(summary);
    gchar *e_body = json_escape(body);
    gint64 ts = g_get_real_time() / G_USEC_PER_SEC;

    const gchar *sid_env = g_getenv("POCKETSHELL_SESSION_ID");
    guint64 sid = sid_env ? g_ascii_strtoull(sid_env, NULL, 10) : 0;

    fprintf(f, "{\"t\":\"notify\",\"id\":%u,\"sessionId\":%" G_GUINT64_FORMAT ",\"app\":\"%s\",\"summary\":\"%s\","
               "\"body\":\"%s\",\"timeout\":%d,\"ts\":%" G_GINT64_FORMAT "}\n",
            id, sid, e_app, e_sum, e_body, timeout, ts);
    fflush(f);
    fclose(f);

    g_free(e_app);
    g_free(e_sum);
    g_free(e_body);
}

/*
 * D-Bus method handler — the ONLY entry point for all notification calls.
 */
static void handle_method_call(GDBusConnection       *connection,
                               const gchar           *sender,
                               const gchar           *object_path,
                               const gchar           *interface_name,
                               const gchar           *method_name,
                               GVariant              *parameters,
                               GDBusMethodInvocation *invocation,
                               gpointer               user_data) {
    if (g_strcmp0(method_name, "Notify") == 0) {
        const gchar *app_name, *app_icon, *summary, *body;
        guint32 replaces_id;
        gint32 expire_timeout;
        GVariantIter *actions_iter;
        GVariant *hints;

        g_variant_get(parameters, "(&su&s&s&sas@a{sv}i)",
                      &app_name, &replaces_id, &app_icon,
                      &summary, &body, &actions_iter, &hints,
                      &expire_timeout);

        guint32 id = (replaces_id > 0) ? replaces_id : next_id++;
        write_bridge_record(id, app_name, summary, body, expire_timeout);

        g_printerr("pocketshell-notif-bridge: Notify id=%u app=%s summary=%s\n",
                    id, app_name, summary);

        g_variant_iter_free(actions_iter);
        g_variant_unref(hints);

        g_dbus_method_invocation_return_value(invocation,
                                              g_variant_new("(u)", id));

    } else if (g_strcmp0(method_name, "GetCapabilities") == 0) {
        GVariantBuilder builder;
        g_variant_builder_init(&builder, G_VARIANT_TYPE("as"));
        g_variant_builder_add(&builder, "s", "body");
        g_variant_builder_add(&builder, "s", "actions");
        g_dbus_method_invocation_return_value(invocation,
                                              g_variant_new("(as)", &builder));

    } else if (g_strcmp0(method_name, "GetServerInformation") == 0) {
        g_dbus_method_invocation_return_value(invocation,
            g_variant_new("(ssss)",
                          "PocketShell", "PocketShell", "0.1", "1.2"));

    } else if (g_strcmp0(method_name, "CloseNotification") == 0) {
        g_dbus_method_invocation_return_value(invocation, NULL);
    }
}

static const GDBusInterfaceVTable vtable = {
    handle_method_call,
    NULL, /* get_property */
    NULL, /* set_property */
};

static void on_bus_acquired(GDBusConnection *connection,
                            const gchar     *name,
                            gpointer         user_data) {
    GDBusNodeInfo *node_info = g_dbus_node_info_new_for_xml(introspection_xml, NULL);
    g_dbus_connection_register_object(connection,
                                      "/org/freedesktop/Notifications",
                                      node_info->interfaces[0],
                                      &vtable,
                                      NULL, NULL, NULL);
    g_dbus_node_info_unref(node_info);
    g_printerr("pocketshell-notif-bridge: registered on %s\n", name);
}

static void on_name_acquired(GDBusConnection *connection,
                             const gchar     *name,
                             gpointer         user_data) {
    g_printerr("pocketshell-notif-bridge: acquired name %s\n", name);
}

static void on_name_lost(GDBusConnection *connection,
                         const gchar     *name,
                         gpointer         user_data) {
    g_printerr("pocketshell-notif-bridge: lost name %s — exiting\n", name);
    if (loop) g_main_loop_quit(loop);
}

static void handle_signal(int sig) {
    if (loop) g_main_loop_quit(loop);
}

int main(int argc, char *argv[]) {
    signal(SIGTERM, handle_signal);
    signal(SIGINT, handle_signal);

    guint owner_id = g_bus_own_name(G_BUS_TYPE_SESSION,
                                    "org.freedesktop.Notifications",
                                    G_BUS_NAME_OWNER_FLAGS_NONE,
                                    on_bus_acquired,
                                    on_name_acquired,
                                    on_name_lost,
                                    NULL, NULL);

    loop = g_main_loop_new(NULL, FALSE);
    g_main_loop_run(loop);
    g_main_loop_unref(loop);
    g_bus_unown_name(owner_id);
    return 0;
}
