# mc — kiosk Wayland compositor as an in-process Android service (libmc.so).
# Sources live in gui-runtime/src (shared with the laptop CLI build); protocol
# code is generated into this dir (committed) by src/gen-protocol.sh.
# Prebuilt wayland/xkbcommon/ffi (LGPL/MIT) are cross-built from source by
# android/build-ndk.sh and committed here (libproot vendor pattern).
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := wayland-server-prebuilt
LOCAL_SRC_FILES := libwayland-server.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := xkbcommon-prebuilt
LOCAL_SRC_FILES := libxkbcommon.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := ffi-prebuilt
LOCAL_SRC_FILES := libffi.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libmc
LOCAL_SRC_FILES := \
	../../../src/mc_core.c \
	../../../src/mc_runtime.c \
	../../../src/mc_jni.cpp \
	../../../src/shm.c \
	../../../src/shell.c \
	../../../src/seat.c \
	../../../src/backends/sink_egl.c \
	../../../src/backends/sink_ppm.c \
	protocol-wayland-server.c \
	protocol-xdg-shell.c

LOCAL_C_INCLUDES := $(LOCAL_PATH) $(LOCAL_PATH)/../../../src $(LOCAL_PATH)/wayland $(LOCAL_PATH)/xkbcommon
LOCAL_CFLAGS := -std=c11 -Wall -Wextra -O2 -DMC_ANDROID=1
LOCAL_CPPFLAGS := -std=c++17 -Wall -Wextra
LOCAL_LDLIBS := -lEGL -lGLESv2 -landroid -llog
LOCAL_SHARED_LIBRARIES := wayland-server-prebuilt xkbcommon-prebuilt ffi-prebuilt

include $(BUILD_SHARED_LIBRARY)
