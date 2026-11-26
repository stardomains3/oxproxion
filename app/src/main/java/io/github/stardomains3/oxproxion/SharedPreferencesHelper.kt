package io.github.stardomains3.oxproxion

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import androidx.core.content.edit

class SharedPreferencesHelper(context: Context) {

    private val apiKeysPrefs: SharedPreferences =
        context.getSharedPreferences(API_KEYS_PREFS_STORE, Context.MODE_PRIVATE)
    val mainPrefs: SharedPreferences = context.getSharedPreferences(MAIN_PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true
        coerceInputValues = true}
    private val gson = Gson() // Kept temporarily for migration only
    interface OnTimeoutChangedListener {
        fun onTimeoutChanged(newMinutes: Int)
    }

    private var timeoutListener: OnTimeoutChangedListener? = null

    fun setTimeoutChangedListener(listener: OnTimeoutChangedListener) {
        this.timeoutListener = listener
    }
    companion object {
        private const val KEY_TOOL_APPROVAL = "tool_approval_enabled"
        private const val KEY_WATERMARK_STT_ENABLED = "watermark_stt_enabled"
        private const val KEY_READING_FOCUS_DIM_ENABLED = "reading_focus_dim_enabled"
        private const val KEY_PREFER_FULLSCREEN_CHAT = "prefer_fullscreen_chat"
        private const val KEY_FLOATING_TRANSCRIBE_X = "floating_transcribe_x"
        private const val KEY_FLOATING_TRANSCRIBE_Y = "floating_transcribe_y"
        const val NOTI_ACTION_READER = "reader"
        const val NOTI_ACTION_COPY = "copy"
        const val NOTI_ACTION_OPEN = "open"
        const val NOTI_ACTION_DISMISS = "dismiss"
        private const val KEY_VOICE_INPUT_MODEL = "voice_input_model"
        private const val KEY_VOICE_INPUT_PROVIDER = "voice_input_provider" // "lan", "cloud", "off"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_MODEL_LIST_SCHEMA_VERSION = "model_list_schema_version"
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2
        private const val KEY_OR_IMAGE_ASPECT_RATIO_PREFIX = "or_img_ar_"
        private const val KEY_OR_IMAGE_RESOLUTION_PREFIX = "or_img_res_"
        private const val KEY_OR_IMAGE_QUALITY_PREFIX = "or_img_q_"
        private const val KEY_OR_IMAGE_FORMAT_PREFIX = "or_img_fmt_"
        private const val KEY_INFERENCE_TEMP_ENABLED = "inference_temp_enabled"
        private const val KEY_INFERENCE_TEMP_VALUE = "inference_temp_value"
        private const val KEY_INFERENCE_TOP_P_ENABLED = "inference_top_p_enabled"
        private const val KEY_INFERENCE_TOP_P_VALUE = "inference_top_p_value"
        private const val KEY_INFERENCE_TOP_K_ENABLED = "inference_top_k_enabled"
        private const val KEY_INFERENCE_TOP_K_VALUE = "inference_top_k_value"
        private const val KEY_INFERENCE_MIN_P_ENABLED = "inference_min_p_enabled"
        private const val KEY_INFERENCE_MIN_P_VALUE = "inference_min_p_value"
        private const val KEY_INFERENCE_REPETITION_PENALTY_ENABLED = "inference_repetition_penalty_enabled"
        private const val KEY_INFERENCE_REPETITION_PENALTY_VALUE = "inference_repetition_penalty_value"
        private const val KEY_INFERENCE_PRESENCE_PENALTY_ENABLED = "inference_presence_penalty_enabled"
        private const val KEY_INFERENCE_PRESENCE_PENALTY_VALUE = "inference_presence_penalty_value"
        const val LAN_PROVIDER_MLX_LM = "mlx_lm"  // NEW

        const val LAN_PROVIDER_HERMES_AGENT = "hermes_agent"  // NEW - Hermes Agent provider
        private const val KEY_AUTO_BACK = "auto_back_enabled"
        private const val KEY_WEB_FETCH_ENGINE = "web_fetch_engine"
        const val KEY_AMOLED_MODE_ENABLED = "amoled_mode_enabled"
        private const val KEY_WEB_FETCH_MAX_USES = "web_fetch_max_uses"
        private const val KEY_VOLUME_SCROLL = "volume_scroll_enabled"
        private const val KEY_ENABLED_TOOLS = "enabled_tools"
        private const val KEY_TOOLS_ENABLED = "tools_enabled_preference"
        const val LAN_PROVIDER_OMLX = "omlx"
        const val LAN_PROVIDER_NATIV = "nativ"

        private const val KEY_CHAT_MEMORY_COUNT = "chat_memory_count"
        private const val KEY_ANIMATE_BAR_ON_ERROR = "animate_bar_on_error"
        private const val SAF_FOLDER_URI = "saffolderuri"
        private const val KEY_USE_COPY_BUTTON2 = "use_copy_button2"

        private const val KEY_SHOW_CITATIONS = "show_citations"
        private const val KEY_EXTENDED_TOP_BAR = "extended_top_bar_enabled"
        private const val KEY_OPENROUTER_TRANSFORMS_ENABLED = "openrouter_transforms_enabled"
        private const val KEY_EXPANDABLE_INPUT = "expandable_input_enabled"
        const val LAN_API_KEY = "lan_api_key"  // NEW
        private const val KEY_TIMEOUT_MINUTES = "timeout_minutes"
        private const val KEY_DISABLE_WEB_SEARCH_AFTER_SEND = "disable_web_search_after_send"
        const val BUBBLE_COLOR_UNSET = Int.MIN_VALUE   // -1 is a valid color (white), so use this
        private const val KEY_AI_DAY = "bubble_ai_color_day"
        private const val KEY_AI_NIGHT = "bubble_ai_color_night"
        private const val KEY_USER_DAY = "bubble_user_color_day"
        private const val KEY_USER_NIGHT = "bubble_user_color_night"
        private const val KEY_AI_TEXT_DAY = "bubble_ai_text_day"
        private const val KEY_AI_TEXT_NIGHT = "bubble_ai_text_night"
        private const val KEY_USER_TEXT_DAY = "bubble_user_text_day"
        private const val KEY_USER_TEXT_NIGHT = "bubble_user_text_night"
        private const val KEY_SCROLLERS_ENABLED = "scrollers_enabled"
        private const val MAX_TOKENS_ENABLED = "max_tokens_enabled"
        private const val KEY_CUSTOM_PROMPTS = "custom_prompts"
        private const val KEY_FLOATING_TRANSCRIBE_ENABLED = "floating_transcribe_enabled"
        private const val KEY_SCROLL_PROGRESS_ENABLED = "scroll_progress_enabled"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_CLEAR_CHAT_DEFAULT = "clear_chat_default"
        private const val KEY_CLEAR_CHAT_DEFAULT2 = "clear_chat_default2"
        const val LAN_PROVIDER_KEY = "lan_provider"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_LAST_AI_RESPONSE_CHANNEL = "last_ai_response_channel_"
        const val LAN_PROVIDER_LLAMA_CPP = "llama_cpp"
        private const val KEY_FONT_SIZEC = "font_sizec"
        private const val KEY_USE_COPY_BUTTON = "use_copy_button"
        const val LAN_PROVIDER_OLLAMA = "ollama"
        private const val KEY_WEB_SEARCH_ENGINE = "web_search_engine"
        private const val KEY_WEB_SEARCH_ENABLED = "web_search_enabled"
        const val LAN_PROVIDER_LM_STUDIO = "lm_studio"
        private const val KEY_LAN_ENDPOINT = "lan_endpoint"
        private const val KEY_PRESETS = "user_presets"
        private const val KEY_SELECTED_FONT = "selected_font"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_CONVERSATION_MODE_ENABLED = "conversation_mode_enabled"
        private const val API_KEYS_PREFS_STORE = "ApiKeysPrefsStore"
        private const val MAIN_PREFS = "MainAppPrefs"
        private const val KEY_MODEL_NEW_CHAT = "modelvalenewchat"
        private const val KEY_MODEL_VALE = "modelvale"
        private const val KEY_CUSTOM_MODELS = "custom_models"
        private const val KEY_DEFAULT_MODELS_SEEDED = "default_models_seeded"
        private const val KEY_SELECTED_SYSTEM_MESSAGE = "selected_system_message"
        private const val KEY_CUSTOM_SYSTEM_MESSAGES = "custom_system_messages"
        private const val KEY_DEFAULT_SYSTEM_MESSAGES_SEEDED = "default_system_messages_seeded"
        private const val KEY_STREAMING_ENABLED = "streaming_enabled"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_OPEN_ROUTER_MODELS = "open_router_models"
        private const val KEY_NOTI_ENABLED = "noti_enabled"
        private const val KEY_EXT_ENABLED = "ext_enabled"
        private const val KEY_EXT_ENABLED2 = "ext_enabled2"
        private const val KEY_REASONING_ENABLED = "reasoning_enabled"
        private const val KEY_INFO_BAR_DISMISSED = "info_bar_dismissed"
        private const val KEY_SORT_ORDER = "sort_order"
        private const val KEY_MAX_TOKENS = "max_tokens"
        private const val KEY_DEFAULT_SYSTEM_MESSAGE = "default_system_message"
        private const val KEY_MIGRATION_COMPLETE = "has_migrated_to_kotlin_serialization"
    }

    init {
        migrateFromGson()
    }

    private fun migrateFromGson() {
        if (!mainPrefs.getBoolean(KEY_MIGRATION_COMPLETE, false)) {
            //   Log.d("Migration", "Starting migration from Gson to Kotlin Serialization")

            runCatching { migrateCustomModels() }.onFailure {
                //    Log.e("Migration", "Failed to migrate custom models", it)
            }
            runCatching { migrateSystemMessages() }.onFailure {
                //     Log.e("Migration", "Failed to migrate system messages", it)
            }
            runCatching { migrateOpenRouterModels() }.onFailure {
                //   Log.e("Migration", "Failed to migrate OpenRouter models", it)
            }
            runCatching { migrateSelectedSystemMessage() }.onFailure {
                //  Log.e("Migration", "Failed to migrate selected system message", it)
            }
            runCatching { migrateDefaultSystemMessage() }.onFailure {
                //  Log.e("Migration", "Failed to migrate default system message", it)
            }

            mainPrefs.edit { putBoolean(KEY_MIGRATION_COMPLETE, true) }
            //  Log.d("Migration", "Migration to Kotlin Serialization complete")
        }
    }


    private fun migrateCustomModels() {
        val oldJson = mainPrefs.getString(KEY_CUSTOM_MODELS, null)
        if (oldJson != null) {
            val type = object : TypeToken<List<LlmModel>>() {}.type
            val oldModels: List<LlmModel> = gson.fromJson(oldJson, type)
            saveCustomModels(oldModels)
        }
    }
    fun getWebFetchEngine(): String =
        mainPrefs.getString(KEY_WEB_FETCH_ENGINE, "openrouter") ?: "openrouter"

    fun saveWebFetchEngine(engine: String) =
        mainPrefs.edit { putString(KEY_WEB_FETCH_ENGINE, engine) }
    fun getPushToTalkEnabled(): Boolean =
        mainPrefs.getBoolean("push_to_talk_enabled", false)

    fun savePushToTalkEnabled(enabled: Boolean) {
        mainPrefs.edit { putBoolean("push_to_talk_enabled", enabled) }
    }
    fun getWebFetchMaxUses(): Int =
        mainPrefs.getInt(KEY_WEB_FETCH_MAX_USES, 0)  // 0 = unset (no limit)

    fun saveWebFetchMaxUses(maxUses: Int) =
        mainPrefs.edit { putInt(KEY_WEB_FETCH_MAX_USES, maxUses) }

    private fun migrateSystemMessages() {
        val oldJson = mainPrefs.getString(KEY_CUSTOM_SYSTEM_MESSAGES, null)
        if (oldJson != null) {
            val type = object : TypeToken<List<SystemMessage>>() {}.type
            val oldMessages: List<SystemMessage> = gson.fromJson(oldJson, type)
            saveCustomSystemMessages(oldMessages)
        }
    }

    private fun migrateOpenRouterModels() {
        val oldJson = mainPrefs.getString(KEY_OPEN_ROUTER_MODELS, null)
        if (oldJson != null) {
            val type = object : TypeToken<List<LlmModel>>() {}.type
            val oldModels: List<LlmModel> = gson.fromJson(oldJson, type)
            saveOpenRouterModels(oldModels)
        }
    }
    fun getCustomPrompts(): List<Prompt> {
        val jsonString = mainPrefs.getString(KEY_CUSTOM_PROMPTS, null)
        return if (jsonString != null) {
            json.decodeFromString(jsonString)
        } else {
            emptyList()
        }
    }
    // --- Inference Parameter Methods ---

    // Temperature (Default: 1.0)
    fun getInferenceTempEnabled(): Boolean = mainPrefs.getBoolean(KEY_INFERENCE_TEMP_ENABLED, false)
    fun saveInferenceTempEnabled(enabled: Boolean) = mainPrefs.edit { putBoolean(KEY_INFERENCE_TEMP_ENABLED, enabled) }
    fun getInferenceTempValue(): String = mainPrefs.getString(KEY_INFERENCE_TEMP_VALUE, "1.0") ?: "1.0"
    fun saveInferenceTempValue(value: String) = mainPrefs.edit { putString(KEY_INFERENCE_TEMP_VALUE, value) }

    // Top P (Default: 1.0)
    fun getInferenceTopPEnabled(): Boolean = mainPrefs.getBoolean(KEY_INFERENCE_TOP_P_ENABLED, false)
    fun saveInferenceTopPEnabled(enabled: Boolean) = mainPrefs.edit { putBoolean(KEY_INFERENCE_TOP_P_ENABLED, enabled) }
    fun getInferenceTopPValue(): String = mainPrefs.getString(KEY_INFERENCE_TOP_P_VALUE, "1.0") ?: "1.0"
    fun saveInferenceTopPValue(value: String) = mainPrefs.edit { putString(KEY_INFERENCE_TOP_P_VALUE, value) }
    fun getModelListSchemaVersion(): Int =
        mainPrefs.getInt(KEY_MODEL_LIST_SCHEMA_VERSION, 0)

    fun saveModelListSchemaVersion(version: Int) {
        mainPrefs.edit { putInt(KEY_MODEL_LIST_SCHEMA_VERSION, version) }
    }
    fun getFloatingTranscribeEnabled(): Boolean =
        mainPrefs.getBoolean(KEY_FLOATING_TRANSCRIBE_ENABLED, false)

    fun saveFloatingTranscribeEnabled(enabled: Boolean) =
        mainPrefs.edit { putBoolean(KEY_FLOATING_TRANSCRIBE_ENABLED, enabled) }

    /**
     * Saves the button's last dragged position.
     * Pass -1 for both to reset to default position.
     */
    fun saveFloatingTranscribePos(x: Int, y: Int) =
        mainPrefs.edit {
            putInt(KEY_FLOATING_TRANSCRIBE_X, x)
            putInt(KEY_FLOATING_TRANSCRIBE_Y, y)
        }

    /**
     * Returns the saved position as a Pair(x, y).
     * Returns (-1, -1) if never saved (use default position).
     */
    fun getFloatingTranscribePos(): Pair<Int, Int> =
        mainPrefs.getInt(KEY_FLOATING_TRANSCRIBE_X, -1) to
                mainPrefs.getInt(KEY_FLOATING_TRANSCRIBE_Y, -1)
    // Top K (Default: 40) - This one was already correct
    fun getInferenceTopKEnabled(): Boolean = mainPrefs.getBoolean(KEY_INFERENCE_TOP_K_ENABLED, false)
    fun saveInferenceTopKEnabled(enabled: Boolean) = mainPrefs.edit { putBoolean(KEY_INFERENCE_TOP_K_ENABLED, enabled) }
    fun getInferenceTopKValue(): Int = mainPrefs.getInt(KEY_INFERENCE_TOP_K_VALUE, 40)
    fun saveInferenceTopKValue(value: Int) = mainPrefs.edit { putInt(KEY_INFERENCE_TOP_K_VALUE, value) }

    // Min P (Default: 0.0)
    fun getInferenceMinPEnabled(): Boolean = mainPrefs.getBoolean(KEY_INFERENCE_MIN_P_ENABLED, false)
    fun saveInferenceMinPEnabled(enabled: Boolean) = mainPrefs.edit { putBoolean(KEY_INFERENCE_MIN_P_ENABLED, enabled) }
    fun getInferenceMinPValue(): String = mainPrefs.getString(KEY_INFERENCE_MIN_P_VALUE, "0.0") ?: "0.0"
    fun saveInferenceMinPValue(value: String) = mainPrefs.edit { putString(KEY_INFERENCE_MIN_P_VALUE, value) }

    // Repetition Penalty (Default: 1.0)
    fun getInferenceRepetitionPenaltyEnabled(): Boolean = mainPrefs.getBoolean(KEY_INFERENCE_REPETITION_PENALTY_ENABLED, false)
    fun saveInferenceRepetitionPenaltyEnabled(enabled: Boolean) = mainPrefs.edit { putBoolean(KEY_INFERENCE_REPETITION_PENALTY_ENABLED, enabled) }
    fun getInferenceRepetitionPenaltyValue(): String = mainPrefs.getString(KEY_INFERENCE_REPETITION_PENALTY_VALUE, "1.0") ?: "1.0"
    fun saveInferenceRepetitionPenaltyValue(value: String) = mainPrefs.edit { putString(KEY_INFERENCE_REPETITION_PENALTY_VALUE, value) }

    // Presence Penalty (Default: 0.0)
    fun getInferencePresencePenaltyEnabled(): Boolean = mainPrefs.getBoolean(KEY_INFERENCE_PRESENCE_PENALTY_ENABLED, false)
    fun saveInferencePresencePenaltyEnabled(enabled: Boolean) = mainPrefs.edit { putBoolean(KEY_INFERENCE_PRESENCE_PENALTY_ENABLED, enabled) }
    fun getInferencePresencePenaltyValue(): String = mainPrefs.getString(KEY_INFERENCE_PRESENCE_PENALTY_VALUE, "0.0") ?: "0.0"
    fun saveInferencePresencePenaltyValue(value: String) = mainPrefs.edit { putString(KEY_INFERENCE_PRESENCE_PENALTY_VALUE, value) }

    fun saveOpenRouterTransformsEnabled(enabled: Boolean) {
        mainPrefs.edit { putBoolean(KEY_OPENROUTER_TRANSFORMS_ENABLED, enabled) }
    }

    fun getOpenRouterTransformsEnabled(): Boolean {
        return mainPrefs.getBoolean(KEY_OPENROUTER_TRANSFORMS_ENABLED, false)
    }
    fun saveCustomPrompts(prompts: List<Prompt>) {
        val jsonString = json.encodeToString(prompts)
        mainPrefs.edit { putString(KEY_CUSTOM_PROMPTS, jsonString) }
    }
    fun getUseCopyButton(): Boolean {
        return mainPrefs.getBoolean(KEY_USE_COPY_BUTTON, false)  // false = Open, true = Copy
    }
    fun saveUseCopyButton(useCopy: Boolean) {
        mainPrefs.edit { putBoolean(KEY_USE_COPY_BUTTON, useCopy) }
    }
    private fun migrateSelectedSystemMessage() {
        val oldJson = mainPrefs.getString(KEY_SELECTED_SYSTEM_MESSAGE, null)
        if (oldJson != null) {
            val oldMessage: SystemMessage = gson.fromJson(oldJson, SystemMessage::class.java)
            saveSelectedSystemMessage(oldMessage)
        }
    }
    fun clearOpenRouterModels() {
        mainPrefs.edit { remove(KEY_OPEN_ROUTER_MODELS) }
    }
    fun saveBiometricEnabled(enabled: Boolean) = mainPrefs.edit { putBoolean(KEY_BIOMETRIC_ENABLED, enabled) }
    fun getBiometricEnabled(): Boolean = mainPrefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)

    fun getAdvancedReasoningEnabled(): Boolean = mainPrefs.getBoolean("advanced_reasoning_enabled", false)
    fun saveAdvancedReasoningEnabled(enabled: Boolean) = mainPrefs.edit {
        putBoolean(
            "advanced_reasoning_enabled",
            enabled
        )
    }
    /** null = user chose "Default" → field omitted from the request entirely. */
    fun getOpenRouterImageAspectRatio(modelId: String): String? =
        mainPrefs.getString(KEY_OR_IMAGE_ASPECT_RATIO_PREFIX + modelId, null)

    fun saveOpenRouterImageAspectRatio(modelId: String, value: String?) {
        if (value == null) mainPrefs.edit { remove(KEY_OR_IMAGE_ASPECT_RATIO_PREFIX + modelId) }
        else mainPrefs.edit { putString(KEY_OR_IMAGE_ASPECT_RATIO_PREFIX + modelId, value) }
    }

    fun getOpenRouterImageResolution(modelId: String): String? =
        mainPrefs.getString(KEY_OR_IMAGE_RESOLUTION_PREFIX + modelId, null)

    fun saveOpenRouterImageResolution(modelId: String, value: String?) {
        if (value == null) mainPrefs.edit { remove(KEY_OR_IMAGE_RESOLUTION_PREFIX + modelId) }
        else mainPrefs.edit { putString(KEY_OR_IMAGE_RESOLUTION_PREFIX + modelId, value) }
    }

    fun getOpenRouterImageQuality(modelId: String): String? =
        mainPrefs.getString(KEY_OR_IMAGE_QUALITY_PREFIX + modelId, null)

    fun saveOpenRouterImageQuality(modelId: String, value: String?) {
        if (value == null) mainPrefs.edit { remove(KEY_OR_IMAGE_QUALITY_PREFIX + modelId) }
        else mainPrefs.edit { putString(KEY_OR_IMAGE_QUALITY_PREFIX + modelId, value) }
    }

    fun getOpenRouterImageOutputFormat(modelId: String): String? =
        mainPrefs.getString(KEY_OR_IMAGE_FORMAT_PREFIX + modelId, null)

    fun saveOpenRouterImageOutputFormat(modelId: String, value: String?) {
        if (value == null) mainPrefs.edit { remove(KEY_OR_IMAGE_FORMAT_PREFIX + modelId) }
        else mainPrefs.edit { putString(KEY_OR_IMAGE_FORMAT_PREFIX + modelId, value) }
    }
    fun getReasoningEffort(): String = mainPrefs.getString("reasoning_effort", "medium") ?: "medium"
    fun saveReasoningEffort(effort: String) = mainPrefs.edit {
        putString(
            "reasoning_effort",
            effort
        )
    }
    fun getVoiceInputModel(): String = mainPrefs.getString(KEY_VOICE_INPUT_MODEL, "") ?: ""
    fun setVoiceInputModel(model: String) = mainPrefs.edit { putString(KEY_VOICE_INPUT_MODEL, model) }

    fun getVoiceInputProvider(): String = mainPrefs.getString(KEY_VOICE_INPUT_PROVIDER, "lan") ?: "lan"
    fun setVoiceInputProvider(provider: String) = mainPrefs.edit { putString(KEY_VOICE_INPUT_PROVIDER, provider) }
    fun saveChatMemoryCount(count: Int) {
        mainPrefs.edit { putInt(KEY_CHAT_MEMORY_COUNT, count) }
    }
    fun getChatMemoryCount(): Int {
        return mainPrefs.getInt(KEY_CHAT_MEMORY_COUNT, Int.MAX_VALUE) // Default to All messages
    }
    fun saveDisableWebSearchAfterSend(enabled: Boolean) {
        mainPrefs.edit { putBoolean(KEY_DISABLE_WEB_SEARCH_AFTER_SEND, enabled) }
    }

    fun getDisableWebSearchAfterSend(): Boolean {
        // Default to TRUE to preserve current behavior for existing users
        return mainPrefs.getBoolean(KEY_DISABLE_WEB_SEARCH_AFTER_SEND, true)
    }

    fun getToolApprovalEnabled(): Boolean =
        mainPrefs.getBoolean(KEY_TOOL_APPROVAL, false)

    fun saveToolApprovalEnabled(enabled: Boolean) {
        mainPrefs.edit { putBoolean(KEY_TOOL_APPROVAL, enabled) }
    }
    fun getReasoningExclude(): Boolean = mainPrefs.getBoolean("reasoning_exclude", false)
    fun saveReasoningExclude(exclude: Boolean) = mainPrefs.edit {
        putBoolean(
            "reasoning_exclude",
            exclude
        )
    }

    fun getReasoningMaxTokens(): Int? = mainPrefs.getInt("reasoning_max_tokens", -1).takeIf { it != -1 }
    fun saveReasoningMaxTokens(tokens: Int?) = mainPrefs.edit {
        putInt(
            "reasoning_max_tokens",
            tokens ?: -1
        )
    }
    private fun migrateDefaultSystemMessage() {
        val oldJson = mainPrefs.getString(KEY_DEFAULT_SYSTEM_MESSAGE, null)
        if (oldJson != null) {
            val oldMessage: SystemMessage = gson.fromJson(oldJson, SystemMessage::class.java)
            saveDefaultSystemMessage(oldMessage)
        }
    }
    fun saveFontSize(size: Int) {
        mainPrefs.edit { putInt(KEY_FONT_SIZE, size) }
    }

    fun getFontSize(): Int {
        return mainPrefs.getInt(KEY_FONT_SIZE, 100)  // Default: 100%
    }
    fun getVolumeScrollEnabled(): Boolean = mainPrefs.getBoolean(KEY_VOLUME_SCROLL, false)

    fun saveVolumeScrollEnabled(enabled: Boolean) = mainPrefs.edit { putBoolean(KEY_VOLUME_SCROLL, enabled) }
    fun saveSortOrder(sortOrder: SortOrder) {
        mainPrefs.edit { putString(KEY_SORT_ORDER, sortOrder.name) }
    }
    fun getTimeoutMinutes(): Int = mainPrefs.getInt(KEY_TIMEOUT_MINUTES, 5)
    fun getReadingFocusDimEnabled(): Boolean =
        mainPrefs.getBoolean(KEY_READING_FOCUS_DIM_ENABLED, false)

    fun saveReadingFocusDimEnabled(enabled: Boolean) {
        mainPrefs.edit { putBoolean(KEY_READING_FOCUS_DIM_ENABLED, enabled) }
    }
    fun saveToolsPreference(isEnabled: Boolean) {
        mainPrefs.edit {
            putBoolean(KEY_TOOLS_ENABLED, isEnabled)
        }
    }

    fun getToolsPreference(): Boolean {
        return mainPrefs.getBoolean(KEY_TOOLS_ENABLED, false)
    }
    fun getEnabledTools(): Set<String> {
        val jsonStr = mainPrefs.getString(KEY_ENABLED_TOOLS, null)
        return if (jsonStr != null && jsonStr.isNotEmpty()) {
            try {
                json.decodeFromString<Set<String>>(jsonStr)
            } catch (e: Exception) {
                //Log.w("SharedPreferencesHelper", "Failed to parse enabled tools, falling back to all enabled", e)
                emptySet()  // Safe fallback: enables all tools
            }
        } else {
            emptySet()  // First use: enables all tools
        }
    }
    fun hasEnabledToolsStored(): Boolean {
        return mainPrefs.contains(KEY_ENABLED_TOOLS)
    }
    fun saveEnabledTools(tools: Set<String>) {
        try {
            val jsonStr = json.encodeToString(tools)
            mainPrefs.edit {
                putString(KEY_ENABLED_TOOLS, jsonStr)
            }  // Async save (efficient, non-blocking)
            // Log.d("SharedPreferencesHelper", "Saved enabled tools: $tools")
        } catch (e: Exception) {
            //  Log.e("SharedPreferencesHelper", "Failed to save enabled tools: $tools", e)
            // Optional: You could add a user-facing error (e.g., Toast) here if needed
        }
    }
    fun isNightMode(context: Context) =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

    // --- AI bubble ---
    fun getAiTextColor(context: Context): Int? = readBubbleColor(context,
        Companion.KEY_AI_TEXT_DAY,
        Companion.KEY_AI_TEXT_NIGHT
    )
    fun setAiTextColor(context: Context, color: Int) = writeBubbleColor(context,
        Companion.KEY_AI_TEXT_DAY,
        Companion.KEY_AI_TEXT_NIGHT, color)
    fun resetAiTextColor(context: Context) = writeBubbleColor(context,
        Companion.KEY_AI_TEXT_DAY,
        Companion.KEY_AI_TEXT_NIGHT,
        Companion.BUBBLE_COLOR_UNSET
    )
    // --- User bubble TEXT color ---
    fun getUserTextColor(context: Context): Int? =
        readBubbleColor(context,
            Companion.KEY_USER_TEXT_DAY,
            Companion.KEY_USER_TEXT_NIGHT
        )

    fun setUserTextColor(context: Context, color: Int) =
        writeBubbleColor(context,
            Companion.KEY_USER_TEXT_DAY,
            Companion.KEY_USER_TEXT_NIGHT, color)

    fun resetUserTextColor(context: Context) =
        writeBubbleColor(context,
            Companion.KEY_USER_TEXT_DAY,
            Companion.KEY_USER_TEXT_NIGHT,
            Companion.BUBBLE_COLOR_UNSET
        )

    fun getAiBubbleColor(context: Context): Int? =
        readBubbleColor(context, KEY_AI_DAY, KEY_AI_NIGHT)

    fun setAiBubbleColor(context: Context, color: Int) =
        writeBubbleColor(context, KEY_AI_DAY, KEY_AI_NIGHT, color)

    fun resetAiBubbleColor(context: Context) =
        writeBubbleColor(context, KEY_AI_DAY, KEY_AI_NIGHT, BUBBLE_COLOR_UNSET)

    // --- User bubble ---
    fun getUserBubbleColor(context: Context): Int? =
        readBubbleColor(context, KEY_USER_DAY, KEY_USER_NIGHT)

    fun setUserBubbleColor(context: Context, color: Int) =
        writeBubbleColor(context, KEY_USER_DAY, KEY_USER_NIGHT, color)

    fun resetUserBubbleColor(context: Context) =
        writeBubbleColor(context, KEY_USER_DAY, KEY_USER_NIGHT, BUBBLE_COLOR_UNSET)

    private fun readBubbleColor(context: Context, dayKey: String, nightKey: String): Int? {
        val key = if (isNightMode(context)) nightKey else dayKey
        val c = mainPrefs.getInt(key, BUBBLE_COLOR_UNSET)
        return if (c == BUBBLE_COLOR_UNSET) null else c
    }

    private fun writeBubbleColor(context: Context, dayKey: String, nightKey: String, value: Int) {
        val key = if (isNightMode(context)) nightKey else dayKey
        mainPrefs.edit { putInt(key, value) }
    }
    fun saveSafFolderUri(uri: String) {
        mainPrefs.edit { putString(SAF_FOLDER_URI, uri) }
    }

    fun getSafFolderUri(): String? {
        return mainPrefs.getString(SAF_FOLDER_URI, null)
    }
    fun saveTimeoutMinutes(minutes: Int) {
        mainPrefs.edit { putInt(KEY_TIMEOUT_MINUTES, minutes) }
        // Notify the listener if it exists
        timeoutListener?.onTimeoutChanged(minutes)
    }
    fun getScrollProgressEnabled(): Boolean = mainPrefs.getBoolean(KEY_SCROLL_PROGRESS_ENABLED, false)
    fun saveScrollProgressEnabled(enabled: Boolean) = mainPrefs.edit {
        putBoolean(KEY_SCROLL_PROGRESS_ENABLED, enabled)
    }
    fun getSortOrder(): SortOrder {
        val sortOrderName = mainPrefs.getString(KEY_SORT_ORDER, SortOrder.ALPHABETICAL.name)
        return SortOrder.valueOf(sortOrderName ?: SortOrder.ALPHABETICAL.name)
    }
    // --- AMOLED Mode ---
    fun isAmoledModeEnabled(): Boolean = mainPrefs.getBoolean(KEY_AMOLED_MODE_ENABLED, false)

    fun setAmoledModeEnabled(enabled: Boolean) = mainPrefs.edit {
        putBoolean(KEY_AMOLED_MODE_ENABLED, enabled)
    }
    fun saveNotiActionSlot1(value: String) {
        mainPrefs.edit { putString("noti_action_slot1", value) }
    }
    fun getNotiActionSlot1(): String =
        mainPrefs.getString("noti_action_slot1",
            NOTI_ACTION_DISMISS
        ) ?: NOTI_ACTION_DISMISS

    fun saveNotiActionSlot2(value: String) {
        mainPrefs.edit { putString("noti_action_slot2", value) }
    }
    fun getNotiActionSlot2(): String =
        mainPrefs.getString("noti_action_slot2",
            NOTI_ACTION_OPEN
        ) ?: NOTI_ACTION_OPEN
    fun migrateNotiActionsIfNeeded() {
        if (!mainPrefs.contains("noti_action_slot1")) {
            val old1 = if (mainPrefs.getBoolean(KEY_USE_COPY_BUTTON2, false))
                NOTI_ACTION_COPY else NOTI_ACTION_DISMISS   // old default was Dismiss
            mainPrefs.edit { putString("noti_action_slot1", old1) }
        }
        if (!mainPrefs.contains("noti_action_slot2")) {
            val old2 = if (mainPrefs.getBoolean(KEY_USE_COPY_BUTTON, false))
                NOTI_ACTION_COPY else NOTI_ACTION_OPEN      // old default was Open
            mainPrefs.edit { putString("noti_action_slot2", old2) }
        }
    }
    fun getUseCopyButton2(): Boolean {
        return mainPrefs.getBoolean(KEY_USE_COPY_BUTTON2, false)  // false = Dismiss, true = Copy
    }
    fun saveUseCopyButton2(useCopy: Boolean) {
        mainPrefs.edit { putBoolean(KEY_USE_COPY_BUTTON2, useCopy) }
    }
    fun getAutoBack(): Boolean {
        return mainPrefs.getBoolean(KEY_AUTO_BACK, false)
    }
    fun saveAutoBack(enabled: Boolean) {
        mainPrefs.edit { putBoolean(KEY_AUTO_BACK, enabled) }
    }
    fun setOpenRouterInfoDismissed(dismissed: Boolean) {
        mainPrefs.edit { putBoolean(KEY_INFO_BAR_DISMISSED, dismissed) }
    }
    fun saveExpandableInput(enabled: Boolean) {
        mainPrefs.edit { putBoolean(KEY_EXPANDABLE_INPUT, enabled) }
    }

    fun getExpandableInput(): Boolean {
        // Defaulting to FALSE or TRUE based on what you prefer.
        // I set it to false so it's opt-in, change to true if you want it on by default.
        return mainPrefs.getBoolean(KEY_EXPANDABLE_INPUT, false)
    }
    fun hasDismissedOpenRouterInfo(): Boolean {
        return mainPrefs.getBoolean(KEY_INFO_BAR_DISMISSED, false)
    }

    fun saveOpenRouterModels(models: List<LlmModel>) {
        val jsonString = json.encodeToString(models)
        mainPrefs.edit { putString(KEY_OPEN_ROUTER_MODELS, jsonString) }
    }

    fun getOpenRouterModels(): List<LlmModel> {
        val jsonString = mainPrefs.getString(KEY_OPEN_ROUTER_MODELS, null)
        return if (jsonString != null) {
            json.decodeFromString(jsonString)
        } else {
            emptyList()
        }
    }
    fun getAnimateBarOnError(): Boolean {
        return mainPrefs.getBoolean(KEY_ANIMATE_BAR_ON_ERROR, false)
    }

    fun saveAnimateBarOnError(enabled: Boolean) {
        mainPrefs.edit { putBoolean(KEY_ANIMATE_BAR_ON_ERROR, enabled) }
    }
    fun saveConversationModeEnabled(isEnabled: Boolean) {
        mainPrefs.edit { putBoolean(KEY_CONVERSATION_MODE_ENABLED, isEnabled) }
    }

    fun getConversationModeEnabled(): Boolean {
        return mainPrefs.getBoolean(KEY_CONVERSATION_MODE_ENABLED, false)
    }
    fun saveStreamingPreference(isEnabled: Boolean) {
        mainPrefs.edit {
            putBoolean(KEY_STREAMING_ENABLED, isEnabled)
        }
    }

    fun getStreamingPreference(): Boolean {
        return mainPrefs.getBoolean(KEY_STREAMING_ENABLED, false)
    }
    fun getReasoningPreference(): Boolean {
        return mainPrefs.getBoolean(KEY_REASONING_ENABLED, false)
    }
    fun saveReasoningPreference(isEnabled: Boolean) {
        mainPrefs.edit {
            putBoolean(KEY_REASONING_ENABLED, isEnabled)
        }
    }
    fun saveSelectedFont(fontName: String) {
        mainPrefs.edit { putString(KEY_SELECTED_FONT, fontName) }
    }
    fun getWebSearchBoolean(): Boolean {
        return mainPrefs.getBoolean(KEY_WEB_SEARCH_ENABLED, false)
    }
    fun saveLastAiResponseForChannel(channelId: Int, responseText: String) {
        mainPrefs.edit { putString("${KEY_LAST_AI_RESPONSE_CHANNEL}${channelId}", responseText) }
    }

    fun getLastAiResponseForChannel(channelId: Int): String? {
        return mainPrefs.getString("${KEY_LAST_AI_RESPONSE_CHANNEL}${channelId}", null)
    }
    fun saveWebSearchEnabled(enabled: Boolean) {
        mainPrefs.edit { putBoolean(KEY_WEB_SEARCH_ENABLED, enabled) }
    }
    fun getWebSearchEngine(): String = mainPrefs.getString(KEY_WEB_SEARCH_ENGINE, "default") ?: "default"

    fun saveWebSearchEngine(engine: String) = mainPrefs.edit {
        putString(
            KEY_WEB_SEARCH_ENGINE,
            engine
        )
    }
    fun getWebSearchContextSize(): String = mainPrefs.getString("KEY_WEB_SEARCH_CONTEXT_SIZE", "medium") ?: "medium"

    fun saveWebSearchContextSize(size: String) = mainPrefs.edit {
        putString("KEY_WEB_SEARCH_CONTEXT_SIZE", size)
    }
    fun getWebSearchMaxResults(): Int = mainPrefs.getInt("KEY_WEB_SEARCH_MAX_RESULTS", 5)

    fun saveWebSearchMaxResults(results: Int) = mainPrefs.edit {
        putInt("KEY_WEB_SEARCH_MAX_RESULTS", results)
    }
    fun getThemeMode(): Int {
        return mainPrefs.getInt(
            KEY_THEME_MODE,
            THEME_SYSTEM
        ) // Default to System
    }

    fun saveThemeMode(mode: Int) {
        mainPrefs.edit { putInt(KEY_THEME_MODE, mode) }
    }
    fun hasMigratedMaverick(): Boolean {
        return mainPrefs.getBoolean("migrated_maverick_to_openrouter", false)
    }

    fun setMigratedMaverick() {
        mainPrefs.edit(commit = true) {
            putBoolean("migrated_maverick_to_openrouter", true)
        }
    }
    fun saveExtendedTopBarEnabled(enabled: Boolean) {
        mainPrefs.edit { putBoolean(KEY_EXTENDED_TOP_BAR, enabled) }
    }
    fun getExtendedTopBarEnabled(): Boolean {
        return mainPrefs.getBoolean(KEY_EXTENDED_TOP_BAR, false)
    }
    fun getSelectedFont(): String {
        return mainPrefs.getString(KEY_SELECTED_FONT, "geologica_light") ?: "geologica_light"
    }
    fun saveFontSizeCh(size: Int) {
        mainPrefs.edit { putInt(KEY_FONT_SIZEC, size) }
    }

    fun getFontSizeCh(): Int {
        return mainPrefs.getInt(KEY_FONT_SIZEC, 100)  // Default: 100%
    }
    fun getNotiPreference(): Boolean {
        return mainPrefs.getBoolean(KEY_NOTI_ENABLED, true)
    }

    fun saveNotiPreference(isEnabled: Boolean) {
        mainPrefs.edit {
            putBoolean(KEY_NOTI_ENABLED, isEnabled)
        }
    }
    fun getExtPreference(): Boolean {
        return mainPrefs.getBoolean(KEY_EXT_ENABLED, false)
    }

    fun saveExtPreference(isEnabled: Boolean) {
        mainPrefs.edit {
            putBoolean(KEY_EXT_ENABLED, isEnabled)
        }
    }
    fun saveExtPreference2(isEnabled: Boolean) {
        mainPrefs.edit {
            putBoolean(KEY_EXT_ENABLED2, isEnabled)
        }
    }
    fun getExtPreference2(): Boolean {
        return mainPrefs.getBoolean(KEY_EXT_ENABLED2, false)
    }
    fun saveKeepScreenOnPreference(enabled: Boolean) {
        mainPrefs.edit { putBoolean(KEY_KEEP_SCREEN_ON, enabled) }
    }
    fun getWatermarkSttEnabled(): Boolean = mainPrefs.getBoolean(KEY_WATERMARK_STT_ENABLED, false)

    fun saveWatermarkSttEnabled(enabled: Boolean) {
        mainPrefs.edit { putBoolean(KEY_WATERMARK_STT_ENABLED, enabled) }
    }
    fun getPreferFullscreenChat(): Boolean = mainPrefs.getBoolean(KEY_PREFER_FULLSCREEN_CHAT, false)

    fun savePreferFullscreenChat(enabled: Boolean) =
        mainPrefs.edit { putBoolean(KEY_PREFER_FULLSCREEN_CHAT, enabled) }
    fun getKeepScreenOnPreference(): Boolean {
        return mainPrefs.getBoolean(KEY_KEEP_SCREEN_ON, false)
    }
    fun saveBotModelPickerSortOrder(sortOrder: BotModelPickerFragment.SortOrder) {
        val value = when (sortOrder) {
            BotModelPickerFragment.SortOrder.ALPHABETICAL -> "alphabetical"
            BotModelPickerFragment.SortOrder.BY_DATE -> "by_date"
        }
        mainPrefs.edit { putString("bot_model_picker_sort_order", value) }
    }

    fun getBotModelPickerSortOrder(): BotModelPickerFragment.SortOrder {
        val value = mainPrefs.getString("bot_model_picker_sort_order", "alphabetical")  // Default to alphabetical
        return when (value) {
            "by_date" -> BotModelPickerFragment.SortOrder.BY_DATE
            else -> BotModelPickerFragment.SortOrder.ALPHABETICAL
        }
    }
    // --- OpenRouterModelsFragment Filters ---
    fun saveOpenRouterFilterType(type: String) = mainPrefs.edit { putString("open_router_filter_type", type) }
    fun getOpenRouterFilterType(): String = mainPrefs.getString("open_router_filter_type", "ALL") ?: "ALL"

    fun saveOpenRouterCostFilter(cost: String) = mainPrefs.edit { putString("open_router_cost_filter", cost) }
    fun getOpenRouterCostFilter(): String = mainPrefs.getString("open_router_cost_filter", "ALL") ?: "ALL"

    // --- BotModelPickerFragment Filters ---
    fun saveBotPickerFilterType(type: String) = mainPrefs.edit { putString("bot_picker_filter_type", type) }
    fun getBotPickerFilterType(): String = mainPrefs.getString("bot_picker_filter_type", "ALL") ?: "ALL"

    fun saveBotPickerCostFilter(cost: String) = mainPrefs.edit { putString("bot_picker_cost_filter", cost) }
    fun getBotPickerCostFilter(): String = mainPrefs.getString("bot_picker_cost_filter", "ALL") ?: "ALL"

    fun saveBotPickerSourceFilter(source: String) = mainPrefs.edit { putString("bot_picker_source_filter", source) }
    fun getBotPickerSourceFilter(): String = mainPrefs.getString("bot_picker_source_filter", "ALL") ?: "ALL"

    // --- API Key Management ---

    fun saveApiKey(alias: String, apiKey: String) {
        try {
            val secretKey = getOrCreateSecretKey(alias)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val iv = cipher.iv
            val encryptedApiKey = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))

            val ivString = Base64.encodeToString(iv, Base64.DEFAULT)
            val encryptedKeyString = Base64.encodeToString(encryptedApiKey, Base64.DEFAULT)

            apiKeysPrefs.edit {
                putString("${alias}_encrypted", encryptedKeyString)
                putString("${alias}_iv", ivString)
            }
        } catch (e: Exception) {
            //  Log.e("API_KEY_STORAGE", "Error encrypting $alias", e)
        }
    }
    fun getShowCitations(): Boolean = mainPrefs.getBoolean(KEY_SHOW_CITATIONS, true)  // Default true (show citations)

    fun saveShowCitations(show: Boolean) = mainPrefs.edit { putBoolean(KEY_SHOW_CITATIONS, show) }
    fun getApiKeyFromPrefs(alias: String): String {
        val encryptedKeyString = apiKeysPrefs.getString("${alias}_encrypted", "")
        val ivString = apiKeysPrefs.getString("${alias}_iv", "")

        return if (encryptedKeyString.isNullOrBlank() || ivString.isNullOrBlank()) {
            ""
        } else {
            try {
                val encryptedKey = Base64.decode(encryptedKeyString, Base64.DEFAULT)
                val iv = Base64.decode(ivString, Base64.DEFAULT)
                decryptApiKey(alias, iv, encryptedKey)
            } catch (e: Exception) {
                //    Log.e("API_KEY_RETRIEVAL", "Error decrypting $alias", e)
                ""
            }
        }
    }

    private fun getOrCreateSecretKey(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        return if (keyStore.containsAlias(alias)) {
            keyStore.getKey(alias, null) as SecretKey
        } else {
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }
    }

    private fun decryptApiKey(alias: String, iv: ByteArray, encryptedData: ByteArray): String {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            val secretKey = keyStore.getKey(alias, null) as SecretKey
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decryptedData = cipher.doFinal(encryptedData)
            return String(decryptedData, Charsets.UTF_8)
        } catch (e: Exception) {
            //  Log.e("API_KEY_DECRYPTION", "Error decrypting $alias", e)
            return ""
        }
    }

    // --- Model Preferences ---

    fun savePreferenceModelnewchat(value: String) {
        mainPrefs.edit(commit = true) {
            putString(KEY_MODEL_NEW_CHAT, value)
        }
    }

    fun getPreferenceModelnew(): String {
        return mainPrefs.getString(KEY_MODEL_NEW_CHAT, "openrouter/free").toString()
    }
    fun getScrollersPreference(): Boolean {
        return mainPrefs.getBoolean(KEY_SCROLLERS_ENABLED, false)
    }
    fun saveScrollersPreference(isEnabled: Boolean) {
        mainPrefs.edit {
            putBoolean(KEY_SCROLLERS_ENABLED, isEnabled)
        }
    }
    fun getPreferenceModel(): String? {
        return mainPrefs.getString(KEY_MODEL_VALE, "mistralai/mistral-medium-3")
    }
    fun getMaxTokensEnabled(): Boolean =
        mainPrefs.getBoolean(MAX_TOKENS_ENABLED, true) // default ON = current behavior

    fun saveMaxTokensEnabled(enabled: Boolean) {
        mainPrefs.edit { putBoolean(MAX_TOKENS_ENABLED, enabled) }
    }
    fun saveMaxTokens(value: String) {
        mainPrefs.edit(commit = true) {
            putString(KEY_MAX_TOKENS, value)
        }
    }

    fun getMaxTokens(): String {
        return mainPrefs.getString(KEY_MAX_TOKENS, "12000").toString()
    }
    fun getCustomModels(): MutableList<LlmModel> {
        val jsonString = mainPrefs.getString(KEY_CUSTOM_MODELS, null)
        return if (jsonString != null) {
            json.decodeFromString<MutableList<LlmModel>>(jsonString)
        } else {
            mutableListOf()
        }
    }

    fun saveCustomModels(models: List<LlmModel>) {
        val jsonString = json.encodeToString(models)
        mainPrefs.edit { putString(KEY_CUSTOM_MODELS, jsonString) }
    }

    fun seedDefaultModelsIfNeeded() {
        if (!mainPrefs.getBoolean(KEY_DEFAULT_MODELS_SEEDED, false)) {
            val defaultModels = listOf(
                LlmModel(
                    displayName = "xAI: Grok Latest",
                    apiIdentifier = "~x-ai/grok-latest",
                    isVisionCapable = true,          // input_modalities: text, image, file
                    isReasoningCapable = true,
                    reasoningEfforts = listOf("xhigh", "high", "medium", "low"),
                    reasoningDefaultEffort = "high",
                    reasoningMandatory = true        // reasoning.mandatory: true
                ),
                LlmModel(
                    displayName = "Z.ai: GLM Latest",
                    apiIdentifier = "~z-ai/glm-latest",
                    isVisionCapable = false,         // text-only input
                    isReasoningCapable = true,
                    reasoningEfforts = listOf("max", "high", "low"),
                    reasoningDefaultEffort = "max",
                    reasoningMandatory = true
                ),
                LlmModel(
                    displayName = "Perplexity: Sonar",
                    apiIdentifier = "perplexity/sonar",
                    isVisionCapable = true            // input_modalities: text, image
                    // no reasoning fields — supported_parameters has no "reasoning"/"reasoning_effort"
                )
            )

            val customModels = getCustomModels()
            customModels.addAll(defaultModels)
            saveCustomModels(customModels)
            mainPrefs.edit { putBoolean(KEY_DEFAULT_MODELS_SEEDED, true) }
        }
    }

    // --- System Message Preferences ---

    fun saveSelectedSystemMessage(systemMessage: SystemMessage) {
        val jsonString = json.encodeToString(systemMessage)
        mainPrefs.edit { putString(KEY_SELECTED_SYSTEM_MESSAGE, jsonString) }
    }

    fun getSelectedSystemMessage(): SystemMessage {
        val jsonString = mainPrefs.getString(KEY_SELECTED_SYSTEM_MESSAGE, null)
        return if (jsonString != null) {
            json.decodeFromString(jsonString)
        } else {
            // Return the saved default message instead of creating a new instance
            getDefaultSystemMessage()
        }
    }

    fun getCustomSystemMessages(): List<SystemMessage> {
        val jsonString = mainPrefs.getString(KEY_CUSTOM_SYSTEM_MESSAGES, null)
        return if (jsonString != null) {
            json.decodeFromString(jsonString)
        } else {
            emptyList()
        }
    }

    fun saveCustomSystemMessages(systemMessages: List<SystemMessage>) {
        val jsonString = json.encodeToString(systemMessages)
        mainPrefs.edit { putString(KEY_CUSTOM_SYSTEM_MESSAGES, jsonString) }
    }

    fun seedDefaultSystemMessagesIfNeeded() {
        if (!mainPrefs.getBoolean(KEY_DEFAULT_SYSTEM_MESSAGES_SEEDED, false)) {
            val defaultSystemMessages = listOf(
                SystemMessage("Spelling Corrector", "Correct the spelling and grammar of the following text. Only provide the corrected text, without any additional commentary or explanation.", isDefault = false),
                SystemMessage("Summarizer", "Summarize the following text. Provide a concise summary, without any additional commentary or explanation. Markdown rendering is supported in your response", isDefault = false)
            )
            val customSystemMessages = getCustomSystemMessages().toMutableList()
            customSystemMessages.addAll(defaultSystemMessages)
            saveCustomSystemMessages(customSystemMessages)
            mainPrefs.edit { putBoolean(KEY_DEFAULT_SYSTEM_MESSAGES_SEEDED, true) }
        }
    }

    fun getDefaultSystemMessage(): SystemMessage {
        val jsonString = mainPrefs.getString(KEY_DEFAULT_SYSTEM_MESSAGE, null)
        return if (jsonString != null) {
            json.decodeFromString(jsonString)
        } else {
            // Fallback to the original default
            SystemMessage("Default", "You are a helpful assistant. Markdown rendering is supported in your response", isDefault = true)
        }
    }

    // Add this method to save the default system message
    fun saveDefaultSystemMessage(systemMessage: SystemMessage) {
        val jsonString = json.encodeToString(systemMessage)
        mainPrefs.edit { putString(KEY_DEFAULT_SYSTEM_MESSAGE, jsonString) }
    }
    fun savePresets(presets: List<Preset>) {
        val jsonString = json.encodeToString(presets)
        mainPrefs.edit { putString(KEY_PRESETS, jsonString) }
    }

    fun getPresets(): List<Preset> {
        val jsonString = mainPrefs.getString(KEY_PRESETS, null)
        return if (jsonString != null) {
            try {
                json.decodeFromString(jsonString)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }
    fun setLanEndpoint(url: String?) {
        // Removing the entry is handy for “clear” / “reset” actions
        mainPrefs.edit {
            if (url.isNullOrBlank()) remove(KEY_LAN_ENDPOINT)
            else putString(KEY_LAN_ENDPOINT, url)
        }
    }
    fun getLanApiKey(): String {
        return mainPrefs.getString(LAN_API_KEY, "any-non-empty-string") ?: "any-non-empty-string"
    }

    fun setLanApiKey(apiKey: String?) {
        mainPrefs.edit {
            putString(LAN_API_KEY, apiKey ?: "any-non-empty-string")  // ALWAYS non-blank
        }
    }
    fun getLanEndpoint(): String? {
        // May be null if the user never set a value
        return mainPrefs.getString(KEY_LAN_ENDPOINT, null)
    }
    fun getLanProvider(): String {
        return mainPrefs.getString(LAN_PROVIDER_KEY, LAN_PROVIDER_OLLAMA) ?: LAN_PROVIDER_OLLAMA
    }
    fun setLanProvider(provider: String) {
        mainPrefs.edit { putString(LAN_PROVIDER_KEY, provider) }
    }
    fun saveClearChatDefault(checked: Boolean) {
        mainPrefs.edit { putBoolean(KEY_CLEAR_CHAT_DEFAULT, checked) }
    }

    fun getClearChatDefault(): Boolean {
        return mainPrefs.getBoolean(KEY_CLEAR_CHAT_DEFAULT, false)  // Default to unchecked (false)
    }
    fun saveClearChatDefault2(checked: Boolean) {
        mainPrefs.edit { putBoolean(KEY_CLEAR_CHAT_DEFAULT2, checked) }
    }

    fun getClearChatDefault2(): Boolean {
        return mainPrefs.getBoolean(KEY_CLEAR_CHAT_DEFAULT2, false)  // Default to unchecked (false)
    }

}

