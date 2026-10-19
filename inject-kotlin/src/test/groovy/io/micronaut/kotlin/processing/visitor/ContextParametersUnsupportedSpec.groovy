package io.micronaut.kotlin.processing.visitor

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.inject.BeanDefinition

/**
 * KSP 2.3 does not expose Kotlin context parameters, so the processor would model
 * {@code context(log: Logger) fun ctx(a: String)} as {@code ctx(String)} while the JVM method is
 * {@code ctx(Logger, String)}. Code generated for such a member fails at runtime or, for around advice,
 * silently does not intercept. Until KSP exposes them the processor refuses to generate code for them.
 */
class ContextParametersUnsupportedSpec extends AbstractKotlinCompilerSpec {

    private static final String MESSAGE = 'Kotlin context parameters are not supported by Micronaut yet: '

    void 'an executable method with context parameters fails the compilation'() {
        when:
        buildBeanDefinition('test.Svc', '''
package test
import jakarta.inject.Singleton
import io.micronaut.context.annotation.Executable

class Logger(val name: String)

@Singleton
open class Svc {
    @Executable
    context(log: Logger)
    open fun ctx(a: String): String = log.name + a
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains(MESSAGE + 'test.Svc.ctx')
        e.message.contains('Svc.kt:11: ' + MESSAGE)
    }

    void 'an executable extension method with context parameters fails the compilation'() {
        when:
        buildBeanDefinition('test.Svc', '''
package test
import jakarta.inject.Singleton
import io.micronaut.context.annotation.Executable

class Logger(val name: String)

@Singleton
class Svc {
    @Executable
    context(log: Logger)
    fun String.ext(a: Int): String = log.name + this + a
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains(MESSAGE + 'test.Svc.ext')
    }

    void 'a suspend executable method with context parameters fails the compilation'() {
        when:
        buildBeanDefinition('test.Svc', '''
package test
import jakarta.inject.Singleton
import io.micronaut.context.annotation.Executable

class Logger(val name: String)

@Singleton
class Svc {
    @Executable
    context(log: Logger)
    suspend fun sus(a: String): String = log.name + a
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains(MESSAGE + 'test.Svc.sus')
    }

    void 'an inject method with context parameters fails the compilation'() {
        when:
        buildBeanDefinition('test.Svc', '''
package test
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class Logger
@Singleton
class Dep

@Singleton
open class Svc {
    var dep: Dep? = null

    @Inject
    context(log: Logger)
    open fun setDependency(dep: Dep) {
        this.dep = dep
    }
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains(MESSAGE + 'test.Svc.setDependency')
    }

    void 'a factory method with context parameters fails the compilation'() {
        when:
        buildBeanDefinition('test.Fac', '''
package test
import jakarta.inject.Singleton
import io.micronaut.context.annotation.Factory

@Singleton
class Logger { val name = "L" }
class Product(val v: String)

@Factory
class Fac {
    @Singleton
    context(log: Logger)
    fun product(): Product = Product(log.name)
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains(MESSAGE + 'test.Fac.product')
    }

    void 'an event listener with context parameters fails the compilation'() {
        when:
        buildBeanDefinition('test.Listener', '''
package test
import jakarta.inject.Singleton
import io.micronaut.runtime.event.annotation.EventListener

@Singleton
class Logger { val name = "L" }
class MyEvent(val v: String)

@Singleton
class Listener {
    @EventListener
    context(log: Logger)
    fun on(e: MyEvent) {
    }
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains(MESSAGE + 'test.Listener.on')
    }

    void 'a method advised by around advice with context parameters fails the compilation'() {
        when:
        buildBeanDefinition('test.Svc', '''
package test
import io.micronaut.aop.Around
import jakarta.inject.Singleton

class Logger(val name: String)

@Retention
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Around
annotation class Trace

@Singleton
open class Svc {
    @Trace
    context(log: Logger)
    open fun ctx(a: String): String = log.name + a

    @Trace
    open fun plain(a: String): String = a
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains(MESSAGE + 'test.Svc.ctx')
    }

    void 'a public method of a class advised by around advice with context parameters fails the compilation'() {
        when:
        buildBeanDefinition('test.Svc', '''
package test
import io.micronaut.aop.Around
import jakarta.inject.Singleton

class Logger(val name: String)

@Retention
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Around
annotation class Trace

@Singleton
@Trace
open class Svc {
    context(log: Logger)
    open fun ctx(a: String): String = log.name + a

    open fun plain(a: String): String = a
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains(MESSAGE + 'test.Svc.ctx')
    }

    void 'a public property of a class advised by around advice with context parameters fails the compilation'() {
        when:
        buildBeanDefinition('test.Svc', '''
package test
import io.micronaut.aop.Around
import jakarta.inject.Singleton

class Logger(val name: String)

@Retention
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Around
annotation class Trace

@Singleton
@Trace
open class Svc {
    context(log: Logger)
    open val cp: String get() = log.name

    open fun plain(a: String): String = a
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains(MESSAGE + 'test.Svc.cp')
    }

    void 'an overload that differs only by context parameters in a class advised by around advice fails the compilation'() {
        when:
        buildBeanDefinition('test.Svc', '''
package test
import io.micronaut.aop.Around
import jakarta.inject.Singleton

class Logger(val name: String)

@Retention
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Around
annotation class Trace

@Singleton
@Trace
open class Svc {
    open fun foo(a: String): String = a

    @Suppress("CONTEXTUAL_OVERLOAD_SHADOWED")
    context(log: Logger)
    open fun foo(a: String): String = log.name + a
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains(MESSAGE + 'test.Svc.foo')
    }

    void 'an introduction method with context parameters fails the compilation'() {
        when:
        buildBeanDefinition('test.$Api$Intercepted', '''
package test
import io.micronaut.aop.Introduction

class Logger(val name: String)

@Retention
@Target(AnnotationTarget.CLASS)
@Introduction
annotation class Stub

@Stub
interface Api {
    context(log: Logger)
    fun ctx(a: String): String

    fun plain(a: String): String
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains(MESSAGE + 'test.Api.ctx')
    }

    void 'a public method of a class with executable methods with context parameters fails the compilation'() {
        when:
        buildBeanDefinition('test.Ctrl', '''
package test
import io.micronaut.context.annotation.Executable
import jakarta.inject.Singleton

class Logger(val name: String)

@Retention
@Target(AnnotationTarget.CLASS)
@Executable
annotation class Endpoint

@Singleton
@Endpoint
class Ctrl {
    context(log: Logger)
    fun index(a: String): String = log.name + a

    fun plain(a: String): String = a
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains(MESSAGE + 'test.Ctrl.index')
    }

    void 'an introspected bean method with context parameters fails the compilation'() {
        when:
        buildBeanIntrospection('test.Pojo', '''
package test
import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Introspected

class Logger(val name: String)

@Introspected
class Pojo(val name: String) {
    @Executable
    context(log: Logger)
    fun describe(): String = log.name + name
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains(MESSAGE + 'test.Pojo.describe')
    }

    void 'a property with context parameters is not a bean property'() {
        when:
        BeanIntrospection introspection = buildBeanIntrospection('test.Pojo', '''
package test
import io.micronaut.core.annotation.Introspected

class Logger(val name: String)

@Introspected
class Pojo {
    var name: String = "n"

    context(log: Logger)
    val cp: String get() = log.name + name

    context(log: Logger)
    var cv: String
        get() = log.name
        set(value) {}
}
''')
        def bean = introspection.instantiate()

        then:
        introspection.propertyNames.toList() == ['name']
        introspection.getRequiredProperty('name', String).get(bean) == 'n'
    }

    void 'context parameters on members that Micronaut does not generate code for compile and run'() {
        given:
        ApplicationContext context = buildContext('''
package test
import io.micronaut.context.annotation.Executable
import jakarta.inject.Singleton

class Logger(val name: String)

context(log: Logger)
fun topLevel(a: String): String = log.name + a

class Helper {
    context(log: Logger)
    fun help(a: String): String = log.name + a
}

@Singleton
open class Svc {
    context(log: Logger)
    fun member(a: String): String = log.name + a

    context(log: Logger)
    private fun privateMember(a: String): String = log.name + a

    context(log: Logger)
    val prop: String get() = log.name

    @Executable
    open fun run(a: String): String = with(Logger("L:")) {
        topLevel(a) + Helper().help(a) + member(a) + privateMember(a) + prop
    }
}
''')
        def definition = getBeanDefinition(context, 'test.Svc')
        def bean = context.getBean(definition)

        expect:
        bean.run('a') == 'L:aL:aL:aL:aL:'
        definition.executableMethods*.methodName == ['run']

        cleanup:
        context.close()
    }

    private static BeanDefinition getBeanDefinition(ApplicationContext context, String name) {
        context.getBeanDefinition(context.classLoader.loadClass(name))
    }
}
