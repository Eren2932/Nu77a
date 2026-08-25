package club.nuva.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import club.nuva.app.ui.design.NuvaAvatar
import club.nuva.app.ui.design.NuvaCard
import club.nuva.app.ui.design.NuvaEmptyState
import club.nuva.app.ui.design.NuvaField
import club.nuva.app.ui.design.NuvaHeader
import club.nuva.app.ui.theme.NuvaSpace
import club.nuva.app.ui.theme.NuvaTheme

/**
 * Find someone on this server and open a chat with them.
 *
 * The card at the top is not decoration: "only this server" is the whole point
 * of the product, and this is the moment the user needs to understand it.
 * Search is local for now; sprint 1 swaps the filter for GET /v1/users?q=.
 */
@Composable
fun NewChatScreen(
    store: ChatDraftStore,
    onOpenConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    val p = NuvaTheme.palette
    var query by remember { mutableStateOf("") }

    val people = remember(query) {
        if (query.isBlank()) {
            store.directory
        } else {
            store.directory.filter {
                it.displayName.contains(query, ignoreCase = true) ||
                    it.username.contains(query, ignoreCase = true)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        NuvaHeader(title = "People", subtitle = "Everyone on this server")

        NuvaField(
            value = query,
            onValueChange = { query = it },
            label = "Find by username",
            placeholder = "mira",
            leadingIcon = Icons.Filled.Search,
            mono = true,
            modifier = Modifier.padding(horizontal = NuvaSpace.gutter),
        )

        Spacer(Modifier.height(NuvaSpace.lg))

        NuvaCard(
            accented = true,
            modifier = Modifier.padding(horizontal = NuvaSpace.gutter),
        ) {
            Column(Modifier.padding(NuvaSpace.lg)) {
                Text(
                    text = "This list is only this server",
                    style = MaterialTheme.typography.titleSmall,
                    color = p.text,
                )
                Spacer(Modifier.height(NuvaSpace.xs))
                Text(
                    text = "There is no global directory in Nuva. People are only " +
                        "discoverable on the server they chose, and the operator of " +
                        "that server is whoever you decided to trust.",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textMuted,
                )
            }
        }

        Spacer(Modifier.height(NuvaSpace.md))

        if (people.isEmpty()) {
            NuvaEmptyState(
                icon = Icons.Filled.PersonSearch,
                title = "Nobody found",
                message = "No account on this server matches “$query”. Usernames are exact.",
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(people, key = { it.id }) { person ->
                    PersonRow(person = person, onClick = { onOpenConversation(store.openWith(person)) })
                }
                item { Spacer(Modifier.height(bottomInset + NuvaSpace.huge)) }
            }
        }
    }
}

@Composable
private fun PersonRow(
    person: ChatDraftStore.Person,
    onClick: () -> Unit,
) {
    val p = NuvaTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = NuvaSpace.gutter, vertical = NuvaSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NuvaAvatar(
            name = person.displayName,
            seed = person.id,
            imageUrl = person.avatarUrl,
            online = person.online,
            size = 46.dp,
        )
        Spacer(Modifier.width(NuvaSpace.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = person.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = p.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "@${person.username}" + if (person.bio.isNotBlank()) " · ${person.bio}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = p.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = if (person.online) "online" else person.presence,
            style = MaterialTheme.typography.labelSmall,
            color = if (person.online) p.mint else p.textFaint,
        )
    }
}
