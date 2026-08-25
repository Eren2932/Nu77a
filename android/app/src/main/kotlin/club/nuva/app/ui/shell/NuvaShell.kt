package club.nuva.app.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import club.nuva.app.di.ServiceLocator
import club.nuva.app.ui.chat.ChatScreen
import club.nuva.app.ui.chat.ChatViewModel
import club.nuva.app.ui.chat.ChatsScreen
import club.nuva.app.ui.chat.ChatsViewModel
import club.nuva.app.ui.chat.NewChatScreen
import club.nuva.app.ui.design.NuvaCanvas
import club.nuva.app.ui.design.NuvaDot
import club.nuva.app.ui.home.DiagnosticsScreen
import club.nuva.app.ui.home.HomeViewModel
import club.nuva.app.ui.profile.EditProfileScreen
import club.nuva.app.ui.profile.ProfileScreen
import club.nuva.app.ui.settings.SettingsScreen
import club.nuva.app.ui.theme.NuvaMotion
import club.nuva.app.ui.theme.NuvaSpace
import club.nuva.app.ui.theme.NuvaTheme

/**
 * The signed-in shell: one NavHost plus a floating tab bar.
 *
 * Two rules baked in:
 *  - the tab bar hides itself on any screen that owns the full height (a chat,
 *    an editor). A bar over a keyboard is wasted space.
 *  - tabs never stack: switching tab pops back to that tab's root, so the back
 *    button cannot walk you through a history you did not build.
 */
object Routes {
    const val CHATS = "chats"
    const val CHAT = "chat/{conversationId}"
    const val NEW_CHAT = "new_chat"
    const val PROFILE = "profile"
    const val PROFILE_EDIT = "profile_edit"
    const val SETTINGS = "settings"
    const val DIAGNOSTICS = "diagnostics"

    fun chat(conversationId: String) = "chat/$conversationId"
}

@Composable
fun NuvaShell(onSwitchServer: () -> Unit) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val showTabs = route == Routes.CHATS || route == Routes.NEW_CHAT || route == Routes.PROFILE

    val realtimeState by ServiceLocator.realtime.state.collectAsStateWithLifecycle()

    NuvaCanvas(glow = route == Routes.PROFILE) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Routes.CHATS,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                composable(Routes.CHATS) {
                    val vm: ChatsViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer { ChatsViewModel(ServiceLocator.chatDrafts) }
                        },
                    )
                    ChatsScreen(
                        viewModel = vm,
                        realtimeState = realtimeState,
                        onOpenChat = { navController.navigate(Routes.chat(it)) },
                        onNewChat = { navController.navigate(Routes.NEW_CHAT) },
                        bottomInset = if (showTabs) 96.dp else 0.dp,
                    )
                }

                composable(
                    route = Routes.CHAT,
                    arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
                ) { entry ->
                    val id = entry.arguments?.getString("conversationId").orEmpty()
                    val vm: ChatViewModel = viewModel(
                        key = "chat-$id",
                        factory = viewModelFactory {
                            initializer { ChatViewModel(id, ServiceLocator.chatDrafts) }
                        },
                    )
                    ChatScreen(
                        viewModel = vm,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Routes.NEW_CHAT) {
                    NewChatScreen(
                        store = ServiceLocator.chatDrafts,
                        onOpenConversation = { conversationId ->
                            navController.navigate(Routes.chat(conversationId))
                        },
                        bottomInset = if (showTabs) 96.dp else 0.dp,
                    )
                }

                composable(Routes.PROFILE) {
                    ProfileScreen(
                        onEdit = { navController.navigate(Routes.PROFILE_EDIT) },
                        onSettings = { navController.navigate(Routes.SETTINGS) },
                        bottomInset = if (showTabs) 96.dp else 0.dp,
                    )
                }

                composable(Routes.PROFILE_EDIT) {
                    EditProfileScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onSwitchServer = onSwitchServer,
                        onDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
                    )
                }

                composable(Routes.DIAGNOSTICS) {
                    val vm: HomeViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer {
                                HomeViewModel(
                                    authRepository = ServiceLocator.authRepository,
                                    api = ServiceLocator.api,
                                    realtime = ServiceLocator.realtime,
                                )
                            }
                        },
                    )
                    DiagnosticsScreen(viewModel = vm, onBack = { navController.popBackStack() })
                }
            }

            AnimatedVisibility(
                visible = showTabs,
                enter = fadeIn(tween(NuvaMotion.FAST)) + slideInVertically(tween(NuvaMotion.NORMAL)) { it },
                exit = fadeOut(tween(NuvaMotion.FAST)) + slideOutVertically(tween(NuvaMotion.NORMAL)) { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                TabBar(
                    current = route,
                    unread = ServiceLocator.chatDrafts.conversations.collectAsStateWithLifecycle().value
                        .sumOf { it.unread },
                    onSelect = { target ->
                        if (target != route) {
                            navController.navigate(target) {
                                popUpTo(Routes.CHATS) { inclusive = target == Routes.CHATS }
                                launchSingleTop = true
                            }
                        }
                    },
                )
            }
        }
    }
}

/**
 * Floating pill tab bar. Not `NavigationBar`: the floating pill is part of the
 * Nuva look, and it lets the message list scroll behind it instead of being
 * cut by an opaque strip.
 */
@Composable
private fun TabBar(
    current: String?,
    unread: Int,
    onSelect: (String) -> Unit,
) {
    val p = NuvaTheme.palette
    Row(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = NuvaSpace.xxl, vertical = NuvaSpace.md)
            .clip(CircleShape)
            .background(p.surface.copy(alpha = 0.97f))
            .padding(horizontal = NuvaSpace.sm, vertical = NuvaSpace.sm),
        horizontalArrangement = Arrangement.spacedBy(NuvaSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab("Chats", Icons.Filled.ChatBubble, current == Routes.CHATS, unread) { onSelect(Routes.CHATS) }
        Tab("People", Icons.Filled.PersonSearch, current == Routes.NEW_CHAT, 0) { onSelect(Routes.NEW_CHAT) }
        Tab("Me", Icons.Filled.Person, current == Routes.PROFILE, 0) { onSelect(Routes.PROFILE) }
    }
}

@Composable
private fun Tab(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    badge: Int,
    onClick: () -> Unit,
) {
    val p = NuvaTheme.palette
    val bg by animateColorAsState(
        targetValue = if (selected) p.accent.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = tween(NuvaMotion.FAST),
        label = "tab-bg",
    )
    val tint = if (selected) p.accent else p.textMuted

    Column(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = NuvaSpace.xl, vertical = NuvaSpace.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(21.dp))
            if (badge > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(start = 10.dp)
                        .size(8.dp),
                ) {
                    NuvaDot(p.coral)
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}
