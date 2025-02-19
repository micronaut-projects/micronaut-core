package io.micronaut.expressions

import io.micronaut.annotation.processing.test.AbstractEvaluatedExpressionsSpec;


class MethodArgumentEvaluationContextExpressionsSpec extends AbstractEvaluatedExpressionsSpec {

    void "test compare >"() {
        given:
        Object result = evaluateSingle("test.Expr", """
            package test;
            import io.micronaut.context.annotation.Executable;
            import io.micronaut.context.annotation.Requires;
            import jakarta.inject.Singleton;

            @Singleton
            class Expr {

                @Executable
                @Requires(value = "#{ #a > #b }")
                public void nn(String a, String b) {
                }
            }

        """, ["two", "one"] as Object[])

        expect:
        result instanceof Boolean && result
    }

    void "test compare <"() {
        given:
        Object result = evaluateSingle("test.Expr", """
            package test;
            import io.micronaut.context.annotation.Executable;
            import io.micronaut.context.annotation.Requires;
            import jakarta.inject.Singleton;

            @Singleton
            class Expr {

                @Executable
                @Requires(value = "#{ #a < #b }")
                public void nn(String a, String b) {
                }
            }

        """, ["two", "one"] as Object[])

        expect:
        result instanceof Boolean && !result
    }

    void "test compare <="() {
        given:
        Object result = evaluateSingle("test.Expr", """
            package test;
            import io.micronaut.context.annotation.Executable;
            import io.micronaut.context.annotation.Requires;
            import jakarta.inject.Singleton;

            @Singleton
            class Expr {

                @Executable
                @Requires(value = "#{ #a <= #b }")
                public void nn(String a, String b) {
                }
            }

        """, ["two", "one"] as Object[])

        expect:
        result instanceof Boolean && !result
    }

    void "test compare >="() {
        given:
        Object result = evaluateSingle("test.Expr", """
            package test;
            import io.micronaut.context.annotation.Executable;
            import io.micronaut.context.annotation.Requires;
            import jakarta.inject.Singleton;

            @Singleton
            class Expr {

                @Executable
                @Requires(value = "#{ #a >= #b }")
                public void nn(String a, String b) {
                }
            }

        """, ["two", "one"] as Object[])

        expect:
        result instanceof Boolean && result
    }

    void "test compare =="() {
        given:
        Object result = evaluateSingle("test.Expr", """
            package test;
            import io.micronaut.context.annotation.Executable;
            import io.micronaut.context.annotation.Requires;
            import jakarta.inject.Singleton;

            @Singleton
            class Expr {

                @Executable
                @Requires(value = "#{ #a == #b }")
                public void nn(String a, String b) {
                }
            }

        """, ["two", "one"] as Object[])

        expect:
        result instanceof Boolean && !result
    }

    void "test compare !="() {
        given:
        Object result = evaluateSingle("test.Expr", """
            package test;
            import io.micronaut.context.annotation.Executable;
            import io.micronaut.context.annotation.Requires;
            import jakarta.inject.Singleton;

            @Singleton
            class Expr {

                @Executable
                @Requires(value = "#{ #a != #b }")
                public void nn(String a, String b) {
                }
            }

        """, ["two", "one"] as Object[])

        expect:
        result instanceof Boolean && result
    }

    void "test method argument access"() {
        given:
        Object result = evaluateSingle("test.Expr", """
            package test;
            import io.micronaut.context.annotation.Executable;
            import io.micronaut.context.annotation.Requires;
            import jakarta.inject.Singleton;

            @Singleton
            class Expr {

                @Executable
                @Requires(value = "#{ #second + 'abc' }")
                void test(String first, String second) {
                }
            }


        """, ["arg0", "arg1"] as Object[])

        expect:
        result instanceof String && result == 'arg1abc'
    }

    void "test wrapped value method argument access"() {
        given:
        Object result = evaluateSingle("test.Expr", """
            package test;
            import io.micronaut.context.annotation.Executable;
            import io.micronaut.context.annotation.Requires;
            import jakarta.inject.Singleton;

            @Singleton
            class Expr {

                @Executable
                @Requires(value = "#{ #second + 'abc' }")
                void test(String first, Integer second) {
                }
            }


        """, ["arg0", 100] as Object[])

        expect:
        result instanceof String && result == '100abc'
    }

    void "test primitive value method argument access"() {
        given:
        Object result = evaluateSingle("test.Expr", """
            package test;
            import io.micronaut.context.annotation.Executable;
            import io.micronaut.context.annotation.Requires;
            import jakarta.inject.Singleton;

            @Singleton
            class Expr {

                @Executable
                @Requires(value = "#{ #second + 'abc' }")
                void test(String first, int second) {
                }
            }


        """, ["arg0", 100] as Object[])

        expect:
        result instanceof String && result == '100abc'
    }
}
