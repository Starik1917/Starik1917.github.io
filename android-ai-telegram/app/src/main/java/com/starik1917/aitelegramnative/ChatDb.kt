package com.starik1917.aitelegramnative

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ChatDb(context: Context) : SQLiteOpenHelper(context, "chat_memory.db", null, 2) {
    data class Row(
        val chatId: Long,
        val messageId: Long,
        val mine: Boolean,
        val senderName: String,
        val text: String,
        val replyTo: String,
        val ts: Long,
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                chat_id INTEGER NOT NULL,
                message_id INTEGER NOT NULL DEFAULT 0,
                mine INTEGER NOT NULL,
                sender_name TEXT NOT NULL DEFAULT '',
                text TEXT NOT NULL,
                reply_to TEXT NOT NULL DEFAULT '',
                ts INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_messages_chat_ts ON messages(chat_id, ts, id)")
        db.execSQL("CREATE UNIQUE INDEX idx_messages_chat_msg ON messages(chat_id, message_id) WHERE message_id != 0")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS messages")
        onCreate(db)
    }

    fun add(row: Row) {
        val v = ContentValues().apply {
            put("chat_id", row.chatId)
            put("message_id", row.messageId)
            put("mine", if (row.mine) 1 else 0)
            put("sender_name", row.senderName)
            put("text", row.text.take(4000))
            put("reply_to", row.replyTo.take(2000))
            put("ts", row.ts)
        }
        writableDatabase.insertWithOnConflict("messages", null, v, SQLiteDatabase.CONFLICT_IGNORE)
        trim(row.chatId, 120)
    }

    fun last(chatId: Long, limit: Int = 50): List<Row> {
        val out = ArrayList<Row>()
        readableDatabase.query(
            "messages",
            arrayOf("chat_id", "message_id", "mine", "sender_name", "text", "reply_to", "ts"),
            "chat_id=?",
            arrayOf(chatId.toString()),
            null,
            null,
            "ts DESC, id DESC",
            limit.coerceIn(1, 120).toString(),
        ).use { c ->
            while (c.moveToNext()) {
                out += Row(
                    chatId = c.getLong(0),
                    messageId = c.getLong(1),
                    mine = c.getInt(2) != 0,
                    senderName = c.getString(3) ?: "",
                    text = c.getString(4) ?: "",
                    replyTo = c.getString(5) ?: "",
                    ts = c.getLong(6),
                )
            }
        }
        out.reverse()
        return out
    }

    fun clearChat(chatId: Long) {
        writableDatabase.delete("messages", "chat_id=?", arrayOf(chatId.toString()))
    }

    fun clearAll() {
        writableDatabase.delete("messages", null, null)
    }

    fun recentChats(limit: Int = 30): List<Pair<Long, String>> {
        val out = ArrayList<Pair<Long, String>>()
        readableDatabase.rawQuery(
            """
            SELECT m.chat_id,
                   COALESCE(NULLIF(MAX(CASE WHEN m.mine=0 THEN m.sender_name ELSE '' END), ''), CAST(m.chat_id AS TEXT)) AS name
            FROM messages m
            GROUP BY m.chat_id
            ORDER BY MAX(m.ts) DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(limit.coerceIn(1, 100).toString()),
        ).use { c ->
            while (c.moveToNext()) out += c.getLong(0) to (c.getString(1) ?: c.getLong(0).toString())
        }
        return out
    }

    private fun trim(chatId: Long, keep: Int) {
        writableDatabase.execSQL(
            """
            DELETE FROM messages
            WHERE chat_id=? AND id NOT IN (
              SELECT id FROM messages WHERE chat_id=? ORDER BY ts DESC, id DESC LIMIT ?
            )
            """.trimIndent(),
            arrayOf(chatId, chatId, keep),
        )
    }
}
