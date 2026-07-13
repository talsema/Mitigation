package dev.arcovia.mitigation.uncertainty.pruning;

import dev.arcovia.mitigation.uncertainty.preparation.RepairPreparationResult;
import org.eclipse.jdt.annotation.NonNull;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Conservatively decides whether source clusters can be solved independently.
 */
public final class ClusterCouplingGuard {

    /**
     * @param clusterPreparations the per-cluster preparation results, one inner list per cluster
     * @return {@code true} iff the clusters can be solved and validated independently
     */
    public boolean isSafeToDecompose(@NonNull List<List<RepairPreparationResult>> clusterPreparations) {
        Objects.requireNonNull(clusterPreparations, "clusterPreparations must not be null");

        List<Set<String>> domainsPerCluster = clusterPreparations.stream()
                .map(ClusterCouplingGuard::candidateDomains)
                .toList();
        return domainsAreDisjoint(domainsPerCluster)
               && requirementsStayWithinClusters(clusterPreparations, domainsPerCluster);
    }

    /**
     * Checks that an action domain belongs to at most one cluster.
     *
     * @param domainsPerCluster the candidate domains for each cluster
     * @return {@code true} when no domain occurs in two clusters
     */
    private static boolean domainsAreDisjoint(List<Set<String>> domainsPerCluster) {
        Set<String> seenDomains = new HashSet<>();
        return domainsPerCluster.stream().flatMap(Set::stream).allMatch(seenDomains::add);
    }

    /**
     * Checks that every required action belongs to the cluster that declares it.
     *
     * @param clusterPreparations the prepared scenarios for each cluster
     * @param domainsPerCluster   the candidate domains for each cluster
     * @return {@code true} when no required action crosses a cluster boundary
     */
    private static boolean requirementsStayWithinClusters(
            List<List<RepairPreparationResult>> clusterPreparations,
            List<Set<String>> domainsPerCluster
    ) {
        return IntStream.range(0, clusterPreparations.size())
                .allMatch(index -> requirementsStayInCluster(
                        clusterPreparations.get(index), domainsPerCluster.get(index)));
    }

    /**
     * Checks all requirements declared by one cluster.
     *
     * @param preparations the cluster's scenario preparations
     * @param localDomains the domains proposed by that cluster
     * @return {@code true} when every required action targets a local domain
     */
    private static boolean requirementsStayInCluster(
            List<RepairPreparationResult> preparations,
            Set<String> localDomains
    ) {
        return preparations.stream()
                .flatMap(preparation -> preparation.allMitigations().stream())
                .flatMap(mitigation -> mitigation.required().stream())
                .flatMap(List::stream)
                .allMatch(required -> localDomains.contains(required.mitigation().domain()));
    }

    /**
     * Collects the architectural domains touched by any candidate in one source cluster.
     *
     * @param cluster the preparation results for one cluster
     * @return the set of candidate-action domains in that cluster
     */
    private static Set<String> candidateDomains(List<RepairPreparationResult> cluster) {
        return cluster.stream()
                .flatMap(preparation -> preparation.allMitigations().stream())
                .map(mitigation -> mitigation.mitigation().domain())
                .collect(Collectors.toSet());
    }
}
