package com.whisprtext.app.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

object ColorGenerator {
    private val avatarColors = listOf(
        0xFFE57373.toInt(), 0xFFF06292.toInt(), 0xFFBA68C8.toInt(), 0xFF9575CD.toInt(),
        0xFF7986CB.toInt(), 0xFF64B5F6.toInt(), 0xFF4FC3F7.toInt(), 0xFF4DB6AC.toInt(),
        0xFF81C784.toInt(), 0xFFAED581.toInt(), 0xFFFFB74D.toInt(), 0xFFFF8A65.toInt()
    )

    fun generateGradient(id: String): Pair<Int, Int> {
        val hash = id.hashCode()
        val index1 = Math.abs(hash) % avatarColors.size
        // Use a different seed for the second color
        val index2 = Math.abs(hash.reverseBits()) % avatarColors.size
        
        var finalIndex2 = index2
        if (index1 == index2) {
            finalIndex2 = (index1 + 1) % avatarColors.size
        }
        
        return avatarColors[index1] to avatarColors[finalIndex2]
    }
    
    private fun Int.reverseBits(): Int {
        var n = this
        var rev = 0
        for (i in 0 until 32) {
            rev = (rev shl 1) or (n and 1)
            n = n shr 1
        }
        return rev
    }
}
