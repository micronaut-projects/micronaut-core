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
package io.micronaut.inject.configproperties

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.env.PropertySource
import spock.lang.Specification

class PrimitiveConfigurationPropertiesSpec extends Specification {

    void "test configuration properties binding"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(PropertySource.of(
            'test',
            ['foo.bar.port':'8080']
        ))

        applicationContext.start()

        MyPrimitiveConfig config = applicationContext.getBean(MyPrimitiveConfig)

        expect:
        config.port == 8080
        config.primitiveDefaultValue == 9999
    }

    @ConfigurationProperties('foo.bar')
    static class MyPrimitiveConfig {
        int port
        int primitiveDefaultValue = 9999
    }
}
