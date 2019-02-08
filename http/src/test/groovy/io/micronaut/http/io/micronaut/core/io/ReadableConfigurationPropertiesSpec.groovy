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
package io.micronaut.http.io.micronaut.core.io

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.exceptions.DependencyInjectionException
import io.micronaut.core.io.Readable
import spock.lang.Specification

class ReadableConfigurationPropertiesSpec extends Specification {

    void "test readable binding success"() {
        given:
        ApplicationContext ctx = ApplicationContext.run('test.readable.logback-file':'classpath:logback.xml')
        MyConfig myConfig = ctx.getBean(MyConfig)

        expect:
        myConfig.logbackFile != null
        myConfig.logbackFile.exists()
        myConfig.logbackFile.asInputStream().text.contains('<configuration>')

        cleanup:
        ctx.close()
    }

    void "test readable binding failure"() {
        when:
        ApplicationContext ctx = ApplicationContext.run('test.readable.logback-file':'classpath:nothere.xml')
        MyConfig myConfig = ctx.getBean(MyConfig)

        then:
        def e = thrown(DependencyInjectionException)
        e.message.contains('No resource exists for value: classpath:nothere.xml')

        cleanup:
        ctx.close()
    }

    @ConfigurationProperties("test.readable")
    static class MyConfig {
        Readable logbackFile
    }
}
