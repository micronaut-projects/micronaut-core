package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.beans.BeanIntrospection
import spock.lang.Unroll

/**
 * KSP does not expose context parameters, so the processor finds them in the declaration's source.
 * These cases check that comments, literals, annotations and the other places where {@code context(}
 * can appear neither hide nor fake a context parameter list.
 */
class ContextParametersDetectionSpec extends AbstractKotlinCompilerSpec {

    private static final String MESSAGE = 'Kotlin context parameters are not supported by Micronaut yet: test.Svc.'

    @Unroll
    void 'context parameters are found when #description'() {
        when:
        buildBeanDefinition('test.Svc', """
package test
import jakarta.inject.Singleton
import io.micronaut.context.annotation.Executable

class Logger(val name: String)
class Tx(val id: Int)

@Singleton
open class Svc {
$member
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains(MESSAGE + name)

        where:
        description                                      | name    | member
        'the modifiers span several lines with comments' | 'ctx'   | '''
    /** Not context(log: Logger) but the docs */
    context(
        log: Logger, // the logger )
        /* the ( transaction */ tx: Tx
    )
    @Executable
    open
    fun ctx(a: String): String = log.name + a
'''
        'annotations with arguments precede them'        | 'ctx'   | '''
    @Suppress("context(", "UNUSED") @Executable(processOnStartup = false)
    context(log: Logger) fun ctx(a: String): String = log.name + a
'''
        'their types are generic and function types'     | 'ctx'   | '''
    @Executable
    context(f: (Int) -> Unit, m: Map<String, List<Int>>)
    fun ctx(a: String): String = a
'''
        'the previous member ends with a type argument'  | 'ctx'   | '''
    lateinit var items: List<String>
    context(log: Logger)
    @Executable
    fun ctx(a: String): String = log.name + a
'''
        'the previous member ends with string templates' | 'ctx'   | '''
    val s = "${"}"}${'"'}" + """ ${ "{" } "context(" """
    context(log: Logger) @Executable fun ctx(a: String): String = log.name + a
'''
        'the name is quoted'                             | 'when'  | '''
    @Executable
    context(log: Logger)
    fun `when`(a: String): String = log.name + a
'''
        'the receiver is generic'                        | 'ext'   | '''
    @Executable
    context(log: Logger)
    fun <T : Comparable<T>> List<T>.ext(a: Int): String = log.name + a
'''
        'the name is on the next line'                   | 'split' | '''
    context(log: Logger) @Executable suspend fun
        split(a: String): String = log.name + a
'''
        'the parameter is anonymous'                     | 'anon'  | '''
    @Executable
    context(_: Logger)
    fun anon(a: String): String = a
'''
    }

    void 'context in comments, literals, types and expressions is not a context parameter list'() {
        given:
        ApplicationContext context = buildContext('''
package test
import jakarta.inject.Singleton
import io.micronaut.context.annotation.Executable

class Logger(val name: String)

fun context(a: Int): Int = a

@Singleton
open class Svc {
    // context(log: Logger)
    @Executable
    open fun afterComment(a: String): String = a

    /* context(log: Logger) */ @Executable
    open fun afterBlockComment(a: String): String = a

    val text = "context(log: Logger)"
    @Executable
    open fun afterString(a: String): String = a

    val call = context(1)
    @Executable
    open fun afterCall(a: String): String = a

    val comparison = 1 > context(1)
    @Executable
    open fun afterComparison(a: String): String = a

    val withContext: context(Logger) () -> String = { "" }
    @Executable
    open fun afterFunctionType(a: String): String = a
}
''')
        def definition = context.getBeanDefinition(context.classLoader.loadClass('test.Svc'))
        def bean = context.getBean(definition)

        expect:
        definition.executableMethods*.methodName.toSorted() == ['afterBlockComment', 'afterCall', 'afterComment', 'afterComparison', 'afterFunctionType', 'afterString']
        definition.executableMethods.every { it.invoke(bean, 'a') == 'a' }

        cleanup:
        context.close()
    }

    void 'context in comments, literals and types does not exclude a bean property'() {
        when:
        BeanIntrospection introspection = buildBeanIntrospection('test.Pojo', '''
package test
import io.micronaut.core.annotation.Introspected

class Logger(val name: String)

@Introspected
class Pojo {
    // context(log: Logger)
    var commented: String = "c"

    val text = "context(log: Logger)"

    var withContext: context(Logger) () -> String = { "" }
}
''')

        then:
        introspection.propertyNames.toList() == ['commented', 'text', 'withContext']
    }

    void 'context parameters are found in a source generated by another processor'() {
        when:
        buildBeanDefinition('test.Generated', '''
package test

class Logger(val name: String)
''', [generatingProcessor('Generated', '''
package test
import jakarta.inject.Singleton
import io.micronaut.context.annotation.Executable

@Singleton
class Generated {
    @Executable
    context(log: Logger)
    fun ctx(a: String): String = log.name + a
}
''')])

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Kotlin context parameters are not supported by Micronaut yet: test.Generated.ctx')
    }

    private static SymbolProcessorProvider generatingProcessor(String name, String source) {
        return { SymbolProcessorEnvironment environment ->
            new SymbolProcessor() {
                boolean generated

                @Override
                List<KSAnnotated> process(Resolver resolver) {
                    if (!generated) {
                        generated = true
                        environment.codeGenerator.createNewFile(new Dependencies(false), 'test', name, 'kt').withCloseable {
                            it.write(source.getBytes('UTF-8'))
                        }
                    }
                    return []
                }
            }
        } as SymbolProcessorProvider
    }
}
