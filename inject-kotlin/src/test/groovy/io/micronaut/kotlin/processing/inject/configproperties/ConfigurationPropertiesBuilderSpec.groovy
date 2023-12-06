package io.micronaut.kotlin.processing.inject.configproperties

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.InstantiatableBeanDefinition
import org.neo4j.driver.Config
import spock.lang.Specification
import static io.micronaut.annotation.processing.test.KotlinCompiler.*

class ConfigurationPropertiesBuilderSpec extends Specification {

    void "test configuration builder on method"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test

import io.micronaut.context.annotation.*

@ConfigurationProperties("test")
class MyProperties {

    @ConfigurationBuilder(factoryMethod="build")
    var test: Test? = null
}

class Test private constructor() {

    var foo: String? = null

    companion object {
        @JvmStatic
        fun build(): Test {
            return Test()
        }
    }
}
''')

        when:"The bean was built and a warning was logged"
        InstantiatableBeanDefinition factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.run(
                'test.foo':'good'
        )
        def bean = factory.instantiate(applicationContext)

        then:
        bean.test.foo == 'good'
    }

//    void "test configuration builder with includes"() {
//        given:
//        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
//package test
//
//import io.micronaut.context.annotation.*
//
//@ConfigurationProperties("test")
//class MyProperties {
//
//    @ConfigurationBuilder(factoryMethod="build", includes=["foo"])
//    var test: Test? = null
//}
//
//class Test private constructor() {
//
//    var foo: String? = null
//    var bar: String? = null
//
//    companion object {
//        @JvmStatic
//        fun build(): Test {
//            return Test()
//        }
//    }
//}
//''')
//
//        when:"The bean was built and a warning was logged"
//        InstantiatableBeanDefinition factory = beanDefinition
//        ApplicationContext applicationContext = ApplicationContext.run(
//                'test.foo':'good',
//                'test.bar':'bad'
//        )
//        def bean = factory.instantiate(applicationContext)
//
//        then:
//        bean.test.foo == 'good'
//        bean.test.bar == null
//    }

    void "test catch and log NoSuchMethodError for when underlying builder changes"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test

import io.micronaut.context.annotation.*

@ConfigurationProperties("test")
class MyProperties {

    @ConfigurationBuilder
    var test = Test()
}

class Test {
    fun setFoo(s: String) {
        throw NoSuchMethodError("setFoo")
    }
}
''')

        expect:"The bean was built and a warning was logged"
        InstantiatableBeanDefinition factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.run(
                'test.foo':'good',
        )
        factory.instantiate(applicationContext)
    }

    void "test with setters that return void"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test

import io.micronaut.context.annotation.*

@ConfigurationProperties("test")
class MyProperties {

    @ConfigurationBuilder
    var test = Test()
}

class Test {
    var foo: String? = null
    var bar: Int = 0
    @Deprecated("message")
    var baz: Long? = null
}
''')

        when:
        InstantiatableBeanDefinition factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.run(
                'test.foo':'good',
                'test.bar': '10',
                'test.baz':'20'
        )
        def bean = factory.instantiate(applicationContext)

        then:
        bean != null
        bean.test != null

        when:
        def test = bean.test

        then:
        test.foo == 'good'
        test.bar == 10
        test.baz == null //deprecated properties not settable
    }

    void "test different inject types for config properties"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.Neo4jProperties', '''
package test

import io.micronaut.context.annotation.*
import org.neo4j.driver.*

@ConfigurationProperties("neo4j.test")
class Neo4jProperties {
    protected var uri: java.net.URI? = null

    @ConfigurationBuilder(
        prefixes=["with"],
        allowZeroArgs=true
    )
    var options: Config.ConfigBuilder = Config.builder()
}
''')
        then:
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods.first().name == 'setUri'

        when:
        InstantiatableBeanDefinition factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.run(
                'neo4j.test.encryptionLevel':'none',
                'neo4j.test.leakedSessionsLogging':true,
                'neo4j.test.maxConnectionPoolSize':2
        )
        def bean = factory.instantiate(applicationContext)

        then:
        bean != null
        bean.options != null

        when:
        Config config = bean.options.build()

        then:
        config.maxConnectionPoolSize() == 2
        !config.encrypted()
        config.logLeakedSessions()
    }

    void "test specifying a configuration prefix"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.Neo4jProperties', '''
package test

import io.micronaut.context.annotation.*
import org.neo4j.driver.*

@ConfigurationProperties("neo4j.test")
class Neo4jProperties {
    protected var uri: java.net.URI? = null

    @ConfigurationBuilder(
        prefixes=["with"],
        allowZeroArgs=true,
        configurationPrefix="options"
    )
    var options: Config.ConfigBuilder = Config.builder()
}
''')
        then:
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods.first().name == 'setUri'

        when:
        InstantiatableBeanDefinition factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.run(
                'neo4j.test.options.encryptionLevel':'none',
                'neo4j.test.options.leakedSessionsLogging':true,
                'neo4j.test.options.maxConnectionPoolSize':2
        )
        def bean = factory.instantiate(applicationContext)

        then:
        bean != null
        bean.options != null

        when:
        Config config = bean.options.build()

        then:
        config.maxConnectionPoolSize() == 2
        !config.encrypted()
        config.logLeakedSessions()
    }

    void "test specifying a configuration prefix with value"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.Neo4jProperties', '''
package test

import io.micronaut.context.annotation.*
import org.neo4j.driver.*

@ConfigurationProperties("neo4j.test")
class Neo4jProperties {
    protected var uri: java.net.URI? = null

    @ConfigurationBuilder(
        prefixes=["with"],
        allowZeroArgs=true,
        value="options"
    )
    var options: Config.ConfigBuilder = Config.builder()
}
''')
        then:
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods.first().name == 'setUri'

        when:
        InstantiatableBeanDefinition factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.run(
                'neo4j.test.options.encryptionLevel':'none',
                'neo4j.test.options.leakedSessionsLogging':true,
                'neo4j.test.options.maxConnectionPoolSize':2
        )
        def bean = factory.instantiate(applicationContext)

        then:
        bean != null
        bean.options != null

        when:
        Config config = bean.options.build()

        then:
        config.maxConnectionPoolSize() == 2
        !config.encrypted()
        config.logLeakedSessions()
    }

    void "test specifying a configuration prefix with value using @AccessorsStyle"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.Neo4jProperties', '''
package test;

import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.AccessorsStyle;
import org.neo4j.driver.*;

@ConfigurationProperties("neo4j.test")
class Neo4jProperties {
    protected var uri: java.net.URI? = null

    @ConfigurationBuilder(
        allowZeroArgs = true,
        value = "options"
    )
    @AccessorsStyle(writePrefixes = ["with"])
    var options: Config.ConfigBuilder = Config.builder()
}
''')
        then:
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods.first().name == 'setUri'

        when:
        InstantiatableBeanDefinition factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.run(
            'neo4j.test.options.encryptionLevel':'none',
            'neo4j.test.options.leakedSessionsLogging':true,
            'neo4j.test.options.maxConnectionPoolSize':2
        )
        def bean = factory.instantiate(applicationContext)

        then:
        bean != null
        bean.options != null

        when:
        Config config = bean.options.build()

        then:
        config.maxConnectionPoolSize() == 2
        !config.encrypted()
        config.logLeakedSessions()
    }

    void "test builder method long and TimeUnit arguments"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.Neo4jProperties', '''
package test

import io.micronaut.context.annotation.*
import org.neo4j.driver.*

@ConfigurationProperties("neo4j.test")
class Neo4jProperties {
    protected var uri: java.net.URI? = null

    @ConfigurationBuilder(
        prefixes=["with"],
        allowZeroArgs=true
    )
    var options: Config.ConfigBuilder = Config.builder()

}
''')
        then:
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods.first().name == 'setUri'

        when:
        InstantiatableBeanDefinition factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.run(
                'neo4j.test.connectionLivenessCheckTimeout': '6s'
        )
        def bean = factory.instantiate(applicationContext)

        then:
        bean != null
        bean.options != null

        when:
        Config config = bean.options.build()

        then:
        config.idleTimeBeforeConnectionTest() == 6000
    }

    void "test using a builder that is marked final"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.Neo4jProperties', '''
package test

import io.micronaut.context.annotation.*
import org.neo4j.driver.*

@ConfigurationProperties("neo4j.test")
class Neo4jProperties {

    @ConfigurationBuilder(
        prefixes=["with"],
        allowZeroArgs=true
    )
    val options: Config.ConfigBuilder = Config.builder()

}
''')
        InstantiatableBeanDefinition factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.run(
                'neo4j.test.connectionLivenessCheckTimeout': '17s'
        )
        def bean = factory.instantiate(applicationContext)

        then:
        bean != null
        bean.options != null

        when:
        Config config = bean.options.build()

        then:
        config.idleTimeBeforeConnectionTest() == 17000
    }

    void "test with setter methods that return this"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test

import io.micronaut.context.annotation.*

@ConfigurationProperties("test")
class MyProperties {

    @ConfigurationBuilder(factoryMethod="build")
    var test: Test? = null
}

class Test private constructor() {

    private var foo: String? = null

    fun getFoo() = foo

    fun setFoo(foo: String): Test {
        this.foo = foo
        return this
    }

    private var bar: Int = 0

    fun getBar() = bar

    fun setBar(bar: Int): Test {
        this.bar = bar
        return this
    }

    private var baz: Long? = null

    fun getBaz() = baz

    @Deprecated("do not use")
    fun setBaz(baz: Long): Test {
        this.baz = baz
        return this
    }

    companion object {
        @JvmStatic fun build(): Test {
            return Test()
        }
    }
}
''')

        when:
        InstantiatableBeanDefinition factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.run(
                'test.foo':'good',
                'test.bar': '10',
                'test.baz':'20'
        )
        def bean = factory.instantiate(applicationContext)

        then:
        bean != null
        bean.test != null

        when:
        def test = bean.test

        then:
        test.foo == 'good'
        test.bar == 10
        test.baz == null //deprecated properties not settable
    }

    void "test configuration builder that are interfaces"() {
        given:
        ApplicationContext ctx = buildContext('''
package test

import io.micronaut.context.annotation.*
import io.micronaut.kotlin.processing.beans.configproperties.AnnWithClass

@ConfigurationProperties("pool")
class PoolConfig {

    @ConfigurationBuilder(prefixes = [""])
    var builder: ConnectionPool.Builder = DefaultConnectionPool.builder()

}

interface ConnectionPool {

    interface Builder {
        fun maxConcurrency(maxConcurrency: Int?): Builder
        fun foo(foo: Foo): Builder
        fun build(): ConnectionPool
    }

    fun getMaxConcurrency(): Int?
}

class DefaultConnectionPool(private val maxConcurrency: Int?): ConnectionPool {

    companion object {
        @JvmStatic
        fun builder(): ConnectionPool.Builder {
            return DefaultBuilder()
        }
    }

    override fun getMaxConcurrency(): Int? = maxConcurrency

    private class DefaultBuilder: ConnectionPool.Builder {

        private var maxConcurrency: Int? = null

        override fun maxConcurrency(maxConcurrency: Int?): ConnectionPool.Builder{
            this.maxConcurrency = maxConcurrency
            return this
        }

        override fun foo(foo: Foo): ConnectionPool.Builder {
            return this
        }

        override fun build(): ConnectionPool{
            return DefaultConnectionPool(maxConcurrency)
        }
    }
}

@AnnWithClass(String::class)
interface Foo
''')
        ctx.getEnvironment().addPropertySource(PropertySource.of(["pool.max-concurrency": 123]))

        when:
        Class testProps = ctx.classLoader.loadClass("test.PoolConfig")
        def testPropBean = ctx.getBean(testProps)

        then:
        noExceptionThrown()
        testPropBean.builder.build().getMaxConcurrency() == 123
    }
}
