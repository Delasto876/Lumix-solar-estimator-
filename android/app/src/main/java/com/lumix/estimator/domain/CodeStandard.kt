package com.lumix.estimator.domain

import kotlinx.serialization.Serializable

/**
 * A82 (spec Phase 19 — "ELECTRICAL CODE / NEC / JS 316", "create an electrical-code lookup
 * architecture"): a real electrical standard the installer/administrator has confirmed is on
 * file (NEC, JS 316, or any other applicable code) — never fabricated content, only metadata
 * describing a real document the administrator attests exists and has entered themselves. This
 * app has no way to ingest, OCR, or verify an actual code document in this environment, so
 * [sourceNote] is a free-text description of where that document lives/how it was verified
 * (e.g. "NEC 2023, purchased copy, office binder" or "JS 316:2011, BSJ website PDF") — the
 * administrator's own attestation, not something this app checks.
 *
 * @param name the standard's name, e.g. "NEC" or "JS 316".
 * @param edition the specific edition/year the administrator has on file, e.g. "2023" — required,
 * since citing "NEC" without an edition is exactly the kind of unverifiable claim the spec's own
 * "do not invent code requirements" instruction rules out.
 * @param sourceNote where/how this standard was obtained — the administrator's own attestation.
 */
@Serializable
data class CodeStandard(
    val id: String,
    val name: String,
    val edition: String,
    val sourceNote: String,
    val addedAtMillis: Long
)

/**
 * One specific citation: "[standardId] §[sectionArticle] is why/how [checkLabel] matters," entered
 * by the administrator against a real section/article of a [CodeStandard] already on file — never
 * generated or inferred by this app. [checkLabel] must exactly match one of
 * [SystemDiagnostics.ALL_CHECK_LABELS], the same fixed set of engineering checks the Diagnostics
 * panel already shows, so a citation always lands on a real, existing check rather than a
 * free-floating claim disconnected from any actual calculation.
 */
@Serializable
data class CodeRequirementReference(
    val id: String,
    val standardId: String,
    val checkLabel: String,
    val sectionArticle: String,
    val relevanceNote: String
)

/**
 * Result of looking up whether a [SystemDiagnostics] check has an administrator-entered code
 * citation behind it. [SourceRequired] is deliberately the default for every check until an
 * administrator adds one — per the spec's own required wording: "If the required standards are
 * not present: say 'Source document required for verification.'"
 */
sealed class CodeReferenceResult {
    data class Found(val standard: CodeStandard, val reference: CodeRequirementReference) : CodeReferenceResult()
    data object SourceRequired : CodeReferenceResult()
}

/**
 * A82: the ONE place a [SystemDiagnostics] check is matched against an administrator-entered
 * [CodeRequirementReference] — pure lookup, no inference, no generated content. Deliberately does
 * NOT expose anything resembling "this system is code-compliant": a citation only ever means "the
 * administrator has linked this check to this specific section of this specific standard on
 * file," never a compliance verdict — see the spec's own "do not state the application is
 * code-compliant simply because a calculation passes."
 */
object CodeReferenceLookup {

    const val SOURCE_REQUIRED_MESSAGE = "Source document required for verification."

    fun referenceFor(
        checkLabel: String,
        standards: List<CodeStandard>,
        references: List<CodeRequirementReference>
    ): CodeReferenceResult {
        val reference = references.firstOrNull { it.checkLabel == checkLabel } ?: return CodeReferenceResult.SourceRequired
        val standard = standards.firstOrNull { it.id == reference.standardId } ?: return CodeReferenceResult.SourceRequired
        return CodeReferenceResult.Found(standard, reference)
    }
}
