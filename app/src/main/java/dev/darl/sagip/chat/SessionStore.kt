package dev.darl.sagip.chat

import android.content.Context
import dev.darl.sagip.data.Severity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ChatSession(val id: String, val title: String, val updatedAt: Long, val messages: List<Message>)

/**
 * Chat history, stored ONLY on the device (app-private file; never uploaded). Chats can
 * contain health details, so the UI offers delete-one and clear-all.
 */
class SessionStore(context: Context) {
    private val file = File(context.filesDir, "chat_sessions.json")

    fun load(): List<ChatSession> = runCatching {
        if (!file.exists()) return emptyList()
        parse(file.readText())
    }.getOrDefault(emptyList())

    fun save(all: List<ChatSession>) {
        runCatching { file.writeText(toJson(all.take(MAX_SESSIONS))) }
    }

    companion object {
        const val MAX_SESSIONS = 40

        fun toJson(all: List<ChatSession>): String = JSONArray().apply {
            all.forEach { s ->
                put(JSONObject().put("id", s.id).put("title", s.title).put("updatedAt", s.updatedAt).put(
                    "messages",
                    JSONArray().apply {
                        s.messages.filter { !it.streaming }.forEach { m ->
                            put(JSONObject()
                                .put("role", m.role.name).put("text", m.text).put("severity", m.severity?.key ?: "")
                                .put("related", JSONArray().apply { m.related.forEach { r -> put(JSONObject().put("id", r.topicId).put("title", r.title).put("sev", r.severity.key)) } })
                                .put("contacts", JSONArray().apply { m.contacts.forEach { c -> put(JSONObject().put("label", c.label).put("number", c.number).put("sample", c.sample)) } }))
                        }
                    },
                ))
            }
        }.toString()

        fun parse(json: String): List<ChatSession> {
            val arr = JSONArray(json)
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val ms = o.getJSONArray("messages")
                ChatSession(
                    o.getString("id"), o.getString("title"), o.getLong("updatedAt"),
                    (0 until ms.length()).map { j ->
                        val m = ms.getJSONObject(j)
                        val rel = m.optJSONArray("related"); val con = m.optJSONArray("contacts")
                        Message(
                            role = Role.valueOf(m.getString("role")), text = m.getString("text"),
                            severity = m.optString("severity").takeIf { it.isNotBlank() }?.let { Severity.from(it) },
                            related = (0 until (rel?.length() ?: 0)).map { k -> rel!!.getJSONObject(k).let { RelatedGuide(it.getString("id"), it.getString("title"), Severity.from(it.getString("sev"))) } },
                            contacts = (0 until (con?.length() ?: 0)).map { k -> con!!.getJSONObject(k).let { ContactChip(it.getString("label"), it.getString("number"), it.optBoolean("sample")) } },
                        )
                    },
                )
            }
        }
    }
}
