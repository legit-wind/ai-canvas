package com.aicanvas.app.ai

/** One observable step of a run (never the model's private reasoning). */
data class AiEvent(val label: String, val detail: String)

/** One request to the AI, kept for this session only so the Activity screen can show how the pipeline went. */
data class AiRun(
    val id: Long,
    val prompt: String,
    val model: String,
    val contextSize: Int = 0,
    val events: List<AiEvent> = emptyList(),
    val elapsedMs: Long? = null,
    val outcome: String = "Running",
    val raw: String = "",
)
