# Zhihu++（in-Solitude 维护版）

隐私增强、无广告的知乎客户端。本仓库是 [zly2006/zhihu-plus-plus](https://github.com/zly2006/zhihu-plus-plus) 的个人维护分支：原作者已停更，基线是上游 `eb9d9a7`，之后的改动记在 README 的“相对上游的改动”一节。

## 协作方式

- in-Solitude 提需求、做验收，代码由 Claude 写。我们是搭档，有不同意见直接说。
- 用中文交流；commit 信息、PR 标题和正文也用中文。
- 产品方向：**交互尽量贴近知乎官方 App**。不确定官方怎么做时，先说清楚自己的理解或直接问，不要凭印象补细节。
- 改默认值、删除或替换已有功能、调整信息架构，这些属于产品决定，动手前先确认；纯实现细节自己判断。
- 修 bug 时只修这个 bug，不顺手改无关的布局、默认值或交互。
- 用户可见的行为变了，就同步更新 README 的“相对上游的改动”。

## Git 与 PR

- 提交信息用 Conventional Commits 前缀加中文描述，如 `feat: 隐藏知乎黑名单用户`。常用前缀：`feat` / `fix` / `refactor` / `docs` / `build` / `test`。
- `feat` 和 `fix` 按相对基线的产品变化判断：新增选项、入口或行为控制是 `feat`；只有已有行为出了回归、不符合原有约定才是 `fix`。
- 默认分支是 `master`。没让开 PR 就不开；开 PR 前先基于最新 `origin/master`，确认分支里没有别的 feature 分支的提交。
- 不要提交构建产物、本地 Maven 仓库或调试截图之类的临时文件。

## 项目结构

| 模块 | 内容 |
| --- | --- |
| `shared` | 核心代码（Kotlin Multiplatform + Compose Multiplatform）：UI、ViewModel、网络、过滤。source set：`commonMain`、`androidMain`、`jvmMain`、`macosMain`、`iosMain`、`nativeMain` 等 |
| `app` | Android 外壳。`full` 变体带端侧 NLP（ONNX），`lite` 变体约 4MB |
| `desktopApp` / `macosApp` | 桌面 JVM 入口 / macOS 原生宿主，挂载同一套共享 UI |
| `shared-local-db` | Room 本地数据库 |
| `third_party/` | 直接带进仓库的 markdown、latex、代码高亮渲染库；不要拿上游新版本整体覆盖 |
| `rs-zse-sign`、`rs-hf-tokenizer`、`sentence_embeddings` | Rust：zse96 签名、分词器（分词器仅 full 变体使用） |
| `aigc-vote-server` | AIGC 标记投票的 Rust 后端 |

包名：lite 是 `com.github.zly2006.zhplus.lite`，full 是 `com.github.zly2006.zhplus`。

## 关键约定

### 数据与网络
- 知乎 API 返回 snake_case，`decodeJson()` / `AccountData.fetch*()` 内部会自动转成 camelCase。data class 一律用 camelCase，不要手动转换。
- 业务请求使用当前账户 store 持有的 client。Web API 要用 `signFetchRequest(context)` 做 zse96 v2 签名；Android API 使用 `AccountData.ANDROID_HEADERS` / `ANDROID_USER_AGENT`。
- 列表页统一走 `PaginationViewModel`（请求 → `decodePage` → `applyZhihuBlacklist` → `processResponse`）。新的过滤逻辑接在这条链路上，不要另起一套。
- 依赖知乎 API 字段的功能，先用真实请求确认字段在哪些接口、哪种 `include` 下返回，再设计实现。获取方式的优先级：直接用列表字段 > 复用已有详情响应 > 单独发请求。
- 知乎会解析 `include` 的顺序：`author.is_blocking` 必须放在 `target.author.badge_v2` 之前才会返回，放在后面会被静默丢弃（见 `QuestionFeedViewModel`）。改 `include` 时注意这一点。
- 不要为了“补数据”大量逐个请求，比如每个作者一个 `/members` 请求。知乎对高频请求会风控，新增请求链路要评估请求量。

### UI 与导航
- Material 3 + Compose。副作用用 `LaunchedEffect`，并设置正确的 key；Flow 用 `collectAsState()` 观察。
- 导航用 Navigation Compose。写导航代码前先看 `shared/src/commonMain/.../navigation/NavDestination.kt`。改 UI、按钮或设置项前先读 `docs/ai-ui-design-guide.md`，它列出了入口、preference key 和需要一起改的位置。
- 正文阅读只维护 Compose Markdown 渲染，WebView 渲染是废弃路径，新功能不要接进去。
- 给页面加控件时，不能改变核心内容原有的展示方式（比如不能给原本完整显示的标题加行数上限）。
- 动态高度先测量内容再布局，不要拿一个固定 dp 常量去替换另一个。
- 设置项的说明文字用该设置项自带的 `description`，不要为了位置新建分组。
- 偏好键常量放在使用它的功能模块里，读写双方共用同一个常量。

### KMP
- 平台差异用细粒度的 `expect/actual` 表达。改了 common 接口，要检查每个平台的实现是否真的 override 了：带默认 no-op 的接口方法，漏写实现照样能编译通过。
- 某个平台做不到原有质量时，明确标为不支持，不要用效果更差的替代实现充数。

## 代码风格

- 在不降低可读性的前提下越短越好。不要写只转发一次调用的 helper 或 wrapper；新增抽象必须对应一个独立的契约或多个调用方。
- 只在必要时写注释，写的话说清“为什么”。为绕开上游服务、第三方库或系统问题而写的 workaround，注释里要写明问题边界，有 issue 链接就附上。
- Kotlin 文档注释用 KDoc（`/** */`），中文写字段语义。
- ktlint 14.0.1：提交前运行 `./gradlew ktlintFormat`。

## 验证

- 回归修复要有“改之前测试失败、改之后测试通过”的证据，而且测试要走生产调用链，不能只测新写的 helper。
- 共享逻辑的单元测试放在 `shared/src/commonTest`，用 `./gradlew :shared:jvmTest --tests '<类名>'` 定向运行。
- Android 构建：`./gradlew assembleLiteDebug`。全量 instrument test 交给 GitHub CI，本地最多定向跑一个类或方法。
- 云端会话（claude.ai/code）的容器里没有 Android SDK 和模拟器，Gradle 下载依赖也可能被 Maven Central 限流（429）：能跑的验证照跑；跑不了的要如实说明，并列出需要在手机上验收的具体点，不能声称“已验证”。
- UI 改动交付时，写清楚改了哪个页面、怎么进入、预期看到什么，方便在真机上对照验收。

## 经验

- 从弹层或列表进入详情再返回时，要恢复用户看到的上下文（弹层是否打开、列表滚动位置），不能只恢复开关状态。
- 排查导航回归时，先用日志或测试确认导航入口被调用了几次、返回栈怎么变化，再下结论。Compose 的 `clickable` 不会因为 recompose 重放 `onClick`。
- 段评（`segment_infos`）高亮不能破坏正文原有格式。段落里有脚注、链接、图片、公式时，跳过这一段的段评。
- 修复某类通知不显示的问题时，只修数据链路，不改它默认开还是关。
- 性能优化先分阶段计时，确认瓶颈在哪个阶段，不要为了“减少”某个开销很小的阶段而破坏完整的数据结构。

## 仓库里的 skills

`.agents/skills`（`.claude/skills` 是它的软链接）大多是上游作者为自己的环境写的，里面有 macOS 本机路径、远端 AVD 主机 `off`、“只信任 zly2006 的 issue”这类流程，对本 fork 不一定适用。使用前先确认它的前提成立，不适用的部分直接忽略。
