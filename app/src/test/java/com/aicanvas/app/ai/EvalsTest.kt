package com.aicanvas.app.ai

import com.aicanvas.app.data.UiSchema
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Local evals: a fixed set of requests with reference answers (test/resources/evals/cases.json).
 * Good answers must pass the UI contract, use only the 12 building blocks and contain the expected blocks;
 * bad answers must be caught. Runs offline with the unit tests, so it never costs API credits.
 */
class EvalsTest {
    private val cases = JSONObject(javaClass.classLoader!!.getResource("evals/cases.json")!!.readText())

    private fun types(n: Any?, out: MutableSet<String>) {
        when (n) {
            is JSONObject -> { n.optString("type").takeIf { it.isNotEmpty() }?.let(out::add); n.keys().forEach { types(n.get(it), out) } }
            is JSONArray -> for (i in 0 until n.length()) types(n.get(i), out)
        }
    }

    @Test fun goodAnswersPass() {
        val good = cases.getJSONArray("good")
        val failures = mutableListOf<String>()
        for (i in 0 until good.length()) {
            val c = good.getJSONObject(i)
            val answer = c.getJSONObject("answer")
            val problems = UiContract.problems(answer)
            val used = mutableSetOf<String>().also { types(answer.optJSONArray("create"), it) }
            val expected = (0 until c.getJSONArray("expect").length()).map { c.getJSONArray("expect").getString(it) }
            val why = when {
                problems.isNotEmpty() -> problems.joinToString()
                !used.all { it in UiSchema.PRIMITIVES } -> "uses old blocks ${used - UiSchema.PRIMITIVES}"
                !used.containsAll(expected) -> "missing ${expected - used}"
                else -> null
            }
            if (why != null) failures += "${c.getString("prompt")}: $why"
        }
        println("Evals: ${good.length() - failures.size}/${good.length()} good answers pass")
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun badAnswersAreCaught() {
        val bad = cases.getJSONArray("bad")
        val missed = (0 until bad.length()).map { bad.getJSONObject(it) }
            .filter { UiContract.problems(it.getJSONObject("answer")).isEmpty() }.map { it.getString("why") }
        println("Evals: ${bad.length() - missed.size}/${bad.length()} bad answers caught")
        assertTrue("Not caught: $missed", missed.isEmpty())
    }
}
