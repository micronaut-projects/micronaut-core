package io.micronaut.kotlin.processing.beans.configproperties

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanFactory
import org.neo4j.driver.v1.Config
import spock.lang.PendingFeature
import spock.lang.Specification

import static io.micronaut.kotlin.processing.KotlinCompiler.*

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
        @JvmStatic fun build(): Test {
            return Test()
        }
    }
}
''')

        when:"The bean was built and a warning was logged"
        BeanFactory factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.run(
                'test.foo':'good'
        )
        def bean = factory.build(applicationContext, beanDefinition)

        then:
        bean.test.foo == 'good'
    }

    void "test configuration builder with includes"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test

import io.micronaut.context.annotation.*

@ConfigurationProperties("test")
class MyProperties {

    @ConfigurationBuilder(factoryMethod="build", includes=["foo"])
    var test: Test? = null
}

class Test private constructor() {
        
    var foo: String? = null
    var bar: String? = null
        
    companion object {
        @JvmStatic fun build(): Test {
            return Test()
        }
    }
}
''')

        when:"The bean was built and a warning was logged"
        BeanFactory factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.run(
                'test.foo':'good',
                'test.bar':'bad'
        )
        def bean = factory.build(applicationContext, beanDefinition)

        then:
        bean.test.foo == 'good'
        bean.test.bar == null
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
        BeanFactory factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.run(
                'test.foo':'good',
                'test.bar': '10',
                'test.baz':'20'
        )
        def bean = factory.build(applicationContext, beanDefinition)

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
import org.neo4j.driver.v1.*

@ConfigurationProperties("neo4j.test")
class Neo4jProperties {
    internal var uri: java.net.URI? = null

    @ConfigurationBuilder(
        prefixes=["with"],
        allowZeroArgs=true
    )
    val options: Config.ConfigBuilder = Config.build()
}
''')
        then:
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods.first().name == 'setUri$main'

        when:
        BeanFactory factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.run(
                'neo4j.test.encryptionLevel':'none',
                'neo4j.test.leakedSessionsLogging':true,
                'neo4j.test.maxIdleSessions':2
        )
        def bean = factory.build(applicationContext, beanDefinition)

        then:
        bean != null
        bean.options != null

        when:
        Config config = bean.options.toConfig()

        then:
        config.maxIdleConnectionPoolSize() == 2
        config.encrypted() == true // deprecated properties are ignored
        config.logLeakedSessions()
    }

    @PendingFeature(reason = "annotation defaults")
    void "test specifying a configuration prefix"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.Neo4jProperties', '''
package test

import io.micronaut.context.annotation.*
import org.neo4j.driver.v1.*

@ConfigurationProperties("neo4j.test")
class Neo4jProperties {
    internal var uri: java.net.URI? = null

    @ConfigurationBuilder(
        prefixes=["with"],
        allowZeroArgs=true,
        configurationPrefix="options"
    )
    val options: Config.ConfigBuilder = Config.build()
}
''')
        then:
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods.first().name == 'setUri$main'

        when:
        BeanFactory factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.run(
                'neo4j.test.options.encryptionLevel':'none',
                'neo4j.test.options.leakedSessionsLogging':true,
                'neo4j.test.options.maxIdleSessions':2
        )
        def bean = factory.build(applicationContext, beanDefinition)

        then:
        bean != null
        bean.options != null

        when:
        Config config = bean.options.toConfig()

        then:
        config.maxIdleConnectionPoolSize() == 2
        config.encrypted() == true // deprecated properties are ignored
        config.logLeakedSessions()
    }

    void "test specifying a configuration prefix with value"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.Neo4jProperties', '''
package test;

import io.micronaut.context.annotation.*;
import org.neo4j.driver.v1.*;

@ConfigurationProperties("neo4j.test")
class Neo4jProperties {
    internal var uri: java.net.URI? = null

    @ConfigurationBuilder(
        prefixes=["with"],
        allowZeroArgs=true,
        value="options"
    )
    val options: Config.ConfigBuilder = Config.build()


}
''')
        then:
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods.first().name == 'setUri$main'

        when:
        BeanFactory factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.run(
                'neo4j.test.options.encryptionLevel':'none',
                'neo4j.test.options.leakedSessionsLogging':true,
                'neo4j.test.options.maxIdleSessions':2
        )
        def bean = factory.build(applicationContext, beanDefinition)

        then:
        bean != null
        bean.options != null

        when:
        Config config = bean.options.toConfig()

        then:
        config.maxIdleConnectionPoolSize() == 2
        config.encrypted() == true // deprecated properties are ignored
        config.logLeakedSessions()
    }

    void "test specifying a configuration prefix with value using @AccessorsStyle"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.Neo4jProperties', '''
package test

import io.micronaut.context.annotation.*
import io.micronaut.core.annotation.AccessorsStyle
import org.neo4j.driver.v1.*

@ConfigurationProperties("neo4j.test")
class Neo4jProperties {
    internal var uri: java.net.URI? = null

    @ConfigurationBuilder(
        allowZeroArgs = true,
        value = "options"
    )
    @AccessorsStyle(writePrefixes = ["with"])
    val options: Config.ConfigBuilder = Config.build()
}
''')
        then:
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods.first().name == 'setUri$main'

        when:
        BeanFactory factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.run(
            'neo4j.test.options.encryptionLevel':'none',
            'neo4j.test.options.leakedSessionsLogging':true,
            'neo4j.test.options.maxIdleSessions':2
        )
        def bean = factory.build(applicationContext, beanDefinition)

        then:
        bean != null
        bean.options != null

        when:
        Config config = bean.options.toConfig()

        then:
        config.maxIdleConnectionPoolSize() == 2
        config.encrypted() == true // deprecated properties are ignored
        config.logLeakedSessions()
    }

    void "test builder method long and TimeUnit arguments"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.Neo4jProperties', '''
package test

import io.micronaut.context.annotation.*
import org.neo4j.driver.v1.*

@ConfigurationProperties("neo4j.test")
class Neo4jProperties {
    internal var uri: java.net.URI? = null

    @ConfigurationBuilder(
        prefixes=["with"],
        allowZeroArgs=true
    )
    val options: Config.ConfigBuilder = Config.build()

}
''')
        then:
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods.first().name == 'setUri$main'

        when:
        BeanFactory factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.run(
                'neo4j.test.connectionLivenessCheckTimeout': '6s'
        )
        def bean = factory.build(applicationContext, beanDefinition)

        then:
        bean != null
        bean.options != null

        when:
        Config config = bean.options.toConfig()

        then:
        config.idleTimeBeforeConnectionTest() == 6000
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
