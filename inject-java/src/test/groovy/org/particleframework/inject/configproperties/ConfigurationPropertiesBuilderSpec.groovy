/*
 * Copyright 2017 original authors
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
package org.particleframework.inject.configproperties

import org.neo4j.driver.v1.Config
import org.particleframework.context.ApplicationContext
import org.particleframework.inject.AbstractTypeElementSpec
import org.particleframework.inject.BeanDefinition
import org.particleframework.inject.BeanFactory
import spock.lang.Ignore

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class ConfigurationPropertiesBuilderSpec extends AbstractTypeElementSpec {

    void "test different inject types for config properties"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.Neo4jProperties', '''
package test;

import org.particleframework.context.annotation.*;
import org.neo4j.driver.v1.*;

@ConfigurationProperties("neo4j.test")
class Neo4jProperties {
    protected java.net.URI uri;
    
    @ConfigurationBuilder(
        prefixes="with", 
        allowZeroArgs=true
    )
    Config.ConfigBuilder options = Config.build();
    
     
}
''')
        then:
        beanDefinition.injectedFields.size() == 1
        beanDefinition.injectedFields.first().name == 'uri'

        when:
        BeanFactory factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.run(
                'neo4j.test.encryptionLevel':'none',
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
        config.encrypted() == false
    }

    void "test specifying a configuration prefix"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.Neo4jProperties', '''
package test;

import org.particleframework.context.annotation.*;
import org.neo4j.driver.v1.*;

@ConfigurationProperties("neo4j.test")
class Neo4jProperties {
    protected java.net.URI uri;
    
    @ConfigurationBuilder(
        prefixes="with", 
        allowZeroArgs=true,
        configurationPrefix="options"
    )
    Config.ConfigBuilder options = Config.build();
    
     
}
''')
        then:
        beanDefinition.injectedFields.size() == 1
        beanDefinition.injectedFields.first().name == 'uri'

        when:
        BeanFactory factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.run(
                'neo4j.test.options.encryptionLevel':'none',
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
        config.encrypted() == false
    }

    @Ignore
    void "test builder method with multiple arguments"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.Neo4jProperties', '''
package test;

import org.particleframework.context.annotation.*;
import org.neo4j.driver.v1.*;

@ConfigurationProperties("neo4j.test")
class Neo4jProperties {
    protected java.net.URI uri;
    
    @ConfigurationBuilder(
        prefixes="with", 
        allowZeroArgs=true
    )
    Config.ConfigBuilder options = Config.build();
        
}
''')
        then:
        beanDefinition.injectedFields.size() == 1
        beanDefinition.injectedFields.first().name == 'uri'

        when:
        BeanFactory factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.run(
                'mongo.test.ConnectionLivenessCheckTimeout.value': 10,
                'neo4j.test.ConnectionLivenessCheckTimeout.unit': 'MINUTES'
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
}
