package com.riisync.app

/**
 * Feature flags for local development. Toggle from code to enable/disable
 * development-only helpers. When set to false, the debugger UI is removed.
 */
object BuildFlags {
    // Set to true during development to expose the internal debugger in Settings.
    // Toggle this to false in order to completely remove the debugger button and related UI.
    const val SHOW_INTERNAL_DEBUGGER = false
}
