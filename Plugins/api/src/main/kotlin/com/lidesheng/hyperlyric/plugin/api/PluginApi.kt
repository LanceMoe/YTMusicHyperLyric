package com.lidesheng.hyperlyric.plugin.api

/** The first stable HyperLyric plugin API contract. */
public const val HYPERLYRIC_PLUGIN_API_VERSION: Int = 1

/** ZIP entry point. A plugin may register one or more extensions during [onLoad]. */
public interface HyperLyricPlugin {
    public fun onLoad(context: PluginContext)
    public fun onEnable() {}
    public fun onConfigChanged(config: PluginConfig) {}
    public fun onUnload() {}
}

public interface HyperLyricExtension {
    public val id: String
}

public interface LyricProcessorExtension : HyperLyricExtension {
    public val stage: PluginProcessorStage
        get() = PluginProcessorStage.TRANSLATION_ENHANCEMENT

    public fun processResult(song: PluginSong): PluginSongResult? = null

    public fun processResult(
        song: PluginSong,
        processingContext: PluginProcessingContext,
    ): PluginSongResult? = processResult(song)
}

public enum class PluginProcessorStage {
    LYRIC_REPLACEMENT,
    TRANSLATION_ENHANCEMENT,
}

public interface PluginContext {
    public val pluginId: String
    public val hostApiVersion: Int
    public val config: PluginConfig
    public val logger: PluginLogger
    public val cache: PluginCache
    public val storage: PluginStorage
    public fun registerExtension(extension: HyperLyricExtension)
}

public data class PluginMediaInfo(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val duration: Long? = null,
    val sourcePackageName: String? = null,
)

public data class PluginProcessingContext(
    val mediaInfo: PluginMediaInfo? = null,
)

public interface PluginConfig {
    public fun getBoolean(key: String, defaultValue: Boolean = false): Boolean
    public fun getString(key: String, defaultValue: String? = null): String?
    public fun getLong(key: String, defaultValue: Long = 0L): Long
    public fun getFloat(key: String, defaultValue: Float = 0f): Float
    public fun getStringSet(key: String, defaultValue: Set<String> = emptySet()): Set<String>
}

public interface PluginLogger {
    public fun debug(message: String)
    public fun info(message: String)
    public fun warn(message: String, throwable: Throwable? = null)
    public fun error(message: String, throwable: Throwable? = null)
    public fun withTag(tag: String): PluginLogger = this
}

public interface PluginStorage {
    public fun getString(key: String, defaultValue: String? = null): String?
    public fun putString(key: String, value: String)
    public fun remove(key: String)
    public fun clear()
}

public interface PluginCache {
    public fun getString(key: String): String?
    public fun putString(key: String, value: String)
    public fun getBytes(key: String): ByteArray?
    public fun putBytes(key: String, value: ByteArray)
    public fun contains(key: String): Boolean
    public fun remove(key: String)
    public fun clear()
}

public interface PluginCacheExtension : HyperLyricExtension {
    public fun listEntries(): List<PluginCacheEntry>
    public fun clearAll()
    public fun clearEntry(entryId: String): Boolean
}

public data class PluginCacheEntry(
    val id: String,
    val title: String,
    val summary: String? = null,
    val sizeBytes: Long? = null,
    val updatedAtEpochMs: Long? = null,
)

public data class PluginSong(
    val id: String? = null,
    val name: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val duration: Long = 0L,
    val metadata: PluginMetadata? = null,
    val lyrics: List<PluginLyricLine>? = null,
)

public enum class PluginSongField {
    ID, NAME, ARTIST, ALBUM, DURATION, METADATA, LYRICS,
}

public enum class PluginLyricsUpdateMode {
    PATCH, REPLACE,
}

public enum class PluginLyricField {
    BEGIN, END, DURATION, IS_ALIGNED_RIGHT, METADATA, TEXT, WORDS,
    SECONDARY, SECONDARY_WORDS, TRANSLATION, TRANSLATION_WORDS, ROMA,
}

public data class PluginSongResult(
    val song: PluginSong,
    val changedFields: Set<PluginSongField>,
    val lyricsUpdateMode: PluginLyricsUpdateMode = PluginLyricsUpdateMode.REPLACE,
    val changedLyricFields: Set<PluginLyricField> = emptySet(),
)

public data class PluginMetadata(
    val values: Map<String, String?> = emptyMap(),
)

public data class PluginLyricLine(
    val begin: Long = 0L,
    val end: Long = 0L,
    val duration: Long = 0L,
    val isAlignedRight: Boolean = false,
    val metadata: PluginMetadata? = null,
    val text: String? = null,
    val words: List<PluginWord>? = null,
    val secondary: String? = null,
    val secondaryWords: List<PluginWord>? = null,
    val translation: String? = null,
    val translationWords: List<PluginWord>? = null,
    val roma: String? = null,
)

public data class PluginWord(
    val begin: Long = 0L,
    val end: Long = 0L,
    val duration: Long = 0L,
    val text: String? = null,
    val metadata: PluginMetadata? = null,
)

/** Semantic setting types used by the host renderer, independent of UI framework classes. */
public enum class PluginSettingType(public val wireName: String) {
    SWITCH("switch"),
    TEXT("text"),
    PASSWORD("password"),
    SELECT("select"),
    MULTI_SELECT("multiSelect"),
    NUMBER("number"),
    SLIDER("slider"),
    ACTION("action");

    public companion object {
        public fun fromWire(value: String): PluginSettingType? =
            entries.firstOrNull { it.wireName == value }
    }
}

public enum class PluginSettingValuePresentation(public val wireName: String) {
    DEFAULT("default"),
    END_ACTION("endAction"),
    SUMMARY("summary"),
    SUMMARY_PREVIEW("summaryPreview");

    public companion object {
        public fun fromWire(value: String): PluginSettingValuePresentation? =
            entries.firstOrNull { it.wireName == value }
    }
}

public enum class PluginSettingInputType(public val wireName: String) {
    DEFAULT("default"),
    URI("uri"),
    NUMBER("number");

    public companion object {
        public fun fromWire(value: String): PluginSettingInputType? =
            entries.firstOrNull { it.wireName == value }
    }
}

public data class PluginSettingGroup(
    val id: String,
    val title: String,
    val titleByLocale: Map<String, String> = emptyMap(),
)

public data class PluginSettingOption(
    val value: String,
    val label: String,
    val labelByLocale: Map<String, String> = emptyMap(),
)

public data class PluginSettingSpec(
    val type: PluginSettingType,
    val key: String,
    val title: String,
    val summary: String? = null,
    val defaultValue: String? = null,
    val options: List<PluginSettingOption> = emptyList(),
    val min: Float? = null,
    val max: Float? = null,
    val step: Float? = null,
    val titleByLocale: Map<String, String> = emptyMap(),
    val summaryByLocale: Map<String, String> = emptyMap(),
    val dialogSummary: String? = null,
    val dialogSummaryByLocale: Map<String, String> = emptyMap(),
    val emptyValueSummary: String? = null,
    val emptyValueSummaryByLocale: Map<String, String> = emptyMap(),
    val valuePresentation: PluginSettingValuePresentation =
        PluginSettingValuePresentation.DEFAULT,
    val previewLineCount: Int = 2,
    val inputType: PluginSettingInputType = PluginSettingInputType.DEFAULT,
    val conflictsWith: List<String> = emptyList(),
    val backup: Boolean = true,
    val group: String? = null,
)

public data class PluginSettingsSchema(
    val settings: List<PluginSettingSpec> = emptyList(),
    val groups: List<PluginSettingGroup> = emptyList(),
)
