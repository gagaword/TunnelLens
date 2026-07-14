# Phase 3 Exit Investigation

## Symptom

On a Pixel 6 running Android 13, Chrome traffic through the loopback SOCKS5 fixture could leave the UI in `NATIVE_STOP_TIMEOUT`. Android released the VPN network and Service, but the process-global hev runtime rejected the next start.

## Localization

Temporary lifecycle markers showed that `hev_socks5_tunnel_stop()` successfully wrote its event twice, while `event_task_entry()` never received it. Therefore the hang was before session termination, both lwIP task joins, task-system return, and teardown.

An LLDB attachment to the debuggable arm64 process captured the scheduler pthread at:

```text
lwip_io_task_entry
  -> ip4_input
    -> udp_input
      -> memp_malloc
```

All Android `VpnService.protect(fd)` callbacks had already returned, excluding the protector callback as the active blocker. The local fixture amplified the issue because it does not proxy TLS and browsers retry aggressively, but the invalid phase boundary existed independently of the fixture.

## Root cause and repair

Phase 3 disabled UDP only by wrapping `hev_socks5_session_udp_new()`. That occurs after UDP packets have already entered lwIP. Under repeated UDP traffic the cooperative scheduler remained in the lwIP input path and did not schedule the stop-event task.

The final repair is deliberately narrow:

- compile hev with `MODERNSOCKS_IPV4_TCP_ONLY`;
- immediately discard short, non-IPv4, and non-TCP TUN packets before `netif.input()`;
- retain UDP-session rejection as defense in depth;
- wait for Android socket protection through a nonblocking pipe and hev cooperative I/O, not a blocking wait on the scheduler pthread;
- publish `Disconnected` from `SocksVpnService.onDestroy()` so a new generation cannot race the previous Service teardown;
- backport official upstream commit `cf312ec`, which initializes the stop-event byte source.

No pthread cancellation, process killing, credential logging, or extended stop timeout is used.

## Verification

Local-only SOCKS5 fixture and `adb reverse` results on Pixel 6 / Android 13:

- 20 rapid connect/stop/reconnect cycles: passed;
- five Chrome concurrency rounds, 15 navigation requests per round: passed;
- 60-second sustained VPN connection followed by stop: passed;
- post-run Service records: zero;
- post-run VPN network records: zero.

Automated unit, instrumentation, Lint, and three-ABI build results are recorded in the phase completion report.

## Upstream disposition

The generic upstream code intentionally supports UDP, so ModernSocks' early TCP-only filter is application-specific and should not be proposed as an upstream behavioral change. The only applicable upstream fix already exists as commit `cf312ec` and is backported locally pending the next reviewed stable release. No speculative upstream issue was filed.
