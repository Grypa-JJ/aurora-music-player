package com.aurora.player.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens

/**
 * Karta-skrót do domeny treści (Etap 22 do Etap 37: przeniesiona z prywatnej `QuickAccessCard`
 * w `LibraryScreen` na poziom design systemu, bo teraz jej używają Home/Odkrywaj/Szukaj, nie
 * tylko Biblioteka). `accentColor` domyślnie = `primary`, ale Home przekazuje kolor per domena
 * (Etap 37, DESIGN.md sekcja "kolor per domena") — reszta wyglądu bez zmian względem oryginału.
 */
@Composable
fun DomainShortcutCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    val tokens = LocalAuroraTokens.current

    Row(
        modifier = modifier
            .width(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(tokens.spacing.s + 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.padding(start = tokens.spacing.s)) {
            Text(
                text = title,
                style = AuroraTextStyles.Label,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = AuroraTextStyles.Caption,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
