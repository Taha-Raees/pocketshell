/* simple-client — dependency-light Wayland test client for mc.
 * Paints an animated gradient checkerboard into a wl_shm buffer, reacts to
 * xdg configure (resize), prints commit latency so mc's present cost can be
 * cross-checked. Build links libwayland-client only. */
#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <time.h>
#include <unistd.h>
#include <sys/mman.h>
#include <sys/syscall.h>
#include <wayland-client.h>
#include "xdg-shell-client-protocol.h"

static struct wl_display *disp;
static struct wl_registry *registry;
static struct wl_compositor *comp;
static struct wl_shm *shm;
static struct wl_seat *seat;
static struct xdg_wm_base *wm;
static struct wl_surface *surf;
static struct xdg_surface *xdg;
static struct xdg_toplevel *toplevel;

static int32_t W = 800, H = 500;   /* our buffer size */
static int configured = 0;
static uint32_t frames = 0;

struct pool { int fd; size_t size; uint8_t *map; struct wl_shm_pool *wl; };

static int
memfd(size_t n)
{
	int fd = (int)syscall(SYS_memfd_create, "simple-client", 0);
	ftruncate(fd, (off_t)n);
	return fd;
}

static uint64_t
now_ns(void)
{
	struct timespec ts;
	clock_gettime(CLOCK_MONOTONIC, &ts);
	return (uint64_t)ts.tv_sec * 1000000000ull + (uint64_t)ts.tv_nsec;
}

static void
draw_frame(uint8_t *pix, int stride, uint32_t t)
{
	for (int y = 0; y < H; y++) {
		uint32_t *row = (uint32_t *)(pix + (size_t)y * stride);
		for (int x = 0; x < W; x++) {
			int chk = ((x >> 5) + (y >> 5)) & 1;
			uint8_t r = (uint8_t)(x * 255 / W);
			uint8_t g = (uint8_t)((y + t) * 255 / H);
			uint8_t b = (uint8_t)(chk ? 200 : 60);
			row[x] = 0xFF000000u | ((uint32_t)r << 16) |
				 ((uint32_t)g << 8) | b;
		}
	}
}

static const struct wl_callback_listener frame_listener;

static void
xdg_surface_listener_handle(void *data, struct xdg_surface *s, uint32_t serial)
{
	(void)data;
	xdg_surface_ack_configure(s, serial);
	configured = 1;
}

static const struct xdg_surface_listener xdg_surf_listener = {
	.configure = xdg_surface_listener_handle,
};

static void
toplevel_close(void *d, struct xdg_toplevel *t) { (void)d; (void)t; exit(0); }
static void
toplevel_configure(void *d, struct xdg_toplevel *t, int32_t w, int32_t h, struct wl_array *states)
{
	(void)d; (void)t; (void)states;
	if (w > 0) W = w;
	if (h > 0) H = h;
}
static const struct xdg_toplevel_listener toplevel_listener = {
	.configure = toplevel_configure,
	.close = toplevel_close,
};

static void
wm_ping(void *d, struct xdg_wm_base *w, uint32_t serial)
{ (void)d; xdg_wm_base_pong(w, serial); }

static const struct xdg_wm_base_listener wm_listener = { .ping = wm_ping };

static void
registry_global(void *data, struct wl_registry *r, uint32_t name,
		const char *iface, uint32_t version)
{
	(void)data; (void)version;
	if (!strcmp(iface, wl_compositor_interface.name))
		comp = wl_registry_bind(r, name, &wl_compositor_interface, 4);
	else if (!strcmp(iface, wl_shm_interface.name))
		shm = wl_registry_bind(r, name, &wl_shm_interface, 1);
	else if (!strcmp(iface, wl_seat_interface.name))
		seat = wl_registry_bind(r, name, &wl_seat_interface, 5);
	else if (!strcmp(iface, xdg_wm_base_interface.name))
		wm = wl_registry_bind(r, name, &xdg_wm_base_interface, 1);
}

static void
registry_remove(void *d, struct wl_registry *r, uint32_t name) { (void)d; (void)r; (void)name; }

static const struct wl_registry_listener reg_listener = {
	.global = registry_global,
	.global_remove = registry_remove,
};

static void
frame_done(void *data, struct wl_callback *cb, uint32_t msec)
{
	(void)data; (void)msec;
	wl_callback_destroy(cb);
	if (!configured) return;

	/* draw + attach + commit with latency print every 60 frames */
	static struct pool p = {0};
	static struct wl_buffer *buf;
	static int bw = -1, bh = -1;
	if (bw != W || bh != H || !buf) {
		if (buf) wl_buffer_destroy(buf);
		if (p.map) munmap(p.map, p.size);
		bw = W; bh = H;
		p.size = (size_t)W * H * 4;
		p.fd = memfd(p.size);
		p.map = mmap(NULL, p.size, PROT_READ | PROT_WRITE, MAP_SHARED, p.fd, 0);
		p.wl = wl_shm_create_pool(shm, p.fd, (int32_t)p.size);
		buf = wl_shm_pool_create_buffer(p.wl, 0, W, H, W * 4,
						WL_SHM_FORMAT_XRGB8888);
	}
	uint64_t t0 = now_ns();
	draw_frame(p.map, W * 4, (uint32_t)(t0 / 1000000ull));
	wl_surface_attach(surf, buf, 0, 0);
	wl_surface_damage(surf, 0, 0, W, H);
	wl_surface_commit(surf);
	if (++frames % 60 == 0)
		printf("client: frame %u draw+commit=%.3fms (%dx%d)\n",
		       frames, (now_ns() - t0) / 1e6, W, H);

	struct wl_callback *nxt = wl_surface_frame(surf);
	wl_callback_add_listener(nxt, &frame_listener, NULL);
	wl_surface_commit(surf); /* arm the frame callback */
}

static const struct wl_callback_listener frame_listener = { .done = frame_done };

int
main(int argc, char **argv)
{
	if (argc > 1) setenv("WAYLAND_DISPLAY", argv[1], 0);
	disp = wl_display_connect(NULL);
	if (!disp) { fprintf(stderr, "client: no display (XDG_RUNTIME_DIR=%s WAYLAND_DISPLAY=%s)\n", getenv("XDG_RUNTIME_DIR"), getenv("WAYLAND_DISPLAY")); return 1; }
	registry = wl_display_get_registry(disp);
	wl_registry_add_listener(registry, &reg_listener, NULL);
	wl_display_roundtrip(disp);
	if (!comp || !shm || !wm) { fprintf(stderr, "client: missing globals\n"); return 2; }

	xdg_wm_base_add_listener(wm, &wm_listener, NULL);
	surf = wl_compositor_create_surface(comp);
	xdg = xdg_wm_base_get_xdg_surface(wm, surf);
	xdg_surface_add_listener(xdg, &xdg_surf_listener, NULL);
	toplevel = xdg_surface_get_toplevel(xdg);
	xdg_toplevel_add_listener(toplevel, &toplevel_listener, NULL);
	xdg_toplevel_set_title(toplevel, "simple-client");
	wl_surface_commit(surf);

	struct wl_callback *cb = wl_surface_frame(surf);
	wl_callback_add_listener(cb, &frame_listener, NULL);
	wl_surface_commit(surf);

	while (wl_display_dispatch(disp) >= 0) {
	}
	return 0;
}
