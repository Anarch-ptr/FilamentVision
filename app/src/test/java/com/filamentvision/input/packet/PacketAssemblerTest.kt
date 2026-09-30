package com.filamentvision.input.packet

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketAssemblerTest {
    @Test
    fun assemblesFragmentsAndDiscardsConsumedPrefix() {
        val assembler = PacketAssembler(8)

        assertTrue(assembler.append(byteArrayOf(1, 2)))
        assertTrue(assembler.append(byteArrayOf(3, 4)))
        assembler.discard(3)
        assertTrue(assembler.append(byteArrayOf(5, 6, 7)))

        assertArrayEquals(byteArrayOf(4, 5, 6, 7), assembler.bytes())
    }

    @Test
    fun overflowIsBoundedAndKeepsNewestBytes() {
        val assembler = PacketAssembler(4)

        assertFalse(assembler.append(byteArrayOf(1, 2, 3, 4, 5, 6)))

        assertEquals(4, assembler.size)
        assertArrayEquals(byteArrayOf(3, 4, 5, 6), assembler.bytes())
    }
}
