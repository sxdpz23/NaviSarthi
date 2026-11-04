package com.example.navisarthi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.navisarthi.ui.theme.VehicleModel

@Composable
internal fun VehiclePicker(
    current: VehicleModel?,
    onSelect: (VehicleModel) -> Unit,
    onDismiss: (() -> Unit)? = null,
) {
    var choice by remember(current) { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = { onDismiss?.invoke() },
        title = { Text("Choose your vehicle") },
        text = {
            Column(Modifier.selectableGroup()) {
                ControlHint("Your vehicle sets the app accent. You can change it in Settings.")
                Spacer(Modifier.height(10.dp))
                VehicleModel.entries.forEach { vehicle ->
                    Row(
                        Modifier.fillMaxWidth().selectable(
                            selected = choice == vehicle,
                            onClick = { choice = vehicle },
                            role = Role.RadioButton,
                        ).heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice == vehicle, onClick = null)
                        Spacer(Modifier.width(10.dp))
                        Text(vehicle.label, Modifier.weight(1f))
                        Box(Modifier.size(14.dp).background(vehicle.accent, CircleShape))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = choice != null, onClick = { choice?.let(onSelect) }) {
                Text(if (current == null) "Continue" else "Save")
            }
        },
        dismissButton = {
            if (onDismiss != null) TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
