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
package io.micronaut.inject.configproperties.inheritance

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
//        parent.is(config)
        parent.host == 'localhost'
        parent.port == 8080
        config.port == 8080
        config.host == 'localhost'
        config.stuff == 'test'

        cleanup:
        applicationContext.stop()
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

        cleanup:
        applicationContext.stop()
    }

    void "test EachProperty inner ConfigurationProperties with setter"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'teams.cubs.wins': 5,
                'teams.cubs.manager.age': 40,
                'teams.mets.wins': 6
        ])

        when:
        ParentEachProps cubs = context.getBean(ParentEachProps, Qualifiers.byName("cubs"))

        then:
        cubs.wins == 5
        cubs.manager.age == 40

        when:
        ParentEachProps.ManagerProps cubsManager = context.getBean(ParentEachProps.ManagerProps, Qualifiers.byName("cubs"))

        then: "The instance is the same"
        cubsManager.is(cubs.manager)

        when:
        ParentEachProps mets = context.getBean(ParentEachProps, Qualifiers.byName("mets"))

        then:
        mets.wins == 6
        mets.manager == null

        and:
        !context.findBean(ParentEachProps.ManagerProps, Qualifiers.byName("mets")).isPresent()
        context.getBeansOfType(ParentEachProps).size() == 2
        context.getBeansOfType(ParentEachProps.ManagerProps).size() == 1
    }

    void "test EachProperty inner ConfigurationProperties with constructor"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'teams.cubs.wins': 5,
                'teams.cubs.manager.age': 40,
                'teams.mets.wins': 6
        ])

        when:
        ParentEachPropsCtor cubs = context.getBean(ParentEachPropsCtor, Qualifiers.byName("cubs"))

        then:
        cubs.wins == 5
        cubs.manager.age == 40
        cubs.name == "cubs"

        when:
        ParentEachPropsCtor.ManagerProps cubsManager = context.getBean(ParentEachPropsCtor.ManagerProps, Qualifiers.byName("cubs"))

        then: "The instance is the same"
        cubsManager.is(cubs.manager)
        cubsManager.name == "cubs"

        when:
        ParentEachPropsCtor mets = context.getBean(ParentEachPropsCtor, Qualifiers.byName("mets"))

        then:
        mets.wins == 6
        mets.manager == null
        mets.name == "mets"

        and:
        !context.findBean(ParentEachPropsCtor.ManagerProps, Qualifiers.byName("mets")).isPresent()
        context.getBeansOfType(ParentEachPropsCtor).size() == 2
        context.getBeansOfType(ParentEachPropsCtor.ManagerProps).size() == 1
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
}
