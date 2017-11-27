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

import org.particleframework.context.ApplicationContext
import org.particleframework.inject.AbstractTypeElementSpec
import org.particleframework.inject.BeanDefinition
import org.particleframework.inject.BeanFactory
import spock.lang.Ignore

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Ignore
class ConfigurationPropertiesBuilderSpec extends AbstractTypeElementSpec {

    void "test different inject types for config properties"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MongoProperties', '''
package test;

import org.particleframework.context.annotation.*;
import com.mongodb.*;

@ConfigurationProperties("foo")
class MongoProperties {
    @Bean(factoryMethod="build", 
          factoryClass=MongoClientOptions.Builder)
    MongoClientOptions options;
}
''')
        then:
        beanDefinition.injectedFields.size() == 1
        beanDefinition.injectedFields.first().name == 'fieldTest'
        beanDefinition.injectedMethods.size() == 1

        when:
        BeanFactory factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.build().start()
        def bean = factory.build(applicationContext, beanDefinition)

        then:
        bean != null
        bean.setter == "unconfigured"
        bean.@fieldTest == "unconfigured"

        when:
        applicationContext.environment.addPropertySource(
                'foo.setterTest' :'foo',
                'foo.fieldTest' :'bar',
        )
        bean = factory.build(applicationContext, beanDefinition)

        then:
        bean != null
        bean.setter == "foo"
        bean.@fieldTest == "bar"

    }
}
