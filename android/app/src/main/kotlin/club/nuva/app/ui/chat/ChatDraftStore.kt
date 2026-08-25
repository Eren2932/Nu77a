package club.nuva.app.ui.chat

import club.nuva.app.data.local.ChatDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The chat store. Real storage, as of 3.1.
 *
 * What changed from 2.5, and it is the whole point of this drop:
 *
 *  - NO SEED DATA. The app starts empty. Six invented conversations with
 *    invented names made the product look like a mockup of itself.
 *  - NO SIMULATED PEER. 2.5 had a coroutine that typed a canned reply back at
 *    you after a delay. That is a demo trick, and it teaches the user to
 *    distrust everything else on the screen.
 *  - REAL PERSISTENCE. Everything goes to SQLite, so a restart keeps your
 *    contacts and your history. Memory-only state was the reason the app felt
 *    like a stub even when the layout was fine.
 *
 * Delivery honesty: without a server, a sent message can truthfully reach
 * `Sent`, meaning "written to this device". `Delivered` and `Read` are states
 * only a server can assert, so nothing in this file ever sets them. The chat
 * UI already renders all five states; two of them simply stay unused until
 * sprint 1 wires the socket.
 *
 * The class keeps its 2.5 name so that no screen has to be touched in this
 * drop. It is renamed to `ChatStore` in 3.2, together with the screen rewrite,
 * as one mechanical change instead of two risky ones.
 */
class ChatDraftStore(
    private val db: ChatDatabase,
    private val scope: CoroutineScope,
) {

    data class Person(
        val id: String,
        val username: String,
        val displayName: String,
        val bio: String = "",
        val avatarUrl: String = "",
        /** Only a server can know this. Stays false until sprint 1. */
        val online: Boolean = false,
        val presence: String = "",
    )

    enum class Delivery { Sending, Sent, Delivered, Read, Failed }

    data class Message(
        val id: String,
        val authorId: String,
        val text: String,
        val sentAtMillis: Long,
        val delivery: Delivery,
    ) {
        val mine: Boolean get() = authorId == ME_ID
    }

    data class Conversation(
        val id: String,
        val peer: Person,
        val messages: List<Message> = emptyList(),
        val unread: Int = 0,
        val muted: Boolean = false,
        /** Server-driven. Stays false until sprint 1. */
        val peerTyping: Boolean = false,
    ) {
        val lastMessage: Message? get() = messages.lastOrNull()
    }

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _people = MutableStateFlow<List<Person>>(emptyList())
    val people: StateFlow<List<Person>> = _people.asStateFlow()

    /** Snapshot for call sites that read the list once, during composition. */
    val directory: List<Person> get() = _people.value

    /** True once the first read from disk has finished, for empty-vs-loading. */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        // Disk I/O never on the main thread. The UI starts empty and fills in
        // one frame later, which is invisible, unlike a startup ANR.
        scope.launch(Dispatchers.IO) { restore() }
    }

    private fun restore() {
        val people = db.people().map {
            Person(
                id = it.id,
                username = it.username,
                displayName = it.displayName,
                bio = it.bio,
                avatarUrl = it.avatarUrl,
            )
        }
        val byId = people.associateBy { it.id }
        val messagesByConversation = db.messages().groupBy { it.conversationId }
        val conversations = db.conversations().mapNotNull { row ->
            val peer = byId[row.peerId] ?: return@mapNotNull null
            Conversation(
                id = row.id,
                peer = peer,
                messages = messagesByConversation[row.id].orEmpty().map { m ->
                    Message(
                        id = m.id,
                        authorId = m.authorId,
                        text = m.text,
                        sentAtMillis = m.sentAt,
                        delivery = runCatching { Delivery.valueOf(m.delivery) }
                            .getOrDefault(Delivery.Sent),
                    )
                },
                unread = row.unread,
                muted = row.muted,
            )
        }
        _people.value = people
        _conversations.value = sorted(conversations)
        _loaded.value = true
    }

    // -- contacts -----------------------------------------------------------

    /**
     * Adds a contact locally. Until the server exposes a directory, this is
     * how a chat comes into existence: you write down who you are talking to.
     * Returns the existing person when the username is already known, so
     * tapping twice cannot produce two of the same contact.
     */
    fun createContact(rawUsername: String, rawDisplayName: String = ""): Person {
        val username = rawUsername.trim().removePrefix("@").lowercase()
        require(username.isNotEmpty()) { "username must not be blank" }

        _people.value.firstOrNull { it.username == username }?.let { return it }

        val person = Person(
            id = "p-" + UUID.randomUUID(),
            username = username,
            displayName = rawDisplayName.trim().ifEmpty { "@$username" },
        )
        _people.value = (_people.value + person).sortedBy { it.displayName.lowercase() }
        scope.launch(Dispatchers.IO) {
            db.upsertPerson(
                ChatDatabase.PersonRow(
                    id = person.id,
                    username = person.username,
                    displayName = person.displayName,
                    bio = person.bio,
                    avatarUrl = person.avatarUrl,
                ),
            )
        }
        return person
    }

    // -- conversations ------------------------------------------------------

    fun conversation(id: String): Conversation? =
        _conversations.value.firstOrNull { it.id == id }

    /** Opens the chat with this person, creating it the first time. */
    fun openWith(person: Person): String {
        _conversations.value.firstOrNull { it.peer.id == person.id }?.let { return it.id }

        val fresh = Conversation(id = "c-" + UUID.randomUUID(), peer = person)
        val createdAt = System.currentTimeMillis()
        _conversations.value = sorted(_conversations.value + fresh)
        scope.launch(Dispatchers.IO) {
            db.upsertConversation(
                ChatDatabase.ConversationRow(
                    id = fresh.id,
                    peerId = person.id,
                    unread = 0,
                    muted = false,
                    createdAt = createdAt,
                ),
            )
        }
        return fresh.id
    }

    fun markRead(conversationId: String) = mutate(conversationId) { it.copy(unread = 0) }

    fun toggleMuted(conversationId: String) = mutate(conversationId) { it.copy(muted = !it.muted) }

    fun deleteConversation(conversationId: String) {
        _conversations.value = _conversations.value.filterNot { it.id == conversationId }
        scope.launch(Dispatchers.IO) { db.deleteConversation(conversationId) }
    }

    // -- messages -----------------------------------------------------------

    fun send(conversationId: String, text: String) {
        val body = text.trim()
        if (body.isEmpty()) return
        val convo = conversation(conversationId) ?: return

        val message = Message(
            id = "m-" + UUID.randomUUID(),
            authorId = ME_ID,
            text = body,
            sentAtMillis = System.currentTimeMillis(),
            delivery = Delivery.Sending,
        )
        _conversations.value = sorted(
            _conversations.value.map {
                if (it.id == convo.id) it.copy(messages = it.messages + message) else it
            },
        )

        scope.launch(Dispatchers.IO) { persist(conversationId, message) }
    }

    /** Re-runs the write for a message that failed to reach the disk. */
    fun retry(conversationId: String, messageId: String) {
        val message = conversation(conversationId)
            ?.messages
            ?.firstOrNull { it.id == messageId }
            ?: return

        setDelivery(conversationId, messageId, Delivery.Sending)
        scope.launch(Dispatchers.IO) {
            persist(conversationId, message.copy(delivery = Delivery.Sending))
        }
    }

    /**
     * Writes the message and reports the outcome truthfully. `Sent` here means
     * "stored on this device"; the socket will upgrade it to Delivered/Read
     * once there is a socket.
     */
    private fun persist(conversationId: String, message: Message) {
        val ok = runCatching {
            db.insertMessage(
                ChatDatabase.MessageRow(
                    id = message.id,
                    conversationId = conversationId,
                    authorId = message.authorId,
                    text = message.text,
                    sentAt = message.sentAtMillis,
                    delivery = Delivery.Sent.name,
                ),
            )
        }.isSuccess
        setDelivery(conversationId, message.id, if (ok) Delivery.Sent else Delivery.Failed)
    }

    private fun setDelivery(conversationId: String, messageId: String, delivery: Delivery) =
        mutate(conversationId) { convo ->
            convo.copy(
                messages = convo.messages.map {
                    if (it.id == messageId) it.copy(delivery = delivery) else it
                },
            )
        }

    private fun mutate(conversationId: String, block: (Conversation) -> Conversation) {
        _conversations.value = sorted(
            _conversations.value.map { if (it.id == conversationId) block(it) else it },
        )
        val updated = conversation(conversationId) ?: return
        scope.launch(Dispatchers.IO) {
            db.upsertConversation(
                ChatDatabase.ConversationRow(
                    id = updated.id,
                    peerId = updated.peer.id,
                    unread = updated.unread,
                    muted = updated.muted,
                    createdAt = updated.messages.firstOrNull()?.sentAtMillis
                        ?: System.currentTimeMillis(),
                ),
            )
        }
    }

    /** Most recent activity first; a chat with no messages yet stays on top. */
    private fun sorted(list: List<Conversation>): List<Conversation> =
        list.sortedByDescending { it.lastMessage?.sentAtMillis ?: Long.MAX_VALUE }

    companion object {
        /** Local author id. Replaced by the account id when sessions land. */
        const val ME_ID = "me"
    }
}
