package com.digibuddy.shared.helper.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.digibuddy.shared.contracts.StaffHelperApplicationSummaryResponse
import com.digibuddy.shared.designsystem.DigibuddyCard
import com.digibuddy.shared.designsystem.DigibuddyColors
import com.digibuddy.shared.designsystem.FriendlyEmptyState

@Composable
fun StaffApprovalPage(
    coordinator:
    StaffApprovalCoordinator,
    onSignOut: () -> Unit,
) {
    val state by
    coordinator.state.collectAsState()

    var confirmApplication by remember {
        mutableStateOf<
            StaffHelperApplicationSummaryResponse?
            >(null)
    }

    LaunchedEffect(coordinator) {
        coordinator.load()
    }

    Column(
        modifier =
            Modifier.fillMaxSize(),
    ) {
        Surface(
            color = DigibuddyColors.Navy,
            contentColor = Color.White,
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 20.dp,
                            vertical = 18.dp,
                        ),
                horizontalArrangement =
                    Arrangement
                        .SpaceBetween,
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "DIGIBUDDY",
                        color =
                            DigibuddyColors.Gold,
                        style =
                            MaterialTheme
                                .typography
                                .labelMedium,
                    )

                    Text(
                        "Admin",
                        style =
                            MaterialTheme
                                .typography
                                .headlineSmall,
                        fontWeight =
                            FontWeight.Bold,
                    )
                }

                TextButton(
                    onClick = onSignOut,
                ) {
                    Text(
                        "Sign out",
                        color = Color.White,
                    )
                }
            }
        }

        state.error?.let {
            Text(
                text = it,
                color =
                    MaterialTheme
                        .colorScheme.error,
                modifier =
                    Modifier.padding(16.dp),
            )
        }

        state.message?.let {
            Text(
                text = it,
                color =
                    DigibuddyColors.Success,
                modifier =
                    Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 8.dp,
                    ),
            )
        }

        LazyColumn(
            modifier =
                Modifier.fillMaxSize(),
            contentPadding =
                androidx.compose.foundation
                    .layout.PaddingValues(
                        20.dp,
                    ),
            verticalArrangement =
                Arrangement.spacedBy(
                    14.dp,
                ),
        ) {
            item {
                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement
                            .SpaceBetween,
                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            "Pending helper applications",
                            style =
                                MaterialTheme
                                    .typography
                                    .titleLarge,
                            fontWeight =
                                FontWeight.Bold,
                        )

                        Text(
                            "${state.applications.size} waiting for review",
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant,
                        )
                    }

                    OutlinedButton(
                        onClick =
                            coordinator::refresh,
                        enabled =
                            !state.loading,
                    ) {
                        Text("Refresh")
                    }
                }
            }

            if (
                state.loading &&
                state.applications.isEmpty()
            ) {
                item {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (
                state.applications.isEmpty()
            ) {
                item {
                    FriendlyEmptyState(
                        "No applications waiting",
                        "Submitted helper applications will appear here.",
                        "A",
                    )
                }
            } else {
                items(
                    state.applications,
                    key = {
                        it.applicationId
                    },
                ) { application ->
                    DigibuddyCard {
                        Text(
                            application.displayName
                                ?: "Helper applicant",
                            style =
                                MaterialTheme
                                    .typography
                                    .titleLarge,
                            fontWeight =
                                FontWeight.Bold,
                        )

                        Text(
                            "Status: ${
                                application.status
                                    .replace(
                                        "_",
                                        " ",
                                    )
                            }",
                        )

                        application.submittedAt
                            ?.let {
                                Text(
                                    "Submitted: $it",
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .onSurfaceVariant,
                                )
                            }

                        Button(
                            onClick = {
                                confirmApplication =
                                    application
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth(),
                            enabled =
                                state
                                    .approvingUserId !=
                                    application.userId,
                        ) {
                            if (
                                state
                                    .approvingUserId ==
                                application.userId
                            ) {
                                Text("Approving...")
                            } else {
                                Text("Approve helper")
                            }
                        }
                    }
                }
            }
        }
    }

    confirmApplication?.let {
            application ->

        AlertDialog(
            onDismissRequest = {
                confirmApplication = null
            },
            title = {
                Text("Approve helper?")
            },
            text = {
                Text(
                    "Approve ${
                        application.displayName
                            ?: "this applicant"
                    } to become a DigiBuddy helper?",
                )
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmApplication =
                            null
                    },
                ) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coordinator.approve(
                            application.userId,
                        )
                        confirmApplication =
                            null
                    },
                ) {
                    Text("Approve")
                }
            },
        )
    }
}
