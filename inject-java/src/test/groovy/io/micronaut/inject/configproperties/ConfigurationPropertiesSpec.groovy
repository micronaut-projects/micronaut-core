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
import io.micronaut.context.env.PropertySource
import spock.lang.Specification

class ConfigurationPropertiesSpec extends Specification {

    void "test submap with generics binding"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                'foo.bar.map.key1.key2.property':10,
                'foo.bar.map.key1.key2.property2.property':10
        )

        expect:
        ctx.getBean(MyConfig).map.containsKey('key1')
        ctx.getBean(MyConfig).map.get("key1") instanceof Map
        ctx.getBean(MyConfig).map.get("key1").get("key2") instanceof MyConfig.Value
        ctx.getBean(MyConfig).map.get("key1").get("key2").property == 10
        ctx.getBean(MyConfig).map.get("key1").get("key2").property2
        ctx.getBean(MyConfig).map.get("key1").get("key2").property2.property == 10

        cleanup:
        ctx.close()
    }

    void "test submap with generics binding and conversion"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                'foo.bar.map.key1.key2.property':'10',
                'foo.bar.map.key1.key2.property2.property':'10'
        )

        expect:
        ctx.getBean(MyConfig).map.containsKey('key1')
        ctx.getBean(MyConfig).map.get("key1") instanceof Map
        ctx.getBean(MyConfig).map.get("key1").get("key2") instanceof MyConfig.Value
        ctx.getBean(MyConfig).map.get("key1").get("key2").property == 10
        ctx.getBean(MyConfig).map.get("key1").get("key2").property2
        ctx.getBean(MyConfig).map.get("key1").get("key2").property2.property == 10

        cleanup:
        ctx.close()
    }

    void "test configuration properties binding"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(PropertySource.of(
            'test',
            ['foo.bar.innerVals': [
                    ['expire-unsigned-seconds': 123], ['expireUnsignedSeconds': 600]
            ],
             'foo.bar.port':'8080',
             'foo.bar.max-size':'1MB',
             'foo.bar.another-size':'1MB',
            'foo.bar.anotherPort':'9090',
            'foo.bar.intList':"1,2,3",
            'foo.bar.stringList':"1,2",
            'foo.bar.flags.one':'1',
            'foo.bar.flags.two':'2',
            'foo.bar.urlList':"http://test.com, http://test2.com",
            'foo.bar.urlList2':["http://test.com", "http://test2.com"],
            'foo.bar.url':'http://test.com']
        ))

        applicationContext.start()

        MyConfig config = applicationContext.getBean(MyConfig)

        expect:
        config.innerVals.size() == 2
        config.innerVals[0].expireUnsignedSeconds == 123
        config.innerVals[1].expireUnsignedSeconds == 600
        config.port == 8080
        config.maxSize == 1048576
        config.anotherSize == 1048576
        config.anotherPort == 9090
        config.intList == [1,2,3]
        config.flags == [one:1, two:2]
        config.urlList == [new URL('http://test.com'),new URL('http://test2.com')]
        config.urlList2 == [new URL('http://test.com'),new URL('http://test2.com')]
        config.stringList == ["1", "2"]
        config.emptyList == null
        config.url.get() == new URL('http://test.com')
        !config.anotherUrl.isPresent()
        config.defaultPort == 9999
        config.defaultValue == 9999
        config.primitiveDefaultValue == 9999
    }

    void "test configuration inner class properties binding"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(PropertySource.of(
            'foo.bar.inner.enabled':'true',
        ))

        applicationContext.start()

        MyConfig config = applicationContext.getBean(MyConfig)

        expect:
        config.inner.enabled
    }
}
