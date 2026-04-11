package com.digibuddy.customer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.digibuddy.core.model.HelperProfile
import com.digibuddy.customer.ui.theme.Teal600

@Composable
fun HelperCard(
    helper: HelperProfile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (helper.avatar != null) {
                    AsyncImage(
                        model = helper.avatar,
                        contentDescription = "${helper.name} avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = Teal600,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Name + availability dot
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = helper.name ?: helper.user?.name ?: "Helper",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (helper.isAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                    )
                }

                // Stars
                StarRatingDisplay(rating = helper.avgRating, ratingCount = helper.ratingCount)

                Spacer(Modifier.height(4.dp))

                // Bio
                if (!helper.bio.isNullOrBlank()) {
                    Text(
                        text = helper.bio!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                }

                // Skills chips
                if (helper.skills.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        helper.skills.take(3).forEach { skill ->
                            SuggestionChip(
                                onClick = {},
                                label = { Text(skill, fontSize = 11.sp) },
                                modifier = Modifier.height(24.dp)
                            )
                        }
                        if (helper.skills.size > 3) {
                            SuggestionChip(
                                onClick = {},
                                label = { Text("+${helper.skills.size - 3}", fontSize = 11.sp) },
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                // Distance + subscription badge
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val distance = helper.distance
                    if (distance != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (distance < 1.0)
                                    "${"%.0f".format(distance * 1000)} m away"
                                else
                                    "${"%.1f".format(distance)} km away",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                "Free with Pro",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        modifier = Modifier.height(24.dp)
                    )
                }
            }
        }
    }
}
