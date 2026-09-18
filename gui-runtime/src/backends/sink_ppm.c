/* mc — headless measurement sink: keeps the newest frame, dumps PPM every N
 * presents, and appends per-present timing to ppm-timing.csv. */
#define _GNU_SOURCE
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include <stdint.h>
#include "../mc.h"

struct ppmsink {
	int w, h;
	uint8_t *own;      /* converted RGB triplets */
	const uint8_t *last;
	int last_stride, last_w, last_h;
	char dir[256];
	int every, seq;
	FILE *csv;
};

static inline uint8_t clamp8(int v) { return v < 0 ? 0 : v > 255 ? 255 : (uint8_t)v; }

static double
now_s(void)
{
	struct timespec ts;
	clock_gettime(CLOCK_MONOTONIC, &ts);
	return ts.tv_sec + ts.tv_nsec / 1e9;
}

static int
ppm_init(mc_sink *s, int *w, int *h)
{
	struct ppmsink *p = s->priv;
	p->w = *w; p->h = *h;
	p->own = malloc((size_t)p->w * p->h * 3);
	char path[300];
	snprintf(path, sizeof(path), "%s/ppm-timing.csv", p->dir);
	p->csv = fopen(path, "w");
	if (p->csv) fprintf(p->csv, "seq,bytes,convert_us\n");
	return 0;
}

static void
ppm_present(mc_sink *s, const uint8_t *pix, int w, int h, int stride)
{
	struct ppmsink *p = s->priv;
	double t0 = now_s();
	int cw = w < p->w ? w : p->w, ch = h < p->h ? h : p->h;
	for (int y = 0; y < ch; y++) {
		const uint8_t *src = pix + (size_t)y * stride;
		uint8_t *dst = p->own + (size_t)y * p->w * 3;
		for (int x = 0; x < cw; x++) {           /* BGRA → RGB */
			dst[x*3+0] = src[x*4+2];
			dst[x*3+1] = src[x*4+1];
			dst[x*3+2] = src[x*4+0];
		}
	}
	double t1 = now_s();
	p->seq++;
	if (p->csv)
		fprintf(p->csv, "%d,%zu,%.1f\n", p->seq,
			(size_t)cw * ch * 4, (t1 - t0) * 1e6);
	if (p->seq % p->every == 0) {
		char path[300];
		snprintf(path, sizeof(path), "%s/frame-%06d.ppm", p->dir, p->seq);
		FILE *f = fopen(path, "wb");
		if (f) {
			fprintf(f, "P6\n%d %d\n255\n", p->w, p->h);
			fwrite(p->own, 1, (size_t)p->w * p->h * 3, f);
			fclose(f);
		}
	}
}

mc_sink *
mc_sink_ppm_create(int width, int height, const char *dir, int every)
{
	mc_sink *s = calloc(1, sizeof(*s));
	struct ppmsink *p = calloc(1, sizeof(*p));
	p->w = width; p->h = height; p->every = every;
	snprintf(p->dir, sizeof(p->dir), "%s", dir);
	s->name = "ppm";
	s->priv = p;
	s->init = ppm_init;
	s->present = ppm_present;
	s->poll_fd = -1;
	return s;
}
