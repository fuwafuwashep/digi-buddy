package com.digibuddy.customer.ui.screens.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.digibuddy.customer.ui.components.DigiBuddyBottomNav
import com.digibuddy.customer.ui.components.DigiBuddyTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onHomeClick: () -> Unit,
    onMapClick: () -> Unit,
    onBookingsClick: () -> Unit,
    onProfileClick: () -> Unit,
    onChatRoomClick: (roomId: String, helperName: String) -> Unit = { _, _ -> }
) {
    Scaffold(
        topBar = {
            DigiBuddyTopBar(
                onNotificationsClick = {},
                onSettingsClick = {}
            )
        },
        bottomBar = {
            DigiBuddyBottomNav(currentRoute = "chat_list") { route ->
                when (route) {
                    "home"      -> onHomeClick()
                    "map"       -> onMapClick()
                    "chat_list" -> { /* already here */ }
                    "bookings"  -> onBookingsClick()
                    "profile"   -> onProfileClick()
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    Icons.Filled.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Messages",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Your conversations with digital helpers will appear here once you connect with one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))

                // ─────────────────────────────────────────────────────────────────
                // TODO: Replace this placeholder with the real chat list.
                //
                // Backend integration steps:
                //  1. Call GET /api/chat/rooms  →  returns List<ChatRoom>
                //  2. Each ChatRoom has: id, bookingId, booking (helper info), messages, updatedAt
                //  3. Render each room as a ListItem with:
                //       - Helper avatar (first letter fallback)
                //       - Helper name
                //       - Last message preview
                //       - Timestamp (updatedAt)
                //  4. On tap, call: onChatRoomClick(room.id, helperName)
                //
                // Example skeleton:
                //   LazyColumn {
                //     items(chatRooms, key = { it.id }) { room ->
                //       val helperName = room.booking?.helper?.name ?: "Helper"
                //       val lastMsg = room.messages.lastOrNull()?.content ?: ""
                //       ListItem(
                //         headlineContent = { Text(helperName) },
                //         supportingContent = { Text(lastMsg, maxLines = 1) },
                //         leadingContent = { AvatarCircle(helperName) },
                //         modifier = Modifier.clickable { onChatRoomClick(room.id, helperName) }
                //       )
                //     }
                //   }
                // ─────────────────────────────────────────────────────────────────

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("How it works", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Chat starts automatically when a helper accepts your booking request. " +
                            "Find a helper on the Map tab, view their profile, and tap \"Request Help\" to get started.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onMapClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Map, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Find a Helper")
                }
            }
        }
    }
}
