package com.healthconnect.export.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for [highlightJsonSyntax] — a JSON syntax highlighter that produces
 * an [AnnotatedString] with per-token [SpanStyle] colors.
 *
 * Token colors (VS Code-inspired dark theme):
 * - Keys (string before `:`)           → Color(0xFF569CD6)  blue
 * - String values                      → Color(0xFF6A9955)  green
 * - Numbers (int, float, negative)     → Color(0xFFB5CEA8)  yellow-green
 * - true / false                       → Color(0xFF569CD6)  blue + SemiBold
 * - null                               → Color(0xFFD16969)  red + SemiBold
 * - Structural (brackets, commas, etc) → Color(0xFFD4D4D4)  light gray
 */
class HighlightJsonSyntaxTest {

    // Colors matching the implementation in ExportScreen.kt
    private val keyColor = Color(0xFF569CD6)
    private val stringColor = Color(0xFF6A9955)
    private val numberColor = Color(0xFFB5CEA8)
    private val boolColor = Color(0xFF569CD6)
    private val nullColor = Color(0xFFD16969)
    private val defaultColor = Color(0xFFD4D4D4)

    // =============================================
    // Edge: empty / minimal
    // =============================================

    @Test
    fun `empty string returns empty annotated string`() {
        val result = highlightJsonSyntax("")
        assertEquals("", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `empty object highlights braces`() {
        val json = "{}"
        val result = highlightJsonSyntax(json)
        assertEquals(json, result.text)
        // Both braces should be defaultColor
        assertHasStyleAt(result, 0, defaultColor)
        assertHasStyleAt(result, 1, defaultColor)
    }

    @Test
    fun `empty array highlights brackets`() {
        val json = "[]"
        val result = highlightJsonSyntax(json)
        assertEquals(json, result.text)
        assertHasStyleAt(result, 0, defaultColor)
        assertHasStyleAt(result, 1, defaultColor)
    }

    @Test
    fun `whitespace-only string`() {
        val json = "   "
        val result = highlightJsonSyntax(json)
        assertEquals(json, result.text)
        // All whitespace gets defaultColor
        assertHasStyleAt(result, 0, defaultColor)
        assertHasStyleAt(result, 1, defaultColor)
        assertHasStyleAt(result, 2, defaultColor)
    }

    // =============================================
    // Simple tokens
    // =============================================

    @Test
    fun `single key-value pair highlights key and string`() {
        val json = """{"name": "Alice"}"""
        val result = highlightJsonSyntax(json)

        assertEquals(json, result.text)

        // {          → default
        assertHasStyleAt(result, 0, defaultColor)
        // "name"     → key (followed by :)
        assertHasStyleRange(result, 1, 7, keyColor)
        // : → default, followed by space
        assertHasStyleAt(result, 7, defaultColor)
        // "Alice"    → string (position 9 is first quote, 16 is closing quote)
        assertHasStyleRange(result, 9, 16, stringColor)
        // }          → default
        assertHasStyleAt(result, 16, defaultColor)
    }

    @Test
    fun `number value is highlighted with number color`() {
        val json = """{"age": 42}"""
        val result = highlightJsonSyntax(json)

        assertHasStyleRange(result, 1, 6, keyColor)      // "age"
        assertHasStyleRange(result, 8, 10, numberColor)   // 42
    }

    @Test
    fun `empty string value is highlighted with string color`() {
        val json = """{"key": ""}"""
        // Positions:
        // 0:{  1:"  2:k  3:e  4:y  5:"  6::  7:   8:"  9:"  10:}
        val result = highlightJsonSyntax(json)

        assertEquals(json, result.text)

        // Key "key" (positions 1-6)
        assertHasStyleRange(result, 1, 6, keyColor)

        // Empty string "" (positions 8-10)
        assertHasStyleRange(result, 8, 10, stringColor)

        // Closing brace (position 10)
        assertHasStyleAt(result, 10, defaultColor)
    }

    // =============================================
    // Booleans
    // =============================================

    @Test
    fun `boolean true is highlighted with bool color and semi-bold`() {
        val json = """{"active": true}"""
        val result = highlightJsonSyntax(json)

        val trueStyles = result.spanStyles.filter {
            result.text.substring(it.start, it.end) == "true"
        }
        assertEquals(1, trueStyles.size)
        assertEquals(boolColor, trueStyles.first().item.color)
        assertEquals(FontWeight.SemiBold, trueStyles.first().item.fontWeight)
    }

    @Test
    fun `boolean false is highlighted with bool color and semi-bold`() {
        val json = """{"active": false}"""
        val result = highlightJsonSyntax(json)

        val falseStyles = result.spanStyles.filter {
            result.text.substring(it.start, it.end) == "false"
        }
        assertEquals(1, falseStyles.size)
        assertEquals(boolColor, falseStyles.first().item.color)
        assertEquals(FontWeight.SemiBold, falseStyles.first().item.fontWeight)
    }

    @Test
    fun `null is highlighted with null color and semi-bold`() {
        val json = """{"value": null}"""
        val result = highlightJsonSyntax(json)

        val nullStyles = result.spanStyles.filter {
            result.text.substring(it.start, it.end) == "null"
        }
        assertEquals(1, nullStyles.size)
        assertEquals(nullColor, nullStyles.first().item.color)
        assertEquals(FontWeight.SemiBold, nullStyles.first().item.fontWeight)
    }

    // =============================================
    // Numbers
    // =============================================

    @Test
    fun `negative number is highlighted`() {
        val json = """{"temp": -5}"""
        val result = highlightJsonSyntax(json)

        // "{"temp": -5}" → space after :, so -5 starts at index 9
        assertHasStyleRange(result, 9, 11, numberColor)   // -5
    }

    @Test
    fun `floating point number is highlighted`() {
        val json = """{"pi": 3.14}"""
        val result = highlightJsonSyntax(json)

        // "{"pi": 3.14}" → space after :, so 3.14 starts at index 7
        assertHasStyleRange(result, 7, 11, numberColor)   // 3.14
    }

    @Test
    fun `scientific notation number is highlighted`() {
        val json = """{"e": 1.5e3}"""
        val result = highlightJsonSyntax(json)

        assertHasStyleRange(result, 6, 11, numberColor)   // 1.5e3
    }

    @Test
    fun `scientific notation with uppercase E is highlighted`() {
        val json = """{"val": 2.5E4}"""
        val result = highlightJsonSyntax(json)

        // "{"val": 2.5E4}" → 2.5E4 at [8, 13)
        assertHasStyleRange(result, 8, 13, numberColor)
    }

    @Test
    fun `scientific notation with negative exponent is highlighted`() {
        val json = """{"val": 1.5e-3}"""
        // Positions:
        // 0:{  1:"  2:v  3:a  4:l  5:"  6::  7:   8:1  9:.  10:5  11:e  12:-  13:3  14:}
        val result = highlightJsonSyntax(json)

        assertEquals(json, result.text)

        // Key "val" (positions 1-6)
        assertHasStyleRange(result, 1, 6, keyColor)

        // Number 1.5e-3 (positions 8-14)
        assertHasStyleRange(result, 8, 14, numberColor)
    }

    @Test
    fun `scientific notation with positive exponent and plus sign is highlighted`() {
        val json = """{"val": 1.5E+2}"""
        // Positions:
        // 0:{  1:"  2:v  3:a  4:l  5:"  6::  7:   8:1  9:.  10:5  11:E  12:+  13:2  14:}
        val result = highlightJsonSyntax(json)

        assertEquals(json, result.text)

        // Key "val" (positions 1-6)
        assertHasStyleRange(result, 1, 6, keyColor)

        // Number 1.5E+2 (positions 8-14)
        assertHasStyleRange(result, 8, 14, numberColor)
    }

    @Test
    fun `zero is highlighted as number`() {
        val json = """{"count": 0}"""
        val result = highlightJsonSyntax(json)

        // "{"count": 0}" → space after :, so 0 starts at index 10
        assertHasStyleRange(result, 10, 11, numberColor)   // 0
    }

    // =============================================
    // Escape sequences
    // =============================================

    @Test
    fun `string with escaped quotes is handled correctly`() {
        // In a raw triple-quoted string, \" is literally backslash-quote
        val json = """{"text": "he said \"hi\""}"""
        // Positions:
        // 0:{  1:"  2:t  3:e  4:x  5:t  6:"  7::  8:   9:"  10:h  11:e
        // 12:  13:s  14:a  15:i  16:d  17:  18:\  19:"  20:h  21:i  22:\  23:"  24:"  25:}
        val result = highlightJsonSyntax(json)

        assertEquals(json, result.text)

        // Key "text" (positions 1-6)
        assertHasStyleRange(result, 1, 7, keyColor)

        // Full string value "he said \"hi\"" (positions 9-24)
        assertHasStyleRange(result, 9, 25, stringColor)
    }

    @Test
    fun `even number of backslashes before quote closes the string correctly`() {
        // JSON with value ending with escaped backslashes before closing quote:
        // {"key": "text\\\\"}  → 4 backslashes = two escaped backslashes (literal \\), then " closes
        val json = """{"key": "text\\\\"}"""
        // Positions:
        // 0:{  1:"  2:k  3:e  4:y  5:"  6::  7:   8:"  9:t 10:e 11:x 12:t
        // 13:\ 14:\ 15:\ 16:\ 17:"  18:}
        val result = highlightJsonSyntax(json)

        assertEquals(json, result.text)

        // Key "key" (positions 1-6)
        assertHasStyleRange(result, 1, 6, keyColor)

        // String value "text\\\\" (positions 8-18 — the quote at 17 closes the string)
        assertHasStyleRange(result, 8, 18, stringColor)

        // Closing brace should be defaultColor (NOT included in string)
        assertHasStyleAt(result, 18, defaultColor)
    }

    @Test
    fun `odd number of backslashes before quote is still inside string`() {
        // JSON with escaped quotes inside the string:
        // {"msg": "say \"hi\""}
        // The \" is: escaped quote — it stays inside the string
        val json = """{"msg": "say \"hi\""}"""
        // Positions:
        // 0:{ 1:" 2:m 3:s 4:g 5:" 6:: 7:  8:" 9:s 10:a 11:y 12:  13:\
        // 14:" 15:h 16:i 17:\ 18:" 19:" 20:}
        val result = highlightJsonSyntax(json)

        assertEquals(json, result.text)

        // Key "msg" (positions 1-6)
        assertHasStyleRange(result, 1, 6, keyColor)

        // Full string value "say \"hi\"" (positions 8-20)
        assertHasStyleRange(result, 8, 20, stringColor)

        // Closing brace should be defaultColor
        assertHasStyleAt(result, 20, defaultColor)
    }

    @Test
    fun `string with newline escape not highlighted as number`() {
        val json = """{"msg": "line1\nline2"}"""
        val result = highlightJsonSyntax(json)

        // Key "msg"
        assertHasStyleRange(result, 1, 6, keyColor)

        // String value "line1\nline2" (starts at index 8)
        assertHasStyleRange(result, 8, 22, stringColor)
    }

    // =============================================
    // Nested structures
    // =============================================

    @Test
    fun `nested object keys and values are highlighted correctly`() {
        val json = """{"outer": {"inner": true}}"""
        val result = highlightJsonSyntax(json)

        // "outer" key (1..8)
        assertHasStyleRange(result, 1, 8, keyColor)
        // "inner" key (11..18)
        assertHasStyleRange(result, 11, 18, keyColor)

        // true value
        val trueStyles = result.spanStyles.filter {
            result.text.substring(it.start, it.end) == "true"
        }
        assertEquals(1, trueStyles.size)
        assertEquals(boolColor, trueStyles.first().item.color)
    }

    @Test
    fun `array elements are highlighted`() {
        val json = """{"nums": [1, 2, 3]}"""
        val result = highlightJsonSyntax(json)

        // "nums" key (1..7)
        assertHasStyleRange(result, 1, 7, keyColor)

        // Each number
        assertHasStyleRange(result, 10, 11, numberColor)  // 1
        assertHasStyleRange(result, 13, 14, numberColor)  // 2
        assertHasStyleRange(result, 16, 17, numberColor)  // 3

        // Structural: [ then ]
        assertHasStyleAt(result, 9, defaultColor)
        assertHasStyleAt(result, 17, defaultColor)
    }

    @Test
    fun `array of strings`() {
        val json = """["a", "b"]"""
        val result = highlightJsonSyntax(json)

        // Strings in an array (not keys, so no check for :) → they are string values
        assertHasStyleRange(result, 1, 4, stringColor)
        assertHasStyleRange(result, 6, 9, stringColor)
    }

    @Test
    fun `array containing null`() {
        val json = """[null]"""
        val result = highlightJsonSyntax(json)

        val nullStyles = result.spanStyles.filter {
            result.text.substring(it.start, it.end) == "null"
        }
        assertEquals(1, nullStyles.size)
        assertEquals(nullColor, nullStyles.first().item.color)
    }

    // =============================================
    // Complete realistic JSON
    // =============================================

    @Test
    fun `realistic health record JSON highlights all token types`() {
        val json = """
        {
            "steps": 8500,
            "active": true,
            "note": null,
            "heart_rate": 72.5
        }
        """.trimIndent()

        val result = highlightJsonSyntax(json)

        assertContainsColored(result, "steps", keyColor)
        assertContainsColored(result, "8500", numberColor)
        assertContainsColored(result, "active", keyColor)
        assertContainsColored(result, "true", boolColor)
        assertContainsColored(result, "note", keyColor)
        assertContainsColored(result, "null", nullColor)
        assertContainsColored(result, "heart_rate", keyColor)
        assertContainsColored(result, "72.5", numberColor)
    }

    @Test
    fun `text is reconstructed correctly for complex input`() {
        val json = """
        [
            {"x": -1, "y": 3.5e2, "ok": true},
            {"x": 0, "y": 0.0, "ok": false, "z": null}
        ]
        """.trimIndent()

        val result = highlightJsonSyntax(json)
        assertEquals(json, result.text)
    }

    @Test
    fun `null as value in various positions`() {
        val json = """{"a": null, "b": null}"""
        val result = highlightJsonSyntax(json)

        val nullStyles = result.spanStyles.filter {
            result.text.substring(it.start, it.end) == "null"
        }
        assertEquals(2, nullStyles.size)
        nullStyles.forEach { style ->
            assertEquals(nullColor, style.item.color)
            assertEquals(FontWeight.SemiBold, style.item.fontWeight)
        }
    }

    // =============================================
    // Token boundaries & coverage
    // =============================================

    @Test
    fun `adjacent tokens do not overlap`() {
        val json = """{"a":1,"b":2}"""
        val result = highlightJsonSyntax(json)

        val styles = result.spanStyles.sortedBy { it.start }
        for (i in 0 until styles.size - 1) {
            assertTrue(
                "Styles at index $i and ${i + 1} overlap: " +
                "[${styles[i].start},${styles[i].end}) and [${styles[i + 1].start},${styles[i + 1].end})",
                styles[i].end <= styles[i + 1].start
            )
        }
    }

    @Test
    fun `every character has a style assigned`() {
        val json = """{"valid": 1, "check": true, "maybe": null, "label": "done", "pi": 3.14}"""
        val result = highlightJsonSyntax(json)

        val coverage = BooleanArray(json.length)
        for (style in result.spanStyles) {
            for (i in style.start until style.end) {
                coverage[i] = true
            }
        }
        val uncovered = coverage.indices.filter { !coverage[it] }
        assertTrue(
            "Characters not covered by any style at positions: $uncovered",
            uncovered.isEmpty()
        )
    }

    @Test
    fun `true inside a string is not highlighted as boolean`() {
        val json = """{"key": "true_story"}"""
        val result = highlightJsonSyntax(json)

        // Key "key" should be keyColor (1..6)
        assertHasStyleRange(result, 1, 6, keyColor)

        // Value "true_story" should be stringColor (8..20 — space after :)
        assertHasStyleRange(result, 8, 20, stringColor)

        // No separate "true" token with boolColor
        val boolTokens = result.spanStyles.filter { it.item.color == boolColor }
                                             .map { result.text.substring(it.start, it.end) }
        assertFalse("'true' inside a string should not be separate bool token", boolTokens.contains("true"))
    }

    // =============================================
    // Helper assertions
    // =============================================

    private fun assertHasStyleAt(
        result: androidx.compose.ui.text.AnnotatedString,
        position: Int,
        color: Color
    ) {
        assertHasStyleRange(result, position, position + 1, color)
    }

    private fun assertHasStyleRange(
        result: androidx.compose.ui.text.AnnotatedString,
        rangeStart: Int,
        rangeEnd: Int,
        color: Color
    ) {
        val matching = result.spanStyles.filter {
            it.start == rangeStart && it.end == rangeEnd && it.item.color == color
        }
        assertTrue(
            "Expected style [$rangeStart,${rangeEnd}) with color $color, but not found.\n" +
            "Actual styles covering that area: " +
            result.spanStyles.filter { it.start <= rangeStart && it.end >= rangeEnd }
                .joinToString("; ") { "[${it.start},${it.end}) color=${it.item.color}" },
            matching.isNotEmpty()
        )
    }

    private fun assertContainsColored(
        result: androidx.compose.ui.text.AnnotatedString,
        token: String,
        color: Color
    ) {
        val startIndex = result.text.indexOf(token)
        assertNotEquals("Token '$token' not found in text", -1, startIndex)

        val endIndex = startIndex + token.length
        val matching = result.spanStyles.filter {
            it.start <= startIndex && it.end >= endIndex && it.item.color == color
        }
        assertTrue(
            "Token '$token' at [$startIndex,$endIndex) should be colored $color.\n" +
            "Styles covering that range: " +
            result.spanStyles.filter { it.start <= startIndex && it.end >= endIndex }
                .joinToString("; ") { "[${it.start},${it.end}) color=${it.item.color}" },
            matching.isNotEmpty()
        )
    }
}
