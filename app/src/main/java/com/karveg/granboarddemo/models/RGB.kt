package com.karveg.granboarddemo.models

data class RGB(val red: Byte, val green: Byte, val blue: Byte) {
    companion object {
        val RED = RGB(0xFF.toByte(), 0x00, 0x00)
        val GREEN = RGB(0x00, 0x80.toByte(), 0x00)
        val WHITE = RGB(0x00, 0x00, 0x00)
        val BLACK = RGB(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val LIME = RGB(0x00, 0xFF.toByte(), 0x00)
        val BLUE = RGB(0x00, 0x00, 0x0FF.toByte())
        val YELLOW = RGB(0xFF.toByte(), 0xFF.toByte(), 0x00)
        val AQUA = RGB(0x00, 0xFF.toByte(), 0xFF.toByte())
        val MAGENTA = RGB(0xFF.toByte(), 0x00, 0xFF.toByte())
        val MAROON = RGB(0x7D.toByte(),0x00,0x00)
        val GREEN_LIGHT = RGB(0x00,0x7D.toByte(),0x00)
        val NAVY = RGB(0x00,0x00,0x7D.toByte())
        val OLIVE = RGB(0x7D.toByte(),0x7D.toByte(),0x00)
        val TEAL = RGB(0x00,0x7D.toByte(),0x7D.toByte())
        val PURPLE = RGB(0x7D.toByte(), 0x00, 0x7D.toByte())
        val GREY = RGB(0x7D.toByte(), 0x7D.toByte(), 0x7D.toByte())
    }
}