package io.github.stardomains3.oxproxion

data class ToolItem(
    val name: String,               // e.g. "make_file"
    val displayName: String,        // Human‑readable e.g. "Create File"
    val description: String,        // Short description shown under the name
    val isEnabled: Boolean // Current state from prefs
) {
    companion object {
        fun getAllToolItems(enabledSet: Set<String>): List<ToolItem> = listOf(
            ToolItem(
                name = "make_file",
                displayName = "Create File",
                description = "Creates and saves a text-based file (TXT/HTML/JSON/Markdown etc.) to Downloads",
                isEnabled = "make_file" in enabledSet
            ),
            ToolItem(
                name = "set_timer",
                displayName = "Set Timer",
                description = "Launch Android timer with optional title",
                isEnabled = "set_timer" in enabledSet
            ),
            ToolItem(
                name = "set_alarm",
                displayName = "Set Alarm",
                description = "Create a system alarm for a specific time with optional title",
                isEnabled = "set_alarm" in enabledSet
            ),

            ToolItem(
                name = "add_calendar_event",
                displayName = "Add Calendar Event",
                description = "Adds an event to the user's calendar. Provide a title and start date/time. Can also provide if all-day and a location.",
                isEnabled = "add_calendar_event" in enabledSet
            ),


            ToolItem(
                name = "list_oxproxion_files",
                displayName = "List oxproxion Files",
                description = "List all files in the Download/oxproxion folder",
                isEnabled = "list_oxproxion_files" in enabledSet
            ),
            ToolItem(
                name = "read_oxproxion_file",
                displayName = "Read oxproxion File",
                description = "Read a single text file from Download/oxproxion folder",
                isEnabled = "read_oxproxion_file" in enabledSet
            )
            // Add more tools here as your app grows
        )
    }
}
