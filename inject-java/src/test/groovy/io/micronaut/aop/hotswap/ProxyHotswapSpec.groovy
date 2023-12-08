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
package io.micronaut.aop.hotswap

import io.micronaut.aop.HotSwappableInterceptedProxy
import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class ProxyHotswapSpec extends Specification {

    void "test AOP setup attributes"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        def newInstance = new HotswappableProxyingClass()

        when:
        HotswappableProxyingClass foo = context.getBean(HotswappableProxyingClass)
        then:
        foo instanceof HotSwappableInterceptedProxy
        foo.interceptedTarget().getClass() == HotswappableProxyingClass
        foo.test("test") == "Name is changed"
        foo.test2("test") == "Name is test"
        foo.interceptedTarget().invocationCount == 2

        foo.swap(newInstance)
        foo.interceptedTarget().invocationCount == 0
        foo.interceptedTarget() != foo
        foo.interceptedTarget().is(newInstance)

        cleanup:
        context.close()
    }
}
