package dev.arcovia.mitigation.uncertainty.verification.pruning;

import dev.arcovia.mitigation.uncertainty.pruning.ClusterCouplingGuard;

import dev.arcovia.mitigation.cost.ActionType;
import dev.arcovia.mitigation.ilp.ActionTerm;
import dev.arcovia.mitigation.ilp.Mitigation;
import dev.arcovia.mitigation.uncertainty.preparation.RepairPreparationResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClusterCouplingGuardTest {

    private final ClusterCouplingGuard guard = new ClusterCouplingGuard();

    @Test
    public void safeWhenDomainsAreDisjointAndNoRequiredClauses() {
        var clusterA = cluster(action("elem-a"));
        var clusterB = cluster(action("elem-b"));
        assertTrue(guard.isSafeToDecompose(List.of(clusterA, clusterB)));
    }

    @Test
    public void unsafeWhenAnElementIsProposedInMoreThanOneCluster() {
        // A contradiction is always on the same element; a shared domain means one could cross clusters.
        var clusterA = cluster(action("shared"));
        var clusterB = cluster(action("shared"));
        assertFalse(guard.isSafeToDecompose(List.of(clusterA, clusterB)));
    }

    @Test
    public void unsafeWhenARequiredClauseReferencesAnotherClustersElement() {
        Mitigation inB = action("elem-b");
        Mitigation inARequiringB = actionRequiring("elem-a", inB);
        var clusterA = cluster(inARequiringB);
        var clusterB = cluster(inB);
        assertFalse(guard.isSafeToDecompose(List.of(clusterA, clusterB)));
    }

    @Test
    public void safeWhenRequiredClausesStayWithinTheSameCluster() {
        Mitigation prerequisiteInA = action("elem-a2");
        Mitigation dependentInA = actionRequiring("elem-a1", prerequisiteInA);
        var clusterA = cluster(dependentInA, prerequisiteInA);
        var clusterB = cluster(action("elem-b"));
        assertTrue(guard.isSafeToDecompose(List.of(clusterA, clusterB)));
    }

    private static Mitigation action(String domain) {
        return new Mitigation(new ActionTerm(domain, List.of(), ActionType.Adding), 1.0, List.of());
    }

    private static Mitigation actionRequiring(String domain, Mitigation prerequisite) {
        return new Mitigation(new ActionTerm(domain, List.of(), ActionType.Adding), 1.0, List.of(List.of(prerequisite)));
    }

    private static List<RepairPreparationResult> cluster(Mitigation... mitigations) {
        return List.of(new RepairPreparationResult(List.of(), Set.of(), List.of(), List.of(mitigations), List.of()));
    }
}
