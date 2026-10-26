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
package io.micronaut.http.missing

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.http.missing.classnotfound.MissingAndPresentIndexedTypeBean
import io.micronaut.http.missing.classnotfound.MissingIndexedTypeBean
import io.micronaut.http.missing.classnotfound.MissingIndexedTypeContextBean
import io.micronaut.http.missing.classnotfound.MissingIndexedTypeGroovyBean
import io.micronaut.http.missing.classnotfound.PresentIndexed
import io.micronaut.http.missing.classnotfound.PresentIndexedInterface
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class MissingBeanSpec extends Specification {

    void "test a controller with a missing bean 1"() {
        when:
            ApplicationContext.run(EmbeddedServer, ['spec.name': "MissingConstructorBeanController"])
        then:
            def e = thrown(BeanInstantiationException)
            e.message == "Failed to initialize the bean [class io.micronaut.http.missing.classnotfound.MissingConstructorBeanController]: io/micronaut/inject/test/external/ExternalBean"
    }

    void "test a controller with a missing bean 2"() {
        when:
            ApplicationContext.run(EmbeddedServer, ['spec.name': "MissingFieldBeanController"])
        then:
            def e = thrown(BeanInstantiationException)
            e.message == "Failed to initialize the bean [class io.micronaut.http.missing.classnotfound.MissingFieldBeanController]: io/micronaut/inject/test/external/ExternalBean"
    }

    void "test a controller with a missing bean 3"() {
        when:
            ApplicationContext.run(EmbeddedServer, ['spec.name': "MissingMethodInjectBeanController"])
        then:
            def e = thrown(BeanInstantiationException)
            e.message == "Failed to initialize the bean [class io.micronaut.http.missing.classnotfound.MissingMethodInjectBeanController]: io/micronaut/inject/test/external/ExternalBean"
    }

    void "test a controller with a missing bean 4"() {
        when:
            ApplicationContext.run(EmbeddedServer, ['spec.name': "MissingExecutableMethodBeanController"])
        then:
            def e = thrown(BeanInstantiationException)
            e.message == "Failed to initialize the bean [class io.micronaut.http.missing.classnotfound.MissingExecutableMethodBeanController]: io/micronaut/inject/test/external/ExternalBean"
    }

    void "a bean whose indexed type is missing loads"() {
        given:
            ApplicationContext ctx = ApplicationContext.run(['spec.name': beanType.simpleName])

        expect:
            ctx.getBean(beanType)
            ctx.findBeanDefinition(beanType).isPresent()
            ctx.getBeanDefinitions(Object).any { it.beanType == beanType }
            ctx.getAllBeanDefinitions().any { it.beanType == beanType }
            ctx.getBeanDefinitions(Qualifiers.byStereotype("io.micronaut.inject.test.external.ExternalIndexed"))*.beanType == [beanType]

        cleanup:
            ctx?.close()

        where:
            beanType << [MissingIndexedTypeBean, MissingIndexedTypeGroovyBean]
    }

    void "an eager bean whose indexed type is missing starts"() {
        setup:
            MissingIndexedTypeContextBean.created = false

        when:
            ApplicationContext ctx = ApplicationContext.run(['spec.name': 'MissingIndexedTypeContextBean'])

        then:
            MissingIndexedTypeContextBean.created

        cleanup:
            ctx?.close()
    }

    void "a bean whose indexed type is missing is still found by its exposed types and stereotypes"() {
        given:
            ApplicationContext ctx = ApplicationContext.run(['spec.name': 'MissingAndPresentIndexedTypeBean'])

        expect:
            ctx.getBean(MissingAndPresentIndexedTypeBean)
            ctx.getBean(PresentIndexedInterface) instanceof MissingAndPresentIndexedTypeBean
            // Not getBeanDefinitions(PresentIndexed): a missing indexed type empties all of the bean's indexes
            ctx.getBeanDefinitions(Qualifiers.byStereotype(PresentIndexed))*.beanType == [MissingAndPresentIndexedTypeBean]

        cleanup:
            ctx?.close()
    }

    void "a bean that fails to initialize is still found by its indexed type"() {
        given:
            ApplicationContext ctx = ApplicationContext.run(['spec.name': 'MissingConstructorIndexedBean'])

        when:
            ctx.getBeanDefinitions(PresentIndexed)

        then:
            def e = thrown(BeanInstantiationException)
            e.message == "Failed to initialize the bean [class io.micronaut.http.missing.classnotfound.MissingConstructorIndexedBean]: io/micronaut/inject/test/external/ExternalBean"

        cleanup:
            ctx?.close()
    }

}
