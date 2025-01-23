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
package io.micronaut.inject.failures

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.exceptions.CircularDependencyException
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton


class FactoryCircularDependencyFailureSpec extends Specification {

    void "test circular dependency with factory failure"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:"A bean is obtained that has a setter with @Inject"
        context.getBean(ElectricalGrid)

        then:"The implementation is injected"
        def e = thrown(CircularDependencyException)
        e.message.normalize() == '''\
Failed to inject value for parameter [stations] of class: io.micronaut.inject.failures.FactoryCircularDependencyFailureSpec$ElectricalGrid

Message: Circular dependency detected
Path Taken:
new i.m.i.f.F$ElectricalGrid(List<ElectricStation> stations)
      \\---> new i.m.i.f.F$ElectricalGrid([List<ElectricStation> stations])
            ^  \\---> @j.i.Singleton i.m.i.f.F$ElectricStation i.m.i.f.F$ElectricStationFactory.nuclearStation#nuclearStation([MeasuringEquipment equipment])
            |        \\---> @j.i.Singleton i.m.i.f.F$MeasuringEquipment#grid
            |              |
            +--------------+'''

        cleanup:
        context.close()
    }

    static class ElectricalGrid {
        @Inject
        ElectricalGrid(List<ElectricStation> stations) {}
    }

    static class ElectricStation {
        ElectricStation() {}
    }

    @Factory
    static class ElectricStationFactory {

        @Singleton
        ElectricStation solarStation() {
            return new ElectricStation()
        }

        @Singleton
        ElectricStation nuclearStation(MeasuringEquipment equipment) {
            return new ElectricStation()
        }

    }

    @Singleton
    static class MeasuringEquipment {
        @Inject protected ElectricalGrid grid
    }
}

