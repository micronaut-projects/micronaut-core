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
package io.micronaut.inject.factory.inject

import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

class InjectExistingFactorySpec extends Specification {

    @Unroll
    void "test that it is possible inject an existing factory instance without a circular dependency issue"() {
        given:
        MyFactory myFactory = new MyFactory()
        ApplicationContext ctx = ApplicationContext.run(["spec.name": getClass().simpleName])
        ctx.inject(myFactory)

        expect:
        myFactory.myService != null

        cleanup:
        ctx.close()

        where:
        times << [1,2,3,4,5,6,7,9,10]
    }
}
