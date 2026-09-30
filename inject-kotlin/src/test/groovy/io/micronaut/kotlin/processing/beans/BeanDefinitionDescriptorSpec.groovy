package io.micronaut.kotlin.processing.beans

import io.micronaut.annotation.processing.test.KotlinCompiler
import io.micronaut.inject.writer.AbstractBeanDefinitionDescriptorSpec

class BeanDefinitionDescriptorSpec extends AbstractBeanDefinitionDescriptorSpec {

    @Override
    protected URL[] compile() {
        // the classes and the resources KSP generates are written to directories
        return KotlinCompiler.buildClassLoader('test.Beans', BEANS).URLs.findAll { new File(it.toURI()).isDirectory() }.unique() as URL[]
    }

    @Override
    protected List<String> getModuleDefinitions() {
        // compiled with the test classes of the module: a bean, a bean of a factory and a proxy
        return [
            'io.micronaut.kotlin.processing.aop.simple.$AnotherClass$Definition',
            'io.micronaut.kotlin.processing.aop.factory.$InterfaceFactory$InterfaceClass0$Definition',
            'io.micronaut.kotlin.processing.aop.factory.$InterfaceFactory$InterfaceClass0$Definition$Intercepted$Definition'
        ]
    }

    private static final String BEANS = '''
package test

import io.micronaut.aop.Around
import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Executable
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.NonBinding
import io.micronaut.context.annotation.Parallel
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Prototype
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Indexed
import io.micronaut.core.annotation.Introspected
import jakarta.inject.Named
import jakarta.inject.Qualifier
import jakarta.inject.Singleton
import kotlin.reflect.KClass

interface Api

interface Other

@Singleton
class Plain : Api

@Context
class Eager

@Parallel
@Singleton
class InParallel

@Primary
@Singleton
class First : Api

@Prototype
class Each

@Singleton
@Bean(typed = [Api::class])
@Indexed(Other::class)
class Typed : Api, Other

@ConfigurationProperties("descriptor.settings")
class Settings {
    var name: String? = null
}

@Singleton
class Startup {
    @Executable(processOnStartup = true)
    fun run() {
    }
}

@Introspected
class Data

@Retention(AnnotationRetention.RUNTIME)
annotation class Marker

@Marker
@Singleton
@Retention(AnnotationRetention.RUNTIME)
annotation class Stereotyped

@Retention(AnnotationRetention.SOURCE)
annotation class Draft

@JvmRepeatable(Tags::class)
@Retention(AnnotationRetention.RUNTIME)
annotation class Tag(val value: String)

@Retention(AnnotationRetention.RUNTIME)
annotation class Tags(val value: Array<Tag>)

@Stereotyped
@Draft
@Tag("a")
@Tag("b")
class Tagged

enum class Mode {
    SOLID, STRIPED
}

@Retention(AnnotationRetention.RUNTIME)
annotation class Detail(val value: String)

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Colored(
    val name: String,
    val shade: Int = 0,
    val dark: Boolean = false,
    val mode: Mode = Mode.SOLID,
    val type: KClass<*> = Any::class,
    val tags: Array<String> = [],
    val levels: IntArray = [],
    val detail: Detail = Detail("none"),
    @get:NonBinding val comment: String = ""
)

@Singleton
@Colored(name = "red", shade = 3, dark = true, mode = Mode.STRIPED, type = String::class, tags = ["a", "b"], levels = [1, 2], detail = Detail("fine"), comment = "ignored")
class Painted

@Singleton
@Colored(name = "#{ 'dyn' + 'amic' }")
class Dynamic

@Singleton
@Named("one")
class NamedOne : Api

@Singleton
@Requires(property = "descriptor.enabled", value = "true", defaultValue = "true")
@Requires(missingProperty = "descriptor.disabled")
@Requires(env = ["test"])
@Requires(notEnv = ["cloud"])
@Requires(classes = [String::class])
@Requires(missingClasses = ["test.Missing"])
@Requires(entities = [Marker::class])
@Requires(configuration = "test")
@Requires(sdk = Requires.Sdk.JAVA, version = "17")
@Requires(resources = ["classpath:descriptor.txt"])
@Requires(os = [Requires.Family.LINUX, Requires.Family.MAC_OS, Requires.Family.WINDOWS, Requires.Family.SOLARIS, Requires.Family.OTHER])
@Requires(notOs = [Requires.Family.SOLARIS])
@Requires(beans = [Plain::class])
@Requires(missingBeans = [Eager::class])
class Conditional

class Product

@Factory
@Indexed(Other::class)
class Products {
    @Singleton
    @Named("first")
    fun first(): Product {
        return Product()
    }

    @Singleton
    fun all(): Array<Product> {
        return arrayOf()
    }
}

@Around
@Retention(AnnotationRetention.RUNTIME)
annotation class Traced

@Singleton
@InterceptorBean(Traced::class)
class TracedInterceptor : MethodInterceptor<Any, Any> {
    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        return context.proceed().toString() + " traced"
    }
}

@Singleton
@Traced
open class Advised {
    open fun hello(): String {
        return "hello"
    }
}
'''
}
