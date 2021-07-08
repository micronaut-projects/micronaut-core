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
package io.micronaut.inject.close

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class BeanCloseOrderSpec extends Specification {

    static List<Class> closed = []

    void "test close order"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(["spec.name": getClass().simpleName])
        ctx.getBean(A)
        ctx.getBean(B)
        ctx.getBean(C)
        ctx.getBean(D)
        ctx.getBean(E)
        ctx.getBean(F)

        when:
        ctx.close()

        then:
        closed == [A,B,C,D,E,F]
    }
}
