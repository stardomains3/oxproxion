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
                description = "Creates and saves a text-based file (TXT/HTML/JSON/Markdown etc.) to the Download/oxproxion folder",
                isEnabled = "make_file" in enabledSet
            ),

            ToolItem(
                name = "delete_files",
                displayName = "Delete File(s)",
                description = "Deletes existing file(s) from the Download/oxproxion workspace.",
                isEnabled = "delete_files" in enabledSet
            ),
            ToolItem(
                name = "get_location",
                displayName = "Get Location",
                description = "Gets current precise location with Plus Code, coordinates, map links, and accuracy",
                isEnabled = "get_location" in enabledSet
            ),
            ToolItem(
                name = "brave_search",
                displayName = "Brave Search",
                description = "Search the web or filter for news with Brave Search API. Supports freshness filters and SafeSearch.",
                isEnabled = "brave_search" in enabledSet
            ),
            ToolItem(
                name = "find_nearby_places",
                displayName = "Brave Place Search",
                description = "Searches for businesses, landmarks, and POIs via Brave Place Search — by location name or coordinates",
                isEnabled = "find_nearby_places" in enabledSet
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
                description = "Create a system alarm for a specific time with optional title. Specify AM/PM." ,
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
                description = "List files and folders in the Download/oxproxion workspace, including subfolders",
                isEnabled = "list_oxproxion_files" in enabledSet
            ),
            ToolItem(
                name = "read_oxproxion_file",
                displayName = "Read oxproxion File",
                description = "Read a text-based file from the Download/oxproxion workspace.",
                isEnabled = "read_oxproxion_file" in enabledSet
            )
            ,
            ToolItem(
                name = "create_folder",
                displayName = "Create Folder",
                description = "Creates a new subfolder in the Download/OpenChat workspace",
                isEnabled = "create_folder" in enabledSet
            ),
            ToolItem(
                name = "open_file",
                displayName = "Open File",
                description = "Opens an existing file from the Download/oxproxion folder using the system's default app. oxproxion has to be in the foreground for this tool to work.",
                isEnabled = "open_file" in enabledSet
            ),
            ToolItem(
                name = "edit_file",
                displayName = "Edit File",
                description = "Overwrites an existing file with new content. Use to update or modify files while keeping the same name.",
                isEnabled = "edit_file" in enabledSet
            ),

            ToolItem(
                name = "copy_file",
                displayName = "Copy/Rename File",
                description = "Copies a file to a new location or renames it. Automatically adds a timestamp if the destination exists to prevent overwriting.",
                isEnabled = "copy_file" in enabledSet
            ),
                    ToolItem(
                    name = "process_plus_code",
            displayName = "Plus Code Converter",
            description = "Convert between geographic coordinates (Lat/Long) and Plus Codes",
            isEnabled = "process_plus_code" in enabledSet
        ),
            ToolItem(
                name = "start_navigation",
                displayName = "Start Navigation",
                description = "Launches Google Maps turn-by-turn navigation. Options include driving, walking, bicycling, or transit modes, plus route avoidance (e.g., no tolls or highways).",
                isEnabled = "start_navigation" in enabledSet
            ),
            ToolItem(
                name = "get_current_datetime",
                displayName = "Get Date & Time",
                description = "Returns current date and time with day, date, time (including seconds), timezone, and UTC offset information",
                isEnabled = "get_current_datetime" in enabledSet
            ),
            ToolItem(
                name = "open_app",
                displayName = "App Launcher / Settings",
                description = "Opens apps by name/package OR opens specific Android settings pages.",
                isEnabled = "open_app" in enabledSet
            ),
            ToolItem(
                name = "search_list_apps",
                displayName = "Search/List Installed Apps",
                description = "Searches for installed apps by name or package to find the correct ID for launching.",
                isEnabled = "search_list_apps" in enabledSet
            ),
            ToolItem(
                name = "set_sound_mode",
                displayName = "Sound Mode",
                description = "Gets or sets the device ringer mode (Normal, Vibrate, Silent).",
                isEnabled = "set_sound_mode" in enabledSet
            )

            // Add more tools here as your app grows
        )
    }
}
