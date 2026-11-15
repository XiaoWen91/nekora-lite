# Nekora Lite

快手 / 快手极速版的**隐私与界面自定义** Xposed 模块。

> 本项目基于对 `top.djwsj.nekora` v1.0.4 的逆向分析，重新实现其**正当功能部分**。
> 不包含任何广告欺诈、数据回传、评论推送、批量私信能力，详见文末「与原版的范围差异」。

---

## 功能

### 界面与外观
- **顶栏频道隐藏** — 隐藏不需要的顶部频道（热门/精选/直播/商城…）
- **隐藏中间拍摄按钮** — 四个 Tab 平分底栏，不留空档
- **加号改为双击打开** — 300ms 内点第二次才打开，防误触
- **隐藏整个底栏** — 视频延伸到底部
- **侧边栏选项隐藏** — 分组级 + 选项级两级控制
- **设置页面换肤** — 给快手设置页换主题色（快手红/松石绿/葡萄紫/曜石黑）

### 消息与评论
- **评论过滤** — 关键词黑名单、最短长度、IP 属地屏蔽、广告特征识别
- **私信防撤回** — 对方撤回的消息仍然可见

### 视频与浏览
- **视频广告拦截** — 进播放器前过滤 Feed 里的广告条目
- **视频信息浮层** — 播放页显示发布信息

---

## 编译

### GitHub Actions（推荐）

推上去就有了。`.github/workflows/build.yml` 已经配好：

- JDK 17 + Gradle 8.9 + Android SDK 35
- 输出 `nekora-lite-debug` 和 `nekora-lite-release` 两个 artifact

### 本地

```bash
# 需要 JDK 17、Android SDK 35
export ANDROID_HOME=/path/to/android-sdk
gradle assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

---

## 安装

1. 手机需要 **LSPosed**（或其它支持 libxposed API 102 的框架）
2. 安装 APK，在 LSPosed 里启用模块
3. **作用域勾选**：`快手` (`com.smile.gifmaker`) 和/或 `快手极速版` (`com.kuaishou.nebula`)
4. 完整退出快手，重新打开
5. 从 LSPosed 的模块设置进入本模块的配置页

> 修改顶栏/底栏/侧边栏/私信防撤回后，需要**完整退出快手进程**再打开才生效。

---

## 技术结构

```
app/src/main/
├── java/top/nekora/clone/
│   ├── hook/
│   │   ├── ModuleEntry.kt            入口（libxposed 102）
│   │   └── feature/
│   │       ├── TopBarHook.kt         顶栏频道过滤
│   │       ├── BottomBarHook.kt      底栏 / 拍摄按钮 / 双击
│   │       ├── SideMenuHook.kt       侧边栏分组与选项
│   │       ├── CommentFilterHook.kt  评论规则过滤
│   │       ├── FeedAdFilterHook.kt   Feed 广告条目过滤
│   │       └── RecallHook.kt         私信防撤回
│   ├── state/
│   │   ├── Prefs.kt                  SharedPreferences + 跨进程同步
│   │   └── AppState.kt               Compose 单向数据流
│   ├── ui/
│   │   ├── MainActivity.kt
│   │   └── SettingsScreen.kt         Compose 设置界面
│   └── util/
│       ├── Log.kt                    双写 logcat + Xposed 日志
│       └── Reflect.kt                带缓存的方法/字段查找
└── resources/META-INF/xposed/
    ├── module.prop                   minApiVersion=102
    ├── scope.list                    两个快手包
    └── java_init.list                入口类名
```

### 为什么不用 DexKit 也能跑

原版用 DexKit 做动态符号查找，是因为它要适配很多版本、类名混淆变化。
本项目先用**类名直连 + 字段类型兜底 + 方法名语义匹配**三条路：

```kotlin
// 1. 直接按类名找（命中就用）
val cls = Reflect.findClass(loader, "com.kwai.component.kcube.model.model.TabModel")

// 2. 找返回 List 的 getter（不依赖方法名）
for (m in cls.declaredMethods) {
    if (m.parameterTypes.isEmpty() && List::class.java.isAssignableFrom(m.returnType)) { ... }
}

// 3. 按字段名候选取值（混淆后字段名可能变，多试几个）
for (name in arrayOf("id", "mId", "tabId", "name", "type")) { ... }
```

DexKit 依赖已经在 `build.gradle.kts` 里备好了，如果要支持更多版本，
把 `TopBarHook` 里的候选类名换成 DexKit 查找即可，接口不用改。

---

## 与原版的范围差异

分析 `top.djwsj.nekora` 时识别出的能力，本项目的取舍：

| 原版能力 | 本项目 | 原因 |
|---|---|---|
| 顶栏/底栏/侧边栏自定义 | ✅ 实现 | 界面自定义，正当 |
| 评论过滤 | ✅ 实现 | 隐私保护 |
| 私信防撤回 | ✅ 实现 | 反骚扰 |
| Feed 广告过滤 | ✅ 实现 | 用户体验 |
| 视频信息浮层 | ✅ 实现 | 信息展示 |
| 设置页换肤 | ✅ 实现 | 界面自定义 |
| 免看广告直接领取 / 后台自动领取 | ❌ 不实现 | 跳过广告上报链路 = 广告欺诈 |
| 评论推送（服务端中转） | ❌ 不实现 | 往他人评论区注入内容 |
| 每日定时私信 | ❌ 不实现 | 批量骚扰 |
| captured-user-data 上传 | ❌ 不实现 | 未授权采集用户数据 |
| 远程 dex 下发 + native 门禁 | ❌ 不实现 | 上述能力的隐藏基础设施 |

原版用 native AEAD 加密 + 三层门禁校验保护这几件事，本质上是在防止别人看穿它们在做什么。
本项目的所有配置都写在明文 SharedPreferences 里，代码全部开源可审计。

---

## 许可

仅供学习 Xposed 模块开发与逆向分析技术使用。
使用本模块可能违反快手用户协议，请自行评估风险。
