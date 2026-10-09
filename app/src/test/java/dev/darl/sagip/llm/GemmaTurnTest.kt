package dev.darl.sagip.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GemmaTurnTest {
    @Test fun wrapsInUserAndModelTurns() {
        val t = LlmEngine.gemmaTurn("  hello  ")
        assertEquals("<start_of_turn>user\nhello<end_of_turn>\n<start_of_turn>model\n", t)
    }

    @Test fun endsOpenOnModelTurn() {
        assertTrue(LlmEngine.gemmaTurn("x").endsWith("<start_of_turn>model\n"))
    }
}
