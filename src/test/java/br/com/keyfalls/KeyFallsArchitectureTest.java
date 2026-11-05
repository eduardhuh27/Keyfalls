package br.com.keyfalls;

import br.com.keyfalls.shared.domain.AggregateRoot;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import jakarta.persistence.Entity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Suite de Governanca Arquitetural (ArchUnit) da API KeyFalls.
 * Falha imediatamente o build CI/CD caso qualquer desenvolvedor viole:
 * 1) A pureza da camada domain (zero Spring, JPA, Hibernate, Jackson, Bean Validation ou Lombok);
 * 2) A direcao de dependencia da Clean/Hexagonal Architecture;
 * 3) O isolamento estrito entre os 7 Bounded Contexts do Monolito Modular;
 * 4) A proibicao de expor AggregateRoots ou @Entity diretamente nos RestControllers.
 */
@AnalyzeClasses(
    packages = "br.com.keyfalls",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class KeyFallsArchitectureTest {

    private static final List<String> BOUNDED_CONTEXTS = List.of(
        "concurso",
        "inscricao",
        "logistica",
        "apuracao",
        "recurso",
        "comunicacao",
        "identidade"
    );

    @ArchTest
    static final ArchRule dominioNaoPodeImportarSpringJpaJacksonValidationOuLombok =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "org.hibernate..",
                "com.fasterxml.jackson..",
                "jakarta.validation..",
                "lombok.."
            )
            .allowEmptyShould(true)
            .because("A camada domain deve permanecer 100% Java 21 puro e imune a acoplamento tecnologico.");

    @ArchTest
    static final ArchRule aplicacaoNaoPodeDependerDeJpaNemDeCamadaWeb =
        noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "jakarta.persistence..",
                "org.hibernate..",
                "org.springframework.web..",
                "..infrastructure..",
                "..interfaces.."
            )
            .allowEmptyShould(true)
            .because("Use Cases na camada application orquestram portas puras, sem conhecer HTTP nem JPA.");

    @ArchTest
    static final ArchRule direcaoDeCamadasCleanArchitectureDeveSerRespeitada =
        Architectures.layeredArchitecture()
            .consideringOnlyDependenciesInAnyPackage("br.com.keyfalls..")
            .layer("Domain").definedBy("..domain..")
            .layer("Application").definedBy("..application..")
            .layer("Infrastructure").definedBy("..infrastructure..")
            .layer("Interfaces").definedBy("..interfaces..")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Interfaces")
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure", "Interfaces")
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
            .whereLayer("Interfaces").mayNotBeAccessedByAnyLayer()
            .allowEmptyShould(true)
            .because("A direcao da dependencia vai sempre de fora para dentro (Dependency Inversion Principle).");

    @ArchTest
    static void nenhumBoundedContextPodeAcessarEntidadesOuRepositoriosInternosDeOutroContexto(JavaClasses classes) {
        for (String origem : BOUNDED_CONTEXTS) {
            for (String destino : BOUNDED_CONTEXTS) {
                if (origem.equals(destino)) {
                    continue;
                }

                DescribedPredicate<JavaClass> classeInternaDeOutroContexto =
                    resideInAPackage("br.com.keyfalls." + destino + "..")
                        .and(DescribedPredicate.not(resideInAnyPackage(
                            "br.com.keyfalls." + destino + ".domain.event..",
                            "br.com.keyfalls.shared.."
                        )));

                noClasses()
                    .that().resideInAPackage("br.com.keyfalls." + origem + "..")
                    .should().dependOnClassesThat(classeInternaDeOutroContexto)
                    .allowEmptyShould(true)
                    .because("O contexto '%s' nao pode acessar repositorios, agregados ou infra interna de '%s'."
                        .formatted(origem, destino))
                    .check(classes);
            }
        }
    }

    @ArchTest
    static final ArchRule entidadesJpaDevemResidirExclusivamenteEmInfrastructure =
        classes()
            .that().areAnnotatedWith(Entity.class)
            .should().resideInAPackage("..infrastructure..")
            .allowEmptyShould(true)
            .because("Entidades JPA jamais podem vazar para domain, application ou interfaces.");

    @ArchTest
    static final ArchRule controllersRestNaoPodemExporEntidadesJpaNemAggregateRootsDiretamente =
        methods()
            .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
            .and().arePublic()
            .should().notHaveRawReturnType(assignableTo(AggregateRoot.class))
            .andShould().notHaveRawReturnType(CanBeAnnotated.Predicates.annotatedWith(Entity.class))
            .allowEmptyShould(true)
            .because("Endpoints REST devem retornar exclusivamente DTOs de Response ou ResponseEntity<DTO>.");

    @ArchTest
    static final ArchRule ausenciaTotalDeCiclosEntreBoundedContexts =
        SlicesRuleDefinition.slices()
            .matching("br.com.keyfalls.(*)..")
            .should().beFreeOfCycles()
            .allowEmptyShould(true)
            .because("Dependencias ciclicas entre contextos inviabilizam a extracao futura para microsservicos.");
}
