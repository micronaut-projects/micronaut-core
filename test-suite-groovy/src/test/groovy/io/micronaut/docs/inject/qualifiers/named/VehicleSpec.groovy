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
package io.micronaut.docs.inject.qualifiers.named

import io.micronaut.context.BeanContext
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class VehicleSpec extends Specification {

    void "test start vehicle"() {

        when:
        // tag::start[]
        def context = BeanContext.run()
        Vehicle vehicle = context.getBean(Vehicle)
        println( vehicle.start() )
        // end::start[]

        then:
        vehicle.start() == "Starting V8"

        cleanup:
        context.close()
    }
}
