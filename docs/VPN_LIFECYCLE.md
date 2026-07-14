# VPN Lifecycle and Resource Ownership

## Phase 3 scope

After Android VPN consent, `SocksVpnService` establishes an IPv4 interface at `198.18.0.1/32` with route `0.0.0.0/0` and the selected profile MTU. It adds no DNS server, IPv6 route, allowed/disallowed application policy, network callback, or reconnect behavior. The Phase 3 native build forwards TCP only; UDP session creation is explicitly rejected.

## State contract

```text
DISCONNECTED / ERROR
        | connect (new generation)
        v
PREPARING_PERMISSION -- denied --> ERROR(PERMISSION_DENIED)
        | already granted / Activity result granted
        v
STARTING -- TUN + native startup grace --> CONNECTED
   |                                      |
   | load / TUN / native failure          | stop / action / revoke / native exit
   v                                      v
 ERROR                                 STOPPING
                                           |
                                           v
                                     DISCONNECTED
```

`VpnConnectionStateStore` accepts transitions only for the active monotonically increasing generation. `Reconnecting` remains modeled but unused until Phase 4. A native start failure, unexpected exit, or bounded-stop failure becomes a structured `ConnectionFailure`; stale jobs cannot overwrite a newer connection.

## Ownership

- `SocksVpnService` owns the original `ParcelFileDescriptor` returned by `establish()`.
- JNI receives a duplicated fd and owns that duplicate. The native core closes it on normal exit; the bounded fallback may close it once through an atomic detach.
- JNI owns its copied configuration bytes and clears them after the blocking hev entry point returns. Kotlin clears its temporary byte array immediately after `start()`.
- JNI serializes one process-global hev session and keeps `EXITED` observable to all concurrent waiters until the next start.
- Every upstream socket passes through `VpnService.protect(fd)` on a joined, short-lived JVM-attached helper pthread. Protected fds are tracked until close so shutdown can interrupt blocked sessions.
- The Service owns its `SupervisorJob`, connection job, monitor job, foreground state, and notification. No Activity, ViewModel, or Composable owns native or descriptor resources.

## Startup

The controller completes Android consent before starting the foreground Service. The Service publishes `Starting`, enters foreground promptly, loads and validates the selected connection profile, decrypts credentials only for this operation, and performs the SOCKS5 startup probe before any TUN exists. That pre-TUN socket uses the ordinary underlying route and is not passed to `VpnService.protect()`; protecting it at this point is unnecessary and can fail on some devices. The Service then establishes the TUN, duplicates its fd, generates escaped in-memory hev YAML, and starts JNI on `Dispatchers.IO`. After a short startup grace with native still running it publishes `Connected` and begins monitoring native exit. Connected health probes and all native upstream sockets are protected so they cannot recurse into the VPN.

The upstream socket wrapper synchronously waits for the helper pthread. If protection fails, the socket is closed and cannot route recursively through the VPN. The configuration uses error-only logging to `/dev/null`; no credentials or full configuration are logged.

## Unified bounded cleanup

Home stop, notification stop, `onRevoke()`, `onDestroy()`, cancellation, startup failure, and unexpected native exit all request the same generation-checked cleanup. Cleanup is idempotent:

1. publish `Stopping` when appropriate and cancel the active startup/monitor job;
2. request cooperative `hev_socks5_tunnel_quit()` and close the Service-owned TUN;
3. wait up to three seconds for native exit;
4. on timeout, shut down every tracked protected socket and force-close native's TUN duplicate, then wait a final two seconds;
5. remove foreground state/notification, publish the terminal state, clear ownership, and call `stopSelf()`;
6. `onDestroy()` repeats only idempotent native/TUN fallbacks and cancels the Service scope.

## Verified Phase 3 device behavior

On a Pixel 6 running Android 13, VPN consent and notification permission were granted. An adb-reversed, loopback-only SOCKS5 fixture verified that the UI reached **IPv4 TCP connected**, both no-auth and protected username/password paths reached the fixture, and HTTP traffic entered the tunnel. Android removed the Service, TUN, VPN network, and protected sockets on stop. No real server or credential was used.

The original stop failure was traced before `event_task_entry`: unsupported UDP packets were still entering lwIP even though SOCKS5 UDP session construction was disabled, keeping the cooperative scheduler in `lwip_io_task_entry -> ip4_input -> udp_input`. Phase 3 now rejects every non-IPv4/TCP packet immediately after TUN read. A separate race was fixed by publishing `Disconnected` only from `onDestroy()`, after the old Service has actually finished teardown.

Post-fix verification completed 20 rapid connect/stop/reconnect cycles, five concurrent Chrome traffic rounds, and a 60-second sustained connection. Every round returned to disconnected and the final Android Service/VPN record counts were zero. Details are in `docs/PHASE3_EXIT_INVESTIGATION.md`.

Phase 4 must add network switching, timeout/error classification, reconnect policy, duration/byte statistics, soak tests, and broader lifecycle/device coverage before reliability claims expand.
