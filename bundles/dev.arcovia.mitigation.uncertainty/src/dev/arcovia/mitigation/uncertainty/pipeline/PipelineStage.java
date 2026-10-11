package dev.arcovia.mitigation.uncertainty.pipeline;

/**
 * A measured stage of the uncertainty-aware repair pipeline.
 */
public enum PipelineStage {
    /**
     * Generates the selected uncertainty scenarios.
     */
    ENUMERATION,
    /**
     * Creates scenario-specific model copies.
     */
    MATERIALIZATION,
    /**
     * Derives violations and repair constraints.
     */
    PREPARATION,
    /**
     * Builds and solves the joint optimization problem.
     */
    SOLVING,
    /**
     * Applies the selected repair to the base model copy.
     */
    APPLICATION,
    /**
     * Re-analyzes the repaired model.
     */
    VALIDATION
}
