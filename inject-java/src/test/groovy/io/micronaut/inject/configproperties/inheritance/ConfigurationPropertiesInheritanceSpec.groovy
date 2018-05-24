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
package io.micronaut.inject.configproperties.inheritance

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.env.PropertySource
import spock.lang.Specification
/**
 * @author Graeme Rocher
 * @since 1.0
 */
class ConfigurationPropertiesInheritanceSpec extends Specification {

    void "test configuration properties binding"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(PropertySource.of(
                'test',
                ['foo.bar.port':'8080',
                'foo.bar.host':'localhost',
                'foo.bar.baz.stuff': 'test']
        ))

        applicationContext.start()

        ChildConfig config = applicationContext.getBean(ChildConfig)
        MyConfig parent = applicationContext.getBean(MyConfig)

        expect:
//        parent.is(config)
        parent.host == 'localhost'
        parent.port == 8080
        config.port == 8080
        config.host == 'localhost'
        config.stuff == 'test'
    }

    void "test configuration properties binding extending POJO"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(PropertySource.of(
                'test',
                ['foo.baz.otherProperty':'x',
                'foo.baz.onlySetter':'y',
                'foo.baz.port': 55]
        ))

        applicationContext.start()

        MyOtherConfig config = applicationContext.getBean(MyOtherConfig)

        expect:
        config.port == 55
        config.otherProperty == 'x'
        config.onlySetter == 'y'
    }
}
