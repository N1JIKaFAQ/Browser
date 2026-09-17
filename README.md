# MyVia — 一个为自己定制的轻量浏览器

灵感来自 [Via 浏览器](https://via浏览器)（注意：Via 本体闭源，本仓库与其无代码关系，仅参考其交互风格）。
MyVia 从零手写，基于 Android `WebView`，目标：**轻、快、完全按自己的习惯定制**。

## 当前功能（v0.1.0 骨架）

- 地址栏直达 / 搜索（Baidu），点击全选、非编辑态显示页面标题（Via 习惯）
- 多标签页（ViewPager2 + 稳定 id 的 TabAdapter）
- 底部导航：后退 / 前进 / 首页 / 标签列表
- 轻量广告拦截：内置常见广告域名表 + 「拦截此站点广告」逐条加黑
- 设置页：主页地址、主题（跟随系统/浅色/深色）、UA（Via 风格隐藏标识 / 默认 / 桌面站点）、无图模式
- 深色模式站点补偿（Algorithmic Darkening + 注入兜底）
- 可被其他 App 以浏览器方式拉起（http/https intent-filter）

## 目录结构

```
app/src/main/java/com/n1jika/myvia/
├── MainActivity.kt          # 主界面：地址栏 + 底部栏 + 标签容器
├── Prefs.kt                 # SharedPreferences 统一配置
├── browser/
│   ├── BrowserView.kt       # 共享配置的 WebView（每标签一个实例）
│   ├── BrowserFragment.kt   # 标签页 Fragment 宿主
│   ├── BrowserEngine.kt     # per-tab WebViewClient/WebChromeClient
│   ├── AdBlocker.kt         # 广告规则匹配
│   └── UrlUtils.kt          # 地址栏输入解析
├── tab/
│   ├── TabItem.kt / TabManager.kt / TabAdapter.kt
└── settings/
    └── SettingsActivity.kt  # PreferenceFragmentCompat 设置页
```

## 开发环境（本机尚未安装，装好后即可编译）

1. 安装 **Android Studio**（自带 JDK 与 SDK Manager）：https://developer.android.com/studio
   或命令行工具：JDK 17+ 与 Android SDK（cmdline-tools + platform 34 + build-tools 34）。
2. 首次用 Android Studio 打开本目录，等待 Gradle 同步（wrapper 已就位，无需本机装 Gradle）。
3. 编译安装到手机：`gradlew assembleDebug`，APK 在 `app/build/outputs/apk/debug/`。
   （手机开启 USB 调试，`adb install app\build\outputs\apk\debug\app-debug.apk`）

> 国内网络同步慢时，可在 `gradle-wrapper.properties` 的 distributionUrl 和
> `settings.gradle.kts` 仓库列表里加腾讯云镜像：`https://mirrors.cloud.tencent.com/gradle/`、
> `https://mirrors.cloud.tencent.com/nexus/repository/maven-public/`。

## 路线图（下一步从这里开始）

- [ ] 书签与历史（Room 持久化）
- [ ] 标签页持久化与冷启动恢复
- [ ] 下载管理（DownloadListener + 通知）
- [ ] 脚本注入 / 内容拦截规则（AdBlock 语法解析）
- [ ] 无痕模式、阅读模式
- [ ] 手势操作（侧滑前进后退）
- [ ] GitHub Actions 自动出 APK

## Git 约定

- `main` 为稳定分支，日常开发可开 `dev` 或 `feature/*` 分支。
- 提交信息用中文或 `feat: / fix: / chore:` 前缀均可，保持一种风格。
