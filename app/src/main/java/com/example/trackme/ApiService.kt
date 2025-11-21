package com.example.trackme

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://gis.xakserv.ru/api/"

    // Регистрация устройства
    fun registerDevice(deviceId: String, name: String, model: String, androidVersion: String, callback: (Boolean, String) -> Unit) {
        val url = "${baseUrl}device.php"
        val json = JSONObject().apply {
            put("device_id", deviceId)
            put("name", name)
            put("model", model)
            put("android_version", androidVersion)
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ApiService", "Register device failed: ${e.message}")
                callback(false, "Network error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val result = response.isSuccessful
                val message = if (result) "Device registered" else "HTTP ${response.code}"
                callback(result, message)
            }
        })
    }

    // Отправка местоположения
    fun sendLocation(deviceId: String, latitude: Double, longitude: Double, accuracy: Float? = null, callback: (Boolean) -> Unit) {
        val url = "${baseUrl}location.php"
        val formBody = FormBody.Builder()
            .add("device_id", deviceId)
            .add("latitude", latitude.toString())
            .add("longitude", longitude.toString())
            .add("accuracy", accuracy?.toString() ?: "0")
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ApiService", "Send location failed: ${e.message}")
                callback(false)
            }

            override fun onResponse(call: Call, response: Response) {
                callback(response.isSuccessful)
            }
        })
    }

    // Проверка команд от сервера
    fun getCommands(deviceId: String, callback: (List<Command>) -> Unit) {
        val url = "${baseUrl}command.php?device_id=$deviceId"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ApiService", "Get commands failed: ${e.message}")
                callback(emptyList())
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (response.isSuccessful) {
                        val jsonString = response.body?.string()
                        val commands = parseCommands(jsonString)
                        callback(commands)
                    } else {
                        callback(emptyList())
                    }
                } catch (e: Exception) {
                    Log.e("ApiService", "Parse commands error: ${e.message}")
                    callback(emptyList())
                }
            }
        })
    }

    // Отметка команды как выполненной
    fun markCommandExecuted(commandId: Int, callback: (Boolean) -> Unit) {
        val url = "${baseUrl}command.php"
        val formBody = FormBody.Builder()
            .add("command_id", commandId.toString())
            .add("status", "executed")
            .build()

        val request = Request.Builder()
            .url(url)
            .put(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ApiService", "Mark command executed failed: ${e.message}")
                callback(false)
            }

            override fun onResponse(call: Call, response: Response) {
                callback(response.isSuccessful)
            }
        })
    }

    private fun parseCommands(jsonString: String?): List<Command> {
        val commands = mutableListOf<Command>()
        try {
            jsonString?.takeIf { it.isNotBlank() }?.let {
                val jsonArray = JSONObject(it).getJSONArray("commands")
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    commands.add(Command(
                        id = item.getInt("id"),
                        type = item.getString("command_type"),
                        deviceId = item.getString("device_id")
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e("ApiService", "Parse commands error: ${e.message}")
        }
        return commands
    }
}

data class Command(
    val id: Int,
    val type: String,
    val deviceId: String
)