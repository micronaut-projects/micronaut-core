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
package io.micronaut.inject.configproperties

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.annotation.BeanProperties
import io.micronaut.context.env.PropertySource
import spock.lang.Specification
/**
 * Created by graemerocher on 13/06/2017.
 */
class ConfigurationPropertiesSpec extends Specification {

    void "test configuration properties binding"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(PropertySource.of(
            'test',
            ['foo.bar.port':'8080',
            'foo.bar.anotherPort':'9090',
            'foo.bar.intList':"1,2,3",
            'foo.bar.stringList':"1,2",
            'foo.bar.inner.enabled':'true',
            'foo.bar.flags.one':'1',
            'foo.bar.flags.two':'2',
            'foo.bar.urlList':"http://test.com, http://test2.com",
            'foo.bar.urlList2':["http://test.com", "http://test2.com"],
            'foo.bar.url':'http://test.com']
        ))

        applicationContext.start()

        MyConfig config = applicationContext.getBean(MyConfig)

        expect:
        config.port == 8080

        config.anotherPort == 9090
        config.intList == [1,2,3]
        config.urlList == [new URL('http://test.com'),new URL('http://test2.com')]
        config.flags == [one:1, two:2]
        config.urlList2 == [new URL('http://test.com'),new URL('http://test2.com')]
        config.stringList == ["1", "2"]
        config.emptyList == null
        config.url.get() == new URL('http://test.com')
        !config.anotherUrl.isPresent()
        config.defaultPort == 9999
        config.defaultValue == 9999
        config.primitiveDefaultValue == 9999
        config.inner.enabled
        !applicationContext.getBeanDefinition(MyConfig).getAnnotation(BeanProperties.class)
    }

    void "test optional configuration"() {
        ApplicationContext context = ApplicationContext.run([
                "config.optional.str": "tst",
                "config.optional.dbl": "123.123",
                "config.optional.itgr": "456",
                "config.optional.lng": "334455",
        ])
        OptionalProperties config = context.getBean(OptionalProperties.class)

        expect:
            config.getStr().get() == "tst"
            config.getDbl().asDouble == 123.123 as double
            config.getItgr().asInt == 456
            config.getLng().asLong == 334455

        cleanup:
            context.close()
    }

}
