package io.micronaut.inject.visitor

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.core.type.TypeHierarchies
import io.micronaut.core.type.TypeHierarchy
import io.micronaut.inject.test.hierarchy.TypeHierarchyFuzz

/**
 * Random generic hierarchies compiled by Groovy (which reads the Java declarations), their records checked against
 * reflection; see {@link TypeHierarchyFuzz}.
 */
class TypeHierarchyFuzzSpec extends AbstractBeanDefinitionSpec {

    static final int CLASSES_PER_SEED = 50

    void "random hierarchies agree with reflection (seed #seed)"() {
        given:
        def generator = new TypeHierarchyFuzz.Generator(seed, TypeHierarchyFuzz.Language.GROOVY)
        def source = generator.source(CLASSES_PER_SEED)
        def classLoader = buildClassLoader(source)
        List<String> mismatches = []

        when:
        for (TypeHierarchyFuzz.Generated generated : generator.generated()) {
            Class<?> type = classLoader.loadClass('fuzz.' + generated.name())
            def record = (TypeHierarchy) classLoader.loadClass(TypeHierarchies.recordClassName(type.name, 'fuzz')).getConstructor().newInstance()
            // every Groovy class implements GroovyObject, which the element model leaves out of the hierarchy
            mismatches.addAll(TypeHierarchyFuzz.check(generated, type, record, { Class<?> c -> c.name == 'groovy.lang.GroovyObject' }))
        }

        then:
        mismatches.empty

        where:
        seed << (1..20)
    }
}
