package br.com.keyfalls;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

import java.util.List;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
    packages = "br.com.keyfalls",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

    private static final List<String> BOUNDED_CONTEXTS = List.of(
        "concurso",
        "inscricao",
        "logistica",
        "apuracao",
        "recurso",
        "comunicacao",
        "identidade"
    );

    /**
     * REGRA 1 (Pureza do Dominio):
     * Nenhuma classe dentro de ..domain.. pode depender de Spring, JPA/Hibernate,
     * Jackson, Bean Validation ou Lombok.
     */
    @ArchTest
    static final ArchRule regra1_dominioDeveSerTotalmenteLivreDeFrameworks =
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
            .because("A camada domain deve permanecer 100% agnostica de frameworks e tecnologias externas.");

    /**
     * REGRA 2 (Direcao das Camadas — Clean & Hexagonal Architecture):
     * A direcao da dependencia vai sempre de fora para dentro:
     * - Domain nao acessa nenhuma camada externa;
     * - Application acessa apenas Domain;
     * - Interfaces acessa Application (e Domain para excecoes/VOs se necessario);
     * - Infrastructure acessa Application e Domain; ninguem acessa Infrastructure ou Interfaces.
     */
    @ArchTest
    static final ArchRule regra2_direcaoEstritaDasCamadas =
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
            .because("A dependencia arquitetural deve apontar exclusivamente de fora para dentro (DIP).");

    /**
     * Complemento explicito da Regra 2: garante que Domain jamais importe Application,
     * Infrastructure ou Interfaces.
     */
    @ArchTest
    static final ArchRule regra2b_dominioNaoAcessaNenhumaCamadaExterna =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..application..", "..infrastructure..", "..interfaces..")
            .allowEmptyShould(true)
            .because("O nucleo de dominio nao conhece casos de uso, adaptadores de persistencia nem controllers HTTP.");

    /**
     * REGRA 3 (Isolamento de Bounded Contexts no Monolito Modular):
     * Um contexto nao pode acessar classes internas (domain, application, infrastructure, interfaces)
     * de outro Bounded Context, exceto eventos de dominio publicados (..domain.event..)
     * e contratos compartilhados explicitos (br.com.keyfalls.shared..).
     */
    @ArchTest
    static void regra3_isolamentoEstritoEntreBoundedContexts(JavaClasses classesImportadas) {
        for (String contextoOrigem : BOUNDED_CONTEXTS) {
            for (String contextoAlvo : BOUNDED_CONTEXTS) {
                if (contextoOrigem.equals(contextoAlvo)) {
                    continue;
                }

                String pacoteAlvoRaiz = "br.com.keyfalls." + contextoAlvo + "..";
                String pacoteEventosPublicadosAlvo = "br.com.keyfalls." + contextoAlvo + ".domain.event..";

                DescribedPredicate<JavaClass> classeInternaProibidaDoOutroContexto =
                    resideInAPackage(pacoteAlvoRaiz)
                        .and(DescribedPredicate.not(resideInAnyPackage(
                            pacoteEventosPublicadosAlvo,
                            "br.com.keyfalls.shared.."
                        )));

                ArchRule isolamentoContexto = noClasses()
                    .that().resideInAPackage("br.com.keyfalls." + contextoOrigem + "..")
                    .should().dependOnClassesThat(classeInternaProibidaDoOutroContexto)
                    .allowEmptyShould(true)
                    .because("O Bounded Context '%s' nao pode acoplar-se a classes internas de '%s' (permitido apenas eventos publicados e shared)."
                        .formatted(contextoOrigem, contextoAlvo));

                isolamentoContexto.check(classesImportadas);
            }
        }
    }

    /**
     * Prevencao de ciclos entre Bounded Contexts.
     */
    @ArchTest
    static final ArchRule regra4_ausenciaDeCiclosEntreContextos =
        SlicesRuleDefinition.slices()
            .matching("br.com.keyfalls.(*)..")
            .should().beFreeOfCycles()
            .allowEmptyShould(true)
            .because("Ciclos entre Bounded Contexts impedem evolucao modular e futura extracao em microsservicos.");
}
