/* mc_jni — JNI bridge: app.pocketshell.gui.McGuiRuntime native methods.
 *
 * The UI thread attaches the Surface (ANativeWindow_fromSurface here, since
 * JNI surface handles are thread-local-ish) and hands the window pointer to
 * the loop thread through the runtime command queue. Input arrives as
 * evdev-keycode/touch commands. Stop requests a clean loop exit and joins. */
#define _GNU_SOURCE
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <jni.h>
#include <cerrno>
#include <android/log.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>

#include "mc_runtime.h"

static pthread_t loop_thread;
static pthread_mutex_t state_mutex = PTHREAD_MUTEX_INITIALIZER;
static bool running = false;
static char *socket_path_copy;
static char *xkb_root_copy;
static int cfg_w, cfg_h;

struct start_arg {
	char *socket;
	char *xkb;
	int w, h;
};

static void *
loop_entry(void *p)
{
	struct start_arg *a = (struct start_arg *)p;
	struct mc_runtime_config cfg = { .socket_path = a->socket,
					 .xkb_root = a->xkb,
					 .width = a->w,
					 .height = a->h };
	int rc = mc_runtime_start(&cfg);
	__android_log_print(ANDROID_LOG_INFO, "mc",
			    "runtime exited rc=%d", rc);
	free(a->socket);
	free(a->xkb);
	free(a);
	return (void *)(intptr_t)rc;
}

JNIEXPORT jint JNICALL
Java_app_pocketshell_gui_McGuiRuntime_nativeStart(JNIEnv *env, jclass,
						  jstring socketPath,
						  jstring xkbRoot,
						  jint width, jint height)
{
	pthread_mutex_lock(&state_mutex);
	if (running) {
		pthread_mutex_unlock(&state_mutex);
		return -EALREADY;
	}
	const char *sp = env->GetStringUTFChars(socketPath, NULL);
	const char *xr = env->GetStringUTFChars(xkbRoot, NULL);
	struct start_arg *a = (struct start_arg *)malloc(sizeof(*a));
	a->socket = strdup(sp);
	a->xkb = strdup(xr);
	a->w = width;
	a->h = height;
	env->ReleaseStringUTFChars(socketPath, sp);
	env->ReleaseStringUTFChars(xkbRoot, xr);

	int rc = pthread_create(&loop_thread, NULL, loop_entry, a);
	if (rc == 0)
		running = true;
	pthread_mutex_unlock(&state_mutex);
	return -rc; /* 0 on success, negative errno */
}

JNIEXPORT void JNICALL
Java_app_pocketshell_gui_McGuiRuntime_nativeAttachSurface(JNIEnv *env, jclass,
							  jobject surface,
							  jint width, jint height)
{
	ANativeWindow *win = ANativeWindow_fromSurface(env, surface);
	if (win)
		mc_runtime_command_surface(win, width, height);
}

JNIEXPORT void JNICALL
Java_app_pocketshell_gui_McGuiRuntime_nativeDetachSurface(JNIEnv *, jclass)
{
	mc_runtime_detach_surface();
}

JNIEXPORT jint JNICALL
Java_app_pocketshell_gui_McGuiRuntime_nativeStop(JNIEnv *, jclass)
{
	pthread_mutex_lock(&state_mutex);
	if (!running) {
		pthread_mutex_unlock(&state_mutex);
		return 0;
	}
	mc_runtime_stop();
	void *ret = NULL;
	int rc = pthread_join(loop_thread, &ret);
	running = false;
	pthread_mutex_unlock(&state_mutex);
	return rc == 0 ? (int)(intptr_t)ret : -rc;
}

JNIEXPORT void JNICALL
Java_app_pocketshell_gui_McGuiRuntime_nativeKey(JNIEnv *, jclass,
						jint evdevCode, jboolean down)
{
	mc_runtime_key((uint32_t)evdevCode, down ? 1 : 0);
}

JNIEXPORT void JNICALL
Java_app_pocketshell_gui_McGuiRuntime_nativePointerMotion(JNIEnv *, jclass,
							  jint x, jint y)
{
	mc_runtime_pointer_motion(x, y);
}

JNIEXPORT void JNICALL
Java_app_pocketshell_gui_McGuiRuntime_nativePointerButton(JNIEnv *, jclass,
							  jint evdevBtn,
							  jboolean down)
{
	mc_runtime_pointer_button(evdevBtn, down ? 1 : 0);
}

JNIEXPORT void JNICALL
Java_app_pocketshell_gui_McGuiRuntime_nativePointerScroll(JNIEnv *, jclass,
							  jint steps)
{
	mc_runtime_pointer_scroll(steps);
}

JNIEXPORT void JNICALL
Java_app_pocketshell_gui_McGuiRuntime_nativeTouch(JNIEnv *, jclass,
						  jint action, jint id,
						  jint x, jint y)
{
	mc_runtime_touch(action, id, x, y);
}
