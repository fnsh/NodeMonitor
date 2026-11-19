package net.freifunk.darmstadt.nodewhisperer.models.informations

import org.junit.Assert.assertEquals
import org.junit.Test

class NodeIdTest {
    /* Bytes >= 0x80 are negative as Byte, bytes < 0x10 need a leading zero */
    private val macAddress = byteArrayOf(0xd4.toByte(), 0x1a, 0x00, 0x0f, 0x80.toByte(), 0xff.toByte())

    @Test
    fun toString_isColonSeparated() {
        assertEquals("d4:1a:00:0f:80:ff", NodeId(macAddress).toString())
    }

    @Test
    fun toGluonNodeId_isLowercaseWithoutSeparators() {
        assertEquals("d41a000f80ff", NodeId(macAddress).toGluonNodeId())
    }
}
