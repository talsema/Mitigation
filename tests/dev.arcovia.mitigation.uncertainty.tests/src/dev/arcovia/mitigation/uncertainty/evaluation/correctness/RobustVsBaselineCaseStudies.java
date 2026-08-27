package dev.arcovia.mitigation.uncertainty.evaluation.correctness;

import dev.abunai.confidentiality.analysis.model.uncertainty.UncertaintySource;
import dev.arcovia.mitigation.uncertainty.loading.UncertaintyModelSpec;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static dev.arcovia.mitigation.uncertainty.evaluation.EvaluationConstraints.*;

/**
 * Defines the fixed corpus of models, predicates, and source selections.
 */
final class RobustVsBaselineCaseStudies {

    private RobustVsBaselineCaseStudies() {
    }

    private static final String ALL_SOURCE_TYPES = "External|Behavior|Interface|Component|Connector";
    private static final String MICROSECEND_ADAPTED = "microSecEnD benchmark adapted with uncertainty";
    private static final String MICROSECEND_ANNOTATED =
            "microSecEnD benchmark with existing uncertainty annotation";
    private static final String ONLINE_BANKING =
            "mitigation-line Online Banking model with DSL translation";
    private static final String AUTH_SERVER_8 = "auth Server8";
    private static final String CUSTOMER_LOCATION = "Customer_Location_Uncertain";


    /**
     * Creates the cases for the evaluation.
     *
     * @param projectDirectory current project directory used to resolve fixture paths
     * @return immutable case definitions
     */
    static List<CaseStudy> create(Path projectDirectory) {
        final Path testDirectoryPath = projectDirectory.getParent().resolve("dev.arcovia.mitigation.ranking.tests");
        final Path onlineBankingFolder = testDirectoryPath.resolve("models").resolve("OBM");
        final Path jferraterFolder = testDirectoryPath.resolve("models").resolve("jferrater");
        final Path koushikkothagalFolder = projectDirectory.resolve("models").resolve("koushikkothagal");
        final Path externalModelFolder = projectDirectory.resolve("models").resolve("DFDExternalUncertaintyMitigation");
        final Path mitigationExampleFolder = projectDirectory.resolve("models").resolve("mitigation_example");

        ComparisonVerdict verdict = ComparisonVerdict.robustFailure(RepairOutcome.NO_ROBUST_REPAIR);
        ComparisonVerdict verdict1 = ComparisonVerdict.robustFailure(RepairOutcome.NO_ROBUST_REPAIR);
        return List.of(
                new CaseStudy("Banking/ext (no uncertainty)",
                        dfd(externalModelFolder, "ext"),
                        PERSONAL_NEVER_LEAVES_EU,
                        source -> false,
                        new CaseExpectation(ComparisonVerdict.NOMINAL_SUFFICIENT, 1, "constructed control", "none")),

                new CaseStudy("OnlineShop (4 scenarios)",
                        dfd(projectDirectory.resolve("models").resolve("UncertainOnlineShopDFD"), "onlineshop"),
                        PERSONAL_NEVER_LEAVES_EU,
                        nullUncertaintySourceFilter(),
                        new CaseExpectation(ComparisonVerdict.NOMINAL_SUFFICIENT, 4, "ABUNAI example", "External")),

                new CaseStudy("OnlineShopSimple",
                        dfd(projectDirectory.resolve("models").resolve("UncertainOnlineShopDFDsimple"), "onlineshop"),
                        PERSONAL_NEVER_LEAVES_EU,
                        nullUncertaintySourceFilter(),
                        new CaseExpectation(ComparisonVerdict.NOMINAL_SUFFICIENT, 2, "ABUNAI example", "External")),

                new CaseStudy("Banking/ext (Banking only; base-manifest, 2 scn)",
                        dfd(externalModelFolder, "ext"),
                        PERSONAL_NEVER_LEAVES_EU,
                        sources("Banking_Data_Location_Uncertain"),
                        new CaseExpectation(ComparisonVerdict.NOMINAL_SUFFICIENT, 2, "thesis running example", "External")),

                new CaseStudy("Banking+Transactions/ext (LATENT, 4 scn)",
                        dfd(externalModelFolder, "ext"),
                        PERSONAL_NEVER_LEAVES_EU,
                        sources("Banking_Data_Location_Uncertain", "Transactions_DB_Location_Uncertain"),
                        new CaseExpectation(verdict1, 4, "constructed latent control", "External")),

                new CaseStudy("Transactions/ext (LATENT only, 2 scn)",
                        dfd(externalModelFolder, "ext"),
                        PERSONAL_NEVER_LEAVES_EU,
                        sources("Transactions_DB_Location_Uncertain"),
                        new CaseExpectation(verdict, 2, "constructed latent control", "External")),

                new CaseStudy("Transactions/ext + ENCRYPTION-escape (LATENT, 2 scn)",
                        dfd(externalModelFolder, "ext"),
                        PERSONAL_OUTSIDE_EU_MUST_BE_ENCRYPTED,
                        sources("Transactions_DB_Location_Uncertain"),
                        new CaseExpectation(ComparisonVerdict.TRANSFER_GAP, 2, "constructed latent control", "External")),

                new CaseStudy("Banking+Transactions/ext + ENCRYPTION-escape (LATENT, 4 scn)",
                        dfd(externalModelFolder, "ext"),
                        PERSONAL_OUTSIDE_EU_MUST_BE_ENCRYPTED,
                        sources("Banking_Data_Location_Uncertain", "Transactions_DB_Location_Uncertain"),
                        new CaseExpectation(ComparisonVerdict.TRANSFER_GAP, 4, "constructed interaction control", "External")),

                new CaseStudy("mitigation_example (Personal; couples -> fallback)",
                        dfd(mitigationExampleFolder, "mitigation_example"),
                        PERSONAL_NEVER_LEAVES_EU,
                        nullUncertaintySourceFilter(),
                        new CaseExpectation(ComparisonVerdict.TRANSFER_GAP, 32, "ARCoViA mitigation example", ALL_SOURCE_TYPES)),

                new CaseStudy("mitigation_example (Public; no violation, 32 scn)",
                        dfd(mitigationExampleFolder, "mitigation_example"),
                        PUBLIC_NEVER_LEAVES_EU,
                        nullUncertaintySourceFilter(),
                        new CaseExpectation(ComparisonVerdict.NOMINAL_SUFFICIENT, 32, "ARCoViA mitigation example", ALL_SOURCE_TYPES)),

                new CaseStudy("microSecEnD koushikkothagal + C3 auth (MIXED, 2 scn)",
                        dfd(koushikkothagalFolder, "koushikkothagal_0"),
                        UNAUTHENTICATED_NEVER_REACHES_INTERNAL,
                        nullUncertaintySourceFilter(),
                        new CaseExpectation(ComparisonVerdict.TRANSFER_GAP, 2, MICROSECEND_ADAPTED, "External")),

                new CaseStudy("microSecEnD koushikkothagal + C9 logging (MIXED, 2 scn)",
                        dfd(koushikkothagalFolder, "koushikkothagal_0"),
                        INTERNAL_MUST_LOG_LOCALLY,
                        nullUncertaintySourceFilter(),
                        new CaseExpectation(ComparisonVerdict.TRANSFER_GAP, 2, MICROSECEND_ADAPTED, "External")),

                new CaseStudy("microSecEnD jferrater + C3 auth (External, 2 scn)",
                        dfd(jferraterFolder, "jferrater"),
                        UNAUTHENTICATED_NEVER_REACHES_INTERNAL,
                        sources(AUTH_SERVER_8),
                        new CaseExpectation(ComparisonVerdict.NOMINAL_SUFFICIENT, 2, MICROSECEND_ANNOTATED, "External")),

                new CaseStudy("microSecEnD jferrater + C6 login attempts (External, 2 scn)",
                        dfd(jferraterFolder, "jferrater"),
                        AUTH_SERVER_MUST_REGULATE_LOGINS,
                        sources(AUTH_SERVER_8),
                        new CaseExpectation(ComparisonVerdict.NOMINAL_SUFFICIENT, 2, MICROSECEND_ANNOTATED, "External")),

                new CaseStudy("microSecEnD jferrater + C9 logging (External, 2 scn)",
                        dfd(jferraterFolder, "jferrater"),
                        INTERNAL_MUST_LOG_LOCALLY,
                        sources(AUTH_SERVER_8),
                        new CaseExpectation(ComparisonVerdict.NOMINAL_SUFFICIENT, 2, MICROSECEND_ANNOTATED, "External")),

                new CaseStudy("Online Banking + M1 Personal/Develop (External, 2 scn)",
                        dfd(onlineBankingFolder, "OBM"),
                        PERSONAL_NEVER_REACHES_DEVELOPMENT,
                        sources(CUSTOMER_LOCATION),
                        new CaseExpectation(ComparisonVerdict.NOMINAL_SUFFICIENT, 2, ONLINE_BANKING, "External")),

                new CaseStudy("Online Banking + M2 Encrypted/Processable (External, 2 scn)",
                        dfd(onlineBankingFolder, "OBM"),
                        ENCRYPTED_NEVER_BECOMES_PROCESSABLE,
                        sources(CUSTOMER_LOCATION),
                        new CaseExpectation(ComparisonVerdict.NOMINAL_SUFFICIENT, 2, ONLINE_BANKING, "External")),

                new CaseStudy("Online Banking + M3 Personal/nonEU (External, 2 scn)",
                        dfd(onlineBankingFolder, "OBM"),
                        PERSONAL_NEVER_LEAVES_EU,
                        sources(CUSTOMER_LOCATION),
                        new CaseExpectation(ComparisonVerdict.NOMINAL_SUFFICIENT, 2, ONLINE_BANKING, "External")),

                new CaseStudy("koushikkothagal + C6 login attempts (External, 2 scn)",
                        dfd(koushikkothagalFolder, "koushikkothagal_0"),
                        AUTH_SERVER_MUST_REGULATE_LOGINS,
                        nullUncertaintySourceFilter(),
                        new CaseExpectation(ComparisonVerdict.NOMINAL_SUFFICIENT, 2, MICROSECEND_ADAPTED, "External")),

                new CaseStudy("koushikkothagal + C4 identity transform (External, 2 scn)",
                        dfd(koushikkothagalFolder, "koushikkothagal_0"),
                        ENTRY_MUST_TRANSFORM_IDENTITY,
                        nullUncertaintySourceFilter(),
                        new CaseExpectation(ComparisonVerdict.NOMINAL_SUFFICIENT, 2, MICROSECEND_ADAPTED, "External")),

                new CaseStudy("microSecEnD jferrater + C5 token validation (External, 2 scn)",
                        dfd(jferraterFolder, "jferrater"),
                        ENTRY_MUST_VALIDATE_TOKEN,
                        sources(AUTH_SERVER_8),
                        new CaseExpectation(ComparisonVerdict.NOMINAL_SUFFICIENT, 2, MICROSECEND_ANNOTATED, "External")));
    }

    /**
     * Returns the frozen expectations of the given cases, keyed by case identifier.
     *
     * @param studies the declared cases
     * @return case identifiers mapped to their frozen expectations, in declaration order
     * @throws IllegalStateException when two cases share an identifier
     */
    static Map<String, CaseExpectation> expectations(List<CaseStudy> studies) {
        return studies.stream()
                .collect(
                        Collectors.collectingAndThen(
                                Collectors.toMap(
                                        CaseStudy::name,
                                        CaseStudy::expectation,
                                        (first, second) -> {
                                            throw new IllegalStateException("Two case studies share an identifier");
                                        },
                                        LinkedHashMap::new
                                ),
                                Collections::unmodifiableMap)
                );
    }

    /**
     * Selects the named uncertainty sources.
     *
     * @param entityNames the entity names to select
     * @return a predicate matching exactly those sources
     */
    private static Predicate<UncertaintySource> sources(String... entityNames) {
        return source -> List.of(entityNames).contains(source.getEntityName());
    }

    /**
     * Selects every uncertainty source the model declares.
     *
     * @return the null filter == no selection
     */
    private static Predicate<UncertaintySource> nullUncertaintySourceFilter() {
        return null;
    }


    /**
     * Creates a model specification from matching fixture files.
     *
     * @param folder directory containing the model files
     * @param base   shared fixture name without an extension
     * @return the model specification
     */
    private static UncertaintyModelSpec dfd(Path folder, String base) {
        return new UncertaintyModelSpec(
                folder.resolve(base + ".dataflowdiagram"),
                folder.resolve(base + ".datadictionary"),
                folder.resolve(base + ".uncertainty"));
    }
}
