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
package io.micronaut.inject.scope

import io.micronaut.context.DefaultBeanContext
import spock.lang.Specification

class ContextScopeSpec extends Specification {

    void "test context scope"() {
        given:
        DefaultBeanContext beanContext = new DefaultBeanContext()

        when:"The context is started"
        beanContext.start()

        then:"So is the bean"
        beanContext.@singletonScope.@singletonByBeanDefinition.values().find() { it.bean instanceof A }
    }
}
