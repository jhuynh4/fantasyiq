package com.fantasyiq;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;

/**
 * Enforces the module boundaries described in the FantasyIQ architecture doc.
 * These rules are what keep the "modular monolith" modular rather than a
 * big ball of mud as the codebase grows. Run this early and often — it's
 * meant to fail loudly the moment a boundary is crossed by accident.
 */
class ArchitectureRulesTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.fantasyiq");
    }

    @Test
    void ingestionMustNotDependOnApiLayer() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.fantasyiq.ingestion..")
                .should().dependOnClassesThat().resideInAPackage("com.fantasyiq.api..");
        rule.check(classes);
    }

    @Test
    void analyticsMustNotDependOnApiLayer() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.fantasyiq.analytics..")
                .should().dependOnClassesThat().resideInAPackage("com.fantasyiq.api..");
        rule.check(classes);
    }

    @Test
    void domainMustNotDependOnIngestionAnalyticsOrApi() {
        // Domain (Player/Team/Game entities + repos) is the innermost layer —
        // everything else depends on it, it depends on nothing feature-specific.
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.fantasyiq.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.fantasyiq.ingestion..",
                        "com.fantasyiq.analytics..",
                        "com.fantasyiq.api..");
        rule.check(classes);
    }

    @Test
    void controllersShouldOnlyLiveInApiPackage() {
        ArchRule rule = com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes()
                .that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                .should().resideInAPackage("com.fantasyiq.api.controller..");
        rule.check(classes);
    }

    @Test
    void noFieldsShouldUseFieldInjection() {
        // Constructor injection only — makes classes testable without a Spring context
        // and makes missing dependencies a compile-time problem, not a runtime one.
        ArchRule rule = com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields()
                .should().beAnnotatedWith(org.springframework.beans.factory.annotation.Autowired.class);
        rule.check(classes);
    }
}
