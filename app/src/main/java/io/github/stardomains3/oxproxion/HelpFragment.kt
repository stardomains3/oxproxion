package io.github.stardomains3.oxproxion

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.View
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.syntax.Prism4jThemeDarkula
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.Prism4j

class HelpFragment : Fragment(R.layout.fragment_help) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val helpContentTextView = view.findViewById<TextView>(R.id.helpContentTextView)
        val sharedPreferencesHelper = SharedPreferencesHelper(requireContext())
        val selectedFontName = sharedPreferencesHelper.getSelectedFont()
        val typeface = try {
            when (selectedFontName) {
                "system_default" -> Typeface.DEFAULT
                "alansans_regular" -> ResourcesCompat.getFont(requireContext(), R.font.alansans_regular)
                "notoserif_regular" -> ResourcesCompat.getFont(requireContext(), R.font.notoserif_regular)
                "alexandria_regular" -> ResourcesCompat.getFont(requireContext(), R.font.alexandria_regular)
                "aronesans_regular" -> ResourcesCompat.getFont(requireContext(), R.font.aronesans_regular)
                "funneldisplay_regular" -> ResourcesCompat.getFont(requireContext(), R.font.funneldisplay_regular)
                "geologica_light" -> ResourcesCompat.getFont(requireContext(), R.font.geologica_light)
                "instrumentsans_regular" -> ResourcesCompat.getFont(requireContext(), R.font.instrumentsans_regular)
                "lexend_regular" -> ResourcesCompat.getFont(requireContext(), R.font.lexend_regular)
                "merriweather_24pt_regular" -> ResourcesCompat.getFont(requireContext(), R.font.merriweather_24pt_regular)
                "merriweathersans_light" -> ResourcesCompat.getFont(requireContext(), R.font.merriweathersans_light)
                "mplus2_regular" -> ResourcesCompat.getFont(requireContext(), R.font.mplus2_regular)
                "nokora_regular" -> ResourcesCompat.getFont(requireContext(), R.font.nokora_regular)
                "notosans_regular" -> ResourcesCompat.getFont(requireContext(), R.font.notosans_regular)
                "opensans_regular" -> ResourcesCompat.getFont(requireContext(), R.font.opensans_regular)
                "outfit_regular" -> ResourcesCompat.getFont(requireContext(), R.font.outfit_regular)
                "poppins_regular" -> ResourcesCompat.getFont(requireContext(), R.font.poppins_regular)
                "readexpro_regular" -> ResourcesCompat.getFont(requireContext(), R.font.readexpro_regular)
                "roboto_regular" -> ResourcesCompat.getFont(requireContext(), R.font.roboto_regular)
                "robotoserif_regular" -> ResourcesCompat.getFont(requireContext(), R.font.robotoserif_regular)
                "sourceserif4_regular" -> ResourcesCompat.getFont(requireContext(), R.font.sourceserif4_regular)
                "tasaorbiter_regular" -> ResourcesCompat.getFont(requireContext(), R.font.tasaorbiter_regular)
                "ubuntusans_regular" -> ResourcesCompat.getFont(requireContext(), R.font.ubuntusans_regular)
                "vendsans_regular" -> ResourcesCompat.getFont(requireContext(), R.font.vendsans_regular)
                else -> ResourcesCompat.getFont(requireContext(), R.font.geologica_light)
            }
        } catch (e: Exception) {
            Typeface.DEFAULT  // Fallback
        }
        helpContentTextView.typeface = typeface
        val markwon = Markwon.builder(requireContext())
            .usePlugin(HtmlPlugin.create())
            .usePlugin(LinkifyPlugin.create(Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(requireContext()))
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder
                        .codeTextColor(Color.LTGRAY)
                        .codeBackgroundColor(Color.argb(128, 0, 0, 0))
                        .codeBlockBackgroundColor(Color.argb(128, 0, 0, 0))
                        .blockQuoteColor(Color.BLACK)
                        .isLinkUnderlined(true)
                }
            })
            .build()


        val markdownContent = """
            # oxproxion Help Guide

            Welcome! This guide will help you understand how to use the **oxproxion** app.

            **oxproxion** is an open-source Android app for chatting with OpenRouter LLMs, supporting both text and image inputs for compatible models. It now also allows chat with Ollama, LM Studio, or llama.cpp models served on your LAN.

            ---

            ## 🚀 Getting Started

            To use the app, you need an **OpenRouter API key with credit**.
            *   Get and fund your key at: <br>[https://openrouter.ai/](https://openrouter.ai/)
            <!---->
            *   Find model info and pricing at: <br>[https://openrouter.ai/models/](https://openrouter.ai/models/)

            ---

            ## 🔗 Important Links

            *   **GitHub Repo**: <br>[stardomains3/oxproxion](https://github.com/stardomains3/oxproxion)
            <!---->
            *   **Support the Dev**: <br>[Buy Me a Coffee](https://www.buymeacoffee.com/oxproxion) ☕

            ---

            ## ✨ Core Features

            *   **Multi-Model Support**: Easily switch between different LLM models.
            *   **Save & Load Chats**: Save sessions and continue conversations later.
            *   **Import & Export**: Manage your chat histories.
            *   **Streaming Responses**: Choose between real-time streaming or full responses.
            *   **Chat with Images**: Use models that support vision.
            *   **Generate Images**: Use models that support it.
            *   **System Messages**: Customize the AI's behavior and persona.
            *   **Modern Tech**: Built for Android using native tools like Kotlin, Jetpack, Coroutines, and Ktor.

            ---

            ## 📱 Main Chat Interface

            ### Interacting with Messages
            *   **Copy AI Response**: Tap the **robot icon** to copy the AI's message. Long Press to copy Markdown RAW.
            *   **Copy User Message**: Tap the **user icon** to copy your message.
            *   **Share AI Response**: Tap the **share icon** to send the AI's text to other apps. Long-press to share the raw markdown of the response.
            *   **Speak AI Response**: Tap the **speaker icon** to speak out loud the AI's response(Up to 3900 characters.) Will not display if your device's text-to-speech engine isn't available. Long-press to save an audio wav file of the AI's response to your downloads folder. (Made on device using Android tools. Generation usually done in seconds.)
            *   **Create PDF of Response**: Tap the **pdf icon** to save just that response as a PDF in your device's Downloads folder.
            *   **Create Markdown File of Response**: Tap the **Markdown icon** to save just that response as a .md file in your device's Downloads folder.
            *   **Edit User Message**: Tap the **edit icon** on a user message to load its text into the input box for editing. Caution: this removes the message and all subsequent messages from the history.
            *   **Resend User Message**: Tap the **resend icon** on a user message to resend the prompt and generate a new response. Caution: this removes all messages after it while keeping the original prompt. Note: if the page has a white background the conversion may make that transparent, but this shouldn't be an issue with the vision model; It just may look different in the image preview you see..

            ### Sending Prompts
            *   **Text Box**: Enter your prompt.
            *   **Send Button**: Send your prompt to the LLM. **Long-press** toggles scroll arrows preference.
            *   **Stop Button**: During an api call, tap the Stop Button to end the api call.
            *   **Attach Document(s) Button**: Click icon to attach **text-based only** files to send with your prompt. Long-press to see what's attached and/or detach them.
            *   **Image Button**: Enabled for vision models. Click icon to attach a single image or take picture up to 12MB in size. Long-click goes straight to camera. Also, PDF pages. Select a PDF, then select a page to send(if single page, no page selection appears.) Uses on-device native Android tools to convert to data for the vision model. Select additional pages in following rounds of the chat. 
            *   **Palette Button**: If using Nano-Banana, tap to set aspect ratio of returned generated image.
            
            ### Other Buttons
            *   **Clear Chat**(button on bottom left): Starts a new chat with the current model. Long-press to start a new chat without a warning alert.
            *   **System Message Button**: Opens your library of system messages. If you currently are on a non-default System Message you can long-press and it will auto switch to your default.
            *   **Saved Chats**: Opens a list of your saved conversations.
            *   **Save Chat**: Saves the chat. Only text chats can be saved. You can name it yourself or let an AI generate a title(qwen/qwen3-30b-a3b-instruct-2507 is used and the entire chat is sent to it for this function.)
            
            ---

            ##  Menu

            Tap the **menu button** (the one on the lower left with nine dots) to show or hide the main menu. The menu will also hide if you tap outside of it.
            If your prompt is filled with spelling/grammar errors you can long-press the menu button to send the prompt to model "qwen/qwen3-30b-a3b-instruct-2507" to fix it and it will automatically correct the prompt in the prompt box.

            ### Contextual Buttons (Enabled during a chat)
            *   **Copy Chat**: Copies the full conversation to your clipboard. Long Press to copy Markdown RAW.
            *   **Save to Markdown**: Saves the full conversation to a markdown file in your downloads folder.
            *   **Save to HTML**: Saves the full conversation to an HTML file in your downloads folder.
            *   **Print Chat**: Prints the full conversation. You can save as PDF here too. You can select page size too.
            *   **PDF Button**: Creates a PDF of the entire chat in your downloads folder.
            
            ### Standard Buttons (Always in the menu)
            *   **Stream Button**: Toggles streaming responses on or off.
            *   **Reasoning Button**: Appears only for models that support reasoning. Toggles reasoning on or off. When enabled, the model uses deeper thinking for more thoughtful responses. When disabled, it explicitly instructs the model not to use reasoning. Reasoning traces are not returned in the response. Defaults to medium effort. If you long-press on the button if it is selected, you can enable Advanced Reasoning settings(see below). 
            *   **Max Tokens Button**: Opens a dialog to set your Max Tokens value. Max Tokens limit the length of the AI's response. A higher number allows longer replies but may increase costs. Default is 12000.
            *   **API Key Button**: Opens a dialog to enter your OpenRouter API key. **Long-press** to check your remaining credits.
            *   **Notification Button**: Receive notifications when the app is backgrounded and you receive a response.
            *   **Extended Dock Button**: Toggles extended dock on or off. If on, an extra row is added to the bottom dock. Long-Press to show a Presets button above the Send button for quick access to presets. Long-press again to hide.
            *   **Web Search Button**: Enables web search(model gets web search information) for **one response only** (auto-off, OpenRouter models only). Long-press to choose engine: [**Default** (native if available, else Exa), **Native only**, **Exa only**].  **Note**: Exa is OpenRouter's search provider. Native is server-side of provider(OpenAI, xAI, Anthropic, etc.) Check OpenRouter/provider's docs for pricing — can be expensive!
            *   **Paste Button**: Pastes the contents of your clipboard to the prompt box; When long-pressed, pastes the clipboard to the prompt box and auto-sends it to your selected model. (This button only appears when extended dock is on.)
            *   **Speech-to-Text Button**: Appears when the prompt box is empty, or a Clear Prompt button when the prompt box has some text. (This button only appears when extended dock is on.)
            *   **Conversation Button**: Toggles "Audio Conversation" mode on or off. When enabled, Speech-to-Text automatically sends recognized prompts to the model, and responses are automatically read aloud via Text-to-Speech.
            *   **Biometric Button**: Toggles fingerprint biometric security on or off. If on, the app will not open without a successful fingerprint reading by the system. 
            *   **Presets Button**: Opens the Presets screen. These enable the user to have pre-selected settings applied to the app: model, system message, reasoning on/off, streaming on/off, and conversation mode on/off, with one tap. These are also exposed as a share target, "Presets", when sharing text to the app; thus making functions like summarization, spelling correction, audio reply, etc with different models/combos fast and easy. Note: because the user can change the model and system message outside the preset, if they are to do that, it will invalidate the preset(it won't apply) and will require the user to edit/save the preset again with the current desired model/system message for it to work.
            *   **Fonts Button**: Opens the fonts dialog where you can choose one of many different fonts for the main chat screen.
            ---

            ## 📂 App Screens

            ### Model Selection
            *   **Access**: Tap the **model name** at the top of the chat screen.
            *   **"Select Model" Screen**: Shows your list of available models. The default is **Maverick Llama**. Tap a model in the list to change the model for the current chat.
            *   **Vision**: If the model supports image upload the icon to the left of the name will show an image icon.
            *   **Image Generation**: If the model supports it the icon to the left of the name will show a palette icon.
            *   **Add a Model**: Use the floating action button to add a custom model.
            *   **Manage**: Tap the edit button on a model to **Edit** or **Delete** it.
            *   **Search**: Tap to search through the models.
            *   **Discover Models**: Tap the **cloud icon** to go to the **"OpenRouter Models"** screen.

            ### "OpenRouter Models" Screen
            *   **Sort**: Toggle between **Alphabetical** and **Newest**.
            *   **Search**: Tap to search through the models.
            *   **Add to Your List**: Tap any model to add it to your "Select Model" screen.
            *   **View Model Info**: Tap the icon on the right of the model to open its info page on the OpenRouter website.
            *   **Refresh**: Tap the **refresh icon** to get the latest list of models from OpenRouter.
            
            ### "LAN Models" Screen (Ollama, LM Studio, or llama.cpp models)
            *   **Add to Your List**: Tap any model to add it to your "Select Model" screen.
            *   **Refresh**: The list will refresh when opened.
            

            ### "Saved Chats" Screen
            *   **Import/Export**: Use the menu bar icons to manage your saved chats.
            *   **Manage**: Tap the edit button on a chat to **Rename** or **Delete** it.
            *   **Search**: Tap to search through the saved chats.

            ### "System Message" Screen
            *   **Defaults**: Comes with "Default", "Spelling and Grammar corrector", and "Summarizer".
            *   **Add New**: Use the floating action button to create your own.
            *   **Manage**: Tap the edit button on a message to **Edit** or **Delete** it.
            *   **Import/Export**: Use the menu bar icons to manage your System Messages.

            ---
            
            ## Image Generation Models
            
             *   Image generation models are denoted by a palette icon at the left of the model list item. The palette icon appears at the top of the main chat when you have a image generation model selected.
             *   Images are downloaded to your Downloads folder.
             *   Tap on the generated image in the chat to open it in your default image viewer.
             *   The generated images are not stored when you save the chat.
             *   The generated images are not passed back in the chat. If you want the model to edit one you need to attach it manually.
            
            ---
            
            ## Advanced Reasoning Settings
            
             *   You can enable it and make additional reasoing settings here. Long-press on reasoning button to make its screen appear.
             *   Enable reasoning traces in response, max reasoning tokens, and effort can be set here.
             *   Reasoning traces are not passed in the chat(they display only). Nor are they included in the whole chat pdf(but they will be in a singular response pdf). They are not saved when you save a chat.
             *   When enabled the Reasoning button will have a bright orange outline.
             
            ---

            ## ℹ️ Other Info

            *   An internet connection is required.
            *   Review OpenRouter's and its model providers' pricing, privacy, and logging policies on their websites.
            *   The app is licensed under the Apache License 2.0.
            *   This app is not affiliated with OpenRouter.ai.
            *   Pasting in to the prompt box strips any unnecessary rich text formatting automatically.
            *   Costs are incurred with using OpenRouter models. Familiarize yourself with model costs at [https://openrouter.ai/models/](https://openrouter.ai/models/)
            *   Markdown content is well-supported in AI response chat messages.
            *   oxproxion app does not have trackers, analytics, nor ads.
            *   If you want to save your saved chats and/or System Messages, be sure to export them before you uninstall the app, otherwise they will be gone for good.
            *   Imports are programmed to not overwrite: System Messages skip duplicates by title, while Saved Chats add new entries even when titles match, leaving all existing items intact.
            *   This open-source app is provided 'as-is' without any warranty, express or implied. Use at your own discretion.
            *   OpenRouter allows Presets which allow you to manage your LLM configurations—models, provider routing, and other features. You can use Presets in oxproxion by just manually adding them in your model list. [https://openrouter.ai/docs/features/presets/](https://openrouter.ai/docs/features/presets/)
            *   The app is a target for multiple text shares: "Prompt"(set the prompt to the shared text), "System Message Chooser"(set the prompt to the shared text and sets the System Message as chosen in the popup), "Auto Send"(Auto sends the prompt to current model with current settings), and "Presets"(Allows the user to apply a chosen preset and options for the shared text.)
            *   Ollama, LM Studio, and llama.cpp endpoint default is plain http, therefore the chat is passed via unencrypted text on your LAN. Unless you have an https endpoint for them.
            *   Ollama, LM Studio, and llama.cpp function is nascent and might not support all capabilities at this time. Furthermore you must set them to be served on your LAN.
            *   Notifications and Connectivity Service: The "Connectivity Service Channel" runs and you can hide that; go into your app settings in the system and go to notifications and you can toggle that notification off. ("Connectivity Service Channel") And the only notifications you will receive(if in the app you have the notification bell active) is if the app is back-grounded and there's either an error or you receive the response from the model. Notifications won't show if app is in the foreground. Notifications are auto-dismissed when returning to the app. Closing the app by swiping it away in Recents will shut down the service properly.
            *   If you make a preset titled "Digital Assistant"(case-insensitive), and have oxproxion as your system digital assistant in your Android settings(Settings->Apps->Default apps), this preset will be applied for when you use it as the system digital assistant.
            ## What You Can Do With oxproxion

            oxproxion puts the power of multiple AI models at your fingertips. With custom system messages, you can tailor your experience for virtually any task. Here are some popular use cases:

            ### Language Translation
            Seamlessly translate conversations by setting a system message like:  
            "You are a professional translator. Translate all responses to [target language] while maintaining the original meaning and tone."

            ### Content Summarization
            Get concise overviews of lengthy articles with:  
            "You are a professional summarizer. Provide a clear, 3-paragraph summary of the following text, highlighting key points, evidence, and conclusions."

            ### Image Analysis & Learning
            Upload images and engage with them using vision models to get detailed descriptions, identify objects, explain concepts in diagrams, or learn from visual content.

            ### Code Development & Debugging
            Receive expert assistance with programming tasks across multiple languages. Ask for explanations, bug fixes, or new feature implementations with context-aware help.

            ### Personalized Companionship
            Create meaningful interactions with a virtual companion using:  
            "You are my supportive companion who shows genuine interest in my life, and provides thoughtful, caring responses."

            ### Educational Tutoring
            Get personalized learning experiences across subjects with:  
            "You are a patient tutor who explains complex concepts in simple terms with relevant examples, checks for understanding, and adapts to my learning pace."

            ### Creative Content Generation
            Craft stories, poems, scripts, or marketing copy with specific styles, tones, and requirements by setting appropriate system instructions.

            ### Research & Analysis
            Conduct deeper investigations on topics with assistance in finding information, analyzing data, and synthesizing findings into coherent insights.

            ### Language Practice
            Improve your language skills through conversation with a patient partner who corrects mistakes gently and explains grammar rules contextually. "You are a friendly language tutor who helps me practice [target language]. Correct my mistakes gently, explain grammar rules in simple terms, and respond using vocabulary appropriate for an intermediate learner. Keep the conversation natural and engaging."

            ### Professional Development
            Get tailored career advice, resume feedback, interview preparation, and industry-specific guidance to advance your professional journey. "You are a career coach who provides actionable advice on resume improvement, interview preparation, and professional growth. Be specific, constructive, and tailored to my industry and experience level."
            
            ### Share Chats
            Use the PDF button and share you chats with family, friends and co-workers.
        """.trimIndent()

        markwon.setMarkdown(helpContentTextView, markdownContent)
        helpContentTextView.movementMethod = LinkMovementMethod.getInstance()
    }
}