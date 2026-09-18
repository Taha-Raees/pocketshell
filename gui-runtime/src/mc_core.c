/* mc_core — compositor globals, surface impl, present tick (shared by the
 * CLI main and the Android runtime service). */
#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include "mc.h"
uint32_t mc_next_serial(struct mc_state *st) { return ++st->serial; }

static void
buffer_destroy_signal(struct wl_listener *l, void *data)
{
	struct mc_surface *s = wl_container_of(l, s, buffer_destroy);
	struct wl_resource *buffer = data;
	if (s->buffer == buffer)
		s->buffer = NULL;
}

void
mc_buffer_detached(struct mc_state *st, struct wl_resource *buffer)
{
	struct mc_surface *s;
	wl_list_for_each(s, &st->surfaces, link)
		if (s->buffer == buffer)
			s->buffer = NULL;
}

/* ---------------- wl_surface ---------------- */

static void
surface_destroy(struct wl_client *client, struct wl_resource *res)
{
	(void)client;
	wl_resource_destroy(res);
}

static void
surface_attach(struct wl_client *client, struct wl_resource *res,
	       struct wl_resource *buffer, int32_t x, int32_t y)
{
	(void)client;
	struct mc_surface *s = wl_resource_get_user_data(res);
	if (x != 0 || y != 0) {
		wl_resource_post_error(res, WL_DISPLAY_ERROR_INVALID_OBJECT,
				       "attach offset != 0 unsupported");
		return;
	}
	if (s->buffer && !wl_list_empty(&s->buffer_destroy.link))
		wl_list_remove(&s->buffer_destroy.link); /* detach old watch */
	s->buffer = buffer;
	if (buffer) {
		s->buffer_destroy.notify = buffer_destroy_signal;
		wl_resource_add_destroy_listener(buffer, &s->buffer_destroy);
		mc_buffer_info(buffer, &s->buf_w, &s->buf_h, &s->buf_stride,
			       &s->buf_format);
	} else {
		s->mapped = false;
	}
}

static void
surface_damage(struct wl_client *c, struct wl_resource *res,
	       int32_t x, int32_t y, int32_t w, int32_t h)
{
	(void)c; (void)x; (void)y; (void)w; (void)h;
	((struct mc_surface *)wl_resource_get_user_data(res))->dirty = true;
}

static void
surface_frame(struct wl_client *client, struct wl_resource *res, uint32_t callback)
{
	struct mc_surface *s = wl_resource_get_user_data(res);
	struct wl_resource *cb =
		wl_resource_create(client, &wl_callback_interface, 1, callback);
	struct wl_resource **slot = wl_array_add(&s->frame_callbacks, sizeof(*slot));
	*slot = cb;
}

static void
surface_commit(struct wl_client *client, struct wl_resource *res)
{
	(void)client;
	struct mc_surface *s = wl_resource_get_user_data(res);
	struct mc_state *st = s->st;

	if (s->buffer && s->role_configured)
		s->mapped = true;
	if (s->mapped)
		s->dirty = true;
	if (st->active == NULL && s->mapped && s->toplevel) {
		st->active = s;
		fprintf(stderr, "mc: active surface mapped (%dx%d stride %d fmt %08x)\n",
			s->buf_w, s->buf_h, s->buf_stride, s->buf_format);
	}
}

static void
surface_damage_buffer(struct wl_client *c, struct wl_resource *res,
		      int32_t x, int32_t y, int32_t w, int32_t h)
{
	(void)c; (void)x; (void)y; (void)w; (void)h;
	((struct mc_surface *)wl_resource_get_user_data(res))->dirty = true;
}

static void
surface_set_opaque_region(struct wl_client *c, struct wl_resource *res, struct wl_resource *region) { (void)c; (void)res; (void)region; }
static void
surface_set_input_region(struct wl_client *c, struct wl_resource *res, struct wl_resource *region) { (void)c; (void)res; (void)region; }
static void
surface_set_buffer_transform(struct wl_client *c, struct wl_resource *res, int32_t t) { (void)c; (void)res; (void)t; }
static void
surface_set_buffer_scale(struct wl_client *c, struct wl_resource *res, int32_t sc) { (void)c; (void)res; (void)sc; }
static void
surface_offset(struct wl_client *c, struct wl_resource *res, int32_t x, int32_t y) { (void)c; (void)res; (void)x; (void)y; }

static void
surface_resource_destroy(struct wl_resource *res)
{
	struct mc_surface *s = wl_resource_get_user_data(res);
	struct mc_state *st = s->st;
	if (st->active == s)
		st->active = NULL;
	wl_array_release(&s->frame_callbacks);
	wl_list_remove(&s->link);
	free(s);
}

static const struct wl_surface_interface surface_impl = {
	.destroy = surface_destroy,
	.attach = surface_attach,
	.damage = surface_damage,
	.frame = surface_frame,
	.set_opaque_region = surface_set_opaque_region,
	.set_input_region = surface_set_input_region,
	.commit = surface_commit,
	.set_buffer_transform = surface_set_buffer_transform,
	.set_buffer_scale = surface_set_buffer_scale,
	.damage_buffer = surface_damage_buffer,
	.offset = surface_offset,
};

/* ---------------- globals: compositor, output ---------------- */

struct wl_compositor_interface mc_compositor_impl;

void
compositor_bind(struct wl_client *client, void *data, uint32_t version, uint32_t id)
{
	struct mc_state *st = data;
	struct wl_resource *r = wl_resource_create(client, &wl_compositor_interface,
						   version < 4 ? version : 4, id);
	wl_resource_set_implementation(r, &mc_compositor_impl, st, NULL);
}

static void
compositor_create_surface(struct wl_client *client, struct wl_resource *res, uint32_t id)
{
	struct mc_state *st = wl_resource_get_user_data(res);
	struct mc_surface *s = calloc(1, sizeof(*s));
	s->st = st;
	s->surface = wl_resource_create(client, &wl_surface_interface,
					wl_resource_get_version(res), id);
	wl_array_init(&s->frame_callbacks);
	wl_list_insert(st->surfaces.prev, &s->link);
	wl_resource_set_implementation(s->surface, &surface_impl, s,
				       surface_resource_destroy);
}

/* Region requests are accepted and ignored (kiosk renders fullscreen). */
static void
region_destroy_req(struct wl_client *c, struct wl_resource *r) { (void)c; wl_resource_destroy(r); }
static void
region_add(struct wl_client *c, struct wl_resource *r, int32_t x, int32_t y, int32_t w, int32_t h) { (void)c; (void)r; (void)x; (void)y; (void)w; (void)h; }
static void
region_subtract(struct wl_client *c, struct wl_resource *r, int32_t x, int32_t y, int32_t w, int32_t h) { (void)c; (void)r; (void)x; (void)y; (void)w; (void)h; }

static void
compositor_create_region(struct wl_client *client, struct wl_resource *res, uint32_t id)
{
	static const struct wl_region_interface region_impl = {
		.destroy = region_destroy_req, .add = region_add, .subtract = region_subtract,
	};
	struct wl_resource *r = wl_resource_create(client, &wl_region_interface,
						   wl_resource_get_version(res), id);
	wl_resource_set_implementation(r, &region_impl, NULL, NULL);
}

static void
output_bind(struct wl_client *client, void *data, uint32_t version, uint32_t id)
{
	struct mc_state *st = data;
	struct wl_resource *r = wl_resource_create(client, &wl_output_interface,
						   version < 3 ? version : 3, id);
	wl_resource_set_implementation(r, NULL, NULL, NULL);
	wl_output_send_geometry(r, 0, 0, 200, 130, WL_OUTPUT_SUBPIXEL_UNKNOWN,
				"mc", "kiosk", WL_OUTPUT_TRANSFORM_NORMAL);
	wl_output_send_mode(r, WL_OUTPUT_MODE_CURRENT | WL_OUTPUT_MODE_PREFERRED,
			    st->out_w, st->out_h, 60000);
	wl_output_send_scale(r, 1);
	wl_output_send_done(r);
}

/* ---------------- present + frame loop ---------------- */

static void
fire_frame_callbacks(struct mc_state *st, struct mc_surface *s, uint32_t serial)
{
	struct wl_resource **cb;
	wl_array_for_each(cb, &s->frame_callbacks) {
		struct wl_resource *r = *cb;
		wl_callback_send_done(r, serial);
		wl_resource_destroy(r);
	}
	wl_array_release(&s->frame_callbacks);
	wl_array_init(&s->frame_callbacks);
	(void)st;
}

int
mc_present_tick(struct mc_state *st)
{
	struct mc_surface *s = st->active;
	uint32_t serial = mc_next_serial(st);

	/* frame callbacks fire once per output frame regardless of map state —
	 * clients (incl. ours) arm them before the first buffer. */
	struct mc_surface *it;
	wl_list_for_each(it, &st->surfaces, link) {
		if (it->frame_callbacks.size > 0)
			fire_frame_callbacks(st, it, serial);
	}

	if (s && s->dirty && s->buffer) {
		struct timespec t0, t1;
		size_t need = (size_t)s->buf_stride * s->buf_h;
		if (!st->stage || st->stage_stride < s->buf_stride ||
		    (int)need > (int)(st->stage_stride * s->buf_h)) {
			free(st->stage);
			st->stage = malloc(need ? need : 1);
			st->stage_stride = s->buf_stride;
		}
		st->stage_w = s->buf_w;
		st->stage_h = s->buf_h;
		if (!mc_buffer_read(s->buffer, st->stage, s->buf_stride))
			return 1;
		clock_gettime(CLOCK_MONOTONIC, &t0);
		st->sink->present(st->sink, st->stage, st->stage_w, st->stage_h,
				  st->stage_stride);
		clock_gettime(CLOCK_MONOTONIC, &t1);
		st->presents++;
		st->present_ns += (uint64_t)(t1.tv_sec - t0.tv_sec) * 1000000000ull
				+ (uint64_t)(t1.tv_nsec - t0.tv_nsec);
		s->dirty = false;
		if (s->buffer)
			wl_buffer_send_release(s->buffer);
		wl_display_flush_clients(st->display);
	}
	wl_event_source_timer_update(st->tick, 16);
	return 0;
}

int
sink_fd_ready(int fd, uint32_t mask, void *data)
{
	mc_sink *sink = data;
	if (mask & WL_EVENT_READABLE && sink->pump)
		sink->pump(sink);
	if (mask & WL_EVENT_HANGUP)
		return -1;
	return 0;
}


/* ---- shared startup pieces (CLI + runtime service) ---- */

void
mc_globals_create(struct mc_state *st)
{
	mc_compositor_impl.create_surface = compositor_create_surface;
	mc_compositor_impl.create_region = compositor_create_region;
	wl_global_create(st->display, &wl_compositor_interface, 4, st, compositor_bind);
	wl_global_create(st->display, &wl_output_interface, 3, st, output_bind);
	if (wl_display_init_shm(st->display) != 0)
		fprintf(stderr, "mc: wl_display_init_shm failed\n");
}

int
mc_cli_tick(void *data)
{
	struct mc_state *st = data;
	mc_present_tick(st);
	mc_seat_tick(st);
	wl_event_source_timer_update(st->tick, 16);
	return 0;
}
