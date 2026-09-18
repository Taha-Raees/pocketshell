/* mc — shared compositor state */
#ifndef MC_H
#define MC_H

#include <stdint.h>
#include <stdbool.h>
#include <wayland-server-core.h>
#include "protocol-wayland-server.h"
#include "protocol-xdg-shell-server.h"
#include "sink.h"

/* One wl_surface the compositor knows about (kiosk v1: exactly one toplevel
 * becomes "active"; popups render as transient overlays). */
struct mc_state;

struct mc_surface {
	struct mc_state *st;
	struct wl_resource *surface;      /* wl_surface resource */
	struct wl_resource *xdg_surface;  /* or NULL */
	struct wl_resource *toplevel;     /* or NULL */
	struct wl_resource *popup;        /* or NULL */
	struct wl_resource *buffer;       /* currently attached wl_buffer */

	/* attached buffer geometry (from the backing shm buffer) */
	int32_t buf_w, buf_h, buf_stride;
	uint32_t buf_format;
	uint8_t *buf_pixels;              /* mapped shm pixels, NULL if none */

	bool role_configured;             /* client acked our configure */
	bool mapped;                      /* has committed a usable buffer */
	bool dirty;
	struct wl_listener buffer_destroy; /* detach watch on attached buffer */

	/* pending frame callbacks (wl_callback resources created by client) */
	struct wl_array frame_callbacks;  /* of struct wl_resource* */

	struct wl_list link;
};

struct mc_state {
	struct wl_display *display;
	struct wl_event_loop *loop;
	struct wl_event_source *tick;

	mc_sink *sink;
	int out_w, out_h;

	uint32_t serial;

	struct mc_surface *active;        /* first mapped toplevel */
	struct wl_list surfaces;          /* struct mc_surface::link */
	uint8_t *stage;                   /* present staging frame (w*h*4) */
	int stage_w, stage_h, stage_stride;

	bool running;
	uint64_t presents, present_ns;    /* sink measurement totals */
};

/* main.c */
uint32_t mc_next_serial(struct mc_state *st);
void mc_buffer_detached(struct mc_state *st, struct wl_resource *buffer);
/* mc_core.c */
void mc_globals_create(struct mc_state *st);
int  mc_present_tick(struct mc_state *st);
int  sink_fd_ready(int fd, uint32_t mask, void *data);
void mc_seat_tick(struct mc_state *st);
int  mc_cli_tick(void *data);

/* shm.c */
bool mc_buffer_info(struct wl_resource *buffer, int32_t *w, int32_t *h,
		    int32_t *stride, uint32_t *format);
bool mc_buffer_read(struct wl_resource *buffer, uint8_t *dst, int dst_stride);

/* shell.c */
bool mc_shell_init(struct mc_state *st);
void mc_shell_reconfigure_all(struct mc_state *st);   /* after sink resize */

/* seat.c */
bool mc_seat_init(struct mc_state *st);
void mc_seat_pointer_motion(struct mc_state *st, int x, int y);
void mc_seat_pointer_button(struct mc_state *st, int evdev_btn, bool down);
void mc_seat_pointer_axis(struct mc_state *st, int steps); /* +down */
void mc_seat_key(struct mc_state *st, uint32_t evdev_code, bool down);
void mc_seat_touch_down(struct mc_state *st, int32_t id, int x, int y);
void mc_seat_touch_motion(struct mc_state *st, int32_t id, int x, int y);
void mc_seat_touch_up(struct mc_state *st, int32_t id);

#endif
