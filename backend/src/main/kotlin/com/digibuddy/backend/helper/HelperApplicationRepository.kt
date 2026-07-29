package com.digibuddy.backend.helper

import com.digibuddy.shared.contracts.HelperAccountStatus
import com.digibuddy.shared.contracts.HelperOnboardingStep
import java.util.UUID

interface HelperApplicationRepository {
    fun findByUser(userId: UUID): HelperApplicationRecord?

    fun create(application: HelperApplicationRecord): HelperApplicationRecord

    fun saveStep(application: HelperApplicationRecord, step: HelperStepRecord): HelperApplicationRecord

    fun updateStatus(application: HelperApplicationRecord, event: HelperApprovalEventRecord): HelperApplicationRecord

    fun approveAndGrantRole(
        application: HelperApplicationRecord,
        event: HelperApprovalEventRecord,
    ): HelperApplicationRecord = updateStatus(application, event)

    fun requiredChanges(applicationId: UUID): List<HelperRequiredChangeRecord>

    fun replaceRequiredChanges(applicationId: UUID, changes: List<HelperRequiredChangeRecord>)

    fun approvalEvents(applicationId: UUID): List<HelperApprovalEventRecord>
}

class InMemoryHelperApplicationRepository : HelperApplicationRepository {
    private val applications = linkedMapOf<UUID, HelperApplicationRecord>()
    private val userApplications = linkedMapOf<UUID, UUID>()
    private val changes = linkedMapOf<UUID, MutableList<HelperRequiredChangeRecord>>()
    private val events = linkedMapOf<UUID, MutableList<HelperApprovalEventRecord>>()

    @Synchronized
    override fun findByUser(userId: UUID): HelperApplicationRecord? = userApplications[userId]?.let(applications::get)

    @Synchronized
    override fun create(application: HelperApplicationRecord): HelperApplicationRecord {
        userApplications[application.userId]?.let { return applications.getValue(it) }
        applications[application.id] = application
        userApplications[application.userId] = application.id
        return application
    }

    @Synchronized
    override fun saveStep(application: HelperApplicationRecord, step: HelperStepRecord): HelperApplicationRecord {
        check(applications[application.id]?.version == application.version - 1 || application.version == 1)
        applications[application.id] = application
        return application
    }

    @Synchronized
    override fun updateStatus(
        application: HelperApplicationRecord,
        event: HelperApprovalEventRecord,
    ): HelperApplicationRecord {
        applications[application.id] = application
        events.getOrPut(application.id) { mutableListOf() } += event
        return application
    }

    @Synchronized
    override fun requiredChanges(applicationId: UUID): List<HelperRequiredChangeRecord> =
        changes[applicationId].orEmpty().filter { it.resolvedAt == null }

    @Synchronized
    override fun replaceRequiredChanges(applicationId: UUID, changes: List<HelperRequiredChangeRecord>) {
        this.changes[applicationId] = changes.toMutableList()
    }

    @Synchronized
    override fun approvalEvents(applicationId: UUID): List<HelperApprovalEventRecord> =
        events[applicationId].orEmpty().toList()
}

internal val ACTIVE_WORK_STATUSES = setOf(HelperAccountStatus.APPROVED)

internal val REQUIRED_HELPER_STEPS = setOf(
    HelperOnboardingStep.LEGAL_NAME,
    HelperOnboardingStep.PUBLIC_PROFILE,
    HelperOnboardingStep.HOME_AND_SERVICE_MODE,
    HelperOnboardingStep.SERVICE_AREA,
    HelperOnboardingStep.SKILLS,
    HelperOnboardingStep.SERVICES,
    HelperOnboardingStep.EXPERIENCE,
    HelperOnboardingStep.LANGUAGES,
    HelperOnboardingStep.PRICING,
    HelperOnboardingStep.AVAILABILITY,
    HelperOnboardingStep.TERMS_AND_POLICIES,
    HelperOnboardingStep.PAYOUT_ONBOARDING,
)
