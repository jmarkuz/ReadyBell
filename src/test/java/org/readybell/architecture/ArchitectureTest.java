package org.readybell.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * The hexagonal / bounded-context rules from docs/architecture, enforced. A failure here means a
 * dependency points the wrong way, not that behaviour is broken.
 */
class ArchitectureTest {

    private static JavaClasses main;

    @BeforeAll
    static void importMainClasses() {
        main = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.readybell");
    }

    @Test
    void domainAndApplicationDoNotDependOnFrameworks() {
        noClasses().that().resideInAnyPackage("org.readybell.(*).domain..", "org.readybell.(*).application..")
                .and().resideOutsideOfPackage("org.readybell.shared.domain.jpa..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta.persistence..", "jakarta.servlet..", "org.hibernate..")
                .because("the domain and the use cases are plain Java; frameworks live in adapters and platform")
                .check(main);
    }

    @Test
    void domainAndApplicationDoNotDependOnAdapters() {
        noClasses().that().resideInAnyPackage("org.readybell.(*).domain..", "org.readybell.(*).application..")
                .should().dependOnClassesThat().resideInAnyPackage("org.readybell.(*).adapter..")
                .because("dependencies point inward: adapters know the application, never the reverse")
                .check(main);
    }

    @Test
    void inboundAdaptersDoNotUseOutboundAdapters() {
        noClasses().that().resideInAnyPackage("org.readybell.(*).adapter.in..")
                .should().dependOnClassesThat().resideInAnyPackage("org.readybell.(*).adapter.out..")
                .check(main);
    }

    @Test
    void sharedKernelKnowsNoContext() {
        noClasses().that().resideInAnyPackage("org.readybell.shared..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.readybell.session..", "org.readybell.ordering..", "org.readybell.subscription..",
                        "org.readybell.restaurant..", "org.readybell.notification..", "org.readybell.platformadmin..",
                        "org.readybell.platform..")
                .check(main);
    }

    @Test
    void contextsDoNotDependOnThePlatformCompositionRoot() {
        noClasses().that().resideInAnyPackage(
                        "org.readybell.session..", "org.readybell.ordering..", "org.readybell.subscription..",
                        "org.readybell.restaurant..", "org.readybell.notification..", "org.readybell.platformadmin..")
                .should().dependOnClassesThat().resideInAnyPackage("org.readybell.platform..")
                .because("platform wires the contexts together, not the other way round")
                .check(main);
    }

    @Test
    void boundedContextsAreFreeOfCycles() {
        slices().matching("org.readybell.(session|ordering|subscription|restaurant|notification|platformadmin)..")
                .should().beFreeOfCycles()
                .check(main);
    }
}
