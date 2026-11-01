package com.aicanvas.app.data

import com.aicanvas.app.ui.Tones
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartersTest {
    @Test fun distinctUseCases() {
        assertTrue(Starters.ALL.size >= 18)
        assertEquals(Starters.ALL.size, Starters.ALL.map { it.title }.toSet().size)
        assertEquals(Starters.ALL.size, Starters.ALL.map { it.key }.toSet().size)
        // Each one says what capability it shows, and no two say the same.
        assertEquals(Starters.ALL.size, Starters.ALL.map { it.shows }.filter { it.isNotBlank() }.toSet().size)
    }
    @Test fun everyToneIsOneOfTheSix() = assertTrue(Starters.ALL.all { it.tone in Tones.ALL })
    @Test fun needsAreKnown() = assertTrue(Starters.ALL.all { it.needs in setOf("", "photo", "calendar") })
    @Test fun skipsWhatIsOnScreen() = assertTrue(Starters.suggest("""{"text":"🔥 Habit streaks"}""", 9, 30).none { it.key == "streak" })
    @Test fun respectsMax() = assertEquals(6, Starters.suggest("", 3).size)

    /** Every starter has at least one eval sample, and its finished card contains the starter's key. */
    @Test fun everyStarterHasAnEval() {
        val good = JSONObject(javaClass.classLoader!!.getResource("evals/cases.json")!!.readText()).getJSONArray("good")
        val cases = (0 until good.length()).map { good.getJSONObject(it) }
        Starters.ALL.forEach { s ->
            val mine = cases.filter { it.optString("starter") == s.key }
            assertTrue("no eval for ${s.title}", mine.isNotEmpty())
            assertTrue("eval card for ${s.title} lacks key ${s.key}", mine.any { it.getJSONObject("answer").optJSONArray("create")?.toString()?.lowercase()?.contains(s.key) == true })
        }
    }
}
