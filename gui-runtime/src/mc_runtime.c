/* mc_runtime — in-process compositor service (Android/JNI embedding).
 *
 * The event loop runs on the caller's thread (JNI starts a pthread). UI
 * threads reach it through a pipe command queue: every command is executed on
 * the loop thread, so no libwayland locking is needed. The EGL surface is
 * attached/detached at runtime via sink_egl's runtime entries.
 */
#define _GNU_SOURCE
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <unistd.h>
#include <errno.h>
#include <android/native_window.h>
#include "mc.h"
#include "mc_runtime.h"

enum cmd_type {
	CMD_SURFACE = 1,   /* ptr = ANativeWindow* (attach) or NULL (detach) */
	CMD_STOP,
	CMD_KEY,           /* a=evdev code, b=down */
	CMD_PTR_MOTION,    /* a=x b=y */
	CMD_PTR_BUTTON,    /* a=btn b=down */
	CMD_PTR_SCROLL,    /* a=steps */
	CMD_TOUCH,         /* a=action b=id c=x d=y */
};

struct mc_command {
	int32_t type;
	int32_t a, b, c, d;
	void *ptr;
};

static struct mc_runtime {
	int cmd_w;                 /* write end, UI threads */
	struct mc_state *st;       /* loop-thread owned */
} rt;

static int queue_push(const struct mc_command *c)
{
	/* commands are <= PIPE_BUF: whole-write is atomic on Linux pipes */
	return write(rt.cmd_w, c, sizeof(*c)) == (ssize_t)sizeof(*c) ? 0 : -1;
}

static void
handle_command(struct mc_state *st, const struct mc_command *c)
{
	switch (c->type) {
	case CMD_SURFACE:
		if (!st->sink)
			break;
		if (c->ptr) {
			mc_sink_egl_runtime_attach(st->sink, (ANativeWindow *)c->ptr,
						   c->a, c->b);
			st->out_w = c->a;
			st->out_h = c->b;
			mc_shell_reconfigure_all(st);
		} else {
			mc_sink_egl_runtime_detach(st->sink);
		}
		break;
	case CMD_STOP:
		st->running = 1;
		break;
	case CMD_KEY:
		mc_seat_key(st, (uint32_t)c->a, c->b != 0);
		break;
	case CMD_PTR_MOTION:
		mc_seat_pointer_motion(st, c->a, c->b);
		break;
	case CMD_PTR_BUTTON:
		mc_seat_pointer_button(st, c->a, c->b != 0);
		break;
	case CMD_PTR_SCROLL:
		mc_seat_pointer_axis(st, c->a);
		break;
	case CMD_TOUCH:
		switch (c->a) {
		case 0: mc_seat_touch_down(st, c->b, c->c, c->d); break;
		case 1: mc_seat_touch_motion(st, c->b, c->c, c->d); break;
		case 2: mc_seat_touch_up(st, c->b); break;
		}
		break;
	default:
		break;
	}
}

static int
cmd_readable(int fd, uint32_t mask, void *data)
{
	struct mc_state *st = data;
	if (!(mask & WL_EVENT_READABLE))
		return 0;
	struct mc_command c;
	for (;;) {
		ssize_t n = read(fd, &c, sizeof(c));
		if (n == (ssize_t)sizeof(c)) {
			handle_command(st, &c);
			continue;
		}
		break; /* EAGAIN / partial / EOF: handled next wakeup */
	}
	return 0;
}

static int
runtime_tick(void *data)
{
	struct mc_state *st = data;
	mc_seat_tick(st);
	mc_present_tick(st);
	wl_event_source_timer_update(st->tick, 16);
	return 0;
}

int
mc_runtime_start(const struct mc_runtime_config *cfg)
{
	if (rt.st)
		return -EALREADY;

	int fds[2];
	if (pipe2(fds, O_NONBLOCK | O_CLOEXEC) != 0)
		return -errno;
	rt.cmd_w = fds[1];

	struct mc_state *st = calloc(1, sizeof(*st));
	if (!st)
		return -ENOMEM;
	st->out_w = cfg->width > 0 ? cfg->width : 1280;
	st->out_h = cfg->height > 0 ? cfg->height : 800;
	wl_list_init(&st->surfaces);

	st->display = wl_display_create();
	if (!st->display) {
		free(st);
		return -ENODEV;
	}
	st->loop = wl_display_get_event_loop(st->display);
	st->sink = mc_sink_egl_create(NULL); /* window arrives via attach */

	if (cfg->xkb_root && cfg->xkb_root[0])
		setenv("XKB_CONFIG_ROOT", cfg->xkb_root, 1);

	if (st->sink->init(st->sink, &st->out_w, &st->out_h) != 0) {
		fprintf(stderr, "mc: egl sink init failed\n");
		return -3;
	}
	if (!mc_shell_init(st) || !mc_seat_init(st))
		return -4;

	mc_globals_create(st);

	unlink(cfg->socket_path);
	if (wl_display_add_socket(st->display, cfg->socket_path) != 0) {
		fprintf(stderr, "mc: add_socket(%s) failed\n", cfg->socket_path);
		return -6;
	}
	fprintf(stderr, "mc: listening on %s\n", cfg->socket_path);

	wl_event_loop_add_fd(st->loop, fds[0], WL_EVENT_READABLE,
			     cmd_readable, st);
	st->tick = wl_event_loop_add_timer(st->loop, runtime_tick, st);
	wl_event_source_timer_update(st->tick, 16);

	rt.st = st;
	while (!st->running) {
		if (wl_event_loop_dispatch(st->loop, -1) != 0)
			break;
		wl_display_flush_clients(st->display);
	}

	wl_display_destroy(st->display);
	if (getenv("MC_STATS"))
		fprintf(stderr, "mc: presents=%llu avg=%.3fms\n",
			(unsigned long long)st->presents,
			st->presents ? st->present_ns / 1e6 / st->presents : 0.0);
	rt.st = NULL;
	free(st);
	close(fds[0]);
	close(rt.cmd_w);
	rt.cmd_w = -1;
	return 0;
}

/* ---- UI-thread entries ---- */

void
mc_runtime_command_surface(ANativeWindow *win, int w, int h)
{
	struct mc_command c = { .type = CMD_SURFACE, .ptr = win, .a = w, .b = h };
	queue_push(&c);
}

void
mc_runtime_detach_surface(void)
{
	struct mc_command c = { .type = CMD_SURFACE, .ptr = NULL };
	queue_push(&c);
}

void
mc_runtime_stop(void)
{
	struct mc_command c = { .type = CMD_STOP };
	if (rt.cmd_w >= 0)
		queue_push(&c);
}

void
mc_runtime_key(uint32_t evdev_code, int down)
{
	struct mc_command c = { .type = CMD_KEY, .a = (int32_t)evdev_code,
				.b = down };
	queue_push(&c);
}

void
mc_runtime_pointer_motion(int x, int y)
{
	struct mc_command c = { .type = CMD_PTR_MOTION, .a = x, .b = y };
	queue_push(&c);
}

void
mc_runtime_pointer_button(int evdev_btn, int down)
{
	struct mc_command c = { .type = CMD_PTR_BUTTON, .a = evdev_btn,
				.b = down };
	queue_push(&c);
}

void
mc_runtime_pointer_scroll(int steps)
{
	struct mc_command c = { .type = CMD_PTR_SCROLL, .a = steps };
	queue_push(&c);
}

void
mc_runtime_touch(int action, int id, int x, int y)
{
	struct mc_command c = { .type = CMD_TOUCH, .a = action, .b = id,
				.c = x, .d = y };
	queue_push(&c);
}
