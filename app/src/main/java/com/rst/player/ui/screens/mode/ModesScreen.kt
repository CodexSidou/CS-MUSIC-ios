package com.rst.player.ui.screens.mode

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rst.player.data.model.PlayerMode
import com.rst.player.ui.components.glassBorder
import com.rst.player.ui.components.neonShadowSoft
import com.rst.player.ui.core.LocalGraph
import com.rst.player.ui.theme.BrandAccent
import com.rst.player.ui.theme.BrandAccentBright
import kotlinx.coroutines.launch

private val CUSTOM_ICONS = listOf(
    "star", "bolt", "heart", "fire", "run", "zen", "bulb", "pool", "music"
)

@Composable
fun ModesScreen(
    onBack: () -> Unit,
    onOpenMode: (String) -> Unit
) {
    val graph = LocalGraph.current
    val modes by graph.modeRepository.modes.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<PlayerMode?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "RST MODES",
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${modes.size} modes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            items(modes, key = { it.id }) { mode ->
                ModeCard(
                    mode = mode,
                    onOpen = { onOpenMode(mode.id) },
                    onDelete = if (mode.isBuiltin) null else ({ deleting = mode })
                )
            }
            item {
                AddModeCard(onClick = { showAddDialog = true })
            }
        }
    }

    if (showAddDialog) {
        AddModeDialog(
            onConfirm = { name, iconKey ->
                scope.launch { graph.modeRepository.addCustomMode(name, iconKey) }
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    deleting?.let { mode ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Remove \"${mode.name}\"?") },
            text = { Text("The mode and its playlist will be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            mode.playlistId?.let { graph.playlistRepository.deletePlaylist(it) }
                            graph.modeRepository.removeMode(mode.id)
                        }
                        deleting = null
                    }
                ) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ModeCard(
    mode: PlayerMode,
    onOpen: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val (accent, accentBright) = modeAccent(mode.iconKey)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .neonShadowSoft(RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(accentBright, accent)))
                .glassBorder(RoundedCornerShape(16.dp), alpha = 0.35f),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = modeIcon(mode.iconKey),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (mode.isBuiltin) "${mode.name} mode" else mode.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (mode.playlistId != null) "Custom playlist" else "Auto mix",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = "Remove mode", tint = MaterialTheme.colorScheme.error)
            }
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun AddModeCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(BrandAccent.copy(alpha = 0.12f))
                .glassBorder(RoundedCornerShape(16.dp), alpha = 0.3f),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                tint = BrandAccentBright,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Add a new mode",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = BrandAccentBright
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Gym, sleep, study — your music, your rules",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            tint = BrandAccentBright
        )
    }
}

@Composable
private fun AddModeDialog(
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var iconKey by remember { mutableStateOf("star") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New mode") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Mode name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Icon",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CUSTOM_ICONS.forEach { key ->
                        val selected = key == iconKey
                        val (accent, accentBright) = modeAccent(key)
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .then(
                                    if (selected) Modifier.background(Brush.linearGradient(listOf(accentBright, accent)))
                                    else Modifier
                                )
                                .clickable { iconKey = key }
                                .glassBorder(CircleShape, alpha = if (selected) 0.5f else 0.25f),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = modeIcon(key),
                                contentDescription = key,
                                tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), iconKey) },
                enabled = name.isNotBlank()
            ) { Text("Create", color = BrandAccentBright) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
