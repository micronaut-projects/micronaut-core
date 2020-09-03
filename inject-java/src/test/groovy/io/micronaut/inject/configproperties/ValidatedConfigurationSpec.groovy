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

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ValidatedBeanDefinition
import spock.lang.Specification

import javax.validation.Validation

class ValidatedConfigurationSpec extends AbstractTypeElementSpec {

    void "test validated config with invalid config"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.start()

        when:
        ValidatedConfig config = applicationContext.getBean(ValidatedConfig)

        then:
        def e = thrown(BeanInstantiationException)
        e.message.contains('url - must not be null')
        e.message.contains('name - must not be blank')

        cleanup:
        applicationContext.close()
    }

    void "test validated config with valid config"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(PropertySource.of(
                'foo.bar.url':'http://localhost',
                'foo.bar.name':'test'
        ))

        applicationContext.start()

        when:
        ValidatedConfig config = applicationContext.getBean(ValidatedConfig)

        then:
        config != null
        config.url == new URL("http://localhost")
        config.name == 'test'

        cleanup:
        applicationContext.close()
    }

    void "test config props with @Valid on field is a validating bean definition"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig', '''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.inject.configproperties.Pojo;

import javax.validation.Valid;
import java.util.List;

@ConfigurationProperties("test.valid")
public class MyConfig {

    @Valid
    private List<Pojo> pojos;

    public List<Pojo> getPojos() {
        return pojos;
    }

    public void setPojos(List<Pojo> pojos) {
        this.pojos = pojos;
    }

}
''')

        then:
        beanDefinition instanceof ValidatedBeanDefinition
    }

    void "test config props with @Valid on getter is a validating bean definition"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig', '''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.inject.configproperties.Pojo;

import javax.validation.Valid;
import java.util.List;

@ConfigurationProperties("test.valid")
public class MyConfig {
  
    private List<Pojo> pojos;

    @Valid
    public List<Pojo> getPojos() {
        return pojos;
    }

    public void setPojos(List<Pojo> pojos) {
        this.pojos = pojos;
    }

}
''')

        then:
        beanDefinition instanceof ValidatedBeanDefinition
    }
}
