package io.micronaut.expressions

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

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

    private static boolean loads(ClassLoader loader, String className) {
        try {
            loader.loadClass(className)
            return true
        } catch (ClassNotFoundException e) {
            return false
        }
    }
}
