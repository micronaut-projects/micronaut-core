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
package io.micronaut.inject.vetoed

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.vetoed.pkg.VetoedPackageBean
import spock.lang.Specification

class VetoedSpec extends Specification {

    void "test vetoed package"() {
        when:
            ApplicationContext context = ApplicationContext.run()
        then:
            context.findBean(VetoedPackageBean).isEmpty()
        cleanup:
            context.stop()
    }

    void "test vetoed bean"() {
        when:
            ApplicationContext context = ApplicationContext.run()
        then:
            context.findBean(VetoedBean1).isEmpty()
        cleanup:
            context.stop()
    }

    void "test produced vetoed bean"() {
        when:
            ApplicationContext context = ApplicationContext.run()
        then:
            context.getBeanDefinitions(VetoedBean2).size() == 1
        cleanup:
            context.stop()
    }

    void "test vetoed bean parent"() {
        when:
            ApplicationContext context = ApplicationContext.run()
            def bean = context.getBean(ParentOfVetoedBean)
        then: "injection in the vetoed superclass is working"
            bean.fieldInjection
            bean.methodInjection
            bean.constructorInjection
        cleanup:
            context.stop()
    }

    void "test vetoed methods and fields bean"() {
        when:
            ApplicationContext context = ApplicationContext.run()
            def bean = context.getBean(VetoedMethodsAndFieldsBean)
        then:
            bean.fieldInjection == null
            bean.methodInjection == null
        cleanup:
            context.stop()
    }

    void "test vetoed executable methods bean"() {
        when:
            ApplicationContext context = ApplicationContext.run()
            def bean = context.getBeanDefinition(VetoedExecutableMethodsBean)
        then:
            bean.executableMethods.collect {it.methodName } == ["foo", "abc"]
        cleanup:
            context.stop()
    }

}
