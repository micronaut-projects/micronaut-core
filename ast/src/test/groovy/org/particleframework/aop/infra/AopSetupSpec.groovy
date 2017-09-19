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

import org.particleframework.aop.internal.AopAttributes
import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class AopSetupSpec extends Specification {

    void "test AOP setup"() {
        given:
        BeanContext beanContext = new DefaultBeanContext().start()

        when:
        Foo foo = beanContext.getBean(Foo)

        then:
        foo instanceof Foo$Intercepted
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
        foo instanceof Foo$Intercepted
        foo.blah("test") == "Name is test"
        AopAttributes.@attributes.get().values().first().values == attrs

        when:
        AopAttributes.remove(Foo, "blah", String)

        then:
        AopAttributes.@attributes.get() == null
    }
}
