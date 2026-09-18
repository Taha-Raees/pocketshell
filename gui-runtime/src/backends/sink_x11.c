/* mc — laptop rehearsal sink: X11 window, XPutImage presentation.
 * Memory layout matches Wayland shm XRGB8888 on LE (B,G,R,x == BGRX ZPixmap),
 * so present is one memcpy into the XImage plus XPutImage. X events feed the
 * seat: X keycode - 8 == Linux evdev code on classic maps (exact here). */
#define _GNU_SOURCE
#include <X11/Xlib.h>
#include <X11/keysym.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <stdint.h>
#include "../mc.h"

struct ksmap { KeySym ks; uint32_t evdev; };
static const struct ksmap ksmap[] = {
	{ XK_a, 30 }, { XK_b, 48 }, { XK_c, 46 }, { XK_d, 32 }, { XK_e, 18 },
	{ XK_f, 33 }, { XK_g, 34 }, { XK_h, 35 }, { XK_i, 23 }, { XK_j, 36 },
	{ XK_k, 37 }, { XK_l, 38 }, { XK_m, 50 }, { XK_n, 49 }, { XK_o, 24 },
	{ XK_p, 25 }, { XK_q, 16 }, { XK_r, 19 }, { XK_s, 31 }, { XK_t, 20 },
	{ XK_u, 22 }, { XK_v, 47 }, { XK_w, 17 }, { XK_x, 45 }, { XK_y, 21 },
	{ XK_z, 44 },
	{ XK_0, 11 }, { XK_1, 2 }, { XK_2, 3 }, { XK_3, 4 }, { XK_4, 5 },
	{ XK_5, 6 }, { XK_6, 7 }, { XK_7, 8 }, { XK_8, 9 }, { XK_9, 10 },
	{ XK_Return, 28 }, { XK_KP_Enter, 96 }, { XK_BackSpace, 14 },
	{ XK_Tab, 15 }, { XK_Escape, 1 }, { XK_space, 57 },
	{ XK_Left, 105 }, { XK_Right, 106 }, { XK_Up, 103 }, { XK_Down, 108 },
	{ XK_Shift_L, 42 }, { XK_Shift_R, 54 }, { XK_Control_L, 29 },
	{ XK_Control_R, 97 }, { XK_Alt_L, 56 }, { XK_Alt_R, 100 },
	{ XK_Super_L, 125 }, { XK_Super_R, 126 },
	{ XK_F1, 59 }, { XK_F2, 60 }, { XK_F3, 61 }, { XK_F4, 62 }, { XK_F5, 63 },
	{ XK_F6, 64 }, { XK_F7, 65 }, { XK_F8, 66 }, { XK_F9, 67 }, { XK_F10, 68 },
	{ XK_F11, 87 }, { XK_F12, 88 },
	{ XK_comma, 51 }, { XK_period, 52 }, { XK_slash, 53 },
	{ XK_semicolon, 39 }, { XK_apostrophe, 40 }, { XK_bracketleft, 26 },
	{ XK_bracketright, 27 }, { XK_minus, 12 }, { XK_equal, 13 },
	{ XK_grave, 41 }, { XK_backslash, 43 },
};

static uint32_t
ks_to_evdev(KeySym ks)
{
	for (size_t i = 0; i < sizeof(ksmap)/sizeof(ksmap[0]); i++)
		if (ksmap[i].ks == ks) return ksmap[i].evdev;
	return 0;
}

struct x11sink {
	Display *dpy;
	Window win;
	int w, h;
};

/* v1 spike keeps everything in one struct via a second allocation */
struct x11full { struct x11sink x; XImage *img; };

static int
x11_init(mc_sink *s, int *w, int *h)
{
	struct x11full *fx = s->priv;
	struct x11sink *x = &fx->x;
	int scr = DefaultScreen(x->dpy);
	x->win = XCreateSimpleWindow(x->dpy, RootWindow(x->dpy, scr),
				     0, 0, *w, *h, 1,
				     BlackPixel(x->dpy, scr), WhitePixel(x->dpy, scr));
	XSelectInput(x->dpy, x->win,
		     KeyPressMask | KeyReleaseMask | ButtonPressMask |
		     ButtonReleaseMask | PointerMotionMask | StructureNotifyMask);
	XStoreName(x->dpy, x->win, "mc kiosk");
	XMapWindow(x->dpy, x->win);
	fx->img = XCreateImage(x->dpy, DefaultVisual(x->dpy, scr),
			       24, ZPixmap, 0, NULL, *w, *h, 32, 0);
	fx->img->data = calloc(1, (size_t)*w * *h * 4);
	x->w = *w; x->h = *h;
	XFlush(x->dpy);
	return 0;
}

static void
x11_present2(mc_sink *s, const uint8_t *pix, int w, int h, int stride)
{
	struct x11full *fx = s->priv;
	struct x11sink *x = &fx->x;
	int cw = w < x->w ? w : x->w;
	int ch = h < x->h ? h : x->h;
	for (int y = 0; y < ch; y++)
		memcpy(fx->img->data + (size_t)y * fx->img->bytes_per_line,
		       pix + (size_t)y * stride, (size_t)cw * 4);
	XPutImage(x->dpy, x->win, DefaultGC(x->dpy, DefaultScreen(x->dpy)),
		  fx->img, 0, 0, 0, 0, cw, ch);
	XFlush(x->dpy);
}

static void
x11_pump(mc_sink *s)
{
	struct x11full *fx = s->priv;
	struct x11sink *x = &fx->x;
	struct mc_state *st = s->host;
	while (XPending(x->dpy)) {
		XEvent ev;
		XNextEvent(x->dpy, &ev);
		switch (ev.type) {
		case KeyPress:
		case KeyRelease: {
			KeySym ks = XLookupKeysym(&ev.xkey, 0);
			uint32_t code = ks_to_evdev(ks);
			if (code)
				mc_seat_key(st, code, ev.type == KeyPress);
			break;
		}
		case MotionNotify:
			mc_seat_pointer_motion(st, ev.xmotion.x, ev.xmotion.y);
			break;
		case ButtonPress:
		case ButtonRelease: {
			bool down = ev.type == ButtonPress;
			if (ev.xbutton.button == Button4) {
				if (down) mc_seat_pointer_axis(st, -1);
				break;
			}
			if (ev.xbutton.button == Button5) {
				if (down) mc_seat_pointer_axis(st, 1);
				break;
			}
			uint32_t b = ev.xbutton.button == Button1 ? 0x110 :
				     ev.xbutton.button == Button2 ? 0x112 : 0x111;
			mc_seat_pointer_button(st, b, down);
			break;
		}
		default:
			break;
		}
	}
}

mc_sink *
mc_sink_x11_create(int width, int height)
{
	Display *dpy = XOpenDisplay(NULL);
	if (!dpy) return NULL;

	mc_sink *s = calloc(1, sizeof(*s));
	struct x11full *fx = calloc(1, sizeof(*fx));
	fx->x.dpy = dpy;
	fx->x.w = width;
	fx->x.h = height;
	s->name = "x11";
	s->priv = fx;
	s->init = x11_init;
	s->present = x11_present2;
	s->pump = x11_pump;
	s->poll_fd = ConnectionNumber(dpy);
	return s;
}
