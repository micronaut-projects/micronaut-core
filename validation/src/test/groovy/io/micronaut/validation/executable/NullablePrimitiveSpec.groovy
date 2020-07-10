package io.micronaut.validation.executable

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import spock.lang.Shared

class NullablePrimitiveSpec extends AbstractTypeElementSpec {

    @Shared
    String errorMsg = "Primitive types can not be null"

    void 'test @Nullable is not allowed on primitive parameters'() {
        when:
        buildTypeElement("""
            package test;

            import io.micronaut.http.annotation.*;

            @Controller("/foo")
            class Foo {

                @Get()
                String abc(@javax.annotation.Nullable boolean boolPrimitive) {
                    return "";
                }
            }
            """.stripIndent()
        )

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains(errorMsg)

        when:
        buildTypeElement("""
            package test;

            import io.micronaut.http.annotation.*;

            @Controller("/foo")
            class Foo {

                @Get()
                String abc(@edu.umd.cs.findbugs.annotations.Nullable boolean boolPrimitive) {
                    return "";
                }
            }
            """.stripIndent()
        )

        then:
        ex = thrown(RuntimeException)
        ex.message.contains(errorMsg)
    }


    void 'test @Nullable is allowed on non-primitie parameters'() {
        when:
        buildTypeElement("""
            package test;

            import io.micronaut.http.annotation.*;

            @Controller("/foo")
            class Foo {

                @Get()
                String abc(@javax.annotation.Nullable Boolean boolType) {
                    return "";
                }
            }
            """.stripIndent()
        )

        then:
        noExceptionThrown()

        when:
        buildTypeElement("""
            package test;

            import io.micronaut.http.annotation.*;

            @Controller("/foo")
            class Foo {

                @Get()
                String abc(@edu.umd.cs.findbugs.annotations.Nullable Boolean boolType) {
                    return "";
                }
            }
            """.stripIndent()
        )

        then:
        noExceptionThrown()
    }

    void 'test @Nullable is allowed primitive parameters and not @Executable classes'() {
        when:
        buildTypeElement("""
            package test;

            @javax.inject.Singleton
            class Foo {
                void bar(@javax.annotation.Nullable int n) {
                }
            }
            """.stripIndent()
        )

        then:
        noExceptionThrown()


        when:
        buildTypeElement("""
            package test;

            @javax.inject.Singleton
            class Foo {
                void bar(@edu.umd.cs.findbugs.annotations.Nullable int n) {
                }
            }
            """.stripIndent()
        )

        then:
        noExceptionThrown()
    }

}
