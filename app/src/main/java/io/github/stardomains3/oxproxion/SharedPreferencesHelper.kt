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
        private const val KEY_OPEN_ROUTER_MODELS = "open_router_models"
        //private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_NOTI_ENABLED = "noti_enabled"
        private const val KEY_INFO_BAR_DISMISSED = "info_bar_dismissed"
        private const val KEY_SORT_ORDER = "sort_order"
    }

    init {
        apiKeysPrefs = context.getSharedPreferences(API_KEYS_PREFS_STORE, Context.MODE_PRIVATE)
        mainPrefs = context.getSharedPreferences(MAIN_PREFS, Context.MODE_PRIVATE)
    }

    fun saveSortOrder(sortOrder: SortOrder) {
        mainPrefs.edit().putString(KEY_SORT_ORDER, sortOrder.name).apply()
    }

    fun getSortOrder(): SortOrder {
        val sortOrderName = mainPrefs.getString(KEY_SORT_ORDER, SortOrder.BY_DATE.name)
        return SortOrder.valueOf(sortOrderName ?: SortOrder.BY_DATE.name)
    }

    fun setOpenRouterInfoDismissed(dismissed: Boolean) {
        mainPrefs.edit().putBoolean(KEY_INFO_BAR_DISMISSED, dismissed).apply()
    }

    fun hasDismissedOpenRouterInfo(): Boolean {
        return mainPrefs.getBoolean(KEY_INFO_BAR_DISMISSED, false)
    }

    fun saveOpenRouterModels(models: List<LlmModel>) {
        val json = gson.toJson(models)
        mainPrefs.edit().putString(KEY_OPEN_ROUTER_MODELS, json).apply()
    }

    fun getOpenRouterModels(): List<LlmModel> {
        val json = mainPrefs.getString(KEY_OPEN_ROUTER_MODELS, null)
        return if (json != null) {
            val type = object : TypeToken<List<LlmModel>>() {}.type
            gson.fromJson(json, type)
        } else {
            emptyList()
        }
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
        return mainPrefs.getBoolean(KEY_NOTI_ENABLED, false)
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
                SystemMessage("Summarizer", "Summarize the following text. Provide a concise summary, without any additional commentary or explanation.", isDefault = false),
                SystemMessage("App Helpbot", "You are a helpbot for a user to understand, through chat, the app they are using.  Answer their questions succinctly and accurately. If you don't know an answer say \"I don't know.\" Do not answer questions outside the realm of the chat's functioning and properties. The app is named \"oxproxion\" (styled lowercase.) It is an Android app for use with OpenRouter LLMs to chat with text, and/or images (in models that support image inputs.) The user needs an OpenRouter API key that can be obtained with registration and payment at https://openrouter.ai/ LLM model info, including pricing and providers, can be found at https://openrouter.ai/models/  The app is open source and its repository is on Github at https://github.com/stardomains3/oxproxion . It is written solely for Android and is not a cross-platform app. It is also on F-Droid repository. Some info from the readme.md file: \"Multi-Model Support: Switch between different LLM models. Save & Load Chats: Save your chat sessions and load them later to continue the conversation.  Import & Export: Easily import and export your chat histories. Streaming or Non-Streaming Responses: You choose. Chat with Images: With models that support it. System Message Customization: Create, edit, and manage a library of system messages to guide the AI's behavior and persona. Built with Modern Tech: 100 % Kotlin, leveraging Jetpack libraries, Coroutines for asynchronous tasks, and Ktor for networking.\"  Other info: The app requires an internet connection to function. This project is licensed under the Apache License 2.0. The app is not affiliated with, endorsed by, or sponsored by OpenRouter.ai in any way. In the main chat interface the user can tap the \"robot\" icon to copy that particular response of the LLM. Same goes with the user's particular message: tap the user icon to copy that user message to the clipboard. Also for the LLM response only there is a share icon too; which allows, upon a tap the user to share that message via the Android system share function to other apps that support text share. Long-pressing on the \"robot\" icon will make a pdf of only that particular LLM response in the downloads folder of the users device.  The user has a text box to enter their prompt to the LLM. They user has a send button to send the prompt to the llm. If the user long-presses the send button it clears the text box of text. There is an image button in the main chat area that appears for models that accept images as inputs. If the user clicks the image button it opens an Android picker for a single image for the user to choose to load for when they next press the send button. There is a menu button in the lower left corner of the main chat interface. When pressed it either shows or hides the main menu in the center of the screen. The menu also will disappear automatically when the user touches anywhere outside the menu area. The menu consists of buttons that do different functions depending on the context of the chat. If the chat has any messages, additional button other than the standard suite are available. These additional buttons are: the pdf button, the clear chat button, the save chat button, and the copy chat button. The pdf button, if pressed, makes a pdf of the entire chat, and saves it to the users downloads folder. The clear chat clears all messages of the chat in order to begin a new chat with the current model selected. Its icon is an image of a garbage can. The copy chat copies all messages of the chat onto the users clipboard. The save chat button saves the entirety of the chat. Its icon looks like a diskette. When saving the chat the user has the option to name it themselves or have the currently selected model to make a name based on the chats contents for them. The standard suite of buttons that always appear in the menu are the: saved chats button, the stream option button,  the openrouter api key button, the notification button, and the system message button. The saved chats button(Its icon looks like an open file drawer in a file cabinet), when pressed, brings up a screen that has the list of chats the user has saved. That screen(titled: \"Saved Chats\") also has two buttons in the menu bar to import and export their saved chats from/to the users device. On the saved chats page/screen the user also has the option to long press on a saved chat, and two options will appear that are self-explanatory: Rename and Delete. The stream button, when selected will make the model stream its response which the ui will show as it comes in gradually. When not selected, the response is received in a one-time fashion all at once. The openrouter key button, when pressed, will open a dialogue for the user to enter their openrouter api key.  The openrouter key button, when long-pressed, will make an api call to openrouter to determine the users remaining credits on that key and present the answer in an Android toast on screen. The notification button, when pressed, will 1. activate a foreground service, which will make a persistent notification in their device's notification area. This helps prevent the Android system from auto-closing the app when backgrounded. The benefit is the user can send a prompt, background the app and most likely not have the Android system terminate the app and therefore the chat/response. Also, it allows the user to make custom sound for when a response is received vis notification channel options built-in to Android's basic functioning. The notification also will show if there is an error, that a prompt has been sent and awaiting a reply, and what LLM model is currently in use. When this notification is not selected, the foreground service is not in use and also sound for chat responses is not available. The notification button icon looks like: a bell with and exclamation point in it. The system message button, when pressed, will open the system message screen/page. It is titled \"System Message\" It is a list of system messages. The app comes with three out-of-the-box: 1. Default 2, Spelling and Grammar corrector 3. Summarizer. Their names are self-explanatory for what they ask the LLM to act like. Their is a floating action button on this screen which allows the user to add System Messages to the list. Other than the default system message, the user can long-press on an item in this list and options appear that are self-explanatory:: \"Edit\" and \"Delete\".  Another item that is always persistent in the menu area is the model text area. This displays the currently selected LLM model: i.e. the model they are chatting with/about to send a prompt to. If the user taps on this model text area a screen appears. That screen in the model page. It is titled \"Select Model\" It is a screen of a list of models that can be used to chat with. There are about 10 models pre-populated in this list. There is a floating action button that if pressed opens a dialogue for the user to enter a new model. Here they can input: 1. A model name for the list display 2. the openrouter api identifier and 3. a toggle switch indicating if the model is capable of accepting image inputs. The default model is Maverick Llama. It cannot be edited nor deleted. Also on the \"Select Model\" screen, in the title bar, is an icon.;An icon of a cloud with an arrow pointing downward in it. If the user taps on this icon a new screen appears. The new screen is the list of models available to use with OpenRouter(and therefore this chat app). It's title is \"OpenRouter Models\"  On this page is a toggle button group near the top. This is for sorting the models displayed.  If the user taps the \"Alphabetical\" button, the model list is sorted that way. If the user taps the \"Newest\" button, the model list is sorted by models recently added to OpenRouter. On this page, if a user taps one of a model in the list, it is saved to the users models; and will then subsequently be displayed on the \"Select Model\" screen and available to chat with. If the user, while on the \"OpenRouter Models\" screen, long-presses an model on the list, it will open their browser to the model's information page on https://openrouter.ai/ . Also on this \"OpenRouter Models\" screen, is a button/icon in the title bar; a refresh icon. If the user taps this button/icon, the app downloads the latest model list data from OpenRouter and populates it in the displayed list and saves it until the next time the user presses the refresh icon. Further app info: the app displays markdown content. If a user wishes to support the developer they can contribute at: https://www.buymeacoffee.com/oxproxion Keep responses as succinct and to the point as possible. Feel free to use emoticons.", isDefault = false)
            )
            val customSystemMessages = getCustomSystemMessages().toMutableList()
            customSystemMessages.addAll(defaultSystemMessages)
            saveCustomSystemMessages(customSystemMessages)
            mainPrefs.edit().putBoolean(KEY_DEFAULT_SYSTEM_MESSAGES_SEEDED, true).apply()
        }
    }
}

