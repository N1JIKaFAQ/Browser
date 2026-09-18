# MyVia — 一个为自己定制的轻量浏览器

灵感来自 [Via 浏览器](https://via浏览器)（注意：Via 本体闭源，本仓库与其无代码关系，仅参考其交互风格）。
MyVia 从零手写，基于 Android `WebView`，目标：**轻、快、完全按自己的习惯定制**。

## 当前功能（v0.2：液态玻璃主页 + 苹果式动效）

主页整屏只有两样东西：**黄金分割位置（屏高 38.2%）的液态玻璃搜索框**，和**右上角圆形玻璃菜单键**。
搜索框除液态玻璃本体外没有任何图标、提示文字或按钮。

- **液态玻璃**：AGSL 着色器，按 Cocos 教程《液态玻璃原理及其高性能实现》
  (forum.cocos.org/raw/171941) 的公式实现，而不是自己臆想的"发光磨砂"：
  - **位移向量场**：折射强度从边缘的 1 线性衰减到中心的 0（中心光线正交、不折射），
    用圆角矩形 SDF 解析式直接算出，省掉一张位移贴图
  - **折射**：采样 UV 沿位移矢量偏移（偏移指向玻璃中心），边缘因此像透镜一样压缩背景
  - **色散**：R 与 B 通道沿位移方向左右各偏移，边缘出现 2–5px 彩边
  - **轮廓光**：位移矢量长度超过阈值才算轮廓，亮心位置由位移矢量方向决定（左上 / 右下），
    最后 `mix(内容, 白, opacity)` 混入白色 —— **全流程没有一次加法**，
    所以不会自发光、不会泛光（这是之前最大的问题）
  - 总采样 3 次（R/G/B），比原帖的 4 次更省
  - 玻璃本体只做"霜化"，程度随背景明度自适应（亮背景薄、暗背景厚），保证黑字可读
  - Android 12 及以下自动降级为霜化玻璃，着色器失败也会降级而不是崩溃
- **界面配色**：内容一律黑色、不发光（地址栏网址、汉堡图标、菜单文字与图标），
  网址在地址栏内**水平居中**；青绿等彩色已从玻璃与品牌色中清除
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
