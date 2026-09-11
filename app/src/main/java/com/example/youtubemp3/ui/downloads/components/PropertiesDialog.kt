package com.vusal.soundra.ui.downloads.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vusal.soundra.R
import com.vusal.soundra.logic.FileDetails

@Composable
fun PropertiesDialog(
    details: FileDetails,
    onDismiss: () -> Unit
) {
    val isUnavailable = details.fileName == "File Missing" || (details.duration == "Unknown" && details.fileSize == "Unknown")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isUnavailable) stringResource(R.string.dialog_properties_unavailable_title) else stringResource(R.string.dialog_properties_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isUnavailable) {
                    Text(
                        text = stringResource(R.string.dialog_properties_unavailable_msg),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    PropertyItem(stringResource(R.string.prop_title), details.title)
                    PropertyItem(stringResource(R.string.prop_file_name), details.fileName)
                    PropertyItem(stringResource(R.string.prop_duration), details.duration)
                    PropertyItem(stringResource(R.string.prop_bitrate), details.bitrate)
                    PropertyItem(stringResource(R.string.prop_sample_rate), details.sampleRate)
                    PropertyItem(stringResource(R.string.prop_channels), details.channels)
                    PropertyItem(stringResource(R.string.prop_format), details.codec)
                    PropertyItem(stringResource(R.string.prop_size), details.fileSize)
                    PropertyItem(stringResource(R.string.prop_created), details.createdDate)
                    PropertyItem(stringResource(R.string.prop_modified), details.modifiedDate)
                    PropertyItem(stringResource(R.string.prop_path), details.absolutePath)
                    PropertyItem(stringResource(R.string.prop_uri), details.contentUri)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.btn_close))
            }
        }
    )
}

@Composable
private fun PropertyItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp
        )
    }
}
