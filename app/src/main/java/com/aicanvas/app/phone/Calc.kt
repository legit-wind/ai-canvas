package com.aicanvas.app.phone

/** Exact arithmetic for the AI's "calc" tool: + − × ÷, %, brackets and decimals. No code is ever run. */
object Calc {
    fun eval(expr: String): Double? = runCatching {
        val p = Parser(expr.replace("×", "*").replace("÷", "/").replace("−", "-").replace(",", "").replace("₹", ""))
        val v = p.sum()
        p.skip()
        if (p.i != p.s.length || v.isNaN() || v.isInfinite()) null else v
    }.getOrNull()

    fun format(v: Double): String =
        if (v % 1.0 == 0.0 && kotlin.math.abs(v) < 1e15) v.toLong().toString() else "%.2f".format(java.util.Locale.US, v)

    private class Parser(val s: String) {
        var i = 0
        fun skip() { while (i < s.length && s[i] == ' ') i++ }
        fun sum(): Double {
            var v = product()
            while (true) { skip(); when (s.getOrNull(i)) { '+' -> { i++; v += product() }; '-' -> { i++; v -= product() }; else -> return v } }
        }
        fun product(): Double {
            var v = unary()
            while (true) { skip(); when (s.getOrNull(i)) { '*' -> { i++; v *= unary() }; '/' -> { i++; v /= unary() }; else -> return v } }
        }
        fun unary(): Double { skip(); return if (s.getOrNull(i) == '-') { i++; -unary() } else percent() }
        fun percent(): Double { var v = atom(); skip(); if (s.getOrNull(i) == '%') { i++; v /= 100 }; return v }
        fun atom(): Double {
            skip()
            if (s.getOrNull(i) == '(') { i++; val v = sum(); skip(); require(s.getOrNull(i) == ')'); i++; return v }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            return s.substring(start, i).toDouble()
        }
    }
}
