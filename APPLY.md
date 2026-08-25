# nuva-4.0-full — сводная поставка

Кладётся поверх корня репозитория Nu77a. Все файлы целые, не патчи.
Позже добавленное перекрывает раннее: ChatScreen.kt здесь — исправленный
(850 строк, без нелегальной заглушки Modifier.heightIn).

## Состав

### Android
- ui/theme/Tokens.kt          — палитра navy/indigo, 29 полей, + bubbleOut*, surfaceRaised, scrim, wave*
- ui/chat/ChatScreen.kt       — экран чата целиком (градиентные пузыри, группировка, реакции, композер, полоса записи)
- ui/chat/ChatsScreen.kt      — список чатов (кольцо онлайна, пилюли непрочитанного)
- data/local/ChatDatabase.kt  — схема v2: kind, duration_ms, waveform, attachment_id, reactions
- data/remote/Dto.kt          — DTO под голос и реакции
- gradle/libs.versions.toml   — media3 в [libraries] (НЕ в [plugins])

### Server (Go)
- ws/protocol_chat.go         — константы send_voice, reaction_add, reaction_remove, reaction_relay
- api/handlers_ws.go          — диспетчер с ctx
- api/handlers_ws_chat.go     — обработчики текста, голоса, реакций
- api/handlers_media.go       — POST /v1/media, дедуп по sha256
- api/server.go, respond.go
- store/messages.go, conversations.go, attachments.go, reactions.go
- store/migrations/0002_media_reactions.sql

## Одно действие руками

В android/app/build.gradle.kts, блок dependencies:

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.common)

Каталог media3 объявляет, но подключить его нужно в build.gradle.kts —
этот файл я вслепую не трогаю.

## Что НЕ сделано (честно)

- Захват звука: жест, таймер и волна настоящие, MediaRecorder не подключён.
- Реакции живут в памяти ViewModel, серверный reaction_relay к клиенту не подключён.
- NuvaShell.kt не тронут — публичные сигнатуры ChatScreen/ChatsScreen сохранены байт в байт,
  поэтому правки шелла не требуются.
- POST /v1/conversations нет — беседу пока нельзя создать из приложения.
