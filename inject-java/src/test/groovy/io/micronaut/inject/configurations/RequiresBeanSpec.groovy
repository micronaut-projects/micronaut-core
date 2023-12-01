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
package io.micronaut.inject.configurations

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.env.PropertySource
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.inject.configurations.requiresbean.RequiresBean
import io.micronaut.inject.configurations.requiresconditionfalse.GitHubActionsBean
import io.micronaut.inject.configurations.requiresconditiontrue.TrueBean
import io.micronaut.inject.configurations.requiresconfig.RequiresConfig
import io.micronaut.inject.configurations.requiresproperty.RequiresProperty
import io.micronaut.inject.configurations.requiressdk.RequiresJava9
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.util.environment.Jvm

class RequiresBeanSpec extends Specification {

    void "test that a configuration can require a bean"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        context.start()

        expect:
        context.containsBean(ABean)
        !context.containsBean(RequiresBean)
        !context.containsBean(RequiresConfig)
        Jvm.current.isJava9Compatible() || !context.containsBean(RequiresJava9)

        cleanup:
        context.close()
    }

    @IgnoreIf({ env["GITHUB_ACTIONS"] } ) // fails on GitHub actions, which is expected
    void "test that a condition can be required for a bean when false"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        expect:
        context.containsBean(ABean)
        !context.containsBean(GitHubActionsBean)

        cleanup:
        context.close()
    }

    void "test that a condition can be required for a bean when true"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        expect:
        context.containsBean(ABean)
        context.containsBean(TrueBean)

        cleanup:
        context.close()
    }

    void "test requires property when not present"() {
        when:
        ApplicationContext context = ApplicationContext.run()

        then:
        !context.containsBean(RequiresProperty)

        when:
        context.getBean(RequiresProperty)

        then:
        NoSuchBeanException e = thrown()
        def list = e.message.readLines().collect { it.trim()}
        list[0] == 'No bean of type [io.micronaut.inject.configurations.requiresproperty.RequiresProperty] exists. The bean [RequiresProperty] is disabled because it is within the package [io.micronaut.inject.configurations.requiresproperty] which is disabled due to bean requirements:'
        list[1] == '* Required property [data-source.url] with value [null] not present'

        cleanup:
        context.close()
    }

    void "test requires property when present"() {
        given:
        ApplicationContext context = ApplicationContext.run(['dataSource.url':'jdbc::blah'])

        expect:
        context.containsBean(RequiresProperty)

        cleanup:
        context.close()
    }
}
