/* mc — minimal Wayland kiosk compositor core.
 *
 * PARALLEL WORK 2 (Agent L(P)) — PocketShell Linux GUI Runtime prototype.
 * Single-active-toplevel kiosk: the first mapped xdg_toplevel fills the sink.
 * Renders client wl_shm buffers (XRGB/ARGB8888) 1:1 top-left into the sink;
 * the sink is the only platform seam (see sink.h).
 */
#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include "mc.h"

/* ---------------- startup ---------------- */

#ifdef MC_ANDROID
/* X11 sink is absent on Android; the EGL/PPM sinks are the targets */
mc_sink *mc_sink_x11_create(int w, int h) { (void)w; (void)h; return NULL; }
#endif

int
main(int argc, char **argv)
{
	struct mc_state st = {0};
	const char *sink_name = "x11";
	const char *socket_name = NULL;
	const char *ppm_dir = ".";
	int ppm_every = 30, win_w = 1024, win_h = 640;

	for (int i = 1; i < argc; i++) {
		if (!strcmp(argv[i], "--sink") && i + 1 < argc) sink_name = argv[++i];
		else if (!strcmp(argv[i], "--size") && i + 1 < argc)
			sscanf(argv[++i], "%dx%d", &win_w, &win_h);
		else if (!strcmp(argv[i], "--socket") && i + 1 < argc) socket_name = argv[++i];
		else if (!strcmp(argv[i], "--ppm-dir") && i + 1 < argc) ppm_dir = argv[++i];
		else if (!strcmp(argv[i], "--ppm-every") && i + 1 < argc) ppm_every = atoi(argv[++i]);
	}

	st.out_w = win_w;
	st.out_h = win_h;
	wl_list_init(&st.surfaces);

	st.display = wl_display_create();
	if (!st.display) { fprintf(stderr, "mc: wl_display_create failed\n"); return 1; }
	st.loop = wl_display_get_event_loop(st.display);

	if (!strcmp(sink_name, "x11")) st.sink = mc_sink_x11_create(win_w, win_h);
	if (!st.sink && !strcmp(sink_name, "x11")) {
		fprintf(stderr, "mc: cannot open X11 display\n");
		return 2;
	}
	else if (!strcmp(sink_name, "ppm")) st.sink = mc_sink_ppm_create(win_w, win_h, ppm_dir, ppm_every);
	else if (!strcmp(sink_name, "egl")) {
		fprintf(stderr, "mc: egl sink requires an ANativeWindow from the Android host\n");
		return 2;
	} else { fprintf(stderr, "mc: unknown sink %s\n", sink_name); return 2; }

	st.sink->host = &st;
	if (st.sink->init(st.sink, &st.out_w, &st.out_h) != 0) {
		fprintf(stderr, "mc: sink %s init failed\n", sink_name);
		return 3;
	}
	fprintf(stderr, "mc: sink %s ready at %dx%d\n", sink_name, st.out_w, st.out_h);

	if (!mc_shell_init(&st) || !mc_seat_init(&st)) return 4;

	mc_globals_create(&st);

	const char *sock = socket_name
		? (wl_display_add_socket(st.display, socket_name) == 0 ? socket_name : NULL)
		: wl_display_add_socket_auto(st.display);
	if (!sock) { fprintf(stderr, "mc: add_socket failed\n"); return 6; }
	fprintf(stderr, "mc: listening on WAYLAND_DISPLAY=%s\n", sock);

	if (st.sink->poll_fd >= 0)
		wl_event_loop_add_fd(st.loop, st.sink->poll_fd, WL_EVENT_READABLE,
				     sink_fd_ready, st.sink);

	st.tick = wl_event_loop_add_timer(st.loop, mc_cli_tick, &st);
	wl_event_source_timer_update(st.tick, 16);

	for (;;) {
		if (wl_event_loop_dispatch(st.loop, -1) != 0) break;
		wl_display_flush_clients(st.display);
	}

	wl_display_destroy(st.display);
	if (getenv("MC_STATS"))
		fprintf(stderr, "mc: presents=%llu present_total=%.3fms avg=%.3fms\n",
			(unsigned long long)st.presents,
			st.present_ns / 1e6,
			st.presents ? st.present_ns / 1e6 / st.presents : 0.0);
	return 0;
}
