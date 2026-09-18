/* mc — sink vtable: the only platform seam in the compositor.
 *
 * The compositor core is protocol + buffer + input logic. A sink owns pixels
 * presentation and (on the laptop) input capture. On Android the sink is
 * EGL/GLES onto an ANativeWindow; input is fed from the Kotlin/JNI side.
 *
 * Pixel contract: 32bpp, little-endian byte order B,G,R,pad per pixel
 * (exactly wl_shm XRGB8888/ARGB8888 memory layout; X11 ZPixmap BGRX matches).
 */
#ifndef MC_SINK_H
#define MC_SINK_H

#include <stdint.h>

typedef struct mc_sink mc_sink;

struct mc_sink {
	const char *name;

	/* Bring the surface up; return 0 and set the drawing size via w and h. */
	int (*init)(mc_sink *s, int *w, int *h);
	void (*deinit)(mc_sink *s);

	/* Present one frame. The core has already copied client shm into
	 * `pix` (packed stride==w*4 is NOT guaranteed — respect stride). */
	void (*present)(mc_sink *s, const uint8_t *pix, int w, int h, int stride);

	/* Poll sink-side OS events (X11 input on the laptop); -1 if none. */
	int poll_fd;
	void (*pump)(mc_sink *s);

	/* Optional resize notification the core can call (e.g. Android surface
	 * size change); sinks resize their backing store and update w and h. */
	void (*resize)(mc_sink *s, int *w, int *h);

	void *priv;
	void *host;        /* mc_state* — set by main.c right after creation */
	void *priv_seat;   /* set by seat.c (input feed target lookup) */
};

/* Laptop rehearsal sinks */
mc_sink *mc_sink_x11_create(int width, int height);
mc_sink *mc_sink_ppm_create(int width, int height, const char *outdir, int every);
/* Android sink: ANativeWindow* comes from JNI (ANativeWindow_fromSurface). */
mc_sink *mc_sink_egl_create(void *anativewindow);

#endif
