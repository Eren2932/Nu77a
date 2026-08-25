# ChatScreen redesign

Single file. Drop it over:
  android/app/src/main/kotlin/club/nuva/app/ui/chat/ChatScreen.kt

Public surface is unchanged, so NuvaShell.kt needs no edit:
  - class ChatViewModel(conversationId: String, store: ChatDraftStore)
  - fun ChatScreen(viewModel, onBack, modifier = Modifier)
  - fun DeliveryTicks(delivery, onLight = false, modifier = Modifier)

No new dependency. No change to ChatDraftStore, the database or the server.
