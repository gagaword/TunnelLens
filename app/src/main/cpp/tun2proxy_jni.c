#include <dlfcn.h>
#include <jni.h>
#include <limits.h>
#include <pthread.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "tunnellens_tun2proxy.h"

#define BRIDGE_ERROR_UNAVAILABLE -1000
#define BRIDGE_ERROR_INVALID_INPUT -1001
#define BRIDGE_ERROR_OUT_OF_MEMORY -1002
#define MAX_ADDRESS_BYTES 64
#define MAX_CREDENTIAL_BYTES 255

typedef uint32_t (*abi_version_fn)(void);
typedef const char *(*version_fn)(void);
typedef int32_t (*start_fn)(const TunnelLensTun2ProxyConfig *);
typedef int32_t (*request_stop_fn)(void);
typedef int32_t (*wait_for_exit_fn)(uint32_t);
typedef int32_t (*is_running_fn)(void);
typedef int32_t (*last_exit_code_fn)(void);
typedef void (*stats_fn)(uint64_t *, uint64_t *);

typedef struct {
    void *handle;
    abi_version_fn abi_version;
    version_fn version;
    start_fn start;
    request_stop_fn request_stop;
    wait_for_exit_fn wait_for_exit;
    is_running_fn is_running;
    last_exit_code_fn last_exit_code;
    stats_fn stats;
    int available;
} Tun2ProxyApi;

typedef struct {
    uint8_t *data;
    size_t size;
} ByteBuffer;

static Tun2ProxyApi api;
static pthread_once_t api_once = PTHREAD_ONCE_INIT;

static void secure_clear(void *value, size_t size)
{
    volatile uint8_t *bytes = value;
    while (size--)
        *bytes++ = 0;
}

static void resolve_api_once(void)
{
    api.handle = dlopen("libtunnellens_tun2proxy.so", RTLD_NOW | RTLD_LOCAL);
    if (!api.handle)
        return;

#define LOAD_SYMBOL(field, name) *(void **)(&api.field) = dlsym(api.handle, name)
    LOAD_SYMBOL(abi_version, "tunnellens_tun2proxy_abi_version");
    LOAD_SYMBOL(version, "tunnellens_tun2proxy_version");
    LOAD_SYMBOL(start, "tunnellens_tun2proxy_start");
    LOAD_SYMBOL(request_stop, "tunnellens_tun2proxy_request_stop");
    LOAD_SYMBOL(wait_for_exit, "tunnellens_tun2proxy_wait_for_exit");
    LOAD_SYMBOL(is_running, "tunnellens_tun2proxy_is_running");
    LOAD_SYMBOL(last_exit_code, "tunnellens_tun2proxy_last_exit_code");
    LOAD_SYMBOL(stats, "tunnellens_tun2proxy_stats");
#undef LOAD_SYMBOL

    if (!api.abi_version || !api.version || !api.start ||
        !api.request_stop || !api.wait_for_exit || !api.is_running ||
        !api.last_exit_code || !api.stats ||
        api.abi_version() != TUNNELLENS_TUN2PROXY_ABI_VERSION) {
        dlclose(api.handle);
        memset(&api, 0, sizeof(api));
        return;
    }
    api.available = 1;
}

static int ensure_api(void)
{
    pthread_once(&api_once, resolve_api_once);
    return api.available;
}

static int copy_bytes(JNIEnv *env, jbyteArray source, size_t max_size,
                      int allow_empty, ByteBuffer *result)
{
    jsize length;
    result->data = NULL;
    result->size = 0;
    if (!source)
        return allow_empty;
    length = (*env)->GetArrayLength(env, source);
    if (length < 0 || (size_t)length > max_size || (!allow_empty && length == 0))
        return 0;
    if (length == 0)
        return 1;
    result->data = malloc((size_t)length);
    if (!result->data)
        return -1;
    (*env)->GetByteArrayRegion(env, source, 0, length, (jbyte *)result->data);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        secure_clear(result->data, (size_t)length);
        free(result->data);
        result->data = NULL;
        return 0;
    }
    result->size = (size_t)length;
    return 1;
}

static void release_bytes(ByteBuffer *buffer, int sensitive)
{
    if (buffer->data) {
        if (sensitive)
            secure_clear(buffer->data, buffer->size);
        free(buffer->data);
    }
    buffer->data = NULL;
    buffer->size = 0;
}

JNIEXPORT jint JNICALL
Java_com_gagaworld_modernsocks_tunnel_Tun2ProxyNative_abiVersion(
    JNIEnv *env, jobject self)
{
    (void)env;
    (void)self;
    return ensure_api() ? (jint)api.abi_version() : BRIDGE_ERROR_UNAVAILABLE;
}

JNIEXPORT jstring JNICALL
Java_com_gagaworld_modernsocks_tunnel_Tun2ProxyNative_version(
    JNIEnv *env, jobject self)
{
    const char *value;
    (void)self;
    if (!ensure_api())
        return (*env)->NewStringUTF(env, "unavailable");
    value = api.version();
    return (*env)->NewStringUTF(env, value ? value : "unavailable");
}

JNIEXPORT jint JNICALL
Java_com_gagaworld_modernsocks_tunnel_Tun2ProxyNative_start(
    JNIEnv *env, jobject self, jint tun_fd, jint proxy_type,
    jbyteArray proxy_address, jint proxy_port, jint mtu,
    jbyteArray username, jbyteArray password, jint dns_strategy,
    jbyteArray dns_address, jboolean ipv6_enabled,
    jlong tcp_timeout_seconds, jlong udp_timeout_seconds, jint max_sessions)
{
    ByteBuffer address = {0};
    ByteBuffer user = {0};
    ByteBuffer pass = {0};
    ByteBuffer dns = {0};
    TunnelLensTun2ProxyConfig config = {0};
    int copy_result;
    int result = BRIDGE_ERROR_INVALID_INPUT;
    (void)self;

    if (tun_fd < 0 || proxy_port <= 0 || proxy_port > UINT16_MAX ||
        mtu <= 0 || mtu > UINT16_MAX || tcp_timeout_seconds <= 0 ||
        udp_timeout_seconds <= 0 || max_sessions <= 0)
        goto close_and_exit;
    if (!ensure_api()) {
        result = BRIDGE_ERROR_UNAVAILABLE;
        goto close_and_exit;
    }

    copy_result = copy_bytes(env, proxy_address, MAX_ADDRESS_BYTES, 0, &address);
    if (copy_result <= 0) {
        result = copy_result < 0 ? BRIDGE_ERROR_OUT_OF_MEMORY : BRIDGE_ERROR_INVALID_INPUT;
        goto close_and_exit;
    }
    copy_result = copy_bytes(env, username, MAX_CREDENTIAL_BYTES, 1, &user);
    if (copy_result <= 0) {
        result = copy_result < 0 ? BRIDGE_ERROR_OUT_OF_MEMORY : BRIDGE_ERROR_INVALID_INPUT;
        goto close_and_exit;
    }
    copy_result = copy_bytes(env, password, MAX_CREDENTIAL_BYTES, 1, &pass);
    if (copy_result <= 0) {
        result = copy_result < 0 ? BRIDGE_ERROR_OUT_OF_MEMORY : BRIDGE_ERROR_INVALID_INPUT;
        goto close_and_exit;
    }
    copy_result = copy_bytes(env, dns_address, MAX_ADDRESS_BYTES, 0, &dns);
    if (copy_result <= 0) {
        result = copy_result < 0 ? BRIDGE_ERROR_OUT_OF_MEMORY : BRIDGE_ERROR_INVALID_INPUT;
        goto close_and_exit;
    }

    config.struct_size = (uint32_t)sizeof(config);
    config.tun_fd = tun_fd;
    config.proxy_type = (uint32_t)proxy_type;
    config.proxy_address = address.data;
    config.proxy_address_len = address.size;
    config.proxy_port = (uint16_t)proxy_port;
    config.mtu = (uint16_t)mtu;
    config.username = user.data;
    config.username_len = user.size;
    config.password = pass.data;
    config.password_len = pass.size;
    config.dns_strategy = (uint32_t)dns_strategy;
    config.dns_address = dns.data;
    config.dns_address_len = dns.size;
    config.ipv6_enabled = ipv6_enabled == JNI_TRUE ? 1 : 0;
    config.tcp_timeout_seconds = (uint64_t)tcp_timeout_seconds;
    config.udp_timeout_seconds = (uint64_t)udp_timeout_seconds;
    config.max_sessions = (uint32_t)max_sessions;

    /* Rust owns tun_fd after this call on every return path. */
    result = api.start(&config);
    tun_fd = -1;

close_and_exit:
    if (tun_fd >= 0)
        close(tun_fd);
    release_bytes(&address, 0);
    release_bytes(&dns, 0);
    release_bytes(&user, 1);
    release_bytes(&pass, 1);
    secure_clear(&config, sizeof(config));
    return result;
}

JNIEXPORT jint JNICALL
Java_com_gagaworld_modernsocks_tunnel_Tun2ProxyNative_requestStop(
    JNIEnv *env, jobject self)
{
    (void)env;
    (void)self;
    return ensure_api() ? api.request_stop() : BRIDGE_ERROR_UNAVAILABLE;
}

JNIEXPORT jint JNICALL
Java_com_gagaworld_modernsocks_tunnel_Tun2ProxyNative_waitForExit(
    JNIEnv *env, jobject self, jlong timeout_millis)
{
    uint32_t timeout;
    (void)env;
    (void)self;
    if (!ensure_api())
        return BRIDGE_ERROR_UNAVAILABLE;
    if (timeout_millis < 0)
        return BRIDGE_ERROR_INVALID_INPUT;
    timeout = timeout_millis > UINT32_MAX ? UINT32_MAX : (uint32_t)timeout_millis;
    return api.wait_for_exit(timeout);
}

JNIEXPORT jboolean JNICALL
Java_com_gagaworld_modernsocks_tunnel_Tun2ProxyNative_isRunning(
    JNIEnv *env, jobject self)
{
    (void)env;
    (void)self;
    return ensure_api() && api.is_running() == 1 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_gagaworld_modernsocks_tunnel_Tun2ProxyNative_lastExitCode(
    JNIEnv *env, jobject self)
{
    (void)env;
    (void)self;
    return ensure_api() ? api.last_exit_code() : BRIDGE_ERROR_UNAVAILABLE;
}

JNIEXPORT jlongArray JNICALL
Java_com_gagaworld_modernsocks_tunnel_Tun2ProxyNative_getStats(
    JNIEnv *env, jobject self)
{
    uint64_t uploaded = 0;
    uint64_t downloaded = 0;
    jlong values[2];
    jlongArray result;
    (void)self;
    if (ensure_api())
        api.stats(&uploaded, &downloaded);
    result = (*env)->NewLongArray(env, 2);
    if (!result)
        return NULL;
    values[0] = (jlong)uploaded;
    values[1] = (jlong)downloaded;
    (*env)->SetLongArrayRegion(env, result, 0, 2, values);
    return result;
}
