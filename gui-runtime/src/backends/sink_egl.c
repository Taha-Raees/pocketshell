/* mc — Android sink: GLES2 texture upload + quad draw onto an ANativeWindow
 * through EGL. Compiled with the NDK (see android/build-ndk.sh); the runtime
 * gate needs the device. BGRA client bytes are swizzled in the fragment
 * shader, so no GL extension is required. */
#include <EGL/egl.h>
#include <EGL/eglplatform.h>
#include <GLES2/gl2.h>
#include <android/native_window.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <stdint.h>
#include "../mc.h"

struct eglsink {
	ANativeWindow *win;
	EGLDisplay dpy;
	EGLContext ctx;
	EGLSurface surf;
	GLuint prog, tex;
	GLint pos_loc, uv_loc;
	int w, h;
};

static const char *VS =
	"attribute vec2 p; attribute vec2 t; varying vec2 uv;\n"
	"void main(){ uv = t; gl_Position = vec4(p, 0.0, 1.0); }\n";

static const char *FS =
	"precision mediump float; varying vec2 uv; uniform sampler2D s;\n"
	"void main(){ gl_FragColor = texture2D(s, uv).bgra; }\n"; /* BGRA→RGBA */

static GLuint
mk_shader(GLenum type, const char *src)
{
	GLuint sh = glCreateShader(type);
	glShaderSource(sh, 1, &src, NULL);
	glCompileShader(sh);
	GLint ok = 0;
	glGetShaderiv(sh, GL_COMPILE_STATUS, &ok);
	if (!ok) { fprintf(stderr, "mc-egl: shader compile failed\n"); return 0; }
	return sh;
}

static int
egl_init(mc_sink *s, int *w, int *h)
{
	struct eglsink *e = s->priv;
	e->dpy = eglGetDisplay(EGL_DEFAULT_DISPLAY);
	if (e->dpy == EGL_NO_DISPLAY) return -1;
	if (!eglInitialize(e->dpy, NULL, NULL)) return -1;

	EGLConfig cfg;
	EGLint ncfg = 0;
	const EGLint cfgattr[] = {
		EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
		EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
		EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8,
		EGL_ALPHA_SIZE, 0, EGL_NONE };
	if (!eglChooseConfig(e->dpy, cfgattr, &cfg, 1, &ncfg) || ncfg < 1) return -1;

	const EGLint ctxattr[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };
	e->ctx = eglCreateContext(e->dpy, cfg, EGL_NO_CONTEXT, ctxattr);
	if (e->ctx == EGL_NO_CONTEXT) return -1;

	e->surf = eglCreateWindowSurface(e->dpy, cfg, (EGLNativeWindowType)e->win, NULL);
	if (e->surf == EGL_NO_SURFACE) return -1;
	if (!eglMakeCurrent(e->dpy, e->surf, e->surf, e->ctx)) return -1;

	eglQuerySurface(e->dpy, e->surf, EGL_WIDTH, &e->w);
	eglQuerySurface(e->dpy, e->surf, EGL_HEIGHT, &e->h);
	*w = e->w; *h = e->h;
	ANativeWindow_acquire(e->win);

	GLuint vs = mk_shader(GL_VERTEX_SHADER, VS);
	GLuint fs = mk_shader(GL_FRAGMENT_SHADER, FS);
	e->prog = glCreateProgram();
	glAttachShader(e->prog, vs);
	glAttachShader(e->prog, fs);
	glLinkProgram(e->prog);
	glUseProgram(e->prog);
	e->pos_loc = glGetAttribLocation(e->prog, "p");
	e->uv_loc = glGetAttribLocation(e->prog, "t");

	glGenTextures(1, &e->tex);
	glBindTexture(GL_TEXTURE_2D, e->tex);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
	glDisable(GL_BLEND);
	fprintf(stderr, "mc-egl: ready %dx%d vendor=%s renderer=%s\n", e->w, e->h,
		glGetString(GL_VENDOR), glGetString(GL_RENDERER));
	return 0;
}

static void
egl_present(mc_sink *s, const uint8_t *pix, int w, int h, int stride)
{
	struct eglsink *e = s->priv;
	if (!e->surf)
		return;
	/* Scale client surface to the full window (kiosk). uv in [0,1]. */
	float cw = (float)w, chh = (float)h, ww = (float)e->w, wh = (float)e->h;
	/* letterbox: pick contained rect, center it */
	float scale = ww / cw < wh / chh ? ww / cw : wh / chh;
	float dw = cw * scale, dh = chh * scale, dx = (ww - dw) / 2, dy = (wh - dh) / 2;
	float x0 = dx / ww * 2 - 1, x1 = (dx + dw) / ww * 2 - 1;
	float y0 = 1 - (dy + dh) / wh * 2, y1 = 1 - dy / wh * 2;
	float pos[8] = { x0,y0, x1,y0, x0,y1, x1,y1 };
	float uv[8]  = { 0,1, 1,1, 0,0, 1,0 };

	glViewport(0, 0, e->w, e->h);
	glClearColor(0, 0, 0, 1);
	glClear(GL_COLOR_BUFFER_BIT);
	glBindTexture(GL_TEXTURE_2D, e->tex);
	glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
	if (stride == w * 4) {
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA,
			     GL_UNSIGNED_BYTE, pix);
	} else {
		/* upload row by row with tight rows */
		uint8_t *tight = malloc((size_t)w * h * 4);
		for (int y = 0; y < h; y++)
			memcpy(tight + (size_t)y * w * 4, pix + (size_t)y * stride,
			       (size_t)w * 4);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA,
			     GL_UNSIGNED_BYTE, tight);
		free(tight);
	}
	glVertexAttribPointer(e->pos_loc, 2, GL_FLOAT, GL_FALSE, 0, pos);
	glEnableVertexAttribArray(e->pos_loc);
	glVertexAttribPointer(e->uv_loc, 2, GL_FLOAT, GL_FALSE, 0, uv);
	glEnableVertexAttribArray(e->uv_loc);
	glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
	eglSwapBuffers(e->dpy, e->surf);
}

mc_sink *
mc_sink_egl_create(void *anativewindow)
{
	mc_sink *s = calloc(1, sizeof(*s));
	struct eglsink *e = calloc(1, sizeof(*e));
	e->win = (ANativeWindow *)anativewindow;
	s->name = "egl";
	s->priv = e;
	s->init = egl_init;
	s->present = egl_present;
	s->poll_fd = -1;
	return s;
}

/* ---- runtime attach/detach (called on the mc loop thread) ---- */

void
mc_sink_egl_runtime_attach(mc_sink *s, void *anativewindow, int w, int h)
{
	struct eglsink *e = s->priv;
	if (!anativewindow)
		return;
	if (!e->surf) {
		/* first attach: full EGL bring-up on this (loop) thread */
		e->win = (ANativeWindow *)anativewindow;
		if (egl_init(s, &w, &h) != 0)
			fprintf(stderr, "mc-egl: runtime init failed\n");
		return;
	}
	if ((ANativeWindow *)anativewindow != e->win) {
		/* surface object replaced: tear down, then bring up fresh */
		mc_sink_egl_runtime_detach(s);
		e->win = (ANativeWindow *)anativewindow;
		if (egl_init(s, &w, &h) != 0)
			fprintf(stderr, "mc-egl: re-init failed\n");
		return;
	}
	/* same surface, maybe new size */
	eglQuerySurface(e->dpy, e->surf, EGL_WIDTH, &e->w);
	eglQuerySurface(e->dpy, e->surf, EGL_HEIGHT, &e->h);
	(void)w; (void)h;
}

void
mc_sink_egl_runtime_detach(mc_sink *s)
{
	struct eglsink *e = s->priv;
	if (e->surf != EGL_NO_SURFACE) {
		eglMakeCurrent(e->dpy, EGL_NO_SURFACE, EGL_NO_SURFACE,
			       EGL_NO_CONTEXT);
		eglDestroySurface(e->dpy, e->surf);
		e->surf = EGL_NO_SURFACE;
	}
	if (e->win) {
		ANativeWindow_release(e->win);
		e->win = NULL;
	}
}
