# YTMusicHyperLyric

一个按 [HyperLyric 插件开发文档](https://github.com/limczhh/HyperLyric/blob/main/docs/plugin-development.md) 初始化、并已针对目标设备改为 LSPosed 直加载的歌词模块源码仓库。

当前推荐使用 `:modules:xposed-lrclib`：

- 作为传统 Xposed/LSPosed APK 注入 `com.android.systemui`；
- 监听 YouTube Music 的 MediaSession，按标题、歌手、专辑和时长查询 LRCLIB；
- 仅接受同步歌词，转换为 Lyricon `Song`/`RichLyricLine` 并发布给 HyperLyric；
- 处理快速切歌、无歌词、网络失败和非法时间轴，不让旧结果写回新歌曲。

`plugins:lrclib` 仍保留为原始 HyperLyric ZIP 实现，但在当前 HyperOS/HyperLyric v7.4 上会因宿主动态加载可写 DEX 失败，不作为推荐交付物。

## 开始开发

1. 在 `Modules/xposed-lrclib/src/main/java/.../LrclibXposedModule.kt` 扩展媒体监听、查询或模型映射。
2. 构建并安装 Debug APK：

```powershell
.\gradlew.bat :modules:xposed-lrclib:assembleDebug --max-workers=2
adb install -r Modules/xposed-lrclib/build/outputs/apk/debug/xposed-lrclib-debug.apk
```

在 LSPosed 启用 `YouTube Music HyperLyric`，作用域只选 `System UI`，然后重启 SystemUI。HyperLyric 本体保持启用并选择「Lyricon」歌词源。

日志：

```powershell
adb logcat -b all -d -v threadtime -s YTMusicHyperLyric:V HyperLyric:V '*:S'
```
