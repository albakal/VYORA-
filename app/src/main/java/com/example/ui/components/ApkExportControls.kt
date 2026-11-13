package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.media.ApkExportHelper
import com.example.ui.theme.LocalVyoraResources
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.vString
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ApkExportControls(
    onStatusMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    saveButtonTag: String = "save_apk_button",
    shareButtonTag: String = "share_apk_button"
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appRes = LocalVyoraResources.current
    val exportFailedText = vString(R.string.apk_export_failed)
    var isExporting by remember { mutableStateOf(false) }

    val saveApkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ApkExportHelper.APK_MIME_TYPE)
    ) { destUri ->
        if (destUri != null) {
            isExporting = true
            scope.launch {
                ApkExportHelper.copyInstalledApkToUri(context, destUri)
                    .onSuccess { bytes ->
                        val mb = String.format(Locale.US, "%.1f", bytes / (1024.0 * 1024.0))
                        onStatusMessage(appRes.getString(R.string.apk_saved_success, mb))
                    }
                    .onFailure { err ->
                        onStatusMessage(err.localizedMessage ?: exportFailedText)
                    }
                isExporting = false
            }
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            enabled = !isExporting,
            onClick = { saveApkLauncher.launch(ApkExportHelper.defaultApkFileName()) },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag(saveButtonTag)
        ) {
            if (isExporting) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = Icons.Filled.Android,
                    contentDescription = vString(R.string.save_apk_button),
                    modifier = Modifier.size(18.dp),
                    tint = NeonCyan
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = vString(R.string.save_apk_button),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        OutlinedButton(
            enabled = !isExporting,
            onClick = {
                isExporting = true
                scope.launch {
                    ApkExportHelper.createShareApkIntent(context)
                        .onSuccess { chooser -> context.startActivity(chooser) }
                        .onFailure { err ->
                            onStatusMessage(err.localizedMessage ?: exportFailedText)
                        }
                    isExporting = false
                }
            },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag(shareButtonTag)
        ) {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = vString(R.string.share_apk_button),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = vString(R.string.share_apk_button),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
