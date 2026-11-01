package com.aicanvas.app.ai

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiContractTest {
    private fun p(json: String) = UiContract.problems(JSONObject(json))

    @Test fun validAnswerHasNoProblems() {
        assertEquals(emptyList<String>(), p("""{"reply":"Hi","create":[{"size":"half","ui":{"type":"column","children":[{"type":"text","text":"x"}]}}]}"""))
    }
    @Test fun textOnlyReplyIsFine() = assertEquals(emptyList<String>(), p("""{"reply":"Hello"}"""))
    @Test fun nullIsAProblem() = assertTrue(UiContract.problems(null).isNotEmpty())
    @Test fun unknownTypeIsReported() = assertTrue(p("""{"create":[{"ui":{"type":"carousel"}}]}""").any { "carousel" in it })
    @Test fun containerNeedsChildren() = assertTrue(p("""{"create":[{"ui":{"type":"row"}}]}""").any { "children" in it })
    @Test fun leafCannotHaveChildren() = assertTrue(p("""{"create":[{"ui":{"type":"text","children":[]}}]}""").any { "cannot" in it })
    @Test fun updateNeedsId() = assertTrue(p("""{"update":[{"tone":"blue"}]}""").any { "id" in it })
    @Test fun badSize() = assertTrue(p("""{"create":[{"size":"huge","ui":{"type":"text"}}]}""").any { "size" in it })
    @Test fun progressRange() = assertTrue(p("""{"create":[{"ui":{"type":"progress","value":4}}]}""").any { "between" in it })
    @Test fun rememberMustBeText() = assertTrue(p("""{"remember":[{"a":1}]}""").isNotEmpty())
    @Test fun repairPromptListsProblems() = assertTrue(Ai.repairPrompt(listOf("bad")).contains("- bad"))
}
