package club.nuva.app.ui.chat

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * SPRINT 2.5 SCAFFOLD — NOT THE REAL MESSAGE STORE.
 * =============================================================================
 * Holds conversations in memory so the entire chat UI can be built, reviewed
 * and installed as a real APK before the server side of chat exists. It fakes
 * exactly three things and nothing else: the initial conversation list, the
 * delivery-state progression, and a canned reply.
 *
 * Why it is safe to ship inside the app for one sprint:
 *  - it touches no network, no disk, no session. Restarting the app resets it.
 *  - the seeded conversations are visibly demo content, wording included.
 *  - the models below (Person / Message / Conversation / Delivery) are the ones
 *    the real implementation will use, so sprint 2 replaces the BODY of this
 *    class and the screens do not change at all.
 *
 * DELETION CHECKLIST for sprint 2:
 *  1. delete this file and the `chatDrafts` field in ServiceLocator
 *  2. point ChatsViewModel / ChatViewModel at the real repository
 *  3. move the models to data/model/ (they are wire-shaped already)
 */
class ChatDraftStore(private val scope: CoroutineScope) {

    @Immutable
    data class Person(
        val id: String,
        val username: String,
        val displayName: String,
        val bio: String = "",
        val avatarUrl: String = "",
        val online: Boolean = false,
        /** Human text, e.g. "last seen 5 min ago". Server will send a timestamp. */
        val presence: String = "",
    )

    /** Mirrors what the server will report per message. */
    enum class Delivery { Sending, Sent, Delivered, Read, Failed }

    @Immutable
    data class Message(
        val id: String,
        val authorId: String,
        val text: String,
        val sentAtMillis: Long,
        val delivery: Delivery,
    ) {
        val mine: Boolean get() = authorId == ME_ID
    }

    @Immutable
    data class Conversation(
        val id: String,
        val peer: Person,
        val messages: List<Message> = emptyList(),
        val unread: Int = 0,
        val muted: Boolean = false,
        val peerTyping: Boolean = false,
    ) {
        val lastMessage: Message? get() = messages.lastOrNull()
    }

    private val _conversations = MutableStateFlow(seed())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    /** People you could start a chat with. Sprint 1 replaces this with search. */
    val directory: List<Person> = directorySeed()

    fun conversation(id: String): Conversation? = _conversations.value.firstOrNull { it.id == id }

    fun markRead(conversationId: String) = mutate(conversationId) { it.copy(unread = 0) }

    fun toggleMuted(conversationId: String) = mutate(conversationId) { it.copy(muted = !it.muted) }

    /** Returns the conversation id, creating the conversation if needed. */
    fun openWith(person: Person): String {
        val existing = _conversations.value.firstOrNull { it.peer.id == person.id }
        if (existing != null) return existing.id
        val fresh = Conversation(id = "c-${person.id}", peer = person)
        _conversations.update { listOf(fresh) + it }
        return fresh.id
    }

    /**
     * Optimistic send: the bubble appears immediately as Sending and then walks
     * the delivery states. This is the exact behaviour the real client needs,
     * because a message that only appears after a server round trip feels broken
     * on a slow connection.
     */
    fun send(conversationId: String, text: String) {
        val body = text.trim()
        if (body.isEmpty()) return
        val id = "m-${System.nanoTime()}"
        val now = System.currentTimeMillis()

        mutate(conversationId) { convo ->
            convo.copy(
                messages = convo.messages + Message(
                    id = id,
                    authorId = ME_ID,
                    text = body,
                    sentAtMillis = now,
                    delivery = Delivery.Sending,
                ),
            )
        }

        scope.launch {
            delay(320)
            setDelivery(conversationId, id, Delivery.Sent)
            delay(520)
            setDelivery(conversationId, id, Delivery.Delivered)
            delay(900)
            setDelivery(conversationId, id, Delivery.Read)

            // Canned reply so the screen can be judged with real motion.
            mutate(conversationId) { it.copy(peerTyping = true) }
            delay(1400)
            mutate(conversationId) { convo ->
                convo.copy(
                    peerTyping = false,
                    messages = convo.messages + Message(
                        id = "m-${System.nanoTime()}",
                        authorId = convo.peer.id,
                        text = REPLIES[convo.messages.size % REPLIES.size],
                        sentAtMillis = System.currentTimeMillis(),
                        delivery = Delivery.Read,
                    ),
                )
            }
        }
    }

    fun retry(conversationId: String, messageId: String) {
        setDelivery(conversationId, messageId, Delivery.Sending)
        scope.launch {
            delay(400)
            setDelivery(conversationId, messageId, Delivery.Sent)
            delay(400)
            setDelivery(conversationId, messageId, Delivery.Delivered)
        }
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
        _conversations.update { list ->
            list.map { if (it.id == conversationId) block(it) else it }
                // Newest activity first, exactly like the real list will be.
                .sortedByDescending { it.lastMessage?.sentAtMillis ?: 0L }
        }
    }

    companion object {
        /** Stands in for the signed-in user id until messages come from the server. */
        const val ME_ID = "me"

        private val REPLIES = listOf(
            "Дошло. Слышно чисто.",
            "Ок, я на связи.",
            "Проверил у себя — работает.",
            "Понял, держи меня в курсе.",
        )

        private fun directorySeed() = listOf(
            Person("u-mira", "mira", "Мира", "Не пишу после полуночи", online = true, presence = "online"),
            Person("u-devzis", "devzis", "devzis", "Держу свой сервер", online = true, presence = "online"),
            Person("u-kir", "kir", "Кирилл", presence = "last seen 12 min ago"),
            Person("u-anon", "quiet_fox", "Quiet Fox", "…", presence = "last seen yesterday"),
            Person("u-lena", "lena", "Лена", presence = "last seen 2 h ago"),
        )

        private fun seed(): List<Conversation> {
            val now = System.currentTimeMillis()
            val people = directorySeed()
            return listOf(
                Conversation(
                    id = "c-u-mira",
                    peer = people[0],
                    unread = 2,
                    messages = listOf(
                        Message("s1", "u-mira", "Это демо-переписка: она живёт в памяти и исчезнет после перезапуска.", now - 3_600_000, Delivery.Read),
                        Message("s2", ME_ID, "Понял. Экран настоящий, данные пока нет.", now - 3_400_000, Delivery.Read),
                        Message("s3", "u-mira", "Напиши что-нибудь — увидишь, как ходят галочки.", now - 900_000, Delivery.Read),
                    ),
                ),
                Conversation(
                    id = "c-u-devzis",
                    peer = people[1],
                    messages = listOf(
                        Message("s4", ME_ID, "Сервер поднял, порт наружу торчит.", now - 7_200_000, Delivery.Read),
                        Message("s5", "u-devzis", "docker compose ps первым делом, всегда.", now - 7_100_000, Delivery.Read),
                    ),
                ),
                Conversation(
                    id = "c-u-kir",
                    peer = people[2],
                    unread = 1,
                    messages = listOf(
                        Message("s6", "u-kir", "Скинь APK, поставлю поверх старого.", now - 86_400_000, Delivery.Read),
                    ),
                ),
            ).sortedByDescending { it.lastMessage?.sentAtMillis ?: 0L }
        }
    }
}
