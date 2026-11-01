package com.aicanvas.app.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenRouterTest {
    @Test fun keepsOnlyFreeTextModels() {
        val body = """{"data":[
          {"id":"b/free","name":"Beta (free)","pricing":{"prompt":"0","completion":"0"},"architecture":{"output_modalities":["text"]}},
          {"id":"paid","name":"Paid","pricing":{"prompt":"0.000001","completion":"0.000002"}},
          {"id":"img","name":"Image only","pricing":{"prompt":"0","completion":"0"},"architecture":{"output_modalities":["image"]}},
          {"id":"a/free","name":"alpha (free)","pricing":{"prompt":"0","completion":"0"}},
          {"id":"a/free","name":"dup","pricing":{"prompt":"0","completion":"0"}}]}"""
        assertEquals(listOf("a/free", "b/free"), OpenRouter.freeModels(body).map { it.id })
    }
    @Test fun emptyWhenNoData() = assertEquals(0, OpenRouter.freeModels("{}").size)
}
