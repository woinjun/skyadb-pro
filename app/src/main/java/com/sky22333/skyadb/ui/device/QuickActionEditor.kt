package com.sky22333.skyadb.ui.device

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sky22333.skyadb.R
import com.sky22333.skyadb.data.QuickActionOrder

@Composable
internal fun QuickActionEditor(
    order: List<String>,
    labels: Map<String, String>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(QuickActionOrder.normalize(order)) }
    var dragging by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_edit)) },
        text = {
            Column {
                Text(stringResource(R.string.quick_drag_hint))
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    draft.forEachIndexed { index, id ->
                        key(id) {
                            Surface(color = if (dragging == id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) {
                                Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.DragHandle, stringResource(R.string.quick_drag),
                                        Modifier.size(48.dp).pointerInput(id) {
                                            var distance = 0f
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { dragging = id; distance = 0f },
                                                onDragEnd = { dragging = null },
                                                onDragCancel = { dragging = null },
                                            ) { change, amount ->
                                                change.consume()
                                                distance += amount.y
                                                val step = 56.dp.toPx()
                                                while (kotlin.math.abs(distance) >= step) {
                                                    val offset = if (distance > 0) 1 else -1
                                                    draft = QuickActionOrder.move(draft, id, offset)
                                                    distance -= offset * step
                                                }
                                            }
                                        },
                                    )
                                    Text(labels[id].orEmpty(), Modifier.weight(1f), maxLines = 2)
                                    IconButton(onClick = { draft = QuickActionOrder.move(draft, id, -1) }, enabled = index > 0) {
                                        Icon(Icons.Outlined.ArrowUpward, stringResource(R.string.quick_up))
                                    }
                                    IconButton(onClick = { draft = QuickActionOrder.move(draft, id, 1) }, enabled = index < draft.lastIndex) {
                                        Icon(Icons.Outlined.ArrowDownward, stringResource(R.string.quick_down))
                                    }
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = { draft = QuickActionOrder.defaults }) { Text(stringResource(R.string.quick_reset)) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft) }) { Text(stringResource(R.string.quick_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.quick_cancel)) } },
    )
}
