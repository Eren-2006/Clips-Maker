package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.core.SettingsDataStore
import com.example.ui.ServerTestState
import com.example.ui.theme.CoralError
import com.example.ui.theme.CoralErrorContainer
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.KineticYellow
import com.example.ui.theme.StudioHairline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextSecondary

@Composable
fun ServerSettingsDialog(
    currentUrl: String,
    testState: ServerTestState,
    onTestConnection: (String) -> Unit,
    onSaveUrl: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draftUrl by remember(currentUrl) { mutableStateOf(currentUrl) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("server_settings_dialog"),
            shape = MaterialTheme.shapes.large,
            color = StudioSurface,
            border = BorderStroke(1.dp, StudioHairline),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.settings_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )

                OutlinedTextField(
                    value = draftUrl,
                    onValueChange = { draftUrl = it },
                    label = { Text(stringResource(R.string.settings_server_url_label)) },
                    placeholder = { Text(SettingsDataStore.DEFAULT_BACKEND_URL) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("server_url_input"),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KineticYellow,
                        unfocusedBorderColor = StudioHairline,
                        focusedContainerColor = StudioSurfaceElevated,
                        unfocusedContainerColor = StudioSurfaceElevated,
                    ),
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = StudioSurfaceElevated,
                    border = BorderStroke(1.dp, StudioHairline),
                ) {
                    Text(
                        text = "Run locally in backend/:\nuvicorn app.main:app --host 0.0.0.0 --port 8000",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = TextSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    )
                }

                when (testState) {
                    ServerTestState.Idle -> Unit
                    ServerTestState.Checking -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = KineticYellow,
                            )
                            Text(
                                text = "Checking backend health...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                        }
                    }
                    is ServerTestState.Connected -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = EmeraldContainer,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = EmeraldSuccess,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = testState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = EmeraldSuccess,
                                )
                            }
                        }
                    }
                    is ServerTestState.Failed -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = CoralErrorContainer,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = CoralError,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = testState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CoralError,
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { onTestConnection(draftUrl) },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("test_server_button"),
                        border = BorderStroke(1.dp, StudioHairline),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.settings_test_connection),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Button(
                        onClick = {
                            onSaveUrl(draftUrl)
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("save_server_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KineticYellow,
                            contentColor = Color(0xFF0A0B0E),
                        ),
                    ) {
                        Text(stringResource(R.string.settings_save))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { draftUrl = SettingsDataStore.DEFAULT_BACKEND_URL },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_reset_default),
                            color = TextSecondary,
                        )
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Text("Close", color = TextSecondary)
                    }
                }
            }
        }
    }
}
