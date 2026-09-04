# Keep the original plugin namespace. The runtime loads the entry class by the
# binary name from manifest.json, and parent-first loading can otherwise collide
# with short names from the host's optimized dex.
-dontoptimize
-dontobfuscate
-keepnames class com.lance.ytmusichyperlyric.plugin.lrclib.**

-keep,allowoptimization class com.lance.ytmusichyperlyric.plugin.lrclib.LrclibPlugin {
    <init>();
}

-keepclassmembers,allowoptimization class * implements com.lidesheng.hyperlyric.plugin.api.HyperLyricPlugin {
    public void onLoad(com.lidesheng.hyperlyric.plugin.api.PluginContext);
    public void onEnable();
    public void onConfigChanged(com.lidesheng.hyperlyric.plugin.api.PluginConfig);
    public void onUnload();
}

-keepclassmembers,allowoptimization class * implements com.lidesheng.hyperlyric.plugin.api.HyperLyricExtension {
    public java.lang.String getId();
    public com.lidesheng.hyperlyric.plugin.api.PluginProcessorStage getStage();
}

-keepclassmembers,allowoptimization class * implements com.lidesheng.hyperlyric.plugin.api.LyricProcessorExtension {
    public com.lidesheng.hyperlyric.plugin.api.PluginSongResult processResult(
        com.lidesheng.hyperlyric.plugin.api.PluginSong
    );
    public com.lidesheng.hyperlyric.plugin.api.PluginSongResult processResult(
        com.lidesheng.hyperlyric.plugin.api.PluginSong,
        com.lidesheng.hyperlyric.plugin.api.PluginProcessingContext
    );
}
