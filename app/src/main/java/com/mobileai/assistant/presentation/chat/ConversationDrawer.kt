package com.mobileai.assistant.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileai.assistant.domain.dot.DotProfile
import com.mobileai.assistant.domain.model.ConversationSession
import com.mobileai.assistant.domain.model.ResponseLanguage
import com.mobileai.assistant.presentation.dot.DotSelectorSection
import com.mobileai.assistant.presentation.dot.parseThemeColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConversationDrawerContent(
    conversations: List<ConversationSession>,
    activeConversationId: String,
    dots: List<DotProfile> = emptyList(),
    activeDot: DotProfile = DotProfile.STARTER_PROFILES.first(),
    selectedDotFilterId: String? = activeDot.id,
    onSelectDot: (DotProfile) -> Unit = {},
    onSelectDotFilter: (String?) -> Unit = {},
    onCreateDotClick: () -> Unit = {},
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onNewChat: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onRenameClick: (ConversationSession) -> Unit,
    onDeleteClick: (ConversationSession) -> Unit,
    onTogglePinClick: ((ConversationSession) -> Unit)? = null,
    onExportClick: ((ConversationSession) -> Unit)? = null,
    onNavigateToSettings: () -> Unit,
    onNavigateToModels: () -> Unit,
    onNavigateToEndpoints: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(320.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 16.dp, horizontal = 12.dp)
    ) {
        // App Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Mobile AI",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Chat History",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Dot Selector Section
        if (dots.isNotEmpty()) {
            DotSelectorSection(
                dots = dots,
                activeDot = activeDot,
                selectedFilterId = selectedDotFilterId,
                onSelectDot = onSelectDot,
                onSelectFilter = onSelectDotFilter,
                onCreateDotClick = onCreateDotClick
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Prominent "+ New Chat" Button
        Button(
            onClick = onNewChat,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "New Chat",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search / Filter Input Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = "Search conversations...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Conversation Sessions List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (conversations.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) "No chat history yet." else "No matching conversations.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else if (searchQuery.isNotBlank()) {
                val showDotBadge = selectedDotFilterId == null
                fun resolveDotForSession(session: ConversationSession): DotProfile? {
                    return dots.find { it.id == session.dotId }
                        ?: DotProfile.STARTER_PROFILES.find { it.id == (session.dotId ?: DotProfile.BUILTIN_GENERAL_ID) }
                }

                // Search Results with snippet matching
                item {
                    SectionHeader(title = "Results (${conversations.size})")
                }
                items(conversations, key = { it.id }) { session ->
                    val isActive = session.id == activeConversationId
                    val snippet = findMatchingSnippet(session, searchQuery)
                    ConversationItemRow(
                        session = session,
                        isActive = isActive,
                        searchSnippet = snippet,
                        dot = resolveDotForSession(session),
                        showDotBadge = showDotBadge,
                        onSelect = { onSelectConversation(session.id) },
                        onRename = { onRenameClick(session) },
                        onDelete = { onDeleteClick(session) },
                        onTogglePin = { onTogglePinClick?.invoke(session) },
                        onExport = { onExportClick?.invoke(session) }
                    )
                }
            } else {
                val showDotBadge = selectedDotFilterId == null
                fun resolveDotForSession(session: ConversationSession): DotProfile? {
                    return dots.find { it.id == session.dotId }
                        ?: DotProfile.STARTER_PROFILES.find { it.id == (session.dotId ?: DotProfile.BUILTIN_GENERAL_ID) }
                }

                // Pinned Section & Recent Section
                val pinnedChats = conversations.filter { it.pinned }
                val recentChats = conversations.filterNot { it.pinned }

                if (pinnedChats.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Pinned")
                    }
                    items(pinnedChats, key = { "pinned_${it.id}" }) { session ->
                        val isActive = session.id == activeConversationId
                        ConversationItemRow(
                            session = session,
                            isActive = isActive,
                            searchSnippet = null,
                            dot = resolveDotForSession(session),
                            showDotBadge = showDotBadge,
                            onSelect = { onSelectConversation(session.id) },
                            onRename = { onRenameClick(session) },
                            onDelete = { onDeleteClick(session) },
                            onTogglePin = { onTogglePinClick?.invoke(session) },
                            onExport = { onExportClick?.invoke(session) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                if (recentChats.isNotEmpty()) {
                    item {
                        SectionHeader(title = if (pinnedChats.isNotEmpty()) "Recent" else "Recent Chats")
                    }
                    items(recentChats, key = { it.id }) { session ->
                        val isActive = session.id == activeConversationId
                        ConversationItemRow(
                            session = session,
                            isActive = isActive,
                            searchSnippet = null,
                            dot = resolveDotForSession(session),
                            showDotBadge = showDotBadge,
                            onSelect = { onSelectConversation(session.id) },
                            onRename = { onRenameClick(session) },
                            onDelete = { onDeleteClick(session) },
                            onTogglePin = { onTogglePinClick?.invoke(session) },
                            onExport = { onExportClick?.invoke(session) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Spacer(modifier = Modifier.height(6.dp))

        // Footer Navigation Options
        Column(modifier = Modifier.fillMaxWidth()) {
            DrawerFooterItem(
                icon = Icons.Default.Settings,
                label = "Settings",
                onClick = onNavigateToSettings
            )
            DrawerFooterItem(
                icon = Icons.Default.FolderOpen,
                label = "Local Models (GGUF)",
                onClick = onNavigateToModels
            )
            DrawerFooterItem(
                icon = Icons.Default.Cloud,
                label = "AI Endpoints",
                onClick = onNavigateToEndpoints
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun ConversationItemRow(
    session: ConversationSession,
    isActive: Boolean,
    searchSnippet: String?,
    dot: DotProfile? = null,
    showDotBadge: Boolean = false,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isActive) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isActive) Icons.Default.ChatBubble else Icons.Default.ChatBubbleOutline,
            contentDescription = null,
            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Title and badges row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                // Dot badge (visible when viewing all dots)
                if (showDotBadge && dot != null) {
                    val dotColor = dot.parseThemeColor()
                    Text(
                        text = dot.name,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        color = dotColor,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(dotColor.copy(alpha = 0.15f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }

                // Target badge
                if (!session.targetId.isNullOrBlank()) {
                    val isRemote = session.targetId.startsWith("remote:")
                    Text(
                        text = if (isRemote) "REMOTE" else "LOCAL",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }

                // Language badge
                if (session.responseLanguage != ResponseLanguage.AUTO) {
                    Text(
                        text = session.responseLanguage.bcp47.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            // Preview Text or Matched Snippet
            val displayText = searchSnippet ?: session.previewText
            if (displayText.isNotBlank()) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = "${session.messages.size} msgs • ${formatDate(session.updatedAt)}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                maxLines = 1
            )
        }

        // Action Toolbar
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Pin Toggle
            IconButton(
                onClick = onTogglePin,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = if (session.pinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (session.pinned) "Unpin" else "Pin",
                    tint = if (session.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp)
                )
            }

            // Quick Export
            IconButton(
                onClick = onExport,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Export chat",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp)
                )
            }

            // Rename
            IconButton(
                onClick = onRename,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Rename chat",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp)
                )
            }

            // Delete
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete chat",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

private fun findMatchingSnippet(session: ConversationSession, query: String): String? {
    val q = query.trim()
    if (q.isBlank()) return null

    val matchingMsg = session.messages.find { it.content.contains(q, ignoreCase = true) } ?: return null
    val content = matchingMsg.content
    val index = content.indexOf(q, ignoreCase = true)
    if (index == -1) return null

    val start = (index - 20).coerceAtLeast(0)
    val end = (index + q.length + 30).coerceAtMost(content.length)
    val prefix = if (start > 0) "..." else ""
    val suffix = if (end < content.length) "..." else ""
    return prefix + content.substring(start, end).replace("\n", " ").trim() + suffix
}

@Composable
private fun DrawerFooterItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatDate(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val oneDay = 24 * 60 * 60 * 1000L

    return when {
        diff < 60 * 1000L -> "Just now"
        diff < 60 * 60 * 1000L -> "${diff / (60 * 1000L)}m ago"
        diff < oneDay -> "${diff / (60 * 60 * 1000L)}h ago"
        diff < 2 * oneDay -> "Yesterday"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}
