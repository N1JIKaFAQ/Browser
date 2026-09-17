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

## 开发环境（本机已配好，命令行构建）

本机安装位置与版本（均为纯 ASCII 路径，避开 Windows 中文路径坑）：

- **JDK 17**：`D:\Java\jdk-17`（Temurin，`JAVA_HOME` 已设为用户环境变量）
- **Android SDK**：`D:\Android\Sdk`（`ANDROID_HOME` 已设；cmdline-tools 布局在 `cmdline-tools\latest`；已装 `platform-tools` / `platforms;android-34` / `build-tools;34.0.0`，许可已接受）
- **Gradle**：无需单装，wrapper 自动拉 8.7；缓存重定向到 `D:\Android\.gradle`（`GRADLE_USER_HOME`，避免占 C 盘）
- **local.properties**（不入库）：`sdk.dir=D:/Android/Sdk`
- **PATH** 已追加：`%JAVA_HOME%\bin`、`%ANDROID_HOME%\cmdline-tools\latest\bin`、`%ANDROID_HOME%\platform-tools`（新开终端生效）

### 构建 / 安装

```bat
cd /d D:\手机浏览器项目
gradlew.bat assembleDebug
```

APK 产出：`app\build\outputs\apk\debug\app-debug.apk`（当前 debug 约 6MB）。
手机开 USB 调试后：

```bat
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

> 项目目录名含中文，`gradle.properties` 已启用 `android.overridePathCheck=true`
> 跳过 AGP 的 ASCII 路径检查（SDK/JDK 都在英文路径，构建实测通过）。
> `sdk.dir` 务必用正斜杠 `D:/Android/Sdk`，反斜杠转义会触发
> “文件名、目录名或卷标语法不正确”。
> 需要 GUI/可视化编辑器/模拟器时再装 Android Studio；命令行编译不需要它。
> 国内网络若变慢，可用腾讯云镜像：`https://mirrors.cloud.tencent.com/gradle/`。

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
