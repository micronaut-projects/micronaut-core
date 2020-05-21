/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.configproperties

import io.micronaut.AbstractBeanDefinitionSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanFactory
import org.neo4j.driver.v1.Config

class ConfigurationPropertiesBuilderSpec extends AbstractBeanDefinitionSpec {
    void "test configuration builder on method"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyPropertiesAA', '''
package test;

import io.micronaut.context.annotation.*

@ConfigurationProperties("test")
class MyPropertiesAA {
    
    TestAA test
    
    @ConfigurationBuilder(factoryMethod="build", includes="foo")
    void setTest(TestAA test) {
      this.test = test;
    }
    
    TestAA getTest() {
      return this.test
    }
}

class TestAA {
    private String foo
    private String bar
    
    private TestAA() {}
    
    public void setFoo(String s) { 
        this.foo = s;
    }
    public String getFoo() {
        return foo;
    }
    public void setBar(String s) { 
        this.bar = s;
    }
    public String getBar() {
        return bar;
    }
    
    static TestAA build() {
        new TestAA()
    } 
}
''')

        when:
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

    void "test configuration builder with includes"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyPropertiesA', '''
package test;

import io.micronaut.context.annotation.*

@ConfigurationProperties("test")
class MyPropertiesA {
    
    @ConfigurationBuilder(factoryMethod="build", includes="foo")
    TestA test
}

class TestA {
    private String foo
    private String bar
    
    private TestA() {}
    
    public void setFoo(String s) { 
        this.foo = s;
    }
    public String getFoo() {
        return foo;
    }
    public void setBar(String s) { 
        this.bar = s;
    }
    public String getBar() {
        return bar;
    }
    
    static TestA build() {
        new TestA()
    } 
}
''')

        when:
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


    void "test configuration builder with factory method and properties"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyPropertiesB', '''
package test

import io.micronaut.context.annotation.*

@ConfigurationProperties("test")
class MyPropertiesB {
    
    @ConfigurationBuilder(factoryMethod="build")
    TestB test
    
}

class TestB {
    String bar
    
    private TestB() {}

    static TestB build() {
        new TestB()
    } 
}
''')

        when:
        BeanFactory factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.run(
                'test.bar':'good',
        )
        def bean = factory.build(applicationContext, beanDefinition)

        then:
        bean.test.bar == 'good'
    }

    void "test catch and log NoSuchMethodError for when underlying builder changes"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test

import io.micronaut.context.annotation.*

@ConfigurationProperties("test")
class MyProperties {
    
    @ConfigurationBuilder
    TestC test = new TestC()
   
}

class TestC {
    public void setFoo(String s) { 
        throw new NoSuchMethodError("setFoo")
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


    void "test with groovy properties"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test

import io.micronaut.context.annotation.*

@ConfigurationProperties("test")
class MyProperties {

    @ConfigurationBuilder
    TestD test = new TestD()

}

class TestD {
    String foo
    int bar
    
    @Deprecated
    Long baz
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
        test.baz == null //deprecated properties are ignored
    }

    void "test different inject types for config properties"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.Neo4jProperties', '''
package test

import io.micronaut.context.annotation.*
import org.neo4j.driver.v1.*

@ConfigurationProperties("neo4j.test")
class Neo4jProperties {
    protected java.net.URI uri
    
    @ConfigurationBuilder(
        prefixes="with", 
        allowZeroArgs=true
    )
    Config.ConfigBuilder options = Config.build()

}
''')
        then:
        beanDefinition.injectedFields.size() == 1
        beanDefinition.injectedFields.first().name == 'uri'

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

    protected java.net.URI uri
    
    @ConfigurationBuilder(
        prefixes="with", 
        allowZeroArgs=true,
        configurationPrefix="options"
    )
    Config.ConfigBuilder options = Config.build()
   
}
''')
        then:
        beanDefinition.injectedFields.size() == 1
        beanDefinition.injectedFields.first().name == 'uri'

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

    protected java.net.URI uri
    
    @ConfigurationBuilder(
        prefixes="with", 
        allowZeroArgs=true,
        value="options"
    )
    Config.ConfigBuilder options = Config.build()
   
}
''')
        then:
        beanDefinition.injectedFields.size() == 1
        beanDefinition.injectedFields.first().name == 'uri'

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
    protected java.net.URI uri
    
    @ConfigurationBuilder(
        prefixes="with", 
        allowZeroArgs=true
    )
    Config.ConfigBuilder options = Config.build()
        
}
''')
        then:
        beanDefinition.injectedFields.size() == 1
        beanDefinition.injectedFields.first().name == 'uri'

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
        prefixes="with", 
        allowZeroArgs=true
    )
    final Config.ConfigBuilder options = Config.build()
        
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


    void "test configuration builder that are interfaces"() {
        given:
        ApplicationContext ctx = buildContext("test.PoolConfig", '''
package test

import io.micronaut.context.annotation.*

@ConfigurationProperties("pool")    
final class PoolConfig { 
    
    @ConfigurationBuilder(prefixes = [""])
    public ConnectionPool.Builder builder = DefaultConnectionPool.builder()
    
}

interface ConnectionPool {
    
    interface Builder {
        Builder maxConcurrency(Integer maxConcurrency)
        ConnectionPool build()
    }
    
    int getMaxConcurrency()
}

class DefaultConnectionPool implements ConnectionPool {
    private final int maxConcurrency
    
    DefaultConnectionPool(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency
    }
    
    static ConnectionPool.Builder builder() {
        return new DefaultBuilder()
    }
    
    @Override 
    int getMaxConcurrency() {
        return maxConcurrency
    }
    
    private static class DefaultBuilder implements ConnectionPool.Builder {
    
        private int maxConcurrency
    
        private DefaultBuilder() {
        }
    
        @Override
        ConnectionPool.Builder maxConcurrency(Integer maxConcurrency) {
            this.maxConcurrency = maxConcurrency
            return this
        }
        
        ConnectionPool build() {
            return new DefaultConnectionPool(maxConcurrency)
        }
    }
}
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
