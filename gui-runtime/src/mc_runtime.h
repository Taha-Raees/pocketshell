/* mc_runtime — in-process compositor service (Android/JNI embedding).
 *
 * The runtime runs the wayland event loop on the CALLER's thread (JNI starts
 * a dedicated pthread). Other threads (UI) reach it through a pipe command
 * queue: surface attach/detach, input, stop. Every command is handled inside
 * the event loop thread — no libwayland locking needed.
 *
 * Present path: client wl_shm damage → GLES2 texSubImage2D → eglSwapBuffers
 * onto the attached ANativeWindow (see backends/sink_egl.c).
 */
#ifndef MC_RUNTIME_H
#define MC_RUNTIME_H

#include <stdint.h>
#include <android/native_window.h>

#ifdef __cplusplus
extern "C" {
#endif

struct mc_runtime_config {
	const char *socket_path;   /* full unix socket path (dir must exist) */
	const char *xkb_root;      /* XKB_CONFIG_ROOT data dir (rules/keycodes…) */
	int width, height;         /* initial output size; updated on surface attach */
};

/* Blocking: runs the loop until mc_runtime_stop() (from another thread) or a
 * fatal error. Returns 0 on clean stop, non-zero error code otherwise. */
int mc_runtime_start(const struct mc_runtime_config *cfg);

/* ---- thread-safe (UI thread) ---- */
void mc_runtime_attach_surface(ANativeWindow *win, int w, int h);
void mc_runtime_detach_surface(void);
void mc_runtime_stop(void);

void mc_runtime_key(uint32_t evdev_code, int down);       /* down: 0/1 */
void mc_runtime_pointer_motion(int x, int y);
void mc_runtime_pointer_button(int evdev_btn, int down);
void mc_runtime_pointer_scroll(int steps);                /* +down */
void mc_runtime_touch(int action, int id, int x, int y);  /* action: 0 down,1 move,2 up */

/* JNI surface arrival gives us the ANativeWindow on the UI thread; the loop
 * thread consumes it. */
void mc_runtime_command_surface(ANativeWindow *win, int w, int h);

#endif

#ifdef __cplusplus
}
#endif
