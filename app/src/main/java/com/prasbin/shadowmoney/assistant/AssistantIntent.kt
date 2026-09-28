package com.prasbin.shadowmoney.assistant

/**
 * Bounded intent set for the deterministic local financial assistant.
 * There is intentionally no "general question" intent — anything not
 * matched here is answered with an honest unsupported/ambiguous response.
 */
enum class AssistantIntent {
    BALANCE,
    INCOME,
    OUTFLOW,
    BUDGET,
    GOALS,
    WORK,
    TELECOM,
    OPPORTUNITIES,
    TRANSACTIONS,
    IMPORT,
    HELP,
    SECRET_TARGET_REFUSAL,
    AMBIGUOUS,
    CLARIFY_PERIOD,
    UNSUPPORTED
}

/**
 * Bounded deterministic time periods. All ranges are computed in the
 * project's established Asia/Kathmandu timezone (see [AssistantTime]).
 */
enum class AssistantPeriod {
    TODAY,
    YESTERDAY,
    THIS_WEEK,
    LAST_WEEK,
    THIS_MONTH,
    LAST_MONTH,
    RECENT
}

/** Intents that answer from stored financial records. */
val AssistantIntent.requiresData: Boolean
    get() = when (this) {
        AssistantIntent.HELP,
        AssistantIntent.SECRET_TARGET_REFUSAL,
        AssistantIntent.AMBIGUOUS,
        AssistantIntent.CLARIFY_PERIOD,
        AssistantIntent.UNSUPPORTED,
        AssistantIntent.IMPORT -> false
        else -> true
    }

/** Deterministic default period when the user does not specify one. */
val AssistantIntent.defaultPeriod: AssistantPeriod?
    get() = when (this) {
        AssistantIntent.INCOME,
        AssistantIntent.OUTFLOW,
        AssistantIntent.BUDGET -> AssistantPeriod.THIS_MONTH
        AssistantIntent.TRANSACTIONS -> AssistantPeriod.RECENT
        else -> null
    }

/** Period used to answer: explicit period, else the intent's default. */
fun ClassifiedQuestion.resolvedPeriod(): AssistantPeriod? =
    period ?: intent.defaultPeriod

/**
 * Result of deterministic intent classification.
 *
 * @property clarification non-null when the response should be a plain
 * guidance message instead of a data-backed answer (period problems).
 */
data class ClassifiedQuestion(
    val intent: AssistantIntent,
    val period: AssistantPeriod? = null,
    val includeArchived: Boolean = false,
    val clarification: String? = null,
    val normalizedInput: String = ""
)
