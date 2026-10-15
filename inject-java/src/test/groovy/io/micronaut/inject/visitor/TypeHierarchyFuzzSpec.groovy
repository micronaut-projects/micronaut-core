package io.micronaut.inject.visitor

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.type.TypeHierarchies
import io.micronaut.core.type.TypeHierarchy
import io.micronaut.inject.test.hierarchy.TypeHierarchyFuzz

/**
 * Random generic hierarchies compiled by javac, their records checked against reflection; see {@link TypeHierarchyFuzz}.
 */
class TypeHierarchyFuzzSpec extends AbstractTypeElementSpec {

    static final int CLASSES_PER_SEED = 50

    void "random hierarchies agree with reflection (seed #seed)"() {
        given:
        def generator = new TypeHierarchyFuzz.Generator(seed, TypeHierarchyFuzz.Language.JAVA)
        def source = generator.source(CLASSES_PER_SEED)
        def classLoader = buildClassLoader('fuzz.Fuzz0', source)
        List<String> mismatches = []

        when:
        for (TypeHierarchyFuzz.Generated generated : generator.generated()) {
            Class<?> type = classLoader.loadClass('fuzz.' + generated.name())
            def record = (TypeHierarchy) classLoader.loadClass(TypeHierarchies.recordClassName(type.name, 'fuzz')).getConstructor().newInstance()
            mismatches.addAll(TypeHierarchyFuzz.check(generated, type, record, { false }))
        }

        then:
        mismatches.empty

        where:
        seed << (1..60)
    }
}
