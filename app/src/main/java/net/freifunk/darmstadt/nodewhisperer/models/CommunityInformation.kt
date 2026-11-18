package net.freifunk.darmstadt.nodewhisperer.models

import net.freifunk.darmstadt.nodewhisperer.models.informations.NodeId

data class CommunityInformation(
    val siteCode: String,
    val name: String,
    val shortName: String,
    val domainNames: Map<String, String>?,
    val mapUrl: String? = null
) {
    fun getNodeMapUrl(nodeId: NodeId): String? {
        return mapUrl?.replace(NODE_ID_PLACEHOLDER, nodeId.toGluonNodeId())
    }

    companion object {
        /* Same placeholder as in meshviewer's config.json */
        const val NODE_ID_PLACEHOLDER = "{NODE_ID}"
    }
}
