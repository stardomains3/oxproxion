package io.github.stardomains3.oxproxion

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SharedPreferencesHelper(context: Context) {

    private val apiKeysPrefs: SharedPreferences
    private val mainPrefs: SharedPreferences
    private val gson = Gson()

    companion object {
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
        //private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_NOTI_ENABLED = "noti_enabled"
    }

    init {
        apiKeysPrefs = context.getSharedPreferences(API_KEYS_PREFS_STORE, Context.MODE_PRIVATE)
        mainPrefs = context.getSharedPreferences(MAIN_PREFS, Context.MODE_PRIVATE)
    }

    /*fun saveSoundPreference(isEnabled: Boolean) {
        with(mainPrefs.edit()) {
            putBoolean(KEY_SOUND_ENABLED, isEnabled)
            apply()
        }
    }

    fun getSoundPreference(): Boolean {
        return mainPrefs.getBoolean(KEY_SOUND_ENABLED, false)
    }*/

    fun saveStreamingPreference(isEnabled: Boolean) {
        with(mainPrefs.edit()) {
            putBoolean(KEY_STREAMING_ENABLED, isEnabled)
            apply()
        }
    }

    fun getStreamingPreference(): Boolean {
        return mainPrefs.getBoolean(KEY_STREAMING_ENABLED, false)
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

            with(apiKeysPrefs.edit()) {
                putString("${alias}_encrypted", encryptedKeyString)
                putString("${alias}_iv", ivString)
                apply()
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
        with(mainPrefs.edit()) {
            putString(KEY_MODEL_NEW_CHAT, value)
            commit()
        }
    }

    fun getPreferenceModelnew(): String {
        return mainPrefs.getString(KEY_MODEL_NEW_CHAT, "meta-llama/llama-4-maverick").toString()
    }
    fun getNotiPreference(): Boolean {
        return mainPrefs.getBoolean(KEY_NOTI_ENABLED, true)
    }
    fun saveNotiPreference(isEnabled: Boolean) {
        with(mainPrefs.edit()) {
            putBoolean(KEY_NOTI_ENABLED, isEnabled)
            apply()
        }
    }
    fun getPreferenceModel(): String? {
        return mainPrefs.getString(KEY_MODEL_VALE, "mistralai/mistral-medium-3")
    }

    fun getCustomModels(): MutableList<LlmModel> {
        val json = mainPrefs.getString(KEY_CUSTOM_MODELS, null)
        return if (json != null) {
            val type = object : TypeToken<MutableList<LlmModel>>() {}.type
            gson.fromJson(json, type)
        } else {
            mutableListOf()
        }
    }

    fun saveCustomModels(models: List<LlmModel>) {
        val json = gson.toJson(models)
        mainPrefs.edit().putString(KEY_CUSTOM_MODELS, json).apply()
    }

    fun seedDefaultModelsIfNeeded() {
        if (!mainPrefs.getBoolean(KEY_DEFAULT_MODELS_SEEDED, false)) {
            val defaultModels = listOf(
                LlmModel("ChatGPT 4o Latest", "openai/chatgpt-4o-latest", true),
                LlmModel("Kimi K2", "moonshotai/kimi-k2", false),
                LlmModel("Grok 3", "x-ai/grok-3", false),
                LlmModel("Mistral Medium 3", "mistralai/mistral-medium-3", true),
                LlmModel("Deepseek R1 0528", "deepseek/deepseek-r1-0528", false),
                LlmModel("Deepseek V3 0324", "deepseek/deepseek-chat-v3-0324", false),
                LlmModel("Qwen3 235B 2507", "qwen/qwen3-235b-a22b-2507", false),
                LlmModel("Ernie 4.5 300B", "baidu/ernie-4.5-300b-a47b", false),
                LlmModel("Gemini 2.5 Flash", "google/gemini-2.5-flash", true),
                LlmModel("Gemini 2.5 Pro", "google/gemini-2.5-pro", true),
                LlmModel("Grok 4", "x-ai/grok-4", true),
                LlmModel("GPT 4.1", "openai/gpt-4.1", true),
                LlmModel("Claude Sonnet 4", "anthropic/claude-sonnet-4", true),
                LlmModel("Perplexity Sonar Pro", "perplexity/sonar-pro", false)
            )
            val customModels = getCustomModels()
            customModels.addAll(defaultModels)
            saveCustomModels(customModels)
            mainPrefs.edit().putBoolean(KEY_DEFAULT_MODELS_SEEDED, true).apply()
        }
    }

    // --- System Message Preferences ---

    fun saveSelectedSystemMessage(systemMessage: SystemMessage) {
        val json = gson.toJson(systemMessage)
        mainPrefs.edit().putString(KEY_SELECTED_SYSTEM_MESSAGE, json).apply()
    }

    fun getSelectedSystemMessage(): SystemMessage {
        val json = mainPrefs.getString(KEY_SELECTED_SYSTEM_MESSAGE, null)
        return if (json != null) {
            gson.fromJson(json, SystemMessage::class.java)
        } else {
            SystemMessage("Default", "You are a helpful assistant.", isDefault = true)
        }
    }

    fun getCustomSystemMessages(): List<SystemMessage> {
        val json = mainPrefs.getString(KEY_CUSTOM_SYSTEM_MESSAGES, null)
        return if (json != null) {
            val type = object : TypeToken<List<SystemMessage>>() {}.type
            gson.fromJson(json, type)
        } else {
            emptyList()
        }
    }

    fun saveCustomSystemMessages(systemMessages: List<SystemMessage>) {
        val json = gson.toJson(systemMessages)
        mainPrefs.edit().putString(KEY_CUSTOM_SYSTEM_MESSAGES, json).apply()
    }

    fun seedDefaultSystemMessagesIfNeeded() {
        if (!mainPrefs.getBoolean(KEY_DEFAULT_SYSTEM_MESSAGES_SEEDED, false)) {
            val defaultSystemMessages = listOf(
                SystemMessage("Spelling Corrector", "Correct the spelling and grammar of the following text. Only provide the corrected text, without any additional commentary or explanation.", isDefault = false),
                SystemMessage("Summarizer", "Summarize the following text. Provide a concise summary, without any additional commentary or explanation.", isDefault = false)
            )
            val customSystemMessages = getCustomSystemMessages().toMutableList()
            customSystemMessages.addAll(defaultSystemMessages)
            saveCustomSystemMessages(customSystemMessages)
            mainPrefs.edit().putBoolean(KEY_DEFAULT_SYSTEM_MESSAGES_SEEDED, true).apply()
        }
    }
}

