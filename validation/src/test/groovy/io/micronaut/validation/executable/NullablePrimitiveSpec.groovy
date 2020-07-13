package io.micronaut.validation.executable

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import spock.lang.Shared
import spock.lang.Unroll

class NullablePrimitiveSpec extends AbstractTypeElementSpec {

    @Shared
    String warning = "@Nullable on primitive types will allow the method to be executed at runtime with null values"

    PrintStream old
    ByteArrayOutputStream out

    void setup() {
        old = System.out
        out = new ByteArrayOutputStream()
        System.out = new PrintStream(out)
    }

    void cleanup() {
        System.out = old
    }

    @Unroll
    void 'test #annotation on primitive params will show a warning'() {
        when:
        buildTypeElement("""
            package test;

            import io.micronaut.http.annotation.*;

            @Controller("/foo")
            class Foo {

                @Get()
                String abc(${annotation} boolean boolPrimitive) {
                    return "";
                }
            }
            """.stripIndent()
        )
        String output = out.toString("UTF8")

        then:
        noExceptionThrown()
        output.contains(warning)

        where:
        annotation << ['@javax.annotation.Nullable', '@edu.umd.cs.findbugs.annotations.Nullable']
    }

    @Unroll
    void 'test #annotation is allowed on non-primitive parameters'() {
        when:
        buildTypeElement("""
            package test;

            import io.micronaut.http.annotation.*;

            @Controller("/foo")
            class Foo {

                @Get()
                String abc(${annotation} Boolean boolType) {
                    return "";
                }
            }
            """.stripIndent()
        )
        String output = out.toString("UTF8")

        then:
        noExceptionThrown()
        !output.contains(warning)

        where:
        annotation << ['@javax.annotation.Nullable', '@edu.umd.cs.findbugs.annotations.Nullable']
    }

    @Unroll
    void 'test #annotation is allowed primitive parameters and not @Executable classes'() {
        when:
        buildTypeElement("""
            package test;

            @javax.inject.Singleton
            class Foo {
                void bar(${annotation} int n) {
                }
            }
            """.stripIndent()
        )
        String output = out.toString("UTF8")

        then:
        noExceptionThrown()
        !output.contains(warning)

        where:
        annotation << ['@javax.annotation.Nullable', '@edu.umd.cs.findbugs.annotations.Nullable']
    }

}
