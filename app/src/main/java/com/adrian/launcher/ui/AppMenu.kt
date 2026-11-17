package com.adrian.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adrian.launcher.data.AppEntry

class AppMenuActions(
    val onTogglePin: () -> Unit,
    val onRename: () -> Unit,
    val onToggleHidden: () -> Unit,
    val onInfo: () -> Unit,
    val onUninstall: () -> Unit,
    val onDismiss: () -> Unit,
)

/** Long-press menu for one app, shown as a centred panel over a scrim. */
@Composable
fun AppMenu(
    app: AppEntry,
    isPinned: Boolean,
    pinnedFull: Boolean,
    isHidden: Boolean,
    accent: Color,
    actions: AppMenuActions,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = actions.onDismiss,
            ),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 40.dp)
                .fillMaxWidth()
                .background(Panel, RoundedCornerShape(18.dp))
                .border(1.dp, Line, RoundedCornerShape(18.dp))
                // Swallow taps on the panel itself so they do not dismiss it.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(vertical = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                AppIcon(app)
                Spacer(Modifier.width(14.dp))
                Text(app.label, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Line))

            when {
                isPinned -> MenuItem("Odepnij z ekranu głównego", Ink, actions.onTogglePin)
                pinnedFull -> MenuItem("Ekran główny pełny ($MAX_PINNED_LABEL)", Faint, null)
                else -> MenuItem("Przypnij do ekranu głównego", accent, actions.onTogglePin)
            }
            MenuItem("Zmień nazwę", Ink, actions.onRename)
            MenuItem(if (isHidden) "Pokaż na liście" else "Ukryj na liście", Ink, actions.onToggleHidden)
            MenuItem("Informacje o aplikacji", Ink, actions.onInfo)
            MenuItem("Odinstaluj", Color(0xFFFF9B8A), actions.onUninstall)
        }
    }
}

private const val MAX_PINNED_LABEL = "6 z 6"

@Composable
private fun MenuItem(label: String, color: Color, onClick: (() -> Unit)?) {
    Text(
        text = label,
        color = color,
        fontSize = 15.sp,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 13.dp),
    )
}

@Composable
fun RenameDialog(
    app: AppEntry,
    onConfirm: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(app.key) { mutableStateOf(app.label) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zmień nazwę") },
        text = {
            Column {
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
                if (app.label != app.originalLabel) {
                    Text(
                        "Oryginalna nazwa: ${app.originalLabel}",
                        color = Quiet,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text("Zapisz") }
        },
        dismissButton = {
            Row {
                if (app.label != app.originalLabel) TextButton(onClick = onReset) { Text("Przywróć") }
                TextButton(onClick = onDismiss) { Text("Anuluj") }
            }
        },
    )
}
