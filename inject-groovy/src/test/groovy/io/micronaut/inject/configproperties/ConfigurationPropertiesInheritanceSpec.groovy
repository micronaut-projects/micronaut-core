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

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class ConfigurationPropertiesInheritanceSpec extends Specification {

    void "test configuration properties binding"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(PropertySource.of(
                'test',
                ['foo.bar.port':'8080',
                'foo.bar.host':'localhost',
                'foo.bar.baz.stuff': 'test']
        ))

        applicationContext.start()

        ChildConfig config = applicationContext.getBean(ChildConfig)
        MyConfig parent = applicationContext.getBean(MyConfig)

        expect:
        parent.host == 'localhost'
        parent.port == 8080
        config.port == 8080
        config.host == 'localhost'
        config.stuff == 'test'

    }

    void "test configuration properties binding extending POJO"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(PropertySource.of(
                'test',
                ['foo.baz.otherProperty':'x',
                'foo.baz.onlySetter':'y',
                'foo.baz.port': 55]
        ))

        applicationContext.start()

        MyOtherConfig config = applicationContext.getBean(MyOtherConfig)

        expect:
        config.port == 55
        config.otherProperty == 'x'
        config.onlySetter == 'y'
    }



    void "test EachProperty array inner ConfigurationProperties with setter"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'teams': [['wins': 5, 'manager': ['age': 40]], ['wins': 6]]
        ])

        when:
        Collection teams = context.getBeansOfType(ParentArrayEachProps)

        then:
        teams[0].wins == 5
        teams[0].manager.age == 40
        teams[1].wins == 6
        teams[1].manager == null

        when:
        Collection<ParentArrayEachProps.ManagerProps> managers = context.getBeansOfType(ParentArrayEachProps.ManagerProps)

        then: "The instance is the same"
        managers.size() == 1
        managers[0].is(teams[0].manager)
    }

    void "test EachProperty array inner ConfigurationProperties with constructor"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'teams': [['wins': 5, 'manager': ['age': 40]], ['wins': 6]]

        ])

        when:
        Collection teams = context.getBeansOfType(ParentArrayEachPropsCtor)

        then:
        teams[0].wins == 5
        teams[0].manager.age == 40
        teams[1].wins == 6
        teams[1].manager == null

        when:
        Collection<ParentArrayEachPropsCtor.ManagerProps> managers = context.getBeansOfType(ParentArrayEachPropsCtor.ManagerProps)

        then: "The instance is the same"
        managers.size() == 1
        managers[0].is(teams[0].manager)
    }

    @ConfigurationProperties('foo.bar')
    static class MyConfig {
        int port
        String host
    }

    @ConfigurationProperties('baz')
    static class ChildConfig extends MyConfig {
        String stuff
    }
}
