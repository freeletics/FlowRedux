package com.freeletics.flowredux.dsl

// TODO find better name
/**
 * Defines which flatMap behavior should be applied whenever a new values is emitted
 */
enum class FlatMapPolicy {
    /**
     * uses flatMapLatest
     */
    LATEST,
    /**
     * Uses flatMapMerge
     */
    MERGE,
    /**
     * Uses flatMapConcat
     */
    CONCAT
}