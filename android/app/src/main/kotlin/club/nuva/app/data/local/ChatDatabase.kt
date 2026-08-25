package club.nuva.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * On-device message storage.
 *
 * Framework SQLite on purpose, not Room: Room needs an annotation processor,
 * which means a Gradle plugin, a KSP version that has to match the Kotlin
 * version, and a whole class of build failures we have already paid for once.
 * Three tables and a dozen queries do not justify that. If the schema grows
 * past ~8 tables, this is the single file to swap for Room.
 *
 * Rules that keep this file boring, which is what you want from storage:
 *  - it knows nothing about UI types, only rows of primitives;
 *  - every method is synchronous and MUST be called off the main thread;
 *  - schema changes go through onUpgrade with a numbered migration, never
 *    through "delete the app and reinstall".
 */
class ChatDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    NAME,
    null,
    VERSION,
) {

    data class PersonRow(
        val id: String,
        val username: String,
        val displayName: String,
        val bio: String,
        val avatarUrl: String,
    )

    data class ConversationRow(
        val id: String,
        val peerId: String,
        val unread: Int,
        val muted: Boolean,
        val createdAt: Long,
    )

    data class MessageRow(
        val id: String,
        val conversationId: String,
        val authorId: String,
        val text: String,
        val sentAt: Long,
        val delivery: String,
        // -- added in schema v2 ---------------------------------------------
        /** "text" or "voice". Defaults keep every v1 call site compiling. */
        val kind: String = KIND_TEXT,
        /** Server attachment id, empty until the upload has been accepted. */
        val attachmentId: String = "",
        /** Voice length in ms. 0 for text. */
        val durationMs: Int = 0,
        /** Comma-separated 0..100 bars. Empty for text. */
        val waveform: String = "",
        /**
         * Absolute path to the audio on this device. Set the moment recording
         * stops, so a voice note is playable before it has been uploaded and
         * still playable offline afterwards.
         */
        val localPath: String = "",
    )

    /** One emoji left by one person on one message. */
    data class ReactionRow(
        val messageId: String,
        val userId: String,
        val emoji: String,
        val reactedAt: Long,
    )

    /**
     * A fresh install builds v1 and then runs every migration, exactly like an
     * upgrading install does. This is the whole point: the two code paths
     * cannot produce different schemas, because there is only one path.
     */
    override fun onCreate(db: SQLiteDatabase) {
        createV1(db)
        migrate(db, from = 1, to = VERSION)
    }

    private fun createV1(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE people (
                id TEXT PRIMARY KEY NOT NULL,
                username TEXT NOT NULL,
                display_name TEXT NOT NULL,
                bio TEXT NOT NULL DEFAULT '',
                avatar_url TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX idx_people_username ON people (username)")
        db.execSQL(
            """
            CREATE TABLE conversations (
                id TEXT PRIMARY KEY NOT NULL,
                peer_id TEXT NOT NULL,
                unread INTEGER NOT NULL DEFAULT 0,
                muted INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE messages (
                id TEXT PRIMARY KEY NOT NULL,
                conversation_id TEXT NOT NULL,
                author_id TEXT NOT NULL,
                text TEXT NOT NULL,
                sent_at INTEGER NOT NULL,
                delivery TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_messages_convo ON messages (conversation_id, sent_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        migrate(db, from = oldVersion, to = newVersion)
    }

    /**
     * Every migration is additive and runs inside a transaction. Nothing here
     * drops a table or a column: losing a user's history on an app update is
     * not a trade we make.
     */
    private fun migrate(db: SQLiteDatabase, from: Int, to: Int) {
        if (from >= to) return

        db.beginTransaction()
        try {
            if (from < 2) {
                db.execSQL("ALTER TABLE messages ADD COLUMN kind TEXT NOT NULL DEFAULT '$KIND_TEXT'")
                db.execSQL("ALTER TABLE messages ADD COLUMN attachment_id TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN duration_ms INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN waveform TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN local_path TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """
                    CREATE TABLE reactions (
                        message_id TEXT NOT NULL,
                        user_id TEXT NOT NULL,
                        emoji TEXT NOT NULL,
                        reacted_at INTEGER NOT NULL,
                        PRIMARY KEY (message_id, user_id, emoji)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX idx_reactions_message ON reactions (message_id)")
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(false)
    }

    // -- people -------------------------------------------------------------

    @Synchronized
    fun people(): List<PersonRow> = readableDatabase
        .query("people", null, null, null, null, null, "display_name COLLATE NOCASE ASC")
        .use { c ->
            val out = ArrayList<PersonRow>(c.count)
            while (c.moveToNext()) {
                out += PersonRow(
                    id = c.str("id"),
                    username = c.str("username"),
                    displayName = c.str("display_name"),
                    bio = c.str("bio"),
                    avatarUrl = c.str("avatar_url"),
                )
            }
            out
        }

    @Synchronized
    fun upsertPerson(row: PersonRow) {
        val values = ContentValues().apply {
            put("id", row.id)
            put("username", row.username)
            put("display_name", row.displayName)
            put("bio", row.bio)
            put("avatar_url", row.avatarUrl)
        }
        writableDatabase.insertWithOnConflict(
            "people",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    // -- conversations ------------------------------------------------------

    @Synchronized
    fun conversations(): List<ConversationRow> = readableDatabase
        .query("conversations", null, null, null, null, null, "created_at ASC")
        .use { c ->
            val out = ArrayList<ConversationRow>(c.count)
            while (c.moveToNext()) {
                out += ConversationRow(
                    id = c.str("id"),
                    peerId = c.str("peer_id"),
                    unread = c.int("unread"),
                    muted = c.int("muted") == 1,
                    createdAt = c.long("created_at"),
                )
            }
            out
        }

    @Synchronized
    fun upsertConversation(row: ConversationRow) {
        val values = ContentValues().apply {
            put("id", row.id)
            put("peer_id", row.peerId)
            put("unread", row.unread)
            put("muted", if (row.muted) 1 else 0)
            put("created_at", row.createdAt)
        }
        writableDatabase.insertWithOnConflict(
            "conversations",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    @Synchronized
    fun deleteConversation(id: String) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("messages", "conversation_id = ?", arrayOf(id))
            writableDatabase.delete("conversations", "id = ?", arrayOf(id))
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    // -- messages -----------------------------------------------------------

    /**
     * Every message we have. Fine while history is local and small; the day a
     * server backfill lands this becomes `messages(conversationId, before,
     * limit)` and the store pages it. Written down so nobody discovers it as a
     * surprise on a 50k-message account.
     */
    @Synchronized
    fun messages(): List<MessageRow> = readableDatabase
        .query("messages", null, null, null, null, null, "sent_at ASC")
        .use { c ->
            val out = ArrayList<MessageRow>(c.count)
            while (c.moveToNext()) {
                out += MessageRow(
                    id = c.str("id"),
                    conversationId = c.str("conversation_id"),
                    authorId = c.str("author_id"),
                    text = c.str("text"),
                    sentAt = c.long("sent_at"),
                    delivery = c.str("delivery"),
                    kind = c.str("kind"),
                    attachmentId = c.str("attachment_id"),
                    durationMs = c.int("duration_ms"),
                    waveform = c.str("waveform"),
                    localPath = c.str("local_path"),
                )
            }
            out
        }

    @Synchronized
    fun insertMessage(row: MessageRow) {
        val values = ContentValues().apply {
            put("id", row.id)
            put("conversation_id", row.conversationId)
            put("author_id", row.authorId)
            put("text", row.text)
            put("sent_at", row.sentAt)
            put("delivery", row.delivery)
            put("kind", row.kind)
            put("attachment_id", row.attachmentId)
            put("duration_ms", row.durationMs)
            put("waveform", row.waveform)
            put("local_path", row.localPath)
        }
        writableDatabase.insertWithOnConflict(
            "messages",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    @Synchronized
    fun updateDelivery(messageId: String, delivery: String) {
        val values = ContentValues().apply { put("delivery", delivery) }
        writableDatabase.update("messages", values, "id = ?", arrayOf(messageId))
    }

    // -- reactions ----------------------------------------------------------

    @Synchronized
    fun reactions(): List<ReactionRow> = readableDatabase
        .query("reactions", null, null, null, null, null, "reacted_at ASC")
        .use { c ->
            val out = ArrayList<ReactionRow>(c.count)
            while (c.moveToNext()) {
                out += ReactionRow(
                    messageId = c.str("message_id"),
                    userId = c.str("user_id"),
                    emoji = c.str("emoji"),
                    reactedAt = c.long("reacted_at"),
                )
            }
            out
        }

    /** Idempotent: a double tap on a laggy connection must not be an error. */
    @Synchronized
    fun addReaction(row: ReactionRow) {
        val values = ContentValues().apply {
            put("message_id", row.messageId)
            put("user_id", row.userId)
            put("emoji", row.emoji)
            put("reacted_at", row.reactedAt)
        }
        writableDatabase.insertWithOnConflict(
            "reactions",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    @Synchronized
    fun removeReaction(messageId: String, userId: String, emoji: String) {
        writableDatabase.delete(
            "reactions",
            "message_id = ? AND user_id = ? AND emoji = ?",
            arrayOf(messageId, userId, emoji),
        )
    }

    /**
     * Called once the upload has been accepted: the row keeps its local audio
     * path so playback keeps working, and gains the server id so other devices
     * can fetch the same bytes.
     */
    @Synchronized
    fun attachUpload(messageId: String, attachmentId: String, delivery: String) {
        val values = ContentValues().apply {
            put("attachment_id", attachmentId)
            put("delivery", delivery)
        }
        writableDatabase.update("messages", values, "id = ?", arrayOf(messageId))
    }

    private fun Cursor.str(column: String): String =
        getString(getColumnIndexOrThrow(column)) ?: ""

    private fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))

    private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))

    companion object {
        private const val NAME = "nuva-chat.db"
        private const val VERSION = 2

        const val KIND_TEXT = "text"
        const val KIND_VOICE = "voice"
    }
}
