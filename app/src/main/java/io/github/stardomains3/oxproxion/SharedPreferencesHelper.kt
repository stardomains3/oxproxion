package io.github.stardomains3.oxproxion

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
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
    private val json = Json { ignoreUnknownKeys = true }
    private val gson = Gson() // Kept temporarily for migration only

    companion object {
        const val LAN_PROVIDER_KEY = "lan_provider"
        const val LAN_PROVIDER_OLLAMA = "ollama"
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
            Log.d("Migration", "Starting migration from Gson to Kotlin Serialization")

            runCatching { migrateCustomModels() }.onFailure {
                Log.e("Migration", "Failed to migrate custom models", it)
            }
            runCatching { migrateSystemMessages() }.onFailure {
                Log.e("Migration", "Failed to migrate system messages", it)
            }
            runCatching { migrateOpenRouterModels() }.onFailure {
                Log.e("Migration", "Failed to migrate OpenRouter models", it)
            }
            runCatching { migrateSelectedSystemMessage() }.onFailure {
                Log.e("Migration", "Failed to migrate selected system message", it)
            }
            runCatching { migrateDefaultSystemMessage() }.onFailure {
                Log.e("Migration", "Failed to migrate default system message", it)
            }

            mainPrefs.edit { putBoolean(KEY_MIGRATION_COMPLETE, true) }
            Log.d("Migration", "Migration to Kotlin Serialization complete")
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

    fun getReasoningEffort(): String = mainPrefs.getString("reasoning_effort", "medium") ?: "medium"
    fun saveReasoningEffort(effort: String) = mainPrefs.edit {
        putString(
            "reasoning_effort",
            effort
        )
    }

    fun getReasoningExclude(): Boolean = mainPrefs.getBoolean("reasoning_exclude", true)  // Default to true (exclude)
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

    fun saveSortOrder(sortOrder: SortOrder) {
        mainPrefs.edit { putString(KEY_SORT_ORDER, sortOrder.name) }
    }

    fun getSortOrder(): SortOrder {
        val sortOrderName = mainPrefs.getString(KEY_SORT_ORDER, SortOrder.ALPHABETICAL.name)
        return SortOrder.valueOf(sortOrderName ?: SortOrder.ALPHABETICAL.name)
    }

    fun setOpenRouterInfoDismissed(dismissed: Boolean) {
        mainPrefs.edit { putBoolean(KEY_INFO_BAR_DISMISSED, dismissed) }
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

    fun getSelectedFont(): String {
        return mainPrefs.getString(KEY_SELECTED_FONT, "geologica_light") ?: "geologica_light"
    }
    fun getNotiPreference(): Boolean {
        return mainPrefs.getBoolean(KEY_NOTI_ENABLED, false)
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
    fun getGeminiAspectRatio(): String? = mainPrefs.getString("gemini_aspect_ratio", null)

    fun saveGeminiAspectRatio(ratio: String) {
        mainPrefs.edit { putString("gemini_aspect_ratio", ratio) }
    }
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
            Log.e("API_KEY_STORAGE", "Error encrypting $alias", e)
        }
    }

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
                Log.e("API_KEY_RETRIEVAL", "Error decrypting $alias", e)
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
            Log.e("API_KEY_DECRYPTION", "Error decrypting $alias", e)
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
        return mainPrefs.getString(KEY_MODEL_NEW_CHAT, "meta-llama/llama-4-maverick").toString()
    }

    fun getPreferenceModel(): String? {
        return mainPrefs.getString(KEY_MODEL_VALE, "mistralai/mistral-medium-3")
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
                LlmModel("OpenAI: ChatGPT-4o", "openai/chatgpt-4o-latest", true),
                LlmModel("MoonshotAI: Kimi K2", "moonshotai/kimi-k2", false),
                LlmModel("xAI: Grok 3", "x-ai/grok-3", false),
                LlmModel("Mistral: Mistral Medium 3", "mistralai/mistral-medium-3", true),
                LlmModel("Deepseek: R1 0528", "deepseek/deepseek-r1-0528", false),
                LlmModel("Deepseek: V3 0324", "deepseek/deepseek-chat-v3-0324", false),
                LlmModel("Qwen: Qwen3 235B A22B Instruct 2507", "qwen/qwen3-235b-a22b-2507", false),
                LlmModel("Baidu: ERNIE 4.5 300B A47B", "baidu/ernie-4.5-300b-a47b", false),
                LlmModel("Google: Gemini 2.5 Flash", "google/gemini-2.5-flash", true),
                LlmModel("Google: Gemini 2.5 Pro", "google/gemini-2.5-pro", true),
                LlmModel("xAI: Grok 4", "x-ai/grok-4", true),
                LlmModel("OpenAI: GPT-4.1", "openai/gpt-4.1", true),
                LlmModel("Anthropic: Claude Sonnet 4", "anthropic/claude-sonnet-4", true),
                LlmModel("Perplexity: Sonar Pro", "perplexity/sonar-pro", false)
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

    fun getLanEndpoint(): String? {
        // May be null if the user never set a value
        return mainPrefs.getString(KEY_LAN_ENDPOINT, null)
    }
    fun getLanProvider(): String {
        return mainPrefs.getString(
            LAN_PROVIDER_KEY,
            LAN_PROVIDER_OLLAMA
        ) ?: LAN_PROVIDER_OLLAMA
    }

    fun setLanProvider(provider: String) {
        mainPrefs.edit().putString(LAN_PROVIDER_KEY, provider).apply()
    }

}

