# TunnelLens Android 技术栈与产品基线

> 用途：TunnelLens 的产品、架构、安全、测试和实施基线。  
> 正式名称：TunnelLens。  
> 定位：Android VPN-to-Debug-Proxy 转发桥。  
> 应用层：Kotlin + Jetpack Compose + Material 3。  
> 网络边界：Android `VpnService` + 经审核并固定版本的多协议 TUN 核心。  
> 更新日期：2026-07-13。

## 1. 产品定位

TunnelLens 面向 Android 开发、测试和接口调试场景。它通过 `VpnService` 捕获整机或指定应用的网络流量，并稳定转发到用户明确配置的桌面调试代理，解决应用忽略 Wi-Fi/系统代理、代理配置易丢失和按应用调试困难的问题。

首要兼容目标是 Charles 与 Reqable，但领域模型、UI 文案和网络边界不得依赖任何单一厂商。后续兼容其他标准代理时，应优先通过标准协议实现，不增加工具专属转发逻辑。

核心价值：

- 无需 root，以本地 VPN 覆盖不遵循系统代理的 Android 应用。
- 支持全局或按应用转发，并可选择是否绕过局域网。
- 支持标准 SOCKS5 与 HTTP/HTTPS 调试代理配置。
- 提供明确的协议握手、连通性、认证和生命周期诊断。
- 不收集请求内容；请求查看、重写和 HTTPS 解密由桌面调试工具完成。

### 1.1 协议定义

| UI 类型 | 线上协议 | 目标用途 |
|---|---|---|
| SOCKS5 | RFC 1928 CONNECT；无认证或用户名/密码 | Charles SOCKS、Reqable SOCKS5、自定义 SOCKS5 |
| HTTP/HTTPS 调试代理 | 普通 HTTP 使用代理语义；HTTPS 目标使用 HTTP `CONNECT` | Charles、Reqable 和其他标准 HTTP 调试代理 |

这里的“HTTPS”表示通过 HTTP 代理转发 HTTPS 目标，通常使用 `CONNECT` 建立隧道。它不表示 TunnelLens 在手机端签发证书或解密 TLS，也不承诺第一版支持“到代理服务器本身使用 TLS 加密”的 HTTPS Proxy endpoint。

### 1.2 明确非目标

- 不在手机端展示、存储、搜索或修改 HTTP 请求和响应。
- 不生成或安装中间人 CA，不绕过 Certificate Pinning。
- 不实现 Shadowsocks、VLESS、VMess、Trojan、订阅或代理链。
- 不开发代理服务端，不自动扫描局域网端口。
- 不使用 root、iptables 或修改系统全局代理。
- UDP、VPN DNS 和 IPv6 必须独立实现并通过泄漏测试后才可宣称支持。
- 不把 SOCKS5 或局域网明文 HTTP Proxy 宣传成加密 VPN。

## 2. 当前实现与目标实现

当前可运行版本仍使用已审核的 `hev-socks5-tunnel` 2.14.4，仅支持 IPv4 TCP → SOCKS5。它已经完成状态机、资源清理、重连、统计、按应用路由和局域网绕过验证。

`SocksVpnService` 已经改为只依赖协议无关的 `TunnelEngine`；当前由 `HevSocks5TunnelEngine` 适配既有 JNI。tun2proxy 0.8.2 已具备固定 Rust/Cargo 源码构建和安全 FFI 候选，但只在显式 `-Ptun2proxyPoc=true` 时进入候选测试 APK，从未接入 Service 运行时。全应用 GPL-3.0-or-later 路线已于 2026-07-14 获得确认；其分发义务与剩余技术阻断项记录在 `docs/DISTRIBUTION.md`、`docs/TUN2PROXY_DEPENDENCY_REVIEW.md` 和 `docs/CARGO_LOCK_REVIEW.md`。

产品转向采用渐进迁移：

1. 先完成 TunnelLens 品牌、通用 UI 和文档基线。
2. Room 配置模型加入 `ProxyProtocol`，现有记录迁移为 `SOCKS5`。
3. 审核并固定支持 HTTP 与 SOCKS5 的新 TUN 核心。
4. 新核心通过同一 `TunnelEngine` 接口接入，完成 Charles/Reqable 双协议真机验收后再替换默认运行时。
5. 在新核心完全达到现有停止与 soak 标准前，不移除 hev 源码和回退能力。

`tun2proxy` 顶层使用 MIT 许可证，提供 Android/TUN fd、HTTP Proxy 与 SOCKS5 支持，并有持续发布；锁文件中的 `socks5-impl` 是 GPL-3.0-or-later，因此 TunnelLens 整体采用 GPL-3.0-or-later 并履行对应源码与声明分发义务。许可证准入不等于生产技术批准；禁止直接引入 latest 分支、来源不明 `.so`，或在技术门禁通过前让普通构建携带该二进制。

## 3. 已采用 Android 技术栈

| 领域 | 采用方案 |
|---|---|
| 语言 | Kotlin 2.2.21；应用层全部 Kotlin |
| 构建 | Gradle 9.3.1、AGP 9.1.1、Kotlin DSL、Version Catalog |
| JDK | JDK 21 |
| SDK | compileSdk 36.1、targetSdk 36、minSdk 26 |
| UI | Jetpack Compose、Material 3、单 `ComponentActivity` |
| 导航 | 稳定版 Navigation Compose |
| 状态 | 不可变 `UiState`、ViewModel、`StateFlow`、单向数据流 |
| 生命周期 | `collectAsStateWithLifecycle()` |
| 并发 | Kotlin Coroutines；IO/网络/native 不阻塞主线程 |
| 数据库 | Room + Flow |
| 偏好 | Preferences DataStore |
| 序列化 | kotlinx.serialization |
| 凭据 | Android Keystore + AES-GCM |
| VPN | Android `VpnService` + foreground service |
| 当前 native | 固定并源码构建的 hev-socks5-tunnel 2.14.4 |
| 候选 native | tun2proxy 0.8.2 + Rust/Cargo 1.88.0；GPL 路线已接受，因技术门禁仅允许显式 POC 构建 |
| 目标 native | 经审核且许可证兼容的多协议 TUN 核心 |
| ABI | arm64-v8a、armeabi-v7a、x86_64 |
| 测试 | JUnit、coroutines-test、Compose UI Test、AndroidX Test、Room device tests |

继续保持单 `app` 模块。只有 Rust/native 构建隔离或测试复用出现现实需求时才拆模块。

## 4. 分层和依赖方向

```text
Compose Screen
    │ immutable UiState / actions
    ▼
ViewModel + StateFlow
    ├── ProfileRepository ── Room
    ├── SettingsRepository ─ DataStore
    ├── CredentialStore ──── Keystore + AES-GCM
    └── VpnController
             │
             ▼
      DebugProxyVpnService
       ├── VpnService.Builder / TUN
       ├── notification / network callback
       ├── protocol-specific preflight
       └── TunnelEngine
              ├── HevSocks5Engine（当前）
              └── MultiProxyEngine（目标）
                       ├── SOCKS5
                       └── HTTP + HTTPS CONNECT
```

依赖只指向接口和领域模型：Composable 不访问 Repository、Service 或 JNI；ViewModel 不持有 Activity、View、NavController 或 native 对象；Service 是 TUN、回调、native 会话和前台通知的唯一所有者。

### 4.1 目标领域接口

```kotlin
enum class ProxyProtocol {
    SOCKS5,
    HTTP,
}

interface TunnelEngine {
    suspend fun start(session: TunnelSession): TunnelStartResult
    suspend fun stop(): TunnelStopResult
    fun metrics(): TunnelMetrics
}

interface ProxyProbe {
    suspend fun probe(endpoint: ProxyEndpoint): ProxyProbeResult
}
```

`HTTP` 在 UI 中显示为“HTTP/HTTPS 调试代理”。协议探测必须与配置类型匹配：SOCKS5 使用 method/auth 握手；HTTP 使用受控的 `CONNECT` 或标准代理探测。不得用“TCP 端口能连接”冒充协议可用。

## 5. UI 信息架构

TunnelLens 使用常见的应用外壳：

- 紧凑屏幕顶栏左侧为菜单按钮，打开导航抽屉。
- 顶栏右侧为三点溢出菜单，放置当前可执行的次级入口。
- 抽屉包含首页、代理配置和设置；不再依赖底部导航表达主结构。
- 大屏可改用 Navigation Rail 或 Permanent Drawer，但路由和语义保持一致。
- 编辑配置和应用路由是嵌套页面，使用返回按钮和页面级保存动作，不显示主抽屉按钮。

主要页面：

- 首页：当前调试代理、协议、VPN 状态、连接按钮、时长和聚合流量。
- 代理配置：新建、编辑、复制、删除、选择、导入导出和协议测试。
- 配置编辑：协议、名称、Host、Port、认证为基础项；MTU、重连、LAN 绕过和按应用路由为高级项。
- 设置：语言、主题、动态颜色、连接行为、安全说明和版本/许可信息。

所有文本进入默认英文与 `values-zh-rCN`；支持字体缩放、48dp 触摸目标、屏幕阅读器语义、深色模式、动态颜色和稳定后备配色。图标不能成为状态的唯一表达方式。

## 6. 数据模型和迁移

目标 `ProxyProfile` 至少包含：

```kotlin
data class ProxyProfile(
    val id: Long,
    val name: String,
    val protocol: ProxyProtocol,
    val host: String,
    val port: Int,
    val hasStoredCredentials: Boolean,
    val mtu: Int,
    val autoReconnect: Boolean,
    val bypassLan: Boolean,
    val appRoutingPolicy: AppRoutingPolicy,
)
```

- Room schema 2→3 增加非空协议列，旧配置默认迁移为 `SOCKS5`。
- UI/领域模型不暴露密文、IV 或已有密码。
- SOCKS5 用户名/密码及 HTTP Proxy 凭据复用版本化 `CredentialStore` envelope。
- 导入导出格式升级时保留显式版本；旧文档按 SOCKS5 解释。
- 默认导出不包含凭据；任何含凭据导出均不在当前范围。

校验规则：名称非空；Host 接受合法域名、IPv4、IPv6；Port 1–65535；MTU 1280–9000；用户名/密码遵守目标协议长度和编码限制；所有 native/URI/导入输入均视为不可信。

## 7. VPN 状态机和生命周期

```text
DISCONNECTED
    │ connect
    ▼
PREPARING_PERMISSION
    ▼ granted
STARTING ── deterministic failure ──► ERROR
    │ protocol probe / TUN / engine ready
    ▼
CONNECTED ── transient failure ──► RECONNECTING
    ▲                                  │ bounded retry
    └──────────────────────────────────┘
    │ stop / revoke / fatal exit
    ▼
STOPPING ── cleanup complete ──► DISCONNECTED
```

- 每次启动使用 generation/token，旧任务不得覆盖新状态。
- 用户停止先取消重连，再进入统一幂等清理。
- 启动前协议探测发生在 TUN 建立前，不调用 `protect()`。
- TUN 建立后的健康探测和所有上游 socket 必须绕过 VPN，防止递归。
- 停止必须有上界；正常、取消、异常、撤销、Service 销毁均关闭 TUN fd、native duplicate、socket、callback、job 和通知。
- 认证/协议不匹配等确定性错误不自动无限重试。

## 8. Native 核心准入规则

任何新核心接入前必须记录：

- 官方仓库、许可证、固定 tag/commit 和源码 checksum。
- 完整传递依赖许可证；顶层宽松许可证不能替代 lock graph 审查。
- 活跃维护证据、Android 支持、TUN fd API、支持的协议与认证方式。
- Rust/C/C++ 工具链、NDK 版本、ABI、可复现源码构建命令。
- JNI/C ABI 边界、线程模型、停止语义、fd 所有权、socket protect 方案。
- 日志与配置脱敏、崩溃符号、16 KiB page size、供应链风险。
- Charles 与 Reqable 的 HTTP、HTTPS CONNECT、SOCKS5 实测结果。
- 快速启停、长连接、并发连接、重连和网络切换 soak。

不得因为候选核心宣称支持 UDP/IPv6/DNS 就直接在产品中开启。每项能力仍需单独泄漏和生命周期验收。

## 9. 安全与隐私

- 不记录目标 URL、Host 历史、请求头、请求体、响应或解密内容。
- 不记录凭据、解密字符串、认证报文或完整 native 配置。
- 凭据只在连接所需的最短范围解密；临时 byte buffer 尽可能清零。
- HTTPS 内容能否在桌面工具中查看，取决于目标应用对该工具 CA 的信任和证书锁定策略。
- TunnelLens 可以给出说明，但不安装 CA、不修改其他应用信任、不提供 pinning bypass。
- HTTP/SOCKS5 到局域网调试代理通常没有传输加密，UI 必须明确提示只应连接可信网络和可信电脑。
- 不提交真实代理地址、密码、签名密钥、开发者账号或机器绝对路径。

## 10. Charles 与 Reqable 兼容验收

首版多协议发布前，使用同一 Pixel 6 和同一局域网分别验证：

| 工具 | SOCKS5 | HTTP | HTTPS 目标经 CONNECT | 备注 |
|---|---:|---:|---:|---|
| Charles | 必测 | 必测 | 必测 | 验证 Access Control、CA 信任说明和连接清理 |
| Reqable | 必测 | 必测 | 必测 | 验证 HTTP/1.x、HTTP/2、WebSocket；QUIC 不在 TCP 首版范围 |

每组至少覆盖：无认证、错误协议、端口不可达、长连接、20 次快速启停、并发请求、Wi-Fi 切换、锁屏、用户停止和代理进程退出。测试只使用临时本地配置，不写入仓库。

## 11. 测试与交付

- 单元：协议模型、迁移、验证、凭据、探测、状态机、重连和 ViewModel。
- JVM/Native：配置编码、C ABI/JNI 输入、并发停止、native exit 和统计。
- Compose：抽屉、溢出菜单、导航、协议选择、表单错误、深色/大字体和无障碍。
- 设备：Room migration、Keystore、VpnService、按应用路由、LAN 绕过和代理兼容矩阵。
- 每次相关变更运行最窄测试；交付前运行 `testDebugUnitTest`、`lintDebug`、`assembleDebug`，设备可用时运行 `connectedDebugAndroidTest`。
- 修复本次引入的编译失败、测试失败和高严重度 Lint 后才可交付。

## 12. 重新定位后的实施顺序

### R0 — 品牌与产品基线

- TunnelLens 名称、图标、通用顶栏/抽屉/溢出菜单。
- 重写技术栈、架构、README、开发规则和计划。
- 保持当前 SOCKS5 运行时可构建可连接。

### R1 — 通用代理配置

- `ProxyProtocol`、Room 2→3、导入格式迁移。
- Profile UI 增加 SOCKS5 / HTTP/HTTPS 调试代理选择。
- 协议特定校验、探测和错误文案。

### R2 — 多协议核心

- 完成 tun2proxy 或替代候选的固定版本审查。
- 引入 `TunnelEngine` 边界和多协议 native 实现。
- 迁移现有 protect、统计、停止与资源所有权测试。

### R3 — Charles / Reqable 验收

- 完成双工具、双代理协议兼容矩阵。
- 加入首次使用、CA 信任、pinning/QUIC 限制和诊断指南。
- 通过长连接、并发、快速启停、重连和网络切换 soak。

### R4 — 独立高级能力

- DNS、UDP、IPv6 逐项设计、实现和泄漏验收。
- 不与 R1/R2 混合开启。

### R5 — 发布

- 隐私政策、VpnService 声明、许可证、R8、native symbols、CI 和签名流程。

## 13. 完成定义

任务只有在以下条件满足时完成：

- 文档与当前实现、目标阶段和用户可见文案一致。
- Debug 构建成功，相关测试通过，无新增高严重度 Lint。
- 正常、错误、取消、重连、撤销和销毁路径均释放资源。
- 默认英文与简体中文资源完整同步。
- UI 支持深色、动态颜色、字体缩放、无障碍和合理屏幕尺寸。
- 不包含真实端点、凭据、密钥、未知二进制或机器路径。

## 14. 参考

- Android VPN：<https://developer.android.com/develop/connectivity/vpn>
- Android Architecture：<https://developer.android.com/topic/architecture/recommendations>
- Compose Material 3：<https://developer.android.com/develop/ui/compose/components>
- Charles SSL Proxying：<https://www.charlesproxy.com/documentation/proxying/ssl-proxying/>
- Reqable：<https://github.com/reqable/reqable-app>
- hev-socks5-tunnel：<https://github.com/heiher/hev-socks5-tunnel>
- tun2proxy：<https://github.com/tun2proxy/tun2proxy>
