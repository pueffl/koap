package com.juul.koap.serialization

private const val hexCode = "0123456789ABCDEF"

internal fun ByteArray.hex(lowerCase: Boolean = false): String {
    val hex = buildString(size * 2) {
        for (byte in this) {
            append(hexCode[byte.toInt() shr 4 and 0xF])
            append(hexCode[byte.toInt() and 0xF])
        }
    }
    return if (lowerCase) hex.toLowerCase() else hex
}
