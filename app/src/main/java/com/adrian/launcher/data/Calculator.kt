package com.adrian.launcher.data

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.pow

/**
 * Tiny calculator for the search pill: + - * / ^ %, parentheses, unary minus, decimal
 * comma or dot, and the × ÷ x signs. Returns null for anything that is not clearly a
 * calculation (no operator, letters, broken syntax), so ordinary searches pass through.
 */
object Calculator {

    fun evaluate(input: String): String? {
        val text = input.trim()
            .replace(',', '.')
            .replace('×', '*').replace('x', '*').replace('X', '*')
            .replace('÷', '/').replace(':', '/')
            .replace(" ", "")
        if (text.isEmpty() || text.none { it.isDigit() }) return null
        if (text.none { it in "+-*/^%" }) return null
        if (text.any { !(it.isDigit() || it in ".+-*/^%()") }) return null
        val value = runCatching { Parser(text).parse() }.getOrNull() ?: return null
        if (value.isNaN() || value.isInfinite()) return null
        return format(value)
    }

    private fun format(v: Double): String {
        val rounded = BigDecimal(v).round(MathContext(12)).stripTrailingZeros()
        val plain = if (rounded.scale() < 0) rounded.setScale(0) else rounded
        return plain.toPlainString().replace('.', ',')
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun parse(): Double {
            val v = expr()
            require(i == s.length)
            return v
        }

        private fun expr(): Double {
            var v = term()
            while (i < s.length && (s[i] == '+' || s[i] == '-')) {
                val op = s[i++]
                val r = term()
                v = if (op == '+') v + r else v - r
            }
            return v
        }

        private fun term(): Double {
            var v = power()
            while (i < s.length && (s[i] == '*' || s[i] == '/' || s[i] == '%')) {
                val op = s[i++]
                if (op == '%' && (i >= s.length || s[i] in "+-*/)")) {
                    v /= 100.0 // "50%" as a percentage
                    continue
                }
                val r = power()
                v = when (op) {
                    '*' -> v * r
                    '/' -> v / r
                    else -> v % r
                }
            }
            return v
        }

        private fun power(): Double {
            val base = unary()
            if (i < s.length && s[i] == '^') {
                i++
                return base.pow(power())
            }
            return base
        }

        private fun unary(): Double {
            if (i < s.length && s[i] == '-') {
                i++
                return -unary()
            }
            if (i < s.length && s[i] == '+') {
                i++
                return unary()
            }
            return atom()
        }

        private fun atom(): Double {
            if (i < s.length && s[i] == '(') {
                i++
                val v = expr()
                require(i < s.length && s[i] == ')')
                i++
                return v
            }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            require(i > start)
            return s.substring(start, i).toDouble()
        }
    }
}
