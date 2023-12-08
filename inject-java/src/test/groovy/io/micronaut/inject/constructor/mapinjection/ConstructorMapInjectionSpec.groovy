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
package io.micronaut.inject.constructor.mapinjection

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class ConstructorMapInjectionSpec extends Specification {

    void "test injection with constructor"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:"A bean is obtained that has a constructor with @Inject"
        B b =  context.getBean(B)

        then:"The implementation is injected"
        b.all != null
        b.all.size() == 2
        b.all.values().contains(context.getBean(AImpl))
        b.all.values().contains(context.getBean(AnotherImpl))
        b.all['one'] instanceof AImpl
        b.all == b.linked

        cleanup:
        context.close()
    }
}
