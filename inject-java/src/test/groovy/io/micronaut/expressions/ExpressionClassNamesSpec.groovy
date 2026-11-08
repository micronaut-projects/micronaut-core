package io.micronaut.expressions

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.annotation.EvaluatedExpressionReferenceCounter

class ExpressionClassNamesSpec extends AbstractTypeElementSpec {

    void "a class compiled again by processors whose classes stay loaded keeps the names of its expression classes"() {
        given: "the processors of this JVM, which compile every class of this spec, as a build tool's daemon keeps them"
        String source = """
            package test;
            import io.micronaut.context.annotation.Value;
            import jakarta.inject.Singleton;

            @Singleton
            class Named {
                @Value("#{ 1 + 1 }")
                public int first;

                @Value("#{ 2 + 2 }")
                public int second;
            }
        """

        when:
        ClassLoader first = buildClassLoader("test.Named", source)
        ClassLoader second = buildClassLoader("test.Named", source)

        then: "each compilation numbers the expressions of the class from 0"
        [first, second].every { loader ->
            loader.loadClass('test.$Named$Expr0') && loader.loadClass('test.$Named$Expr1') && !loads(loader, 'test.$Named$Expr2')
        }
    }

    void "compilations that overlap in one loader keep numbering on, so that none reuses a name another gave"() {
        given: "another compilation of these processors runs meanwhile"
        Object other = new Object()
        EvaluatedExpressionReferenceCounter.compilationStarted(other)
        String source = """
            package test;
            import io.micronaut.context.annotation.Value;
            import jakarta.inject.Singleton;

            @Singleton
            class Overlapping {
                @Value("#{ 1 + 1 }")
                public int first;
            }
        """

        when:
        ClassLoader first = buildClassLoader("test.Overlapping", source)
        ClassLoader second = buildClassLoader("test.Overlapping", source)

        then:
        first.loadClass('test.$Overlapping$Expr0')
        second.loadClass('test.$Overlapping$Expr1')

        cleanup:
        EvaluatedExpressionReferenceCounter.compilationFinished(other)
    }

    private static boolean loads(ClassLoader loader, String className) {
        try {
            loader.loadClass(className)
            return true
        } catch (ClassNotFoundException e) {
            return false
        }
    }
}
