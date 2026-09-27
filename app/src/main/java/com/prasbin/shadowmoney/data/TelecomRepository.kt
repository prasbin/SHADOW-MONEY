package com.prasbin.shadowmoney.data

import com.prasbin.shadowmoney.data.model.SIM_STATUS_ARCHIVED
import com.prasbin.shadowmoney.data.model.SIM_STATUS_ACTIVE
import com.prasbin.shadowmoney.data.model.TelecomPackage
import com.prasbin.shadowmoney.data.model.TelecomSim
import com.prasbin.shadowmoney.data.model.TelecomSubscription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class TelecomRepository(
    private val telecomDao: TelecomDao,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    fun observeSims(): Flow<List<TelecomSim>> = telecomDao.observeSims()

    fun observePackages(): Flow<List<TelecomPackage>> = telecomDao.observePackages()

    fun observeSubscriptions(): Flow<List<TelecomSubscription>> = telecomDao.observeSubscriptions()

    fun observeChanges(): Flow<Unit> = combine(
        telecomDao.observeSims(),
        telecomDao.observePackages(),
        telecomDao.observeSubscriptionCount()
    ) { _, _, _ -> Unit }

    suspend fun loadSummary(): TelecomSummary {
        val sims = telecomDao.observeSims().first()
        val packages = telecomDao.observePackages().first()
        val subscriptions = telecomDao.observeSubscriptions().first()
        return loadSummaryFrom(sims, packages, subscriptions)
    }

    suspend fun loadSummaryFrom(
        sims: List<TelecomSim>,
        packages: List<TelecomPackage>,
        subscriptions: List<TelecomSubscription>
    ): TelecomSummary {
        val activeSims = sims.filter { it.status == SIM_STATUS_ACTIVE }
        val activeSubscriptions = subscriptions.filter { it.isActive }
        val expectedMonthly = activeSubscriptions.sumOf { subscription ->
            val pkg = packages.firstOrNull { it.id == subscription.packageId }
            TelecomMath.subscriptionMonthlyCost(subscription, pkg)
        }
        val upcoming = TelecomRenewals.upcoming(activeSubscriptions, sims, packages, clock())
        return TelecomSummary(
            activeSimCount = activeSims.size,
            activeSubscriptionCount = activeSubscriptions.size,
            expectedMonthlyCostMinor = expectedMonthly,
            nextRenewal = upcoming.firstOrNull(),
            upcomingRenewals = upcoming
        )
    }

    suspend fun createSim(label: String, carrier: String, phoneNumber: String, notes: String): Long {
        validateLabel(label)
        val now = clock()
        return telecomDao.insertSim(
            TelecomSim(
                label = label,
                carrier = carrier,
                phoneNumber = phoneNumber,
                notes = notes,
                createdTimestamp = now,
                updatedTimestamp = now
            )
        )
    }

    suspend fun updateSim(sim: TelecomSim, label: String, carrier: String, phoneNumber: String, notes: String) {
        validateLabel(label)
        telecomDao.updateSim(
            sim.copy(
                label = label,
                carrier = carrier,
                phoneNumber = phoneNumber,
                notes = notes,
                updatedTimestamp = clock()
            )
        )
    }

    suspend fun archiveSim(sim: TelecomSim) {
        telecomDao.updateSimStatus(sim.id, SIM_STATUS_ARCHIVED, clock())
    }

    suspend fun activateSim(sim: TelecomSim) {
        telecomDao.updateSimStatus(sim.id, SIM_STATUS_ACTIVE, clock())
    }

    suspend fun createPackage(
        name: String,
        carrier: String,
        category: String,
        priceMinor: Long,
        period: Int,
        notes: String
    ): Long {
        validateLabel(name)
        validatePrice(priceMinor)
        val now = clock()
        return telecomDao.insertPackage(
            TelecomPackage(
                name = name,
                carrier = carrier,
                category = category,
                priceMinor = priceMinor,
                period = period,
                notes = notes,
                createdTimestamp = now,
                updatedTimestamp = now
            )
        )
    }

    suspend fun updatePackage(
        pkg: TelecomPackage,
        name: String,
        carrier: String,
        category: String,
        priceMinor: Long,
        period: Int,
        notes: String
    ) {
        validateLabel(name)
        validatePrice(priceMinor)
        telecomDao.updatePackage(
            pkg.copy(
                name = name,
                carrier = carrier,
                category = category,
                priceMinor = priceMinor,
                period = period,
                notes = notes,
                updatedTimestamp = clock()
            )
        )
    }

    suspend fun archivePackage(pkg: TelecomPackage) {
        telecomDao.updatePackageActive(pkg.id, false, clock())
    }

    suspend fun activatePackage(pkg: TelecomPackage) {
        telecomDao.updatePackageActive(pkg.id, true, clock())
    }

    suspend fun createSubscription(
        simId: Long,
        packageId: Long,
        startTimestamp: Long,
        renewalTimestamp: Long,
        monthlyCostMinor: Long
    ): Long {
        if (telecomDao.getSimById(simId) == null) {
            throw IllegalArgumentException("SIM does not exist")
        }
        if (telecomDao.getPackageById(packageId) == null) {
            throw IllegalArgumentException("Package does not exist")
        }
        if (monthlyCostMinor < 0L) {
            throw IllegalArgumentException("Monthly cost cannot be negative")
        }
        val now = clock()
        return telecomDao.insertSubscription(
            TelecomSubscription(
                simId = simId,
                packageId = packageId,
                startTimestamp = startTimestamp,
                renewalTimestamp = renewalTimestamp,
                monthlyCostMinor = monthlyCostMinor,
                createdTimestamp = now,
                updatedTimestamp = now
            )
        )
    }

    suspend fun updateSubscription(
        subscription: TelecomSubscription,
        simId: Long,
        packageId: Long,
        startTimestamp: Long,
        renewalTimestamp: Long,
        monthlyCostMinor: Long
    ) {
        if (telecomDao.getSimById(simId) == null) {
            throw IllegalArgumentException("SIM does not exist")
        }
        if (telecomDao.getPackageById(packageId) == null) {
            throw IllegalArgumentException("Package does not exist")
        }
        if (monthlyCostMinor < 0L) {
            throw IllegalArgumentException("Monthly cost cannot be negative")
        }
        telecomDao.updateSubscription(
            subscription.copy(
                simId = simId,
                packageId = packageId,
                startTimestamp = startTimestamp,
                renewalTimestamp = renewalTimestamp,
                monthlyCostMinor = monthlyCostMinor,
                updatedTimestamp = clock()
            )
        )
    }

    suspend fun deactivateSubscription(subscription: TelecomSubscription) {
        telecomDao.updateSubscriptionActive(subscription.id, false, clock())
    }

    suspend fun activateSubscription(subscription: TelecomSubscription) {
        telecomDao.updateSubscriptionActive(subscription.id, true, clock())
    }

    suspend fun deleteSubscription(subscription: TelecomSubscription) {
        telecomDao.deleteSubscription(subscription)
    }

    private fun validateLabel(label: String) {
        if (label.isBlank()) {
            throw IllegalArgumentException("Label is required")
        }
    }

    private fun validatePrice(priceMinor: Long) {
        if (priceMinor <= 0L) {
            throw IllegalArgumentException("Price must be positive")
        }
    }
}
