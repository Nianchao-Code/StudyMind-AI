package com.studymind.app.agent;

/**
 * Summarization strategy selected based on content analysis result.
 * Agent selects the most appropriate output format by subject and structure.
 */
public enum SummarizationStrategy {
    /** General academic content - balanced definitions, concepts, key points */
    GENERAL,
    /** Math/formula-heavy - emphasize formulas, derivations, theorems */
    MATH_FORMULA,
    /** Algorithm/programming - emphasize pseudocode, complexity, implementation */
    ALGORITHM,
    /** Circuit/physics - emphasize formulas, units, typical calculations */
    CIRCUIT_PHYSICS,
    /** Concept/theory - emphasize definitions, comparisons, common pitfalls */
    CONCEPT_THEORY
}
