# MyVia — 一个为自己定制的轻量浏览器

灵感来自 [Via 浏览器](https://via浏览器)（注意：Via 本体闭源，本仓库与其无代码关系，仅参考其交互风格）。
MyVia 从零手写，基于 Android `WebView`，目标：**轻、快、完全按自己的习惯定制**。

## 当前功能（v0.2：液态玻璃主页 + 苹果式动效）

主页整屏只有两样东西：**黄金分割位置（屏高 38.2%）的液态玻璃搜索框**，和**右上角圆形玻璃菜单键**。
搜索框除液态玻璃本体外没有任何图标、提示文字或按钮。

- **液态玻璃**：AGSL 着色器（Android 13+）实现圆角 SDF + 边缘折射 + 受光面高光 + 内部模糊 + 色调；
  Android 12 降级为模糊玻璃、11 及以下为磨砂描边（`GlassCapability` 统一判定，业务代码不感知版本）
- **交互动画**（全部弹簧物理，参数集中在 `ui/motion/Springs.kt`）：
  - 点菜单键：按下缩到 0.9，松手过冲回弹 + 轻触感反馈
  - 点搜索框：弹性放大 1.03，整屏背景加模糊并压暗 12%
  - 确认搜索：搜索框飞到左上（占屏宽 60%、左边距 16dp），菜单键同时从右上角飞到它右侧，
    两者到位时做一次**碰撞形变回弹**（横向挤压 2.8%、纵向按泊松比收缩）
  - 加载中：圆角边框上有**流光顺时针绕行**（1.6s/圈，尾部渐隐），经过处玻璃边缘高光同步增强；
    加载完成流光收束淡出
  - 长网址：首尾各 12dp 渐隐，不硬截断
- **下拉菜单**：液态玻璃面板从菜单键位置展开，**可视区固定 4 项**，超出可滚动（惯性 + 越界回弹 + 上下渐隐）；
  条目表驱动（书签 / 历史 / 下载 / 隐身 / 分享 / 添加书签 / 电脑模式 / 工具箱 / 设置），加功能只加一行
- **浏览器内核**：地址栏直达 / 搜索解析、多标签（ViewPager2 + 稳定 id）、广告拦截（内置表 + 自定义黑名单）、
  UA 切换（含电脑模式）、深色站点补偿、可被其他 App 拉起

> 玻璃观感调参入口：`ui/glass/GlassTokens.kt`（圆角、折射带宽与强度、高光、流光周期与颜色、背景模糊等）。

## 目录结构

```
app/src/main/java/com/n1jika/myvia/
├── MainActivity.kt              # Compose 承载外壳，保留 WebView 内核逻辑
├── Prefs.kt                     # SharedPreferences 统一配置
├── browser/                     # WebView 内核（View 体系，未改动）
│   ├── BrowserView.kt / BrowserFragment.kt / BrowserEngine.kt
│   ├── AdBlocker.kt / UrlUtils.kt
├── tab/                         # 标签数据与 ViewPager2 适配
├── settings/                    # 旧版设置页（P4 将重写为液态玻璃版）
└── ui/                          # 新 UI 外壳（Compose）
    ├── BrowserRoot.kt           # 三态状态机（主页/编辑/浏览）+ 飞行动画编排
    ├── ScreenCapture.kt         # 抓窗口快照给玻璃当背景
    ├── glass/                   # 玻璃基座：GlassSurface / 着色器 / Token / 能力分级 / 背景源
    ├── motion/                  # Springs / 碰撞形变 / 触感反馈
    ├── home/                    # 主页背景（程序生成渐变，后续支持自定义图片）
    ├── addressbar/              # 地址栏（极简、首尾渐隐）
    └── menu/                    # 液态玻璃下拉菜单 + 手绘图标
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
