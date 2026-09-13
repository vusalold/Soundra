package com.vm.soundra.ui.downloads.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vm.soundra.R

@Composable
fun RenameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var error by remember { mutableStateOf<String?>(null) }
    val errorEmpty = stringResource(R.string.dialog_rename_error_empty)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_rename_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = if (it.isBlank()) errorEmpty else null
                    },
                    label = { Text(stringResource(R.string.dialog_rename_label)) },
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name)
                    } else {
                        error = errorEmpty
                    }
                }
            ) {
                Text(stringResource(R.string.btn_change))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}
