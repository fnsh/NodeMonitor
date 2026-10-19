package net.freifunk.darmstadt.nodewhisperer.models

import android.net.wifi.ScanResult
import android.os.Build
import android.util.Log

class WifiScanResult {
    class InformationElement(val id: Int, val bytes: ByteArray)

    var ssid: String
    var bssid: String
    var informationElements: List<InformationElement> = listOf()
    var rssi: Int

    constructor(scanResult: ScanResult) {
        bssid = scanResult.BSSID
        ssid = scanResult.SSID
        informationElements = readInformationElements(scanResult)
        rssi = scanResult.level
    }

    private fun readInformationElements(scanResult: ScanResult): List<InformationElement> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return scanResult.informationElements.map {
                val buffer = it.bytes
                val elementBytes = ByteArray(buffer.remaining())
                buffer.get(elementBytes)
                InformationElement(it.id, elementBytes)
            }
        }

        /* Before API 30, the elements are only exposed through the hidden informationElements field */
        return try {
            val elements = ScanResult::class.java.getField("informationElements").get(scanResult) as Array<*>?
            elements.orEmpty().filterNotNull().map {
                InformationElement(
                    it.javaClass.getField("id").getInt(it),
                    it.javaClass.getField("bytes").get(it) as ByteArray
                )
            }
        } catch (e: Exception) {
            Log.w("WifiScanResult", "Unable to read Information Elements", e)
            emptyList()
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun getGluonElements(): List<GluonElement> {
        var gluonElements = informationElements.filter {
            /* Filter Vendor Specific Elements */
            it.id == 221
        }.map {
            it.bytes
        }.filter {
            /* Filter for Gluon Elements */
            it.size >= 4 && it[0] == 0x00.toByte() && it[1] == 0x20.toByte() && it[2] == 0x91.toByte() && it[3] == 0x04.toByte()
        }.map {
            /* Remove the first 4 bytes */
            it.copyOfRange(4, it.size)
        }.toList()

        if (gluonElements.size > 1) {
            Log.w("WifiScanResult", "Multiple Gluon Elements found in one ScanResult")
            return emptyList()
        }

        val outputElements = mutableListOf<GluonElement>()
        gluonElements.forEach() {
            /* Parse TLV Values */
            var tmp = it
            while (true) {
                if (tmp.size < 2) {
                    break
                }

                val type = tmp[0].toInt()
                val length = tmp[1].toInt()

                if (tmp.size < 2 + length) {
                    break
                }

                val value = tmp.copyOfRange(2, 2 + length)
                tmp = tmp.copyOfRange(2 + length, tmp.size)

                outputElements.add(GluonElement(type, value))
            }
        }

        return outputElements
    }
}