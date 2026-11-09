package io.micronaut.kotlin.processing.expressions

import spock.lang.Specification

import static io.micronaut.annotation.processing.test.KotlinCompiler.buildContext

class ExpressionClassNamesSpec extends Specification {

    void "the erased signature of a function tells apart the expression classes of its overloads and parameters"() {
        given:
        def ctx = buildContext('''
            package test

            import io.micronaut.context.annotation.Executable
            import io.micronaut.context.annotation.Value
            import jakarta.inject.Inject
            import jakarta.inject.Singleton

            @Singleton
            open class Shapes {

                @set:Inject
                @setparam:Value("#{ 5 + 5 }")
                open var mutable: String = ""

                @Executable
                open fun one(@Value("#{ 1 + 1 }") a: Array<String>) {
                }

                @Executable
                open fun one(@Value("#{ 1 + 1 }") a: Array<Int>) {
                }

                @Executable
                open fun one(@Value("#{ 1 + 1 }") a: String) {
                }

                @Executable
                open fun many(@Value("#{ 2 + 2 }") vararg a: String) {
                }

                @Executable
                open fun <T : CharSequence> bounded(@Value("#{ 3 + 3 }") t: T) {
                }

                @Executable
                open fun <T> unbounded(@Value("#{ 3 + 3 }") t: T) {
                }

                @Executable
                open fun String.extended(@Value("#{ 4 + 4 }") a: String) {
                }
            }
        ''')

        when:
        def names = classNames(ctx.classLoader, 'test', '$Shapes$Expr$')

        then: "each overload, whether its parameter is an array, a vararg, a type parameter or one of an extension, has a class of its own"
        names.size() == 8
        names.count { hashed(it, '$Shapes$Expr$method$one$param0$Value$value$') } == 3
        ['many', 'bounded', 'unbounded', 'extended'].every { function ->
            names.count { hashed(it, "\$Shapes\$Expr\$method\$${function}\$param0\$Value\$value\$") } == 1
        }

        and: "a setter parameter is named after its property"
        names.count { hashed(it, '$Shapes$Expr$setter$mutable$param0$Value$value$') } == 1

        cleanup:
        ctx.close()
    }

    private static boolean hashed(String name, String prefix) {
        return name.startsWith(prefix) && name.substring(prefix.length()) ==~ /\p{XDigit}{16}/
    }

    private static List<String> classNames(ClassLoader loader, String packageName, String prefix) {
        return ((URLClassLoader) loader).URLs
                .collect { new File(new File(it.toURI()), packageName) }
                .findAll { it.directory }
                .collectMany { dir -> dir.list().findAll { it.startsWith(prefix) && it.endsWith('.class') }*.minus('.class') }
                .unique()
    }
}
