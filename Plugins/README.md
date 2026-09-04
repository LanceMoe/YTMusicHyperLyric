# HyperLyric plugins

插件模块遵循 HyperLyric 的 Plugin API 约定。`api` 只用于编译期，宿主在运行时通过父 ClassLoader 提供，不能打包进插件 DEX。

## 构建

```powershell
.\gradlew.bat :plugins:lrclib:packageDebugPlugin --max-workers=2
.\gradlew.bat :plugins:lrclib:packagePlugin --max-workers=2
```

生成文件位于 `Plugins/modules/lrclib/build/outputs/plugin/`。

插件安装、卸载或代码升级后需要重启 SystemUI；启用/停用和普通设置变化无需重启。
