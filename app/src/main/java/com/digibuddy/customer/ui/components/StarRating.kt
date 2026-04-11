package com.digibuddy.customer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digibuddy.customer.ui.theme.Grey600
import com.digibuddy.customer.ui.theme.StarEmpty
import com.digibuddy.customer.ui.theme.StarYellow

/** Display-only star rating (supports half-stars). */
@Composable
fun StarRatingDisplay(
    rating: Double,
    ratingCount: Int = 0,
    maxStars: Int = 5,
    starSize: Dp = 18.dp,
    modifier: Modifier = Modifier
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        repeat(maxStars) { index ->
            val starValue = index + 1
            val icon = when {
                rating >= starValue          -> Icons.Filled.Star
                rating >= starValue - 0.5   -> Icons.Filled.StarHalf
                else                        -> Icons.Filled.StarBorder
            }
            val tint = if (rating >= starValue - 0.5) StarYellow else StarEmpty
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(starSize)
            )
        }
        if (ratingCount > 0) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = "(${ratingCount})",
                fontSize = 12.sp,
                color = Grey600
            )
        }
    }
}

/** Interactive star rating picker. */
@Composable
fun StarRatingInput(
    currentRating: Int,
    onRatingChange: (Int) -> Unit,
    maxStars: Int = 5,
    starSize: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        repeat(maxStars) { index ->
            val starValue = index + 1
            Icon(
                imageVector = if (currentRating >= starValue) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = "$starValue stars",
                tint = if (currentRating >= starValue) StarYellow else StarEmpty,
                modifier = Modifier
                    .size(starSize)
                    .clickable { onRatingChange(starValue) }
            )
        }
    }
}
