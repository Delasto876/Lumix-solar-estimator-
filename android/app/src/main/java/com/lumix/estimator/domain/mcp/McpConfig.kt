package com.lumix.estimator.domain.mcp

/**
 * A85 (Phase 24 — "If MCP architecture is useful, build it as an optional local/development
 * interface. Do not require a paid MCP service... Initially expose read-only information only"):
 * unlike [com.lumix.estimator.domain.monitoring.MonitoringConfig]/[com.lumix.estimator.domain.ai
 * .AiConfig], this needs no credentials — an MCP tool registry is just a set of read-only query
 * functions over this app's own already-computed data (see [McpToolRegistry]), not a call to any
 * paid external service. [enabled] exists purely as an explicit off switch, defaulting to false so
 * nothing exposes app data until a developer deliberately turns this on for local/dev use — there
 * is no real MCP transport/host wired up in this Android app to expose it to yet.
 */
object McpConfig {
    @Volatile var enabled: Boolean = false
        private set

    fun setEnabled(value: Boolean) {
        enabled = value
    }
}
