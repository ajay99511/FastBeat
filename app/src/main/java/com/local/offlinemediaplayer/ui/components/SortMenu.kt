package com.local.offlinemediaplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.offlinemediaplayer.viewmodel.SortState
import com.local.offlinemediaplayer.viewmodel.SortableField

/**
 * Shared sort dropdown with direction toggling. Each field appears once;
 * tapping the active field flips ascending/descending (shown by an up/down
 * arrow), tapping another field selects it with its default direction.
 */
@Composable
fun <T> SortDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    fields: List<T>,
    sortState: SortState<T>,
    onSortChange: (SortState<T>) -> Unit
) where T : Enum<T>, T : SortableField {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
    ) {
        fields.forEach { field ->
            val isSelected = field == sortState.field
            DropdownMenuItem(
                text = {
                    Text(
                        text = field.label,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                trailingIcon = {
                    if (isSelected) {
                        Icon(
                            imageVector = if (sortState.ascending) Icons.Default.ArrowUpward
                            else Icons.Default.ArrowDownward,
                            contentDescription = if (sortState.ascending) "Sorted ascending"
                            else "Sorted descending",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                onClick = {
                    onSortChange(sortState.select(field))
                    onDismissRequest()
                }
            )
        }
    }
}
