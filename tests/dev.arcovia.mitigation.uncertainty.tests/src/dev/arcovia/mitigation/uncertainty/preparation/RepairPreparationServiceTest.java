package dev.arcovia.mitigation.uncertainty.preparation;

import dev.arcovia.mitigation.ilp.*;
import dev.arcovia.mitigation.sat.Label;
import dev.arcovia.mitigation.sat.NodeLabel;
import org.dataflowanalysis.analysis.dfd.core.DFDFlowGraphCollection;
import org.dataflowanalysis.analysis.dfd.core.DFDVertex;
import org.dataflowanalysis.analysis.dsl.AnalysisConstraint;
import org.dataflowanalysis.analysis.dsl.constraint.ConstraintDSL;
import org.dataflowanalysis.converter.dfd2web.DataFlowDiagramAndDictionary;
import org.dataflowanalysis.converter.web2dfd.Web2DFDConverter;
import org.dataflowanalysis.converter.web2dfd.WebEditorConverterModel;
import org.dataflowanalysis.dfd.datadictionary.DataDictionary;
import org.dataflowanalysis.dfd.datadictionary.ForwardingAssignment;
import org.dataflowanalysis.dfd.datadictionary.Pin;
import org.dataflowanalysis.dfd.datadictionary.datadictionaryFactory;
import org.dataflowanalysis.dfd.dataflowdiagram.DataFlowDiagram;
import org.dataflowanalysis.dfd.dataflowdiagram.dataflowdiagramFactory;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RepairPreparationServiceTest {
    private final Path current = Path.of(System.getProperty("user.dir"));

    private final String minDfd = current.resolve("models")
            .resolve("minsat.json")
            .toString();

    @Test
    public void preparesMitigationsThatSolveTheSingleScenarioModel() throws Exception {
        var baselineOptimization = new OptimizationManager(minDfd, List.of(createDeleteNodeConstraint()), false);
        baselineOptimization.repair();

        var dfd = new Web2DFDConverter().convert(new WebEditorConverterModel(minDfd));
        var preparation = new RepairPreparationService().prepare(dfd, List.of(createDeleteNodeConstraint()));
        List<Mitigation> chosenMitigations = new ILPSolver().solve(preparation.mitigations(),
                new java.util.HashSet<>(preparation.allMitigations()),
                preparation.contradictions());

        assertEquals(1, preparation.preparedConstraints().size());
        assertFalse(preparation.violatingNodes().isEmpty());
        assertFalse(preparation.mitigations().isEmpty());
        assertFalse(preparation.allMitigations().isEmpty());
        assertNotNull(chosenMitigations);
        assertFalse(chosenMitigations.isEmpty());
        assertEquals(baselineOptimization.getCost(), chosenMitigations.stream().mapToDouble(Mitigation::cost).sum(), 0.0001);
    }

    @Test
    public void rejectsCustomEvaluationFunctionConstraintsForRobustPreparation() {
        var customConstraint = new Constraint(
                List.of(new MitigationStrategy(List.of(new NodeLabel(new Label("Location", "nonEU"))), 1,
                        MitigationType.DeleteNodeLabel))
        );
        customConstraint.addEvalFunction(createCustomEvaluationFunction());

        var dfd = new Web2DFDConverter().convert(new WebEditorConverterModel(minDfd));

        var exception = assertThrows(UnsupportedOperationException.class,
                () -> new RepairPreparationService().prepare(dfd, List.of(customConstraint)));

        assertTrue(exception.getMessage().contains("DSL-backed constraints"));
        assertTrue(exception.getMessage().contains("out of scope"));
    }

    @Test
    void rejectsCyclicDataFlowDiagrams() {
        assertThrows(IllegalStateException.class,
                () -> new ViolationAnalysis().findViolatingNodes(cyclicModel(), List.of()));
    }

    private Constraint createDeleteNodeConstraint() {
        AnalysisConstraint dsl = new ConstraintDSL().ofData()
                .withLabel("Sensitivity", "Personal")
                .neverFlows()
                .toVertex()
                .withCharacteristic("Stereotype", "internal")
                .create();

        return new Constraint(dsl, List.of(
                new MitigationStrategy(List.of(new NodeLabel(new Label("Stereotype", "internal"))), 1,
                        MitigationType.DeleteNode)));
    }

    private EvaluationFunction createCustomEvaluationFunction() {
        return new EvaluationFunction() {
            @Override
            public Set<Node> evaluate(DFDFlowGraphCollection flowGraph) {
                throw new AssertionError("Custom evaluation must be rejected before evaluation");
            }

            @Override
            public boolean isMatched(DFDVertex node) {
                throw new AssertionError("Custom evaluation must be rejected before matching");
            }
        };
    }

    private DataFlowDiagramAndDictionary cyclicModel() {
        var dictionaryFactory = datadictionaryFactory.eINSTANCE;
        var diagramFactory = dataflowdiagramFactory.eINSTANCE;
        DataDictionary dictionary = dictionaryFactory.createDataDictionary();
        DataFlowDiagram diagram = diagramFactory.createDataFlowDiagram();
        var loopNode = diagramFactory.createProcess();
        loopNode.setId("cycle-node");
        loopNode.setEntityName("cycle-node");
        diagram.getNodes().add(loopNode);
        var sinkNode = diagramFactory.createStore();
        sinkNode.setId("cycle-sink");
        sinkNode.setEntityName("cycle-sink");
        diagram.getNodes().add(sinkNode);

        var behavior = dictionaryFactory.createBehavior();
        var input = dictionaryFactory.createPin();
        input.setId("cycle-input");
        var cycleOutput = dictionaryFactory.createPin();
        cycleOutput.setId("cycle-output");
        var sinkOutput = dictionaryFactory.createPin();
        sinkOutput.setId("sink-output");
        behavior.getInPin().add(input);
        behavior.getOutPin().add(cycleOutput);
        behavior.getOutPin().add(sinkOutput);
        behavior.getAssignment().add(forward(input, cycleOutput));
        behavior.getAssignment().add(forward(input, sinkOutput));
        dictionary.getBehavior().add(behavior);
        loopNode.setBehavior(behavior);

        var sinkBehavior = dictionaryFactory.createBehavior();
        var sinkInput = dictionaryFactory.createPin();
        sinkInput.setId("sink-input");
        sinkBehavior.getInPin().add(sinkInput);
        dictionary.getBehavior().add(sinkBehavior);
        sinkNode.setBehavior(sinkBehavior);

        var loop = diagramFactory.createFlow();
        loop.setId("cycle-flow");
        loop.setSourceNode(loopNode);
        loop.setSourcePin(cycleOutput);
        loop.setDestinationNode(loopNode);
        loop.setDestinationPin(input);
        diagram.getFlows().add(loop);
        var exit = diagramFactory.createFlow();
        exit.setId("cycle-exit");
        exit.setSourceNode(loopNode);
        exit.setSourcePin(sinkOutput);
        exit.setDestinationNode(sinkNode);
        exit.setDestinationPin(sinkInput);
        diagram.getFlows().add(exit);
        return new DataFlowDiagramAndDictionary(diagram, dictionary);
    }

    private ForwardingAssignment forward(Pin input, Pin output) {
        var assignment = datadictionaryFactory.eINSTANCE.createForwardingAssignment();
        assignment.getInputPins().add(input);
        assignment.setOutputPin(output);
        return assignment;
    }
}
