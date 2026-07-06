package com.example.ekrantarjimon

import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Xitoychadan o'zbekchaga tarjima qiladi.
 * Google Translate'ning ochiq (bepul, API kaliti shart bo'lmagan) nuqtasidan foydalanadi.
 * Shaxsiy foydalanish uchun. Internet talab qiladi.
 */
object Translator {

    private const val ENDPOINT = "https://translate.googleapis.com/translate_a/single"

    /** Bitta matn bo'lagini tarjima qiladi. Xatolikda asl matnni qaytaradi. */
    fun translate(text: String, from: String = "zh-CN", to: String = "uz"): String {
        if (text.isBlank()) return text
        return try {
            val q = URLEncoder.encode(text, "UTF-8")
            val urlStr = "$ENDPOINT?client=gtx&sl=$from&tl=$to&dt=t&q=$q"
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }

            val code = conn.responseCode
            if (code != 200) {
                conn.disconnect()
                return text
            }

            val response = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
            conn.disconnect()

            parse(response).ifBlank { text }
        } catch (e: Exception) {
            text
        }
    }

    private fun parse(json: String): String {
        return try {
            val root = JSONArray(json)
            val sentences = root.getJSONArray(0)
            val sb = StringBuilder()
            for (i in 0 until sentences.length()) {
                val part = sentences.getJSONArray(i)
                sb.append(part.getString(0))
            }
            sb.toString()
        } catch (e: Exception) {
            ""
        }
    }
}
