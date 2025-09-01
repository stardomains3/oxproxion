package io.github.stardomains3.oxproxion

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
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

    private lateinit var markwon: Markwon

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val helpContentTextView = view.findViewById<TextView>(R.id.helpContentTextView)

        val prism4j = Prism4j(ExampleGrammarLocator())
        val theme = Prism4jThemeDarkula.create()
        val syntaxHighlightPlugin = SyntaxHighlightPlugin.create(prism4j, theme)

        markwon = Markwon.builder(requireContext())
            .usePlugin(HtmlPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(syntaxHighlightPlugin)
            .usePlugin(TablePlugin.create(requireContext()))
            .usePlugin(TaskListPlugin.create(requireContext()))
            .usePlugin(CoilImagesPlugin.create(requireContext()))
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder
                        .codeTextColor(Color.LTGRAY)
                        .codeBackgroundColor(Color.DKGRAY)
                        .codeBlockBackgroundColor(Color.DKGRAY)
                        .blockQuoteColor(Color.BLACK)
                        .isLinkUnderlined(true)
                }
            })
            .build()

        val markdownContent = """
            # oxproxion Help Guide

            Welcome! This guide will help you understand how to use the **oxproxion** app.

            **oxproxion** is an open-source Android app for chatting with OpenRouter LLMs, supporting both text and image inputs for compatible models.

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
            *   **Copy AI Response**: Tap the **robot icon** to copy the AI's message.
            *   **Copy User Message**: Tap the **user icon** to copy your message.
            *   **Share AI Response**: Tap the **share icon** to send the AI's text to other apps.
            *   **Create PDF of Response**: **Long-press** the **robot icon** to save just that response as a PDF in your device's Downloads folder.

            ### Sending Prompts
            *   **Text Box**: Enter your prompt.
            *   **Send Button**: Send your prompt to the LLM. **Long-press** it to clear the text box.
            *   **Stop Button**: During an api call, tap the Stop Button to end the api call.
            *   **Image Button**: Appears for vision models. Click to attach a single image up to 12MB in size.
            
            ### Other Buttons
            *   **Clear Chat**(button on bottom left): Starts a new chat with the current model. Long-press to start a new chat without a warning alert.
            *   **System Message Button**: Opens your library of system messages. If you currently are on a non-default System Message you can long-press and it will auto switch to your default.
            *   **Saved Chats**: Opens a list of your saved conversations.
            *   **Save Chat**: Saves the chat. You can name it yourself or let the AI (qwen/qwen3-30b-a3b-instruct-2507 is used) generate a title. Only text chats can be saved.
            
            ---

            ##  Menu

            Tap the **menu button** (the one on the lower left with nine dots) to show or hide the main menu. The menu will also hide if you tap outside of it.
            If your prompt is filled with spelling/grammar errors you can long-press the menu button to send the prompt to openai/gpt-oss-20b model to fix it and it will automatically correct the prompt.

            ### Contextual Buttons (Enabled during a chat)
            *   **PDF Button**: Creates a PDF of the entire chat in your downloads folder.
            *   **Copy Chat**: Copies the full conversation to your clipboard.

            ### Standard Buttons (Always in the menu)
            *   **Stream Button**: Toggles streaming responses on or off.
            *   **API Key Button**: Opens a dialog to enter your OpenRouter API key. **Long-press** to check your remaining credits.
            *   **Notification Button**: Activates a foreground service to prevent the app from closing in the background and enables you to customize the notification.

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

            ## ℹ️ Other Info

            *   An internet connection is required.
            *   Review OpenRouter's and its model providers' pricing, privacy, and logging policies on their websites.
            *   The app is licensed under the Apache License 2.0.
            *   This app is not affiliated with OpenRouter.ai.
            *   Pasting in to the prompt box strips any unnecessary rich text formatting automatically.
            *   Costs are incurred with using OpenRouter models. Familiarize yourself with model costs at [https://openrouter.ai/models/](https://openrouter.ai/models/)
            *   Markdown content is well-supported in AI response chat messages.
            *   If you want to save your saved chats and/or System Messages, be sure to export them before you uninstall the app, otherwise they will be gone for good.
            *   Imports are programmed to not overwrite: System Messages skip duplicates by title, while Saved Chats add new entries even when titles match, leaving all existing items intact.
            *   This open-source app is provided 'as-is' without any warranty, express or implied. Use at your own discretion.
            *   OpenRouter allows Presets which allow you to manage your LLM configurations—models, provider routing, and other features. You can use Presets in oxproxion by just manually adding them in your model list. [https://openrouter.ai/docs/features/presets/](https://openrouter.ai/docs/features/presets/)
            
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
    }
}