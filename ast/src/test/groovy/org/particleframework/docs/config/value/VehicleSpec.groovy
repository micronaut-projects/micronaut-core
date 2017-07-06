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
package org.particleframework.docs.config.value

import org.particleframework.context.ApplicationContext
import org.particleframework.context.DefaultApplicationContext
import org.particleframework.context.DefaultBeanContext
import org.particleframework.context.env.MapPropertySource
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class VehicleSpec extends Specification {

    void "test start vehicle with configuration"() {


        when:
        // tag::start[]
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(new MapPropertySource('my.engine.cylinders':'8'))
        applicationContext.start()

        Vehicle vehicle = applicationContext
                .getBean(Vehicle)
        println(vehicle.start())
        // end::start[]

        then:
        vehicle.start() == "Starting V8 Engine"
    }

    void "test start vehicle without configuration"() {


        when:
        // tag::start[]
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.start()

        Vehicle vehicle = applicationContext
                .getBean(Vehicle)
        println(vehicle.start())
        // end::start[]

        then:
        vehicle.start() == "Starting V6 Engine"
    }
}
