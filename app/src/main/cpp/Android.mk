ROOT_PATH := $(call my-dir)
HEV_PATH := $(ROOT_PATH)/third_party/hev-socks5-tunnel

include $(HEV_PATH)/third-part/yaml/Android.mk
include $(HEV_PATH)/third-part/lwip/Android.mk
include $(HEV_PATH)/third-part/hev-task-system/Android.mk

LOCAL_PATH := $(ROOT_PATH)
SRCDIR := $(HEV_PATH)/src
REV_ID := 4d6c334
include $(HEV_PATH)/build.mk
SRCFILES := $(filter-out $(HEV_PATH)/src/hev-jni.c,$(SRCFILES))

include $(CLEAR_VARS)
LOCAL_MODULE := modern_socks_tunnel
LOCAL_SRC_FILES := \
    modern_socks_jni.c \
    tun2proxy_jni.c \
    $(patsubst $(ROOT_PATH)/%,%,$(SRCFILES))
LOCAL_C_INCLUDES := \
    $(HEV_PATH)/src \
    $(HEV_PATH)/src/misc \
    $(HEV_PATH)/src/core/include \
    $(HEV_PATH)/third-part/yaml/include \
    $(HEV_PATH)/third-part/lwip/src/include \
    $(HEV_PATH)/third-part/lwip/src/ports/include \
    $(HEV_PATH)/third-part/hev-task-system/include \
    $(ROOT_PATH)/../rust/tunnellens-tun2proxy/include
LOCAL_CFLAGS += -DFD_SET_DEFINED -DSOCKLEN_T_DEFINED -DENABLE_LIBRARY
LOCAL_CFLAGS += -DMODERNSOCKS_HEV_VERSION=\"2.14.4-4d6c334-ms1\"
LOCAL_CFLAGS += -DMODERNSOCKS_IPV4_TCP_ONLY=1
LOCAL_CFLAGS += $(VERSION_CFLAGS)
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
LOCAL_CFLAGS += -mfpu=neon
endif
LOCAL_STATIC_LIBRARIES := yaml lwip hev-task-system
LOCAL_LDFLAGS += -Wl,--wrap=socket
LOCAL_LDFLAGS += -Wl,--wrap=close
LOCAL_LDFLAGS += -Wl,--wrap=hev_socks5_session_udp_new
LOCAL_LDFLAGS += -Wl,-z,max-page-size=16384
LOCAL_LDFLAGS += -Wl,-z,common-page-size=16384
LOCAL_LDLIBS += -llog -ldl
include $(BUILD_SHARED_LIBRARY)
