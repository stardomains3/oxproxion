package io.github.stardomains3.oxproxion

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.footnotes.FootnotesExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension
import org.commonmark.ext.ins.InsExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

object MarkdownRenderer {
    private val extensions = listOf(
        TablesExtension.create(),
        StrikethroughExtension.create(),
        TaskListItemsExtension.create(),
        AutolinkExtension.create(),
        HeadingAnchorExtension.create(),
        FootnotesExtension.create(),
        InsExtension.create()
    )
    private val parser: Parser = Parser.builder()
        .extensions(extensions)
        .build()
    private val renderer: HtmlRenderer = HtmlRenderer.builder()
        .extensions(extensions)
        .build()
    fun toHtmlExp(markdown: String): String {
        val document: Node = parser.parse(markdown)
        var htmlBody = renderer.render(document)

        // Wrap tables for horizontal scrolling
        htmlBody = htmlBody.replace(
            Regex("<table[^>]*>.*?</table>", RegexOption.DOT_MATCHES_ALL),
            "<div class=\"table-wrapper\">\$0</div>"
        )

        // Wrap code blocks with copy button
        htmlBody = htmlBody.replace(
            Regex("<pre[^>]*>.*?</pre>", RegexOption.DOT_MATCHES_ALL),
            "<div class=\"code-block\"><button class=\"copy-btn\" onclick=\"copyCode(this)\">Copy</button>\$0</div>"
        )

        val css = """
    <style>
        * { box-sizing: border-box; }
        body { 
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
            font-size: 16px; 
            line-height: 1.6; 
            color: #24292e; 
            background: #ffffff; 
            margin: 0; 
            padding: 24px; 
            max-width: 900px;
            margin-left: auto;
            margin-right: auto;
        }
        a { color: #0366d6; text-decoration: none; word-wrap: break-word; }
        a:hover { text-decoration: underline; }
        h1, h2, h3, h4, h5, h6 { 
            color: #24292e; 
            margin-top: 24px; 
            margin-bottom: 16px; 
            font-weight: 600; 
            line-height: 1.25;
        }
        h1 { font-size: 2em; border-bottom: 1px solid #eaecef; padding-bottom: 8px; }
        h2 { font-size: 1.5em; border-bottom: 1px solid #eaecef; padding-bottom: 8px; }
        p { margin-top: 0; margin-bottom: 16px; }
        strong { font-weight: 600; }
        del { text-decoration: line-through; color #6a737d; }
        
        .table-wrapper {
            width: 100%; 
            overflow-x: auto; 
            margin: 16px 0; 
            border: 1px solid #e1e4e8;
            border-radius: 6px;
        }
        table { 
            width: 100%; 
            border-collapse: collapse; 
        }
        th, td { 
            border: 1px solid #e1e4e8; 
            padding: 12px; 
            text-align: left; 
        }
        th { background: #f6f8fa; font-weight: 600; }
        tr:nth-child(even) { background: #f6f8fa; }

        .code-block {
            position: relative;
            margin: 16px 0; 
            border-radius: 6px; 
            background: #f6f8fa; 
            border: 1px solid #e1e4e8;
        }
        pre { 
            margin: 0; 
            padding: 16px; 
            padding-top: 44px; 
            font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
            font-size: 14px; 
            line-height: 1.45;
            overflow-x: auto;
        }
        code {
            font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
            font-size: 85%;
            background: rgba(27, 31, 35, 0.05);
            padding: 0.2em 0.4em;
            border-radius: 3px;
        }
        pre code {
            background: transparent;
            padding: 0;
            font-size: 100%;
        }
        
        .copy-btn {
            position: absolute;
            top: 8px;
            right: 8px;
            background: #ffffff;
            border: 1px solid #d1d5e8;
            border-radius: 6px;
            padding: 6px 12px;
            font-size: 12px;
            font-family: inherit;
            color: #24292e;
            cursor: pointer;
            transition: all 0.2s;
            z-index: 10;
        }
        .copy-btn:hover {
            background: #f3f4f6;
            border-color: #b1b5c8;
        }
        .copy-btn.copied {
            background: #28a745;
            color: white;
            border-color: #28a745;
        }

        ul, ol { padding-left: 24px; margin-bottom: 16px; }
        li { margin: 4px 0; }
        li:has(input[type="checkbox"]) { list-style-type: none; margin-left: -20px; }
        input[type="checkbox"] { margin-right: 8px; vertical-align: middle; }
        
        blockquote { 
            border-left: 4px solid #dfe2e5; 
            padding-left: 16px; 
            color: #6a737d; 
            margin: 0 0 16px 0; 
        }
        
        img { max-width: 100%; height: auto; border-radius: 6px; }
        hr { height: 4px; padding: 0; margin: 24px 0; background-color: #e1e4e8; border: 0; }
    </style>
    """.trimIndent()

        val js = """
    <script>
        async function copyCode(btn) {
            const pre = btn.parentElement.querySelector('pre');
            const text = pre.innerText;
            
            try {
                await navigator.clipboard.writeText(text);
                showFeedback(btn);
            } catch (err) {
                // Fallback for older browsers or http contexts
                const textarea = document.createElement('textarea');
                textarea.value = text;
                textarea.style.position = 'fixed';
                textarea.style.opacity = '0';
                document.body.appendChild(textarea);
                textarea.select();
                try {
                    document.execCommand('copy');
                    showFeedback(btn);
                } catch (e) {
                    btn.textContent = 'Failed';
                    setTimeout(() => btn.textContent = 'Copy', 2000);
                }
                document.body.removeChild(textarea);
            }
        }
        
        function showFeedback(btn) {
            btn.textContent = 'Copied!';
            btn.classList.add('copied');
            setTimeout(() => {
                btn.textContent = 'Copy';
                btn.classList.remove('copied');
            }, 2000);
        }
    </script>
    """.trimIndent()

        return """
    <!DOCTYPE html>
    <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            $css
        </head>
        <body>
            $htmlBody
            $js
        </body>
    </html>
    """.trimIndent()
    }
    fun toHtml(markdown: String, fontName: String = "system_default"): String {
        val document: Node = parser.parse(markdown)
        var htmlBody = renderer.render(document)

        // Wrap Tables
        htmlBody = htmlBody.replace(
            Regex("<table[^>]*>.*?</table>", RegexOption.DOT_MATCHES_ALL),
            "<div class=\"table-scroll-wrapper\">\$0</div>"
        )

        // Wrap Code Blocks
        htmlBody = htmlBody.replace(
            Regex("<pre[^>]*>.*?</pre>", RegexOption.DOT_MATCHES_ALL),
            "<div class=\"code-wrapper\">\$0</div>"
        )

        // --- DYNAMIC FONT CSS ---
        val fontCss = if (fontName != "system_default") {
            """
            @font-face {
                font-family: 'UserSelectedFont';
                src: url('file:///android_res/font/$fontName.ttf'); 
            }
            body { 
                font-family: 'UserSelectedFont', -apple-system, sans-serif !important; 
            }
            """
        } else {
            ""
        }

        val css = """
        <style>
            /* --- SCREEN STYLES (Default) --- */
            * { box-sizing: border-box; }
            
            $fontCss
            
            body { 
                font-size: 16px; line-height: 1.6; color: #cecece; 
                background: #000000; margin: 0; padding: 16px; overflow-x: hidden;
            }
            a { 
    color: #4fc3f7; 
    word-wrap: break-word; 
    overflow-wrap: break-word; 
    word-break: break-word;
    white-space: normal; 
    display: inline-block;
    max-width: 100%;
}
            h1,h2,h3 { color: #dbdbdb; border-bottom: 1px solid #333; margin-top: 24px; }
            strong { color: #dbdbdb; }
            del { color: #d32f2f; text-decoration: line-through; }
            
            .table-scroll-wrapper {
                width: 100%; overflow-x: auto; margin: 16px 0; background: #111; border-radius: 8px;
                -webkit-overflow-scrolling: touch; 
            }
            table { 
                width: max-content; min-width: 100%; max-width: 100vw; 
                border-collapse: collapse; margin: 0;
            }
            th, td { 
                border: 1px solid #444; padding: 12px 16px; text-align: left; color: #cecece;
                white-space: normal; vertical-align: top;
            }
            th { background: #222; color: #FFFFFF; font-weight: bold; }
            tr:nth-child(even) { background: #1a1a1a; }

            .code-wrapper {
                position: relative; margin: 12px 0; border-radius: 8px; overflow: hidden;
                background: #22272e; border: 1px solid #444;
            }
            pre { margin: 0; }
            pre code.hljs {
                padding: 40px 16px 16px 16px !important;
                font-family: monospace; font-size: 14px; overflow-x: auto;
            }
            .copy-btn {
                position: absolute; top: 8px; right: 8px; 
                background: #333; color: #fff; border: 1px solid #555; 
                padding: 6px 12px; border-radius: 4px; font-size: 12px; font-weight: bold;
                cursor: pointer; z-index: 100;
            }

            ul, ol { padding-left: 24px; margin: 12px 0; }
            li:has(input[type="checkbox"]) { list-style-type: none; margin-left: -4px; }
            input[type="checkbox"] { margin-right: 10px; transform: scale(1.2); cursor: default; vertical-align: -2px; }
            blockquote { border-left: 4px solid #4fc3f7; padding-left: 16px; color: #ccc; margin: 16px 0; background: #111; }
            a { color: #4fc3f7; }
            
            /* ADDED: Ensure images don't overflow the screen */
            img { max-width: 100%; height: auto; border-radius: 4px; }

            @media print {
                body {
                    background-color: #FFFFFF !important;
                    color: #000000 !important;
                    margin: 0 !important;
                    padding: 20px !important; 
                    overflow: visible !important;
                }
                .table-scroll-wrapper {
                    overflow: visible !important; display: block !important;
                    background: transparent !important; margin: 10px 0 !important;
                }
                table {
                    width: 100% !important; max-width: 100% !important; min-width: 0 !important;
                    table-layout: auto !important; border: 1px solid #000 !important;
                }
                th, td {
                    color: #000 !important; border: 1px solid #000 !important;
                    word-wrap: break-word !important; overflow-wrap: break-word !important;
                    white-space: normal !important; font-size: 10pt !important;
                }
                th { background-color: #eee !important; }
                tr:nth-child(even) { background-color: transparent !important; }
                .code-wrapper { background: #f5f5f5 !important; border: 1px solid #ccc !important; }
                pre code.hljs {
                    white-space: pre-wrap !important; word-break: break-all !important;
                    color: #000 !important; background: #f5f5f5 !important;
                }
                .copy-btn { display: none !important; }
                a { color: #000 !important; text-decoration: underline !important; }
                h1, h2, h3, strong { color: #000 !important; border-color: #000 !important; }
                blockquote { border-color: #000 !important; color: #444 !important; background: transparent !important; }
               
                .code-wrapper {
        overflow: visible !important;
        position: static !important; /* Avoid absolute positioning issues */
        margin: 12px 0 !important;
        width: 100% !important;
        page-break-inside: avoid !important;
    }
    
    pre {
        overflow: visible !important;
        margin: 0 !important;
        padding: 12px 8px !important; /* Adjust for print */
        white-space: pre-wrap !important;
        word-wrap: break-word !important;
        word-break: break-word !important; /* Less aggressive than break-all */
        hyphens: auto !important;
        box-sizing: border-box !important;
        width: 100% !important;
        max-width: 100% !important;
    }
    
    pre code, pre code.hljs {
        white-space: pre-wrap !important;
        word-break: break-word !important;
        overflow-wrap: break-word !important;
        font-size: 10pt !important; /* Smaller for print */
        display: block !important;
        width: 100% !important;
    }
    .copy-btn { display: none !important; }
            }
        </style>
        """.trimIndent()

        val js = """
        <script src="highlight.min.js"></script>
        <script>
            hljs.highlightAll();
            (function() {
                var wrappers = document.querySelectorAll('.code-wrapper');
                wrappers.forEach(function(wrapper) {
                    var btn = document.createElement('button');
                    btn.className = 'copy-btn';
                    btn.textContent = '📋 Copy';
                    btn.addEventListener('click', function(e) {
                        e.stopPropagation();
                        var pre = wrapper.querySelector('pre');
                        var textToCopy = pre.textContent; 
                        if (window.Android && window.Android.copyToClipboard) {
                            window.Android.copyToClipboard(textToCopy);
                            showFeedback(btn);
                        } else {
                            navigator.clipboard.writeText(textToCopy).then(function() { showFeedback(btn); });
                        }
                    });
                    wrapper.appendChild(btn);
                });
                function showFeedback(btn) {
                    btn.textContent = '✅ Copied!';
                    btn.style.background = '#2a5a2a';
                    setTimeout(function() { btn.textContent = '📋 Copy'; btn.style.background = '#333'; }, 2000);
                }
            })();
        </script>
        """.trimIndent()

        return """
        <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0">
                <link rel="stylesheet" href="github-dark-dimmed.min.css">
                $css
            </head>
            <body>
                $htmlBody
                $js
            </body>
        </html>
        """.trimIndent()
    }
}

