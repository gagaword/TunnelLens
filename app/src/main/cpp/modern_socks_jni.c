#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <pthread.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <time.h>
#include <unistd.h>

#include "hev-main.h"
#include <hev-task.h>
#include <hev-task-io.h>

#define RESULT_OK 0
#define RESULT_ALREADY_RUNNING -100
#define RESULT_INVALID_INPUT -101
#define RESULT_OUT_OF_MEMORY -102
#define RESULT_THREAD_FAILED -103
#define RESULT_NOT_RUNNING -104
#define RESULT_WAIT_TIMEOUT -105
#define MAX_CONFIG_BYTES 65536
#define MAX_TRACKED_SOCKETS 4096

typedef enum {
    STATE_IDLE,
    STATE_STARTING,
    STATE_RUNNING,
    STATE_EXITED,
} TunnelState;

typedef struct {
    unsigned char *config;
    unsigned int config_len;
    int tun_fd;
} TunnelArguments;

static JavaVM *java_vm;
static pthread_mutex_t state_mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t state_condition = PTHREAD_COND_INITIALIZER;
static pthread_t tunnel_thread;
static TunnelState tunnel_state = STATE_IDLE;
static int tunnel_result = RESULT_NOT_RUNNING;
static int thread_join_pending;
static jobject socket_protector;
static jmethodID protect_method;
static struct timespec start_time;
static pthread_mutex_t tun_fd_mutex = PTHREAD_MUTEX_INITIALIZER;
static int active_tun_fd = -1;
static pthread_mutex_t socket_set_mutex = PTHREAD_MUTEX_INITIALIZER;
static int tracked_sockets[MAX_TRACKED_SOCKETS];
static size_t tracked_socket_count;

static void secure_clear(void *value, size_t size)
{
    volatile unsigned char *bytes = value;
    while (size--)
        *bytes++ = 0;
}

typedef struct {
    int fd;
    int result;
    int signal_fd;
} ProtectCall;

static void *protector_entry(void *data)
{
    ProtectCall *call = data;
    JNIEnv *env = NULL;
    unsigned char completed = 1;
    if ((*java_vm)->AttachCurrentThread(java_vm, &env, NULL) != JNI_OK) {
        (void)write(call->signal_fd, &completed, sizeof(completed));
        return NULL;
    }
    call->result = (*env)->CallBooleanMethod(env, socket_protector,
                                             protect_method, call->fd);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        call->result = 0;
    }
    (void)write(call->signal_fd, &completed, sizeof(completed));
    (*java_vm)->DetachCurrentThread(java_vm);
    return NULL;
}

static int protect_socket(int fd)
{
    pthread_t worker;
    int signal_pipe[2];
    unsigned char completed;
    ssize_t read_result;
    ProtectCall call = { .fd = fd, .result = 0, .signal_fd = -1 };
    if (!socket_protector || !protect_method)
        return 0;
    if (pipe(signal_pipe) != 0)
        return 0;
    if (fcntl(signal_pipe[0], F_SETFL, O_NONBLOCK) != 0) {
        close(signal_pipe[0]);
        close(signal_pipe[1]);
        return 0;
    }
    call.signal_fd = signal_pipe[1];
    if (pthread_create(&worker, NULL, protector_entry, &call) != 0) {
        close(signal_pipe[0]);
        close(signal_pipe[1]);
        return 0;
    }
    /* This runs on an hev cooperative task stack. Waiting through hev's I/O
     * API yields the task instead of blocking the single scheduler pthread,
     * so the stop event and other sessions remain runnable. */
    read_result = hev_task_io_read(signal_pipe[0], &completed,
                                   sizeof(completed), NULL, NULL);
    pthread_join(worker, NULL);
    close(signal_pipe[0]);
    close(signal_pipe[1]);
    return read_result == (ssize_t)sizeof(completed) && call.result;
}

int __real_socket(int domain, int type, int protocol);
int __real_close(int fd);

static int track_socket(int fd)
{
    int result = 0;
    pthread_mutex_lock(&socket_set_mutex);
    if (tracked_socket_count < MAX_TRACKED_SOCKETS) {
        tracked_sockets[tracked_socket_count++] = fd;
        result = 1;
    }
    pthread_mutex_unlock(&socket_set_mutex);
    return result;
}

static void untrack_socket(int fd)
{
    pthread_mutex_lock(&socket_set_mutex);
    for (size_t index = 0; index < tracked_socket_count; index++) {
        if (tracked_sockets[index] == fd) {
            tracked_sockets[index] = tracked_sockets[--tracked_socket_count];
            break;
        }
    }
    pthread_mutex_unlock(&socket_set_mutex);
}

int __wrap_close(int fd)
{
    untrack_socket(fd);
    return __real_close(fd);
}

int __wrap_socket(int domain, int type, int protocol)
{
    int fd = __real_socket(domain, type, protocol);
    if (fd < 0)
        return fd;
    if ((domain == AF_INET || domain == AF_INET6) &&
        (!protect_socket(fd) || !track_socket(fd))) {
        close(fd);
        errno = EPERM;
        return -1;
    }
    return fd;
}

static void shutdown_tracked_sockets(void)
{
    pthread_mutex_lock(&socket_set_mutex);
    for (size_t index = 0; index < tracked_socket_count; index++)
        shutdown(tracked_sockets[index], SHUT_RDWR);
    pthread_mutex_unlock(&socket_set_mutex);
}

/* Phase 3 is deliberately TCP-only. Returning NULL makes upstream drop the
 * lwIP UDP flow without creating a SOCKS5 UDP session. */
void *__wrap_hev_socks5_session_udp_new(void *pcb, void *mutex)
{
    (void)pcb;
    (void)mutex;
    return NULL;
}

static void *tunnel_entry(void *data)
{
    TunnelArguments *arguments = data;
    int result;
    JNIEnv *env = NULL;

    pthread_mutex_lock(&state_mutex);
    tunnel_state = STATE_RUNNING;
    pthread_cond_broadcast(&state_condition);
    pthread_mutex_unlock(&state_mutex);

    result = hev_socks5_tunnel_main_from_str(arguments->config,
                                              arguments->config_len,
                                              arguments->tun_fd);
    pthread_mutex_lock(&tun_fd_mutex);
    if (active_tun_fd == arguments->tun_fd) {
        close(active_tun_fd);
        active_tun_fd = -1;
    }
    pthread_mutex_unlock(&tun_fd_mutex);
    secure_clear(arguments->config, arguments->config_len);
    free(arguments->config);
    secure_clear(arguments, sizeof(*arguments));
    free(arguments);
    if ((*java_vm)->AttachCurrentThread(java_vm, &env, NULL) == JNI_OK) {
        if (socket_protector)
            (*env)->DeleteGlobalRef(env, socket_protector);
        (*java_vm)->DetachCurrentThread(java_vm);
    }
    socket_protector = NULL;
    protect_method = NULL;

    pthread_mutex_lock(&state_mutex);
    tunnel_result = result;
    tunnel_state = STATE_EXITED;
    pthread_cond_broadcast(&state_condition);
    pthread_mutex_unlock(&state_mutex);
    return NULL;
}

JNIEXPORT jint JNICALL
Java_com_gagaworld_modernsocks_tunnel_NativeTunnel_start(
    JNIEnv *env, jobject self, jint tun_fd, jbyteArray config, jobject protector)
{
    (void)self;
    jsize config_len;
    jclass protector_class;
    TunnelArguments *arguments;
    int create_result;

    if (tun_fd < 0 || !config || !protector) {
        if (tun_fd >= 0)
            close(tun_fd);
        return RESULT_INVALID_INPUT;
    }
    config_len = (*env)->GetArrayLength(env, config);
    if (config_len <= 0 || config_len > MAX_CONFIG_BYTES) {
        close(tun_fd);
        return RESULT_INVALID_INPUT;
    }

    arguments = calloc(1, sizeof(*arguments));
    if (!arguments) {
        close(tun_fd);
        return RESULT_OUT_OF_MEMORY;
    }
    arguments->config = malloc((size_t)config_len);
    if (!arguments->config) {
        close(tun_fd);
        free(arguments);
        return RESULT_OUT_OF_MEMORY;
    }
    (*env)->GetByteArrayRegion(env, config, 0, config_len,
                               (jbyte *)arguments->config);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        close(tun_fd);
        secure_clear(arguments->config, (size_t)config_len);
        free(arguments->config);
        free(arguments);
        return RESULT_INVALID_INPUT;
    }
    arguments->config_len = (unsigned int)config_len;
    arguments->tun_fd = tun_fd;

    pthread_mutex_lock(&state_mutex);
    /* Keep EXITED observable for every concurrent waiter. The next start is the
     * single transition back to IDLE, after reaping the previous thread if no
     * waiter has done so already. */
    if (tunnel_state == STATE_EXITED) {
        if (thread_join_pending) {
            thread_join_pending = 0;
            pthread_join(tunnel_thread, NULL);
        }
        tunnel_state = STATE_IDLE;
    }
    if (tunnel_state != STATE_IDLE) {
        pthread_mutex_unlock(&state_mutex);
        close(tun_fd);
        secure_clear(arguments->config, (size_t)config_len);
        free(arguments->config);
        free(arguments);
        return RESULT_ALREADY_RUNNING;
    }
    protector_class = (*env)->GetObjectClass(env, protector);
    protect_method = (*env)->GetMethodID(env, protector_class, "protect", "(I)Z");
    (*env)->DeleteLocalRef(env, protector_class);
    if (!protect_method) {
        (*env)->ExceptionClear(env);
        pthread_mutex_unlock(&state_mutex);
        close(tun_fd);
        secure_clear(arguments->config, (size_t)config_len);
        free(arguments->config);
        free(arguments);
        return RESULT_INVALID_INPUT;
    }
    socket_protector = (*env)->NewGlobalRef(env, protector);
    if (!socket_protector) {
        protect_method = NULL;
        pthread_mutex_unlock(&state_mutex);
        close(tun_fd);
        secure_clear(arguments->config, (size_t)config_len);
        free(arguments->config);
        free(arguments);
        return RESULT_OUT_OF_MEMORY;
    }
    tunnel_state = STATE_STARTING;
    tunnel_result = RESULT_NOT_RUNNING;
    clock_gettime(CLOCK_MONOTONIC, &start_time);
    pthread_mutex_lock(&tun_fd_mutex);
    active_tun_fd = tun_fd;
    pthread_mutex_unlock(&tun_fd_mutex);
    create_result = pthread_create(&tunnel_thread, NULL, tunnel_entry, arguments);
    if (create_result != 0) {
        tunnel_state = STATE_IDLE;
        pthread_mutex_unlock(&state_mutex);
        (*env)->DeleteGlobalRef(env, socket_protector);
        socket_protector = NULL;
        protect_method = NULL;
        pthread_mutex_lock(&tun_fd_mutex);
        if (active_tun_fd == tun_fd)
            active_tun_fd = -1;
        pthread_mutex_unlock(&tun_fd_mutex);
        close(tun_fd);
        secure_clear(arguments->config, (size_t)config_len);
        free(arguments->config);
        free(arguments);
        return RESULT_THREAD_FAILED;
    }
    thread_join_pending = 1;
    pthread_mutex_unlock(&state_mutex);
    return RESULT_OK;
}

JNIEXPORT void JNICALL
Java_com_gagaworld_modernsocks_tunnel_NativeTunnel_forceCloseTun(
    JNIEnv *env, jobject self)
{
    (void)env;
    (void)self;
    pthread_mutex_lock(&tun_fd_mutex);
    int fd = active_tun_fd;
    active_tun_fd = -1;
    pthread_mutex_unlock(&tun_fd_mutex);
    if (fd >= 0)
        close(fd);
}

JNIEXPORT void JNICALL
Java_com_gagaworld_modernsocks_tunnel_NativeTunnel_forceShutdownSockets(
    JNIEnv *env, jobject self)
{
    (void)env;
    (void)self;
    shutdown_tracked_sockets();
}

JNIEXPORT void JNICALL
Java_com_gagaworld_modernsocks_tunnel_NativeTunnel_stop(
    JNIEnv *env, jobject self)
{
    (void)env;
    (void)self;
    int should_stop;
    struct timespec now;
    int64_t elapsed_ms;

    /* Upstream quit waits for its event fd. Give fast initialization failures
     * time to publish EXITED so quit can never wait on an event fd that will
     * not be created. Normal initialization completes well inside this guard. */
    do {
        pthread_mutex_lock(&state_mutex);
        should_stop = tunnel_state == STATE_STARTING || tunnel_state == STATE_RUNNING;
        pthread_mutex_unlock(&state_mutex);
        if (!should_stop)
            return;
        clock_gettime(CLOCK_MONOTONIC, &now);
        elapsed_ms = (now.tv_sec - start_time.tv_sec) * 1000 +
                     (now.tv_nsec - start_time.tv_nsec) / 1000000;
        if (elapsed_ms >= 250)
            break;
        usleep(25 * 1000);
    } while (1);
    if (should_stop)
        hev_socks5_tunnel_quit();
}

static void deadline_from_now(struct timespec *deadline, int64_t timeout_ms)
{
    clock_gettime(CLOCK_REALTIME, deadline);
    deadline->tv_sec += timeout_ms / 1000;
    deadline->tv_nsec += (timeout_ms % 1000) * 1000000;
    if (deadline->tv_nsec >= 1000000000L) {
        deadline->tv_sec += 1;
        deadline->tv_nsec -= 1000000000L;
    }
}

JNIEXPORT jint JNICALL
Java_com_gagaworld_modernsocks_tunnel_NativeTunnel_waitForExit(
    JNIEnv *env, jobject self, jlong timeout_ms)
{
    (void)env;
    (void)self;
    int wait_result = 0;
    int result;
    int should_join = 0;
    struct timespec deadline;

    if (timeout_ms < 0)
        return RESULT_INVALID_INPUT;
    deadline_from_now(&deadline, timeout_ms);
    pthread_mutex_lock(&state_mutex);
    if (tunnel_state == STATE_IDLE) {
        pthread_mutex_unlock(&state_mutex);
        return RESULT_NOT_RUNNING;
    }
    while (tunnel_state != STATE_EXITED && wait_result == 0)
        wait_result = pthread_cond_timedwait(&state_condition, &state_mutex,
                                             &deadline);
    if (tunnel_state != STATE_EXITED) {
        pthread_mutex_unlock(&state_mutex);
        return RESULT_WAIT_TIMEOUT;
    }
    result = tunnel_result;
    if (thread_join_pending) {
        thread_join_pending = 0;
        should_join = 1;
    }
    pthread_mutex_unlock(&state_mutex);

    if (should_join)
        pthread_join(tunnel_thread, NULL);

    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_gagaworld_modernsocks_tunnel_NativeTunnel_isRunning(
    JNIEnv *env, jobject self)
{
    (void)env;
    (void)self;
    pthread_mutex_lock(&state_mutex);
    int running = tunnel_state == STATE_STARTING || tunnel_state == STATE_RUNNING;
    pthread_mutex_unlock(&state_mutex);
    return running ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlongArray JNICALL
Java_com_gagaworld_modernsocks_tunnel_NativeTunnel_getStats(
    JNIEnv *env, jobject self)
{
    (void)self;
    size_t tx_packets = 0;
    size_t tx_bytes = 0;
    size_t rx_packets = 0;
    size_t rx_bytes = 0;
    jlong values[4];
    jlongArray result = (*env)->NewLongArray(env, 4);
    if (!result)
        return NULL;
    hev_socks5_tunnel_stats(&tx_packets, &tx_bytes, &rx_packets, &rx_bytes);
    values[0] = (jlong)tx_packets;
    values[1] = (jlong)tx_bytes;
    values[2] = (jlong)rx_packets;
    values[3] = (jlong)rx_bytes;
    (*env)->SetLongArrayRegion(env, result, 0, 4, values);
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_gagaworld_modernsocks_tunnel_NativeTunnel_version(
    JNIEnv *env, jobject self)
{
    (void)self;
    return (*env)->NewStringUTF(env, MODERNSOCKS_HEV_VERSION);
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved)
{
    (void)reserved;
    java_vm = vm;
    return JNI_VERSION_1_6;
}
