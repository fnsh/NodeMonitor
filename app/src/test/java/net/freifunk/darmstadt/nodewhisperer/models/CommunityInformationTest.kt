package net.freifunk.darmstadt.nodewhisperer.models

import net.freifunk.darmstadt.nodewhisperer.models.informations.NodeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommunityInformationTest {
    private val nodeId = NodeId("d4:1a:d1:14:79:ec")

    private fun community(mapUrl: String?) =
        CommunityInformation("ffmuc", "Freifunk München", "München", emptyMap(), mapUrl)

    @Test
    fun getNodeMapUrl_replacesPlaceholder() {
        assertEquals(
            "https://map.ffmuc.net/#/map/d41ad11479ec",
            community("https://map.ffmuc.net/#/map/{NODE_ID}").getNodeMapUrl(nodeId)
        )
    }

    @Test
    fun getNodeMapUrl_withoutMapUrl_isNull() {
        assertNull(community(null).getNodeMapUrl(nodeId))
    }
}
