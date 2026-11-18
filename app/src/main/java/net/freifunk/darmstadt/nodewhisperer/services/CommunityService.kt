package net.freifunk.darmstadt.nodewhisperer.services

import android.content.Context
import net.freifunk.darmstadt.nodewhisperer.models.CommunityInformation
import org.json.JSONObject

class CommunityService {
    private var context: Context

    constructor(context: Context) {
        this.context = context
    }

    private fun openAndLoadJsonFile(siteCode: String): JSONObject {
        /* Load Android asset */
        val assetManager = context.assets
        val inputStream = assetManager.open("site/${siteCode}.json")
        val size = inputStream.available()
        val buffer = ByteArray(size)
        inputStream.read(buffer)
        inputStream.close()
        return JSONObject(String(buffer))
    }
    fun getCommunityInformation(siteCode: String): CommunityInformation {
        /* Load JSON file */
        val jsonObject = openAndLoadJsonFile(siteCode)

        /* Create CommunityInformation object */
        val name = jsonObject.getString("name")
        val shortName = jsonObject.getString("short_name")
        val domainNames = mutableMapOf<String, String>()
        val domainNamesJson = jsonObject.getJSONObject("domain_names")
        for (key in domainNamesJson.keys()) {
            domainNames[key] = domainNamesJson.getString(key)
        }
        val mapUrl = if (jsonObject.isNull("map_url")) null else jsonObject.getString("map_url")

        return CommunityInformation(siteCode, name, shortName, domainNames, mapUrl)
    }
}