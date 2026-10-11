package dev.arcovia.mitigation.uncertainty.pipeline;

/**
 * Receives timing boundaries from the uncertainty-aware repair pipeline.
 */
public interface PipelineTiming {
    /**
     * Marks the start of one pipeline stage.
     *
     * @param stage the stage that starts
     */
    void start(PipelineStage stage);

    /**
     * Marks the end of one pipeline stage.
     *
     * @param stage the stage that ends
     */
    void stop(PipelineStage stage);

    /**
     * Returns a timing sink that ignores all boundaries.
     *
     * @return the shared no-op timing sink
     */
    static PipelineTiming none() {
        return NoOpPipelineTiming.INSTANCE;
    }

    /**
     * Avoids allocations when a caller does not collect timings.
     */
    enum NoOpPipelineTiming implements PipelineTiming {
        /**
         * The shared no-op timing sink.
         */
        INSTANCE;

        @Override
        public void start(PipelineStage stage) {
            // Intentionally empty.
        }

        @Override
        public void stop(PipelineStage stage) {
            // Intentionally empty.
        }
    }
}
