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
package org.particleframework.aop.infra

import org.particleframework.aop.Intercepted
import org.particleframework.aop.internal.AopAttributes
import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import org.particleframework.inject.BeanDefinition
import spock.lang.Ignore
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class AopSetupSpec extends Specification {

    @Ignore
    void "test AOP setup"() {
        given:
        BeanContext beanContext = new DefaultBeanContext().start()

        when:"the bean definition is obtained"
        BeanDefinition<Foo> beanDefinition = beanContext.findBeanDefinition(Foo).get()

        then:
        beanDefinition.findMethod("blah", String).isPresent()
        // should not be a reflection based method
        beanDefinition.findMethod("blah", String).get().getClass().getName().contains("Reflection")

        when:
        Foo foo = beanContext.getBean(Foo)


        then:
        foo instanceof Intercepted
        beanContext.findExecutableMethod(Foo, "blah", String).isPresent()
        // should not be a reflection based method
        !beanContext.findExecutableMethod(Foo, "blah", String).get().getClass().getName().contains("Reflection")
        foo.blah("test") == "Name is test"
        AopAttributes.@attributes.get() == null

    }

    void "test AOP setup attributes"() {
        given:
        BeanContext beanContext = new DefaultBeanContext().start()

        when:
        Foo foo = beanContext.getBean(Foo)
        def attrs = AopAttributes.get(Foo, "blah", String)
        then:
        foo instanceof Intercepted
        foo.blah("test") == "Name is test"
        AopAttributes.@attributes.get().values().first().values == attrs

        when:
        AopAttributes.remove(Foo, "blah", String)

        then:
        AopAttributes.@attributes.get() == null
    }
}
