package io.micronaut.kotlin.processing.inject.configproperties

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanFactory
import org.neo4j.driver.v1.Config
import spock.lang.Specification
import static io.micronaut.kotlin.processing.KotlinCompiler.*

class ConfigurationPropertiesBuilderSpec extends Specification {

    void "test configuration builder on method"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test;

import io.micronaut.context.annotation.*;

@ConfigurationProperties("test")
class MyProperties {
    
    @ConfigurationBuilder(factoryMethod="build")
    var test: Test? = null
}

class Test private constructor() {

    var foo: String? = null
    
    companion object {
        fun build(): Test {
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
package test;

import io.micronaut.context.annotation.*;

@ConfigurationProperties("test")
class MyProperties {
    
    @ConfigurationBuilder(factoryMethod="build", includes=["foo"])
    var test: Test? = null
}

class Test private constructor() {

    var foo: String? = null
    var bar: String? = null
    
    companion object {
        fun build(): Test {
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
        BeanFactory factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.run(
                'test.foo':'good',
        )
        factory.build(applicationContext, beanDefinition)
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
    protected var uri: java.net.URI? = null
    
    @ConfigurationBuilder(
        prefixes=["with"], 
        allowZeroArgs=true
    )
    var options: Config.ConfigBuilder = Config.build()
}
''')
        then:
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods.first().name == 'setUri'

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

    void "test specifying a configuration prefix"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.Neo4jProperties', '''
package test

import io.micronaut.context.annotation.*
import org.neo4j.driver.v1.*

@ConfigurationProperties("neo4j.test")
class Neo4jProperties {
    protected var uri: java.net.URI? = null
    
    @ConfigurationBuilder(
        prefixes=["with"], 
        allowZeroArgs=true,
        configurationPrefix="options"
    )
    var options: Config.ConfigBuilder = Config.build()
}
''')
        then:
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods.first().name == 'setUri'

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
package test

import io.micronaut.context.annotation.*
import org.neo4j.driver.v1.*

@ConfigurationProperties("neo4j.test")
class Neo4jProperties {
    protected var uri: java.net.URI? = null
    
    @ConfigurationBuilder(
        prefixes=["with"], 
        allowZeroArgs=true,
        value="options"
    )
    var options: Config.ConfigBuilder = Config.build()
}
''')
        then:
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods.first().name == 'setUri'

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
package test;

import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.AccessorsStyle;
import org.neo4j.driver.v1.*;

@ConfigurationProperties("neo4j.test")
class Neo4jProperties {
    protected var uri: java.net.URI? = null

    @ConfigurationBuilder(
        allowZeroArgs = true,
        value = "options"
    )
    @AccessorsStyle(writePrefixes = ["with"])
    var options: Config.ConfigBuilder = Config.build()
}
''')
        then:
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods.first().name == 'setUri'

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
    protected var uri: java.net.URI? = null
    
    @ConfigurationBuilder(
        prefixes=["with"], 
        allowZeroArgs=true
    )
    var options: Config.ConfigBuilder = Config.build()
        
}
''')
        then:
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods.first().name == 'setUri'

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

    void "test using a builder that is marked final"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.Neo4jProperties', '''
package test

import io.micronaut.context.annotation.*
import org.neo4j.driver.v1.*

@ConfigurationProperties("neo4j.test")
class Neo4jProperties {
    
    @ConfigurationBuilder(
        prefixes=["with"], 
        allowZeroArgs=true
    )
    val options: Config.ConfigBuilder = Config.build()
        
}
''')
        BeanFactory factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.run(
                'neo4j.test.connectionLivenessCheckTimeout': '17s'
        )
        def bean = factory.build(applicationContext, beanDefinition)

        then:
        bean != null
        bean.options != null

        when:
        Config config = bean.options.toConfig()

        then:
        config.idleTimeBeforeConnectionTest() == 17000
    }
}
