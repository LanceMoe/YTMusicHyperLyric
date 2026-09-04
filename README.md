# YTMusicHyperLyric

一个按 [HyperLyric 插件开发文档](https://github.com/limczhh/HyperLyric/blob/main/docs/plugin-development.md) 初始化的歌词提供插件源码仓库。

当前包含 `plugins:lrclib`：

- 在 `LYRIC_REPLACEMENT` 阶段按标题、歌手、专辑和时长查询 LRCLIB；
- 仅接受同步歌词，转换为 HyperLyric 的 `PluginLyricLine` 并使用 `REPLACE` 写回；
- 使用宿主提供的 `PluginCache` 缓存 LRC 正文；
- 网络失败、超时、空结果、非法时间轴和快速切歌都会安全返回 `null`，保留现有歌词；
- 不依赖宿主 `:app`、Android `Context`、MediaSession、View 或 Xposed 对象。

## 开始开发

1. 修改 `Plugins/modules/lrclib/src/main/plugin/manifest.json` 中发布后的稳定 `id` 前先确定最终值。
2. 在 `LrclibProcessor` 中扩展查询、候选匹配和歌词字段映射。
3. 若需要新增外部库，在插件模块中使用 `implementation`；HyperLyric API 保持 `compileOnly(project(":plugins:api"))`。
4. 用以下任务生成插件包：

```powershell
.\gradlew.bat :plugins:lrclib:packageDebugPlugin --max-workers=2
.\gradlew.bat :plugins:lrclib:packagePlugin --max-workers=2
```

输出目录：`Plugins/modules/lrclib/build/outputs/plugin/`。

安装、卸载或代码升级后需要重启 SystemUI；普通设置变更不需要重启。
