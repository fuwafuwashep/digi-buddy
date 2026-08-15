package com.digibuddy.shared.contracts

import kotlinx.serialization.Serializable

@Serializable
data class StaffHelperApplicationSummaryResponse(
    val userId: String,
    val applicationId: String,
    val displayName: String?,
    val status: String,
    val submittedAt: String?,
)

@Serializable
data class StaffHelperApplicationListResponse(
    val items: List<StaffHelperApplicationSummaryResponse>,
)
