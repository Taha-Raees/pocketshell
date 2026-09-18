/* mc — wl_seat: keyboard (xkbcommon keymap shipped via shm fd), pointer, touch.
 * Host input arrives through the mc_seat_* feeders (X11 sink on the laptop,
 * JNI on Android: Android KeyEvents map to Linux evdev codes at the bridge). */
#define _GNU_SOURCE
#include <sys/syscall.h>
#include <sys/mman.h>
#include <unistd.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <time.h>
#include <xkbcommon/xkbcommon.h>
#include "mc.h"

#ifndef SYS_memfd_create
#define SYS_memfd_create 319
#endif

#define SEAT_VERSION 5
#define CAPS (WL_SEAT_CAPABILITY_KEYBOARD | WL_SEAT_CAPABILITY_POINTER | WL_SEAT_CAPABILITY_TOUCH)

struct mc_seat {
	struct mc_state *st;
	struct xkb_context *xkb_ctx;
	struct xkb_keymap *xkb_map;
	struct xkb_state *xkb_st;
	int keymap_fd;
	size_t keymap_size;

	struct wl_resource *seat_global;      /* per-client seat resource */
	struct wl_resource *keyboard;         /* may be NULL */
	struct wl_resource *pointer;
	struct wl_resource *touch;

	struct mc_surface *kb_focus;
	struct mc_surface *ptr_focus;
	uint32_t last_key_time;
};

static uint32_t
now_ms(void)
{
	struct timespec ts;
	clock_gettime(CLOCK_MONOTONIC, &ts);
	return (uint32_t)(ts.tv_sec * 1000 + ts.tv_nsec / 1000000);
}

static int
make_memfd(const char *name, size_t size)
{
	int fd = (int)syscall(SYS_memfd_create, name, 0);
	if (fd < 0) return -1;
	if (ftruncate(fd, (off_t)size) != 0) { close(fd); return -1; }
	return fd;
}

/* ---- keyboard ---- */

static void
keyboard_release_req(struct wl_client *c, struct wl_resource *r)
{ (void)c; wl_resource_destroy(r); }

static const struct wl_keyboard_interface keyboard_impl = {
	.release = keyboard_release_req,
};

static void
keyboard_resource_destroy(struct wl_resource *r)
{
	struct mc_seat *seat = wl_resource_get_user_data(r);
	if (seat) seat->keyboard = NULL;
}

static bool
keyboard_setup(struct mc_seat *seat)
{
	struct xkb_rule_names names = { .rules = "evdev", .model = "pc105", .layout = "us" };
	seat->xkb_ctx = xkb_context_new(XKB_CONTEXT_NO_FLAGS);
	if (!seat->xkb_ctx) return false;
	seat->xkb_map = xkb_keymap_new_from_names(seat->xkb_ctx, &names,
						  XKB_KEYMAP_COMPILE_NO_FLAGS);
	if (!seat->xkb_map) return false;
	seat->xkb_st = xkb_state_new(seat->xkb_map);

	const char *str = xkb_keymap_get_as_string(seat->xkb_map,
						   XKB_KEYMAP_FORMAT_TEXT_V1);
	seat->keymap_size = strlen(str) + 1;
	seat->keymap_fd = make_memfd("mc-keymap", seat->keymap_size);
	if (seat->keymap_fd < 0) return false;
	void *m = mmap(NULL, seat->keymap_size, PROT_READ | PROT_WRITE,
		       MAP_SHARED, seat->keymap_fd, 0);
	if (m == MAP_FAILED) return false;
	memcpy(m, str, seat->keymap_size);
	munmap(m, seat->keymap_size);
	return true;
}

/* ---- pointer / touch ---- */

static void
pointer_release_req(struct wl_client *c, struct wl_resource *r)
{ (void)c; wl_resource_destroy(r); }

static const struct wl_pointer_interface pointer_impl = {
	.set_cursor = NULL, /* set below via copy? wl_pointer is const — see init */
	.release = pointer_release_req,
};

static void
touch_release_req(struct wl_client *c, struct wl_resource *r)
{ (void)c; wl_resource_destroy(r); }

static const struct wl_touch_interface touch_impl = {
	.release = touch_release_req,
};

/* wl_pointer_interface is const; our copy adds set_cursor */
static struct wl_pointer_interface mc_pointer_impl;

static void
pointer_set_cursor(struct wl_client *c, struct wl_resource *r, uint32_t serial,
		   struct wl_resource *surface, int32_t hx, int32_t hy)
{ (void)c; (void)r; (void)serial; (void)surface; (void)hx; (void)hy; /* no cursor rendering in kiosk v1 */ }

/* ---- seat ---- */

static void
seat_get_keyboard(struct wl_client *client, struct wl_resource *res, uint32_t id)
{
	struct mc_seat *seat = wl_resource_get_user_data(res);
	seat->keyboard = wl_resource_create(client, &wl_keyboard_interface,
					    wl_resource_get_version(res), id);
	wl_resource_set_implementation(seat->keyboard, &keyboard_impl, seat,
				       keyboard_resource_destroy);
	wl_keyboard_send_keymap(seat->keyboard, WL_KEYBOARD_KEYMAP_FORMAT_XKB_V1,
				seat->keymap_fd, (int32_t)seat->keymap_size);
	wl_keyboard_send_repeat_info(seat->keyboard, 30, 250);
	/* focus is delivered on the next tick when a surface is active */
}

static void
seat_get_pointer(struct wl_client *client, struct wl_resource *res, uint32_t id)
{
	struct mc_seat *seat = wl_resource_get_user_data(res);
	seat->pointer = wl_resource_create(client, &wl_pointer_interface,
					   wl_resource_get_version(res), id);
	wl_resource_set_implementation(seat->pointer, &mc_pointer_impl, seat, NULL);
}

static void
seat_get_touch(struct wl_client *client, struct wl_resource *res, uint32_t id)
{
	struct mc_seat *seat = wl_resource_get_user_data(res);
	seat->touch = wl_resource_create(client, &wl_touch_interface,
					 wl_resource_get_version(res), id);
	wl_resource_set_implementation(seat->touch, &touch_impl, seat, NULL);
}

static void
seat_release_req(struct wl_client *c, struct wl_resource *r)
{ (void)c; wl_resource_destroy(r); }

static const struct wl_seat_interface seat_impl = {
	.get_keyboard = seat_get_keyboard,
	.get_pointer = seat_get_pointer,
	.get_touch = seat_get_touch,
	.release = seat_release_req,
};

static void
seat_bind(struct wl_client *client, void *data, uint32_t version, uint32_t id)
{
	struct mc_seat *seat = data;
	if (version > SEAT_VERSION) version = SEAT_VERSION;
	seat->seat_global = wl_resource_create(client, &wl_seat_interface, version, id);
	wl_resource_set_implementation(seat->seat_global, &seat_impl, seat, NULL);
	wl_seat_send_capabilities(seat->seat_global, CAPS);
	if (version >= WL_SEAT_NAME_SINCE_VERSION)
		wl_seat_send_name(seat->seat_global, "mc-kiosk");
}

/* ---- focus + input feeds (called from sink / future JNI bridge) ---- */

static void
send_enter_for(struct mc_seat *seat, struct mc_surface *s)
{
	if (!seat->keyboard || !s || !s->surface) return;
	struct wl_array keys;
	wl_array_init(&keys);
	wl_keyboard_send_enter(seat->keyboard, mc_next_serial(seat->st), s->surface, &keys);
	wl_array_release(&keys);
	/* push current modifier state */
	xkb_state_update_mask(seat->xkb_st, 0, 0, 0, 0, 0, 0);
	wl_keyboard_send_modifiers(seat->keyboard, mc_next_serial(seat->st),
				   xkb_state_serialize_mods(seat->xkb_st, XKB_STATE_MODS_DEPRESSED),
				   xkb_state_serialize_mods(seat->xkb_st, XKB_STATE_MODS_LOCKED),
				   xkb_state_serialize_mods(seat->xkb_st, XKB_STATE_MODS_LATCHED),
				   xkb_state_serialize_group(seat->xkb_st, XKB_STATE_LAYOUT_EFFECTIVE));
	if (seat->pointer && s->surface) {
		wl_pointer_send_enter(seat->pointer, mc_next_serial(seat->st),
				      s->surface, 0, 0);
	}
}

/* called from the present tick: maintain focus */
void
mc_seat_tick(struct mc_state *st)
{
	struct mc_seat *seat = st->sink->priv_seat;
	if (!seat) return;
	if (seat->kb_focus != st->active && st->active) {
		seat->kb_focus = st->active;
		send_enter_for(seat, st->active);
	}
}

void
mc_seat_key(struct mc_state *st, uint32_t evdev_code, bool down)
{
	struct mc_seat *seat = st->sink->priv_seat;
	if (!seat || !seat->keyboard || !seat->kb_focus) return;
	uint32_t t = now_ms();
	xkb_state_update_key(seat->xkb_st, evdev_code + 8,
			     down ? XKB_KEY_DOWN : XKB_KEY_UP);
	wl_keyboard_send_key(seat->keyboard, mc_next_serial(st), t,
			     evdev_code, down ? WL_KEYBOARD_KEY_STATE_PRESSED
					      : WL_KEYBOARD_KEY_STATE_RELEASED);
	wl_keyboard_send_modifiers(seat->keyboard, mc_next_serial(st),
				   xkb_state_serialize_mods(seat->xkb_st, XKB_STATE_MODS_DEPRESSED),
				   xkb_state_serialize_mods(seat->xkb_st, XKB_STATE_MODS_LOCKED),
				   xkb_state_serialize_mods(seat->xkb_st, XKB_STATE_MODS_LATCHED),
				   xkb_state_serialize_group(seat->xkb_st, XKB_STATE_LAYOUT_EFFECTIVE));
	wl_client_flush(wl_resource_get_client(seat->keyboard));
}

void
mc_seat_pointer_motion(struct mc_state *st, int x, int y)
{
	struct mc_seat *seat = st->sink->priv_seat;
	if (!seat || !seat->pointer) return;
	if (!seat->ptr_focus && st->active) {
		seat->ptr_focus = st->active;
	}
	if (!seat->ptr_focus) return;
	wl_pointer_send_motion(seat->pointer, now_ms(), wl_fixed_from_int(x),
			       wl_fixed_from_int(y));
	wl_pointer_send_frame(seat->pointer);
	wl_client_flush(wl_resource_get_client(seat->pointer));
}

void
mc_seat_pointer_button(struct mc_state *st, int evdev_btn, bool down)
{
	struct mc_seat *seat = st->sink->priv_seat;
	if (!seat || !seat->pointer || !seat->ptr_focus) return;
	wl_pointer_send_button(seat->pointer, mc_next_serial(st), now_ms(),
			       (uint32_t)evdev_btn,
			       down ? WL_POINTER_BUTTON_STATE_PRESSED
				    : WL_POINTER_BUTTON_STATE_RELEASED);
	wl_pointer_send_frame(seat->pointer);
	wl_client_flush(wl_resource_get_client(seat->pointer));
}

void
mc_seat_pointer_axis(struct mc_state *st, int steps)
{
	struct mc_seat *seat = st->sink->priv_seat;
	if (!seat || !seat->pointer || !seat->ptr_focus || steps == 0) return;
	uint32_t axis = WL_POINTER_AXIS_VERTICAL_SCROLL;
	double per = 10.0 * steps;
	wl_pointer_send_axis(seat->pointer, now_ms(), axis, wl_fixed_from_double(per));
	if (wl_resource_get_version(seat->pointer) >= WL_POINTER_AXIS_VALUE120_SINCE_VERSION) {
		wl_pointer_send_axis_value120(seat->pointer, axis, 120 * steps);
	}
	wl_pointer_send_frame(seat->pointer);
	wl_client_flush(wl_resource_get_client(seat->pointer));
}

/* ---- touch (Android bridge uses these) ---- */
void
mc_seat_touch_down(struct mc_state *st, int32_t id, int x, int y)
{
	struct mc_seat *seat = st->sink->priv_seat;
	if (!seat || !seat->touch || !st->active) return;
	wl_touch_send_down(seat->touch, mc_next_serial(st), now_ms(),
			   st->active->surface, id, wl_fixed_from_int(x), wl_fixed_from_int(y));
	wl_client_flush(wl_resource_get_client(seat->touch));
}

void
mc_seat_touch_motion(struct mc_state *st, int32_t id, int x, int y)
{
	struct mc_seat *seat = st->sink->priv_seat;
	if (!seat || !seat->touch) return;
	wl_touch_send_motion(seat->touch, now_ms(), id,
			     wl_fixed_from_int(x), wl_fixed_from_int(y));
	wl_client_flush(wl_resource_get_client(seat->touch));
}

void
mc_seat_touch_up(struct mc_state *st, int32_t id)
{
	struct mc_seat *seat = st->sink->priv_seat;
	if (!seat || !seat->touch) return;
	wl_touch_send_up(seat->touch, mc_next_serial(st), now_ms(), id);
	wl_client_flush(wl_resource_get_client(seat->touch));
}

bool
mc_seat_init(struct mc_state *st)
{
	struct mc_seat *seat = calloc(1, sizeof(*seat));
	seat->st = st;
	if (!keyboard_setup(seat)) {
		fprintf(stderr, "mc: xkbcommon keymap setup failed (need xkeyboard-config data)\n");
		return false;
	}
	mc_pointer_impl.set_cursor = pointer_set_cursor;
	mc_pointer_impl.release = pointer_release_req;
	st->sink->priv_seat = seat;
	wl_global_create(st->display, &wl_seat_interface, SEAT_VERSION, seat, seat_bind);
	return true;
}
