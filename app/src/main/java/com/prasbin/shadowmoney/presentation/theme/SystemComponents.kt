package com.prasbin.shadowmoney.presentation.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SystemCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    accentColor: Color = NeonCyan
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = CardColor,
        border = border(
            width = 1.dp,
            color = BorderColor,
            shape = RoundedCornerShape(12.dp)
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = DarkOnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.displayMedium,
                color = accentColor,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SystemPanel(
    title: String,
    modifier: Modifier = Modifier,
    children: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = DarkSurfaceVariant,
        border = border(
            width = 1.dp,
            color = BorderColor,
            shape = RoundedCornerShape(12.dp)
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = NeonCyan
            )
            Spacer(modifier = Modifier.height(12.dp))
            children()
        }
    }
}
