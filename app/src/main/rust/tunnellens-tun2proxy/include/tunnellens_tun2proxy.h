#ifndef TUNNELLENS_TUN2PROXY_H
#define TUNNELLENS_TUN2PROXY_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define TUNNELLENS_TUN2PROXY_ABI_VERSION 1u
#define TUNNELLENS_TUN2PROXY_PROXY_SOCKS5 1u
#define TUNNELLENS_TUN2PROXY_PROXY_HTTP 2u
#define TUNNELLENS_TUN2PROXY_DNS_DIRECT 0u
#define TUNNELLENS_TUN2PROXY_DNS_OVER_TCP 1u
#define TUNNELLENS_TUN2PROXY_DNS_VIRTUAL 2u

typedef struct TunnelLensTun2ProxyConfig {
    uint32_t struct_size;
    int32_t tun_fd;
    uint32_t proxy_type;
    const uint8_t *proxy_address;
    size_t proxy_address_len;
    uint16_t proxy_port;
    uint16_t mtu;
    const uint8_t *username;
    size_t username_len;
    const uint8_t *password;
    size_t password_len;
    uint32_t dns_strategy;
    const uint8_t *dns_address;
    size_t dns_address_len;
    uint8_t ipv6_enabled;
    uint8_t reserved[7];
    uint64_t tcp_timeout_seconds;
    uint64_t udp_timeout_seconds;
    uint32_t max_sessions;
} TunnelLensTun2ProxyConfig;

uint32_t tunnellens_tun2proxy_abi_version(void);
const char *tunnellens_tun2proxy_version(void);

/* Consumes config->tun_fd on every call when it is non-negative. */
int32_t tunnellens_tun2proxy_start(const TunnelLensTun2ProxyConfig *config);
int32_t tunnellens_tun2proxy_request_stop(void);
int32_t tunnellens_tun2proxy_wait_for_exit(uint32_t timeout_millis);
int32_t tunnellens_tun2proxy_is_running(void);
int32_t tunnellens_tun2proxy_last_exit_code(void);
void tunnellens_tun2proxy_stats(uint64_t *uploaded, uint64_t *downloaded);

#ifdef __cplusplus
}
#endif

#endif
