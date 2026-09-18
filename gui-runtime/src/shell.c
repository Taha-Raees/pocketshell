/* mc — xdg_shell (xdg_wm_base v1…): kiosk toplevel lifecycle + popup stub.
 * Toplevels get an immediate configure at the sink size; the client acks,
 * attaches and commits — only then do we treat it as mapped/renderable. */
#define _GNU_SOURCE
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include "mc.h"

/* ---- positioner (popup geometry v1) ---- */

struct mc_positioner { int32_t w, h, off_x, off_y; };

static void
positioner_destroy_req(struct wl_client *c, struct wl_resource *r)
{ (void)c; wl_resource_destroy(r); }

static void
positioner_set_size(struct wl_client *c, struct wl_resource *r, int32_t w, int32_t h)
{ (void)c; struct mc_positioner *p = wl_resource_get_user_data(r); p->w = w; p->h = h; }

static void
positioner_set_anchor_rect(struct wl_client *c, struct wl_resource *r,
			   int32_t x, int32_t y, int32_t w, int32_t h)
{ (void)c; (void)r; (void)x; (void)y; (void)w; (void)h; }

static void
positioner_set_anchor(struct wl_client *c, struct wl_resource *r, uint32_t a)
{ (void)c; (void)r; (void)a; }

static void
positioner_set_gravity(struct wl_client *c, struct wl_resource *r, uint32_t g)
{ (void)c; (void)r; (void)g; }

static void
positioner_set_constraint_adjustment(struct wl_client *c, struct wl_resource *r, uint32_t ca)
{ (void)c; (void)r; (void)ca; }

static void
positioner_set_offset(struct wl_client *c, struct wl_resource *r, int32_t x, int32_t y)
{ (void)c; struct mc_positioner *p = wl_resource_get_user_data(r); p->off_x = x; p->off_y = y; }

static void
positioner_set_reactive(struct wl_client *c, struct wl_resource *r) { (void)c; (void)r; }

static void
positioner_set_parent_size(struct wl_client *c, struct wl_resource *r,
			   int32_t w, int32_t h) { (void)c; (void)r; (void)w; (void)h; }

static void
positioner_set_anchor_rect19(struct wl_client *c, struct wl_resource *r,
			     int32_t x, int32_t y, int32_t w, int32_t h)
{ (void)c; (void)r; (void)x; (void)y; (void)w; (void)h; }

static const struct xdg_positioner_interface positioner_impl = {
	.destroy = positioner_destroy_req,
	.set_size = positioner_set_size,
	.set_anchor_rect = positioner_set_anchor_rect,
	.set_anchor = positioner_set_anchor,
	.set_gravity = positioner_set_gravity,
	.set_constraint_adjustment = positioner_set_constraint_adjustment,
	.set_offset = positioner_set_offset,
	.set_reactive = positioner_set_reactive,
	.set_parent_size = positioner_set_parent_size,
};

/* ---- helpers ---- */

static struct mc_surface *
surface_by_wl_surface(struct mc_state *st, struct wl_resource *wl_surface)
{
	struct mc_surface *s;
	wl_list_for_each(s, &st->surfaces, link)
		if (s->surface == wl_surface)
			return s;
	return NULL;
}

static struct mc_surface *
surface_by_xdg_surface(struct mc_state *st, struct wl_resource *xdg)
{
	struct mc_surface *s;
	wl_list_for_each(s, &st->surfaces, link)
		if (s->xdg_surface == xdg)
			return s;
	return NULL;
}

static void
configure_toplevel(struct mc_state *st, struct mc_surface *s)
{
	if (!s->toplevel || !s->xdg_surface)
		return;
	uint32_t states = XDG_TOPLEVEL_STATE_ACTIVATED;
	struct wl_array arr;
	wl_array_init(&arr);
	uint32_t *v = wl_array_add(&arr, sizeof(uint32_t));
	*v = states;
	xdg_toplevel_send_configure(s->toplevel, st->out_w, st->out_h, &arr);
	wl_array_release(&arr);
	xdg_surface_send_configure(s->xdg_surface, mc_next_serial(st));
	s->role_configured = false; /* wait for the fresh ack */
}

void
mc_shell_reconfigure_all(struct mc_state *st)
{
	struct mc_surface *s;
	wl_list_for_each(s, &st->surfaces, link)
		if (s->toplevel)
			configure_toplevel(st, s);
	wl_display_flush_clients(st->display);
}

/* ---- xdg_toplevel ---- */

static void
toplevel_destroy_req(struct wl_client *c, struct wl_resource *r)
{ (void)c; wl_resource_destroy(r); }

static void
toplevel_set_parent(struct wl_client *c, struct wl_resource *r, struct wl_resource *p)
{ (void)c; (void)r; (void)p; }

static void
toplevel_set_title(struct wl_client *c, struct wl_resource *r, const char *title)
{ (void)c; (void)r; fprintf(stderr, "mc: toplevel title: %s\n", title); }

static void
toplevel_set_app_id(struct wl_client *c, struct wl_resource *r, const char *id)
{ (void)c; (void)r; fprintf(stderr, "mc: toplevel app_id: %s\n", id); }

static void
toplevel_nop_uu(struct wl_client *c, struct wl_resource *r, struct wl_resource *a, uint32_t s)
{ (void)c; (void)r; (void)a; (void)s; }

static void
toplevel_nop_uu_iipp(struct wl_client *c, struct wl_resource *r, struct wl_resource *a,
		     uint32_t s, int32_t x, int32_t y)
{ (void)c; (void)r; (void)a; (void)s; (void)x; (void)y; }

static void
toplevel_nop_uuu32(struct wl_client *c, struct wl_resource *r, struct wl_resource *a, uint32_t s, uint32_t e)
{ (void)c; (void)r; (void)a; (void)s; (void)e; }

static void
toplevel_set_max_size(struct wl_client *c, struct wl_resource *r, int32_t w, int32_t h)
{ (void)c; (void)r; (void)w; (void)h; }

static void
toplevel_set_min_size(struct wl_client *c, struct wl_resource *r, int32_t w, int32_t h)
{ (void)c; (void)r; (void)w; (void)h; }

static void
toplevel_nop(struct wl_client *c, struct wl_resource *r) { (void)c; (void)r; }

static void
toplevel_set_fullscreen(struct wl_client *c, struct wl_resource *r, struct wl_resource *o)
{ (void)c; (void)r; (void)o; }

static const struct xdg_toplevel_interface toplevel_impl = {
	.destroy = toplevel_destroy_req,
	.set_parent = toplevel_set_parent,
	.set_title = toplevel_set_title,
	.set_app_id = toplevel_set_app_id,
	.show_window_menu = toplevel_nop_uu_iipp,
	.move = toplevel_nop_uu,
	.resize = toplevel_nop_uuu32,
	.set_max_size = toplevel_set_max_size,
	.set_min_size = toplevel_set_min_size,
	.set_maximized = toplevel_nop,
	.unset_maximized = toplevel_nop,
	.set_fullscreen = toplevel_set_fullscreen,
	.unset_fullscreen = toplevel_nop,
	.set_minimized = toplevel_nop,
};

/* ---- xdg_popup (v1: protocol-honest stub — configured, not rendered) ---- */

static void
popup_destroy_req(struct wl_client *c, struct wl_resource *r)
{ (void)c; wl_resource_destroy(r); }

static void
popup_grab(struct wl_client *c, struct wl_resource *r, struct wl_resource *seat, uint32_t serial)
{ (void)c; (void)r; (void)seat; (void)serial; }

static void
popup_reposition(struct wl_client *c, struct wl_resource *r, struct wl_resource *pos, uint32_t token)
{ (void)c; (void)r; (void)pos; (void)token; }

static const struct xdg_popup_interface popup_impl = {
	.destroy = popup_destroy_req,
	.grab = popup_grab,
	.reposition = popup_reposition,
};

/* ---- xdg_surface ---- */

static void
xdg_destroy_req(struct wl_client *c, struct wl_resource *r)
{ (void)c; wl_resource_destroy(r); }

static void
xdg_get_toplevel(struct wl_client *client, struct wl_resource *res, uint32_t id)
{
	struct mc_surface *s = wl_resource_get_user_data(res);
	s->toplevel = wl_resource_create(client, &xdg_toplevel_interface, 1, id);
	wl_resource_set_implementation(s->toplevel, &toplevel_impl, s, NULL);
	configure_toplevel(s->st, s);
}

static void
xdg_get_popup(struct wl_client *client, struct wl_resource *res, uint32_t id,
	      struct wl_resource *parent, struct wl_resource *positioner)
{
	struct mc_surface *s = wl_resource_get_user_data(res);
	struct mc_positioner *p = wl_resource_get_user_data(positioner);
	s->popup = wl_resource_create(client, &xdg_popup_interface, 1, id);
	wl_resource_set_implementation(s->popup, &popup_impl, s, NULL);
	xdg_popup_send_configure(s->popup, p->off_x, p->off_y, p->w, p->h);
	xdg_surface_send_configure(res, mc_next_serial(s->st));
}

static void
xdg_ack_configure(struct wl_client *c, struct wl_resource *res, uint32_t serial)
{
	(void)c; (void)serial;
	struct mc_surface *s = wl_resource_get_user_data(res);
	s->role_configured = true;
}

static void
xdg_set_window_geometry(struct wl_client *c, struct wl_resource *r,
			int32_t x, int32_t y, int32_t w, int32_t h)
{ (void)c; (void)r; (void)x; (void)y; (void)w; (void)h; }

static const struct xdg_surface_interface xdg_surface_impl = {
	.destroy = xdg_destroy_req,
	.get_toplevel = xdg_get_toplevel,
	.get_popup = xdg_get_popup,
	.set_window_geometry = xdg_set_window_geometry,
	.ack_configure = xdg_ack_configure,
};

/* ---- xdg_wm_base ---- */

static void
wm_destroy_req(struct wl_client *c, struct wl_resource *r)
{ (void)c; wl_resource_destroy(r); }

static void
wm_create_positioner(struct wl_client *client, struct wl_resource *res, uint32_t id)
{
	struct mc_positioner *p = calloc(1, sizeof(*p));
	struct wl_resource *r = wl_resource_create(client, &xdg_positioner_interface, 1, id);
	wl_resource_set_implementation(r, &positioner_impl, p, NULL);
}

static void
wm_get_xdg_surface(struct wl_client *client, struct wl_resource *res, uint32_t id,
		   struct wl_resource *wl_surface)
{
	struct mc_state *st = wl_resource_get_user_data(res);
	struct mc_surface *s = surface_by_wl_surface(st, wl_surface);
	if (!s) {
		wl_resource_post_error(res, XDG_WM_BASE_ERROR_DEFUNCT_SURFACES,
				       "unknown wl_surface");
		return;
	}
	if (s->xdg_surface) {
		wl_resource_post_error(res, XDG_WM_BASE_ERROR_ROLE,
				       "wl_surface already has an xdg_surface");
		return;
	}
	struct wl_resource *r = wl_resource_create(client, &xdg_surface_interface, 1, id);
	wl_resource_set_implementation(r, &xdg_surface_impl, s, NULL);
	s->xdg_surface = r;
}

static const struct xdg_wm_base_interface wm_impl = {
	.destroy = wm_destroy_req,
	.create_positioner = wm_create_positioner,
	.get_xdg_surface = wm_get_xdg_surface,
};

static void
wm_bind(struct wl_client *client, void *data, uint32_t version, uint32_t id)
{
	struct wl_resource *r = wl_resource_create(client, &xdg_wm_base_interface,
						   version < 1 ? 1 : (version > 6 ? 6 : version), id);
	wl_resource_set_implementation(r, &wm_impl, data, NULL);
}

bool
mc_shell_init(struct mc_state *st)
{
	wl_global_create(st->display, &xdg_wm_base_interface, 6, st, wm_bind);
	return true;
}
