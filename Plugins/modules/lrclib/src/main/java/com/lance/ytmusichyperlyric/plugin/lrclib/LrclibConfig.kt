package com.lance.ytmusichyperlyric.plugin.lrclib

import com.lidesheng.hyperlyric.plugin.api.PluginConfig

internal data class LrclibConfig(
    val enabled: Boolean,
    val endpoint: String,
) {
    companion object {
        private const val DEFAULT_ENDPOINT = "https://lrclib.net/api/get"

        fun from(config: PluginConfig): LrclibConfig {
            val endpoint = config.getString("api_endpoint", DEFAULT_ENDPOINT)
                ?.trim()
                ?.takeIf { it.startsWith("https://") }
                ?: DEFAULT_ENDPOINT
            return LrclibConfig(
                enabled = config.getBoolean("enabled", false),
                endpoint = endpoint,
            )
        }
    }
}
