/* mc — shm buffers via libwayland's built-in wl_shm (wl_display_init_shm in
 * main.c already advertises the global and installs the SIGBUS handler).
 * v1 accepts XRGB8888 / ARGB8888. Buffer geometry + pixels are read through
 * wl_shm_buffer_* accessors inside begin/end access windows. */
#define _GNU_SOURCE
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include "mc.h"

bool
mc_buffer_info(struct wl_resource *buffer, int32_t *w, int32_t *h,
	       int32_t *stride, uint32_t *format)
{
	struct wl_shm_buffer *sb = wl_shm_buffer_get(buffer);
	if (!sb)
		return false;
	uint32_t f = wl_shm_buffer_get_format(sb);
	if (f != WL_SHM_FORMAT_XRGB8888 && f != WL_SHM_FORMAT_ARGB8888) {
		fprintf(stderr, "mc: unsupported shm format %08x\n", f);
		return false;
	}
	*w = wl_shm_buffer_get_width(sb);
	*h = wl_shm_buffer_get_height(sb);
	*stride = wl_shm_buffer_get_stride(sb);
	*format = f;
	return true;
}

/* Copy the whole frame out through the SIGBUS-guarded accessor. */
bool
mc_buffer_read(struct wl_resource *buffer, uint8_t *dst, int dst_stride)
{
	struct wl_shm_buffer *sb = wl_shm_buffer_get(buffer);
	if (!sb)
		return false;
	int32_t w = wl_shm_buffer_get_width(sb);
	int32_t h = wl_shm_buffer_get_height(sb);
	int32_t stride = wl_shm_buffer_get_stride(sb);
	const uint8_t *base = wl_shm_buffer_get_data(sb);

	wl_shm_buffer_begin_access(sb);
	for (int y = 0; y < h; y++)
		memcpy(dst + (size_t)y * dst_stride, base + (size_t)y * stride,
		       (size_t)w * 4);
	wl_shm_buffer_end_access(sb);
	return true;
}
