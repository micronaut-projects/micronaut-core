package io.micronaut.expressions

import io.micronaut.annotation.processing.test.AbstractEvaluatedExpressionsSpec

class AnnotationLevelContextExpressionsSpec extends AbstractEvaluatedExpressionsSpec {

    void "test annotation level context"() {
        given:
        Object result = evaluateSingle("test.Expr", """

            package test;
            import io.micronaut.context.annotation.AnnotationExpressionContext;
            import io.micronaut.context.annotation.Executable;
            import io.micronaut.context.annotation.Requires;
            import jakarta.inject.Singleton;

            @Singleton
            @CustomAnnotation("#{ #getAnnotationLevelValue() }")
            class Expr {
            }

            @Singleton
            class CustomContext {
                String getAnnotationLevelValue() {
                    return "annotationLevelValue";
                }
            }

            @AnnotationExpressionContext(CustomContext.class)
            @interface CustomAnnotation {
                String value();
            }

        """)

        expect:
        result instanceof String && result == "annotationLevelValue"

    }

    void "test annotation member level context"() {
        given:
        Object result = evaluateSingle("test.Expr", """

            package test;
            import io.micronaut.context.annotation.AnnotationExpressionContext;
            import io.micronaut.context.annotation.Executable;
            import io.micronaut.context.annotation.Requires;
            import jakarta.inject.Singleton;

            @Singleton
            @CustomAnnotation(customValue = "#{ #getAnnotationLevelValue() }")
            class Expr {
            }

            @Singleton
            class CustomContext {
                String getAnnotationLevelValue() {
                    return "annotationLevelValue";
                }
            }

            @interface CustomAnnotation {
                @AnnotationExpressionContext(CustomContext.class)
                String customValue();
            }

        """)

        expect:
        result instanceof String && result == "annotationLevelValue"

    }

    void "test method parameters excluded from the annotation level context"() {
        given:
        Object result = evaluateSingle("test.Expr", """

            package test;
            import io.micronaut.context.annotation.AnnotationExpressionContext;
            import io.micronaut.context.annotation.Executable;
            import jakarta.inject.Singleton;

            @Singleton
            class Expr {
                @Executable
                @CustomAnnotation("#{ value }")
                void method(String value) {
                }
            }

            @Singleton
            class CustomContext {
                public String getValue() {
                    return "contextValue";
                }
            }

            @AnnotationExpressionContext(value = CustomContext.class, methodArguments = false)
            @interface CustomAnnotation {
                String value();
            }

        """)

        expect:
        result == "contextValue"
    }

    void "test method parameters excluded from the annotation member level context"() {
        given:
        Object result = evaluateSingle("test.Expr", """

            package test;
            import io.micronaut.context.annotation.AnnotationExpressionContext;
            import io.micronaut.context.annotation.Executable;
            import jakarta.inject.Singleton;

            @Singleton
            class Expr {
                @Executable
                @CustomAnnotation(customValue = "#{ value }")
                void method(String value) {
                }
            }

            @Singleton
            class CustomContext {
                public String getValue() {
                    return "contextValue";
                }
            }

            @interface CustomAnnotation {
                @AnnotationExpressionContext(value = CustomContext.class, methodArguments = false)
                String customValue();
            }

        """)

        expect:
        result == "contextValue"
    }

    void "test method parameters included in the annotation level context by default"() {
        given:
        Object result = evaluateSingle("test.Expr", """

            package test;
            import io.micronaut.context.annotation.AnnotationExpressionContext;
            import io.micronaut.context.annotation.Executable;
            import jakarta.inject.Singleton;

            @Singleton
            class Expr {
                @Executable
                @CustomAnnotation("#{ #getContextValue() + argument }")
                void method(String argument) {
                }
            }

            @Singleton
            class CustomContext {
                public String getContextValue() {
                    return "contextValue";
                }
            }

            @AnnotationExpressionContext(CustomContext.class)
            @interface CustomAnnotation {
                String value();
            }

        """, ["Argument"] as Object[])

        expect:
        result == "contextValueArgument"
    }

    void "test method parameters excluded from the annotation level context of a static method"() {
        given:
        Object result = evaluateSingle("test.Expr", """

            package test;
            import io.micronaut.context.annotation.AnnotationExpressionContext;
            import io.micronaut.context.annotation.Executable;
            import jakarta.inject.Singleton;

            @Singleton
            class Expr {
                @Executable
                @CustomAnnotation("#{ value }")
                static void method(String value) {
                }
            }

            @Singleton
            class CustomContext {
                public String getValue() {
                    return "contextValue";
                }
            }

            @AnnotationExpressionContext(value = CustomContext.class, methodArguments = false)
            @interface CustomAnnotation {
                String value();
            }

        """)

        expect:
        result == "contextValue"
    }
}
