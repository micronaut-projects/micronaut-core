package io.micronaut.inject.visitor.beans

import io.micronaut.annotation.processing.test.JavaFileObjects
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

import javax.tools.JavaFileObject

/**
 * The introspection of a {@code @Vetoed} class annotated with an {@code @Introspected} stereotype is
 * generated like any other, even when an introspection of the class is already on the classpath (a
 * recompilation without a clean). The only exception is the vetoed Java class generated for a Python class,
 * marked {@code @PythonClass}: its introspection is generated from the Python class itself, earlier in the
 * same compilation, and the runtime annotations copied onto the generated class must not produce it a second
 * time.
 */
class VetoedIntrospectionSpec extends AbstractTypeElementSpec {

    private static final String PYTHON_CLASS_ANNOTATION = 'io.micronaut.context.python.annotation.PythonClass'

    /**
     * {@link TestBean} is introspected on the test classpath: recompiling a class of that name finds its
     * introspection through the classpath, as a build without a clean does.
     */
    private static final String RECOMPILED_TEST_BEAN = '''
package io.micronaut.inject.visitor.beans;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Vetoed;

@Vetoed
@Introspected
%s
public class TestBean {
    private String title;
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}
'''

    /**
     * A stand-in for the marker of generated Python classes: only its name matters to the visitor, and the
     * real annotation type lives in a module this one does not depend on.
     */
    private static final String PYTHON_CLASS_STUB = '''
package io.micronaut.context.python.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PythonClass {
}
'''

    void "a vetoed Java class recompiled next to an introspection of it on the classpath gets a fresh introspection"() {
        expect:
        introspectionWritten('io.micronaut.inject.visitor.beans.TestBean', RECOMPILED_TEST_BEAN.formatted(''))
    }

    void "the vetoed class generated for a Python class keeps the introspection generated from the Python class"() {
        expect:
        !introspectionWritten('io.micronaut.inject.visitor.beans.TestBean', RECOMPILED_TEST_BEAN.formatted('@' + PYTHON_CLASS_ANNOTATION))
    }

    void "the vetoed class generated for a Python class without an introspection is introspected"() {
        expect:
        introspectionWritten('test.PythonStub', '''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Vetoed;

@Vetoed
@Introspected
@io.micronaut.context.python.annotation.PythonClass
public class PythonStub {
    private String title;
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}
''')
    }

    private boolean introspectionWritten(String className, String source) {
        String introspectionClassFile = className.replace('.', '/').replaceAll('/([^/]+)$', '/\\$$1\\$Introspection.class')
        try (def parser = newJavaParser()) {
            Iterable<? extends JavaFileObject> generated = parser.generate(
                    JavaFileObjects.forSourceString(PYTHON_CLASS_ANNOTATION, PYTHON_CLASS_STUB),
                    JavaFileObjects.forSourceString(className, source)
            )
            return generated.any { it.name.endsWith(introspectionClassFile) }
        }
    }
}
