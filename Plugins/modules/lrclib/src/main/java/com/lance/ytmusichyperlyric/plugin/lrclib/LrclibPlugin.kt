package com.lance.ytmusichyperlyric.plugin.lrclib

import com.lidesheng.hyperlyric.plugin.api.HyperLyricPlugin
import com.lidesheng.hyperlyric.plugin.api.PluginConfig
import com.lidesheng.hyperlyric.plugin.api.PluginContext

/** HyperLyric entry point. The class is public and has the required no-arg constructor. */
class LrclibPlugin : HyperLyricPlugin {
    private var context: PluginContext? = null

    override fun onLoad(context: PluginContext) {
        this.context = context
        context.registerExtension(LrclibProcessor(context))
        context.logger.info("lifecycle=onLoad")
    }

    override fun onEnable() {
        context?.logger?.info("lifecycle=onEnable")
    }

    override fun onConfigChanged(config: PluginConfig) {
        context?.logger?.info("lifecycle=onConfigChanged")
    }

    override fun onUnload() {
        context?.logger?.info("lifecycle=onUnload")
        context = null
    }
}
