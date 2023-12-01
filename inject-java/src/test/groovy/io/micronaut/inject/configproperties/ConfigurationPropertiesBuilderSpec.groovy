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

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.InstantiatableBeanDefinition
import org.neo4j.driver.Config

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class ConfigurationPropertiesBuilderSpec extends AbstractTypeElementSpec {

    void "test configuration builder on method"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test;

import io.micronaut.context.annotation.*;

@ConfigurationProperties("test")
class MyProperties {

    private Test test;

    @ConfigurationBuilder(factoryMethod="build")
    void setTest(Test test) {
        this.test = test;
    }

    Test getTest() {
        return this.test;
    }

}

class Test {
    private String foo;
    private Test() {}
    public void setFoo(String s) {
        this.foo = s;
    }
    public String getFoo() {
        return foo;
    }

    static Test build() {
        return new Test();
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

    void "test configuration builder with includes"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test;

import io.micronaut.context.annotation.*;

@ConfigurationProperties("test")
class MyProperties {

    @ConfigurationBuilder(factoryMethod="build", includes="foo")
    Test test;

}

class Test {
    private String foo;
    private String bar;
    private Test() {}
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

    static Test build() {
        return new Test();
    }
}
''')

        when:"The bean was built and a warning was logged"
        InstantiatableBeanDefinition factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.run(
                'test.foo':'good',
                'test.bar':'bad'
        )
        def bean = factory.instantiate(applicationContext)

        then:
        bean.test.foo == 'good'
        bean.test.bar == null
    }

    void "test configuration builder with factory method"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test;

import io.micronaut.context.annotation.*;

@ConfigurationProperties("test")
class MyProperties {

    @ConfigurationBuilder(factoryMethod="build")
    Test test;

}

class Test {
    private String foo;
    private Test() {}
    public void setFoo(String s) {
        this.foo = s;
    }
    public String getFoo() {
        return foo;
    }

    static Test build() {
        return new Test();
    }
}
''')

        when:"The bean was built and a warning was logged"
        InstantiatableBeanDefinition factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.run(
                'test.foo':'good',
        )
        def bean = factory.instantiate(applicationContext)

        then:
        bean.test.foo == 'good'
    }

    void "test catch and log NoSuchMethodError for when underlying builder changes"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test;

import io.micronaut.context.annotation.*;

@ConfigurationProperties("test")
class MyProperties {

    @ConfigurationBuilder
    Test test = new Test();


}

class Test {
    public void setFoo(String s) {
        throw new NoSuchMethodError("setFoo");
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
package test;

import io.micronaut.context.annotation.*;
import java.lang.Deprecated;

@ConfigurationProperties("test")
class MyProperties {

    @ConfigurationBuilder
    Test test = new Test();


}

class Test {
    private String foo;
    private int bar;
    private Long baz;

    public void setFoo(String s) { this.foo = s;}
    public void setBar(int s) {this.bar = s;}
    @Deprecated
    public void setBaz(Long s) {this.baz = s;}

    public String getFoo() { return this.foo; }
    public int getBar() { return this.bar; }
    public Long getBaz() { return this.baz; }
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

    void "test with setters without arguments and do not allow zero args"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test;

import io.micronaut.context.annotation.*;
import java.lang.Deprecated;

@ConfigurationProperties("test")
class MyProperties {

    @ConfigurationBuilder
    Test test = new Test();


}

class Test {
    private Boolean foo;
    private Boolean bar;

    public void setFoo() { this.foo = true;}
    public Boolean getFoo() { return this.foo; }

    public void setBar(Boolean bar) { this.bar = bar;}
    public Boolean getBar() { return this.bar; }
}
''')

        when:
        InstantiatableBeanDefinition factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.run(
                'test.foo':'true',
                'test.bar':'true',
        )
        def bean = factory.instantiate(applicationContext)

        then:
        bean != null
        bean.test != null

        when:
        def test = bean.test

        then:
        test.foo == null
        test.bar == true
    }

    void "test different inject types for config properties"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.Neo4jProperties', '''
package test;

import io.micronaut.context.annotation.*;
import org.neo4j.driver.*;

@ConfigurationProperties("neo4j.test")
class Neo4jProperties {
    protected java.net.URI uri;

    @ConfigurationBuilder(
        prefixes="with",
        allowZeroArgs=true
    )
    Config.ConfigBuilder options = Config.builder();


}
''')
        then:
        beanDefinition.injectedFields.size() == 1
        beanDefinition.injectedFields.first().name == 'uri'

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
package test;

import io.micronaut.context.annotation.*;
import org.neo4j.driver.*;

@ConfigurationProperties("neo4j.test")
class Neo4jProperties {
    protected java.net.URI uri;

    @ConfigurationBuilder(
        prefixes="with",
        allowZeroArgs=true,
        configurationPrefix="options"
    )
    Config.ConfigBuilder options = Config.builder();


}
''')
        then:
        beanDefinition.injectedFields.size() == 1
        beanDefinition.injectedFields.first().name == 'uri'

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
package test;

import io.micronaut.context.annotation.*;
import org.neo4j.driver.*;

@ConfigurationProperties("neo4j.test")
class Neo4jProperties {
    protected java.net.URI uri;

    @ConfigurationBuilder(
        prefixes="with",
        allowZeroArgs=true,
        value="options"
    )
    Config.ConfigBuilder options = Config.builder();


}
''')
        then:
        beanDefinition.injectedFields.size() == 1
        beanDefinition.injectedFields.first().name == 'uri'

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
    protected java.net.URI uri;

    @ConfigurationBuilder(
        allowZeroArgs = true,
        value = "options"
    )
    @AccessorsStyle(writePrefixes = "with")
    Config.ConfigBuilder options = Config.builder();
}
''')
        then:
        beanDefinition.injectedFields.size() == 1
        beanDefinition.injectedFields.first().name == 'uri'

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
package test;

import io.micronaut.context.annotation.*;
import org.neo4j.driver.*;

@ConfigurationProperties("neo4j.test")
class Neo4jProperties {
    protected java.net.URI uri;

    @ConfigurationBuilder(
        prefixes="with",
        allowZeroArgs=true
    )
    Config.ConfigBuilder options = Config.builder();

}
''')
        then:
        beanDefinition.injectedFields.size() == 1
        beanDefinition.injectedFields.first().name == 'uri'

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
package test;

import io.micronaut.context.annotation.*;
import org.neo4j.driver.*;

@ConfigurationProperties("neo4j.test")
class Neo4jProperties {

    @ConfigurationBuilder(
        prefixes="with",
        allowZeroArgs=true
    )
    public final Config.ConfigBuilder options = Config.builder();

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
}
