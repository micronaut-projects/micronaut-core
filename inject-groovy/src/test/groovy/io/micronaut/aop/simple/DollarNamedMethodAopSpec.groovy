/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.aop.simple

import io.micronaut.aop.Intercepted
import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * {@code $} is a legal identifier character, so a method the user declared with a leading {@code $}
 * is an ordinary method that advice has to apply to.
 */
class DollarNamedMethodAopSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    void "test AOP advice is applied to a method declared with a leading dollar"() {
        given:
        DollarNamedMethodBean bean = context.getBean(DollarNamedMethodBean)

        expect:
        bean instanceof Intercepted
        bean.$mutated('test') == 'Name is changed'
        bean.ordinary('test') == 'Name is test'
    }
}
