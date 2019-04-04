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
package io.micronaut.docs.config.builder

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class VehicleSpec extends Specification {

    void "test start vehicle"() {
        when:
        // tag::start[]
        ApplicationContext applicationContext = ApplicationContext.run(
                ['my.engine.cylinders':'4',
                 'my.engine.manufacturer'          : 'Subaru',
                 'my.engine.crank-shaft.rod-length': 4,
                 'my.engine.spark-plug.name'       : '6619 LFR6AIX',
                 'my.engine.spark-plug.type'       : 'Iridium',
                 'my.engine.spark-plug.companyName': 'NGK'
                ],
                "test"
        )

        Vehicle vehicle = applicationContext
                .getBean(Vehicle)
        println(vehicle.start())
        // end::start[]

        then:
        vehicle.start() == "Subaru Engine Starting V4 [rodLength=4.0, sparkPlug=Iridium(NGK 6619 LFR6AIX)]"

        cleanup:
        applicationContext.close()
    }

}
