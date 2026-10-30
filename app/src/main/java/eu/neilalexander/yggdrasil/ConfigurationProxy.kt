package eu.neilalexander.yggdrasil

import android.content.Context
import mobile.Mobile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object ConfigurationProxy {
    private lateinit var json: JSONObject
    private lateinit var file: File

    operator fun invoke(applicationContext: Context): ConfigurationProxy {
        val dir = applicationContext.externalMediaDirs.firstOrNull()
            ?: applicationContext.getExternalFilesDir(null)
            ?: applicationContext.filesDir
        if (!dir.exists()) {
            dir.mkdirs()
        }
        file = File(dir, "yggdrasil.conf")
        ensureFileExists()
        fix()
        return this
    }

    private fun ensureFileExists() {
        if (!file.exists() || file.length() == 0L) {
            file.parentFile?.let {
                if (!it.exists()) {
                    it.mkdirs()
                }
            }
            val conf = Mobile.generateConfigJSON()
            file.writeBytes(conf)
        }
    }

    fun resetJSON() {
        val conf = Mobile.generateConfigJSON()
        file.writeBytes(conf)
        fix()
    }

    fun resetKeys() {
        val newJson = JSONObject(String(Mobile.generateConfigJSON()))
        updateJSON { json ->
            json.put("PrivateKey", newJson.getString("PrivateKey"))
        }
    }

    fun setKeys(privateKey: String) {
        updateJSON { json ->
            json.put("PrivateKey", privateKey)
        }
    }

    fun updateJSON(fn: (JSONObject) -> Unit) {
        ensureFileExists()
        val rawText = file.readText(Charsets.UTF_8).removePrefix("\uFEFF").trim()
        json = try {
            JSONObject(rawText)
        } catch (e: Exception) {
            JSONObject(String(Mobile.generateConfigJSON()))
        }
        fn(json)
        val str = json.toString(2)
        file.writeText(str, Charsets.UTF_8)
    }

    private fun fix() {
        updateJSON { json ->
            json.put("AdminListen", "none")
            json.put("IfName", "none")
            json.put("IfMTU", 65535)

            if (!json.has("Peers") || json.isNull("Peers")) {
                json.put("Peers", JSONArray())
            }
            if (!json.has("AllowedPublicKeys") || json.isNull("AllowedPublicKeys")) {
                json.put("AllowedPublicKeys", JSONArray())
            }

            if (json.has("MulticastInterfaces") &&
                json.getJSONArray("MulticastInterfaces").length() > 0 &&
                json.getJSONArray("MulticastInterfaces").get(0) is String
            ) {
                val ar = JSONArray()
                ar.put(0, JSONObject("""
                    {
                        "Regex": ".*",
                        "Beacon": true,
                        "Listen": true,
                        "Password": ""
                    }
                """.trimIndent()))
                json.put("MulticastInterfaces", ar)
            }
        }
    }

    fun getJSON(): JSONObject {
        fix()
        return json
    }

    fun getJSONByteArray(): ByteArray {
        fix()
        return json.toString(2).toByteArray(Charsets.UTF_8)
    }

    var multicastListen: Boolean
        get() = (json.getJSONArray("MulticastInterfaces").get(0) as JSONObject).getBoolean("Listen")
        set(value) {
            updateJSON { json ->
                (json.getJSONArray("MulticastInterfaces").get(0) as JSONObject).put("Listen", value)
            }
        }

    var multicastBeacon: Boolean
        get() = (json.getJSONArray("MulticastInterfaces").get(0) as JSONObject).getBoolean("Beacon")
        set(value) {
            updateJSON { json ->
                (json.getJSONArray("MulticastInterfaces").get(0) as JSONObject).put("Beacon", value)
            }
        }

    var multicastPassword: String
        get() = (json.getJSONArray("MulticastInterfaces").get(0) as JSONObject).optString("Password")
        set(value) {
            updateJSON { json ->
                (json.getJSONArray("MulticastInterfaces").get(0) as JSONObject).put("Password", value)
            }
        }
}
