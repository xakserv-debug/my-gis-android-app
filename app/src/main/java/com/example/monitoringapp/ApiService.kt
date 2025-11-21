package com.example.monitoringapp

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

    // Регистрация устройства - ИСПРАВЛЕНО: всегда POST
    fun registerDevice(
        deviceId: String,
        name: String,
        model: String,
        androidVersion: String,
        callback: (Boolean, String) -> Unit
    ) {
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
            .post(body)  // ВАЖНО: POST а не GET
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                android.util.Log.e("ApiService", "Register device failed: ${e.message}")
                callback(false, "Network error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val result = response.isSuccessful
                val message = if (result) "Device registered" else "HTTP ${response.code}"
                android.util.Log.d("ApiService", "Register device: $result - $message")
                callback(result, message)
            }
        })
    }

    // Отправка местоположения - ИСПРАВЛЕНО
    fun sendLocation(
        deviceId: String,
        latitude: Double,
        longitude: Double,
        accuracy: Float?,
        callback: (Boolean) -> Unit
    ) {
        val url = "${baseUrl}location.php"
        val json = JSONObject().apply {
            put("device_id", deviceId)
            put("latitude", latitude)
            put("longitude", longitude)
            put("accuracy", accuracy ?: 0)
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)  // POST запрос
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                android.util.Log.e("ApiService", "Send location failed: ${e.message}")
                callback(false)
            }

            override fun onResponse(call: Call, response: Response) {
                val success = response.isSuccessful
                android.util.Log.d("ApiService", "Send location: $success")
                callback(success)
            }
        })
    }

    // Проверка команд - ЭТОТ остается GET
    fun getCommands(deviceId: String, callback: (List<Command>) -> Unit) {
        val url = "${baseUrl}command.php?device_id=$deviceId"
        val request = Request.Builder()
            .url(url)
            .get()  // ЭТОТ запрос правильно GET
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                android.util.Log.e("ApiService", "Get commands failed: ${e.message}")
                callback(emptyList())
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (response.isSuccessful) {
                        val jsonString = response.body?.string()
                        android.util.Log.d("ApiService", "Get commands response: $jsonString")
                        val commands = parseCommands(jsonString)
                        callback(commands)
                    } else {
                        android.util.Log.e("ApiService", "Get commands HTTP error: ${response.code}")
                        callback(emptyList())
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ApiService", "Parse commands error: ${e.message}")
                    callback(emptyList())
                }
            }
        })
    }

    // Отметка команды как выполненной - ИСПРАВЛЕНО
    fun markCommandExecuted(commandId: Int, callback: (Boolean) -> Unit) {
        val url = "${baseUrl}command.php"
        val json = JSONObject().apply {
            put("command_id", commandId)
            put("status", "executed")
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .put(body)  // PUT запрос
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                android.util.Log.e("ApiService", "Mark command failed: ${e.message}")
                callback(false)
            }

            override fun onResponse(call: Call, response: Response) {
                val success = response.isSuccessful
                android.util.Log.d("ApiService", "Mark command: $success")
                callback(success)
            }
        })
    }

    private fun parseCommands(jsonString: String?): List<Command> {
        val commands = mutableListOf<Command>()
        try {
            jsonString?.takeIf { it.isNotBlank() }?.let {
                val jsonObject = JSONObject(it)
                if (jsonObject.has("commands")) {
                    val jsonArray = jsonObject.getJSONArray("commands")
                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.getJSONObject(i)
                        commands.add(Command(
                            id = item.getInt("id"),
                            type = item.getString("command_type"),
                            deviceId = item.getString("device_id")
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ApiService", "Parse commands error: ${e.message}")
        }
        return commands
    }
}

data class Command(
    val id: Int,
    val type: String,
    val deviceId: String
)