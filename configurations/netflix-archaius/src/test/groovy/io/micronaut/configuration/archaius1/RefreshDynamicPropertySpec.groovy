/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.configuration.archaius1

import com.netflix.config.DynamicIntProperty
import com.netflix.config.DynamicPropertyFactory
import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.context.scope.refresh.RefreshEvent
import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
class RefreshDynamicPropertySpec extends Specification {

    void "test refresh dynamic property"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(['foo.bar':5])

        when:
        DynamicIntProperty intProperty = DynamicPropertyFactory.instance.getIntProperty('foo.bar', 10)

        then:
        intProperty.get() == 5

        when:
        applicationContext.publishEvent(new RefreshEvent(['foo.bar':20]))

        then:
        intProperty.get() == 20

        when:
        applicationContext.publishEvent(new RefreshEvent(['foo.bar':null]))

        then:
        intProperty.get() == 10

        when:
        applicationContext.publishEvent(new RefreshEvent(['foo.bar':30]))

        then:
        intProperty.get() == 30
    }
}
