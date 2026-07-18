package com.whisprtext.app

import com.whisprtext.app.data.local.entity.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatGroupingLogicTest {

    private fun calculateShowTimestamp(messages: List<MessageEntity>): Set<String> {
        val chronological = messages.reversed()
        val result = mutableSetOf<String>()
        if (chronological.isEmpty()) return result

        var currentBlockSender = chronological[0].senderId
        var lastMessageTimeInBlock = chronological[0].createdAt
        var currentBlockCount = 0

        for (i in chronological.indices) {
            val msg = chronological[i]
            val timeGap = msg.createdAt - lastMessageTimeInBlock

            val senderChanged = msg.senderId != currentBlockSender
            val timeGapExceeded = i > 0 && timeGap >= 300_000
            val countExceeded = currentBlockCount >= 10

            if (senderChanged || timeGapExceeded || countExceeded) {
                if (i > 0) {
                    result.add(chronological[i - 1].id)
                }
                currentBlockSender = msg.senderId
                lastMessageTimeInBlock = msg.createdAt
                currentBlockCount = 1
            } else {
                currentBlockCount++
                lastMessageTimeInBlock = msg.createdAt
            }
        }
        result.add(chronological.last().id)
        return result
    }

    @Test
    fun testSameSenderWithin5Minutes_OnlyLastShowsTimestamp() {
        val now = 1000000L
        val messages = listOf(
            MessageEntity("m3", "c1", "u1", "d1", "Msg 3", now + 2000, "sent"),
            MessageEntity("m2", "c1", "u1", "d1", "Msg 2", now + 1000, "sent"),
            MessageEntity("m1", "c1", "u1", "d1", "Msg 1", now, "sent")
        )
        // chronological order: m1, m2, m3
        // All same sender, small gaps. Only m3 (the latest) should show timestamp.
        
        val showTimestamp = calculateShowTimestamp(messages)
        assertEquals(setOf("m3"), showTimestamp)
    }

    @Test
    fun testSenderChange_ShowsTimestampBeforeChange() {
        val now = 1000000L
        val messages = listOf(
            MessageEntity("m3", "c1", "u2", "d1", "Msg 3", now + 2000, "sent"),
            MessageEntity("m2", "c1", "u1", "d1", "Msg 2", now + 1000, "sent"),
            MessageEntity("m1", "c1", "u1", "d1", "Msg 1", now, "sent")
        )
        // chronological order: m1 (u1), m2 (u1), m3 (u2)
        // m2 is the last of u1's block. m3 is the last of u2's block.
        
        val showTimestamp = calculateShowTimestamp(messages)
        assertEquals(setOf("m2", "m3"), showTimestamp)
    }

    @Test
    fun testTimeGapExceeded_ShowsTimestampBeforeGap() {
        val now = 1000000L
        val fiveMins = 300_000L
        val messages = listOf(
            MessageEntity("m3", "c1", "u1", "d1", "Msg 3", now + fiveMins + 1000, "sent"),
            MessageEntity("m2", "c1", "u1", "d1", "Msg 2", now + 1000, "sent"),
            MessageEntity("m1", "c1", "u1", "d1", "Msg 1", now, "sent")
        )
        // chronological order: m1, m2, gap, m3
        // m2 should show timestamp because it's followed by a 5-min gap.
        // m3 should show timestamp because it's the last message.
        
        val showTimestamp = calculateShowTimestamp(messages)
        assertEquals(setOf("m2", "m3"), showTimestamp)
    }

    @Test
    fun testMaxCountExceeded_ShowsTimestampAt10() {
        val now = 1000000L
        val messages = (1..15).map { i ->
            MessageEntity("m$i", "c1", "u1", "d1", "Msg $i", now + i * 1000, "sent")
        }.reversed() // descending order like in UI state
        
        // chronological: m1..m10, m11..m15
        // m10 should show timestamp (end of block of 10)
        // m15 should show timestamp (last message)
        
        val showTimestamp = calculateShowTimestamp(messages)
        assertTrue(showTimestamp.contains("m10"))
        assertTrue(showTimestamp.contains("m15"))
        assertEquals(2, showTimestamp.size)
    }
}
