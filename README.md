<div align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="80" height="80" alt="VoxHub Logo">
  <h1>VoxHub</h1>

  <p>
    <img src="https://img.shields.io/badge/Platform-Android_7.0+-brightgreen?logo=android" alt="Platform">
    <img src="https://img.shields.io/badge/Kotlin-2.4-blue?logo=kotlin" alt="Kotlin">
    <img src="https://img.shields.io/badge/Jetpack_Compose-Material_3-purple?logo=jetpackcompose" alt="Compose">
  </p>
</div>

VoxHub 是一款面向 Android 的 AI 文字转语音客户端，通过标准系统 TTS 接口，将云端语音合成能力接入阅读器及其他支持 Android TTS 的应用。

## 功能

- **系统 TTS 集成**：实现 Android 标准 TTS 接口，无缝接入开源阅读、静读天下等各类支持系统 TTS 的第三方应用。
- **多模式语音合成**：全面支持 Xiaomi MiMo-V2.5-TTS 系列，以及计划中的更多 TTS 提供商支持。
- **本地安全与隐私保护**：API Key 加密存储；隐私优先，不保存任何朗读正文。
- **低资源占用**：安装包仅数 MB，TTS 合成时占用不到 100MB。

> *Xiaomi MiMo-V2.5-TTS 限时免费 [https://platform.xiaomimimo.com/console](https://platform.xiaomimimo.com/console)*

## 预览

<div align="center">
  <img src="docs/screenshots/reading.png" width="30%" alt="朗读试听">
  <img src="docs/screenshots/plans.png" width="30%" alt="方案管理">
  <img src="docs/screenshots/settings.png" width="30%" alt="系统设置">
</div>

## 快速开始

1. 前往 [Releases 页面](https://github.com/Procyon-3/VoxHub/releases) 下载并安装最新的 APK。
2. 从受支持的 TTS 服务提供商处申请 API Key。
3. 打开 VoxHub，在方案标签页添加一个语音合成方案并启用。
4. 在设置标签页将 VoxHub 设置为系统默认 TTS 引擎。
5. （可选）将 VoxHub 加入安卓电池优化白名单。
6. 在开源阅读、静读天下等支持系统 TTS 的第三方应用中选择系统 TTS，即可使用 VoxHub 进行语音合成。

## Q&A

**Q：为什么 TTS 运行一段时间后就不工作了？**  
A：部分系统采用较为严格的后台管理策略，VoxHub 的服务可能在转入后台后，被冻结或杀死。需要为 VoxHub 添加电池优化白名单。具体操作步骤因不同手机厂商而异。

## 目前支持的 TTS 服务提供商
- Xiaomi MiMo-V2.5-TTS

## 本地构建

可直接构建 Debug 版本：
```bash
# 克隆仓库
git clone https://github.com/Procyon-3/VoxHub.git
cd VoxHub
# 使用 Gradle 编译 Debug APK
./gradlew assembleDebug
```

## 开源许可证

本项目基于 [GPL-3.0](LICENSE) 开源。
