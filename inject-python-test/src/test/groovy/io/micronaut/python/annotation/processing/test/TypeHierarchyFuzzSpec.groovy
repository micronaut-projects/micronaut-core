package io.micronaut.python.annotation.processing.test

import io.micronaut.core.type.TypeHierarchies
import io.micronaut.core.type.TypeHierarchy
import io.micronaut.inject.test.hierarchy.TypeHierarchyFuzz
import io.micronaut.python.compiler.PyronautCompiler

/**
 * Random generic hierarchies declared in Python, their records checked against reflection on the compiled stubs;
 * see {@link TypeHierarchyFuzz}. Python has no wildcards, arrays, raw types or intersection bounds, so the grammar is
 * the smaller one.
 */
class TypeHierarchyFuzzSpec extends AbstractPythonTypeElementSpec {

    static final int CLASSES_PER_SEED = 50

    void "random hierarchies agree with reflection (seed #seed)"() {
        given:
        def generator = new TypeHierarchyFuzz.Generator(seed, TypeHierarchyFuzz.Language.PYTHON)
        def source = generator.source(CLASSES_PER_SEED)
        def classLoader = PyronautCompiler.builder().pythonCode(source).build().buildClassLoader()
        List<String> mismatches = []

        when:
        for (TypeHierarchyFuzz.Generated generated : generator.generated()) {
            Class<?> type = classLoader.loadClass('python.' + generated.name())
            def record = (TypeHierarchy) classLoader.loadClass(TypeHierarchies.recordClassName(type.name, 'python')).getConstructor().newInstance()
            mismatches.addAll(TypeHierarchyFuzz.checkPython(generated, type, record))
        }

        then:
        mismatches.empty

        where:
        seed << (1..20)
    }
}
