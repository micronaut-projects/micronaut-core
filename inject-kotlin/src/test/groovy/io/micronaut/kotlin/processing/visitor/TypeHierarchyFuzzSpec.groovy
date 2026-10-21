package io.micronaut.kotlin.processing.visitor

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.core.type.TypeHierarchies
import io.micronaut.inject.test.hierarchy.TypeHierarchyFuzz

/**
 * Random generic hierarchies compiled by KSP, their records checked against reflection; see {@link TypeHierarchyFuzz}.
 */
class TypeHierarchyFuzzSpec extends AbstractKotlinCompilerSpec {

    static final int CLASSES_PER_SEED = 50

    void "random hierarchies agree with reflection (seed #seed)"() {
        given:
        def generator = new TypeHierarchyFuzz.Generator(seed, TypeHierarchyFuzz.Language.KOTLIN)
        def source = generator.source(CLASSES_PER_SEED)
        def classLoader = buildClassLoader('fuzz.Fuzz0', source)
        TypeHierarchies.invalidate()
        List<String> mismatches = []

        when:
        for (TypeHierarchyFuzz.Generated generated : generator.generated()) {
            Class<?> type = classLoader.loadClass('fuzz.' + generated.name())
            def record = TypeHierarchies.find(type.name, classLoader).orElseThrow { new AssertionError("No record of " + type.name) }
            // KSP maps java.util.List to kotlin.collections.List, whose super types have no SequencedCollection, and Kotlin
            // adds its own marker interfaces to classes extending the mapped collections
            mismatches.addAll(TypeHierarchyFuzz.check(generated, type, record, { Class<?> c -> c.name.startsWith('kotlin.') || c.name == 'java.util.SequencedCollection' }))
        }

        then:
        mismatches.empty

        where:
        seed << (1..20)
    }
}
