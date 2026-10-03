package com.prasbin.shadowmoney.data.connections

/**
 * Honest, research-backed availability for each target provider. Every string is a
 * factual finding about official interfaces — nothing here is a working integration
 * and nothing here is invented capability.
 *
 * Research basis (official sources, checked 2026-10-03):
 *  - Sanima: customer portals only (sajiloebanking.sanimabank.com, Sanima mobile app,
 *    onlineservices.sanimabank.com). No developer portal or account-data API published.
 *  - Global IME: customer app/web only (Global Smart Plus, com.swifttechnology.globalsmart
 *    by Swift Technology). No developer portal or account-data API published.
 *  - eSewa: official public developer docs exist (developer.esewa.com.np) but are
 *    explicitly merchant/partner payment integration (ePay, intent, token, status check).
 *    No consumer wallet balance or statement API is published.
 */
data class ProviderAvailability(
    val provider: Provider,
    val displayName: String,
    val officialInterface: String,
    val dataSourceType: String,
    val safeNextAction: String,
    val balanceRead: String,
    val transactionRead: String,
    val paymentInitiate: String,
    val authModel: String,
    val approvalRequired: String,
    val status: ConnectionStatus,
    val note: String,
    val capabilities: Set<ConnectionCapability> = emptySet()
) {
    /** Honest supported-capabilities line; never implies availability of a connection. */
    fun supportedCapabilitiesLabel(): String =
        if (capabilities.isEmpty()) {
            "Supported: none — no data interface available"
        } else {
            "Supported: ${capabilities.joinToString(", ") { it.name }}"
        }
}

object ProviderCatalog {

    const val NOT_AVAILABLE_CONSUMER_API =
        "Not available through official public consumer API"

    fun all(): List<ProviderAvailability> = listOf(sanima(), globalIme(), eSewa())

    fun forProvider(provider: Provider): ProviderAvailability? =
        all().firstOrNull { it.provider == provider }

    fun sanima() = ProviderAvailability(
        provider = Provider.SANIMA,
        displayName = "Sanima Sajilo eBanking",
        officialInterface = "Customer portals only (internet banking, mobile app). No developer API published.",
        dataSourceType = "No official consumer data source — customer portals only",
        safeNextAction = "Check your balance in the Sanima app or internet banking yourself; " +
            "no account-data API exists to connect here.",
        balanceRead = NOT_AVAILABLE_CONSUMER_API,
        transactionRead = NOT_AVAILABLE_CONSUMER_API,
        paymentInitiate = NOT_AVAILABLE_CONSUMER_API,
        authModel = "n/a — no third-party API published",
        approvalRequired = "Bank-level partnership and NRB-compliant arrangement required",
        status = ConnectionStatus.UNAVAILABLE,
        note = "NOT AVAILABLE THROUGH OFFICIAL PUBLIC CONSUMER API",
        capabilities = emptySet()
    )

    fun globalIme() = ProviderAvailability(
        provider = Provider.GLOBAL_IME,
        displayName = "Global IME Global Smart Plus",
        officialInterface = "Customer app and web banking only (Global Smart Plus). No developer API published.",
        dataSourceType = "No official consumer data source — customer app and web banking only",
        safeNextAction = "Check your balance in Global Smart Plus yourself; no account-data " +
            "API exists to connect here.",
        balanceRead = NOT_AVAILABLE_CONSUMER_API,
        transactionRead = NOT_AVAILABLE_CONSUMER_API,
        paymentInitiate = NOT_AVAILABLE_CONSUMER_API,
        authModel = "n/a — no third-party API published",
        approvalRequired = "Bank-level partnership required (bank or its app vendor)",
        status = ConnectionStatus.UNAVAILABLE,
        note = "NOT AVAILABLE THROUGH OFFICIAL PUBLIC CONSUMER API",
        capabilities = emptySet()
    )

    fun eSewa() = ProviderAvailability(
        provider = Provider.ESEWA,
        displayName = "eSewa",
        officialInterface = "Official public developer docs exist (developer.esewa.com.np) for merchants and partners.",
        dataSourceType = "MERCHANT API — NOT A PERSONAL WALLET SYNC INTERFACE",
        safeNextAction = "Merchant integration only; personal wallet balance sync is not " +
            "offered through official APIs.",
        balanceRead = NOT_AVAILABLE_CONSUMER_API,
        transactionRead = "Merchant payment-status only; no consumer wallet history API published",
        paymentInitiate = "Available to approved merchants (ePay / intent / token flows)",
        authModel = "Merchant credentials (merchant ID/secret, Basic or Bearer token)",
        approvalRequired = "eSewa merchant/partner onboarding required",
        status = ConnectionStatus.UNAVAILABLE,
        note = "OFFICIAL API EXISTS — MERCHANT PAYMENT CATEGORY; CONSUMER WALLET DATA SYNC NOT AVAILABLE",
        capabilities = setOf(ConnectionCapability.PAYMENT_INITIATE)
    )
}
