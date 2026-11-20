package com.controlmystick.hid

import java.text.Normalizer

/**
 * Convierte caracteres a (modificador, usage) según la distribución de teclado US,
 * que es la que Android TV usa por defecto para teclados físicos genéricos.
 */
object KeyMapper {

    data class Stroke(val modifiers: Int, val usage: Int)

    private const val S = Key.MOD_LEFT_SHIFT

    private val symbols: Map<Char, Stroke> = buildMap {
        put(' ', Stroke(0, Key.SPACE))
        put('\n', Stroke(0, Key.ENTER))
        put('\t', Stroke(0, Key.TAB))
        put('-', Stroke(0, 0x2D)); put('_', Stroke(S, 0x2D))
        put('=', Stroke(0, 0x2E)); put('+', Stroke(S, 0x2E))
        put('[', Stroke(0, 0x2F)); put('{', Stroke(S, 0x2F))
        put(']', Stroke(0, 0x30)); put('}', Stroke(S, 0x30))
        put('\\', Stroke(0, 0x31)); put('|', Stroke(S, 0x31))
        put(';', Stroke(0, 0x33)); put(':', Stroke(S, 0x33))
        put('\'', Stroke(0, 0x34)); put('"', Stroke(S, 0x34))
        put('`', Stroke(0, 0x35)); put('~', Stroke(S, 0x35))
        put(',', Stroke(0, 0x36)); put('<', Stroke(S, 0x36))
        put('.', Stroke(0, 0x37)); put('>', Stroke(S, 0x37))
        put('/', Stroke(0, 0x38)); put('?', Stroke(S, 0x38))
        // Símbolos con Shift sobre la fila de números
        "!@#$%^&*(".forEachIndexed { i, c -> put(c, Stroke(S, 0x1E + i)) }
        put(')', Stroke(S, 0x27))
    }

    fun map(c: Char): Stroke? = when (c) {
        in 'a'..'z' -> Stroke(0, 0x04 + (c - 'a'))
        in 'A'..'Z' -> Stroke(S, 0x04 + (c - 'A'))
        in '1'..'9' -> Stroke(0, 0x1E + (c - '1'))
        '0' -> Stroke(0, 0x27)
        else -> symbols[c]
    }

    /** Quita tildes y diacríticos (á -> a, ñ -> n) para que se puedan teclear. */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
}
