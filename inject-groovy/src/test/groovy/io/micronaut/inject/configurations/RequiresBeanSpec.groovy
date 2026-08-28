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
import io.micronaut.inject.configurations.requiresbean.RequiresBean
import io.micronaut.inject.configurations.requirescondition2.TrueLambdaBean
import io.micronaut.inject.configurations.requiresconditiontrue.TrueBean
import io.micronaut.inject.configurations.requiresconfig.RequiresConfig
import io.micronaut.inject.configurations.requiresproperty.RequiresProperty
import io.micronaut.inject.configurations.requiressdk.RequiresJava9
import spock.lang.Specification
import spock.util.environment.Jvm

/**
 * Created by graemerocher on 19/05/2017.
 */
class RequiresBeanSpec extends Specification {

    void "test that a configuration can require a bean"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        expect:
        context.containsBean(ABean)
        context.containsBean(TrueBean)
        context.containsBean(TrueLambdaBean)
        !context.containsBean(RequiresBean)
        !context.containsBean(RequiresConfig)
        Jvm.current.isJava9Compatible() || !context.containsBean(RequiresJava9)
//        !context.containsBean(GitHubActionsBean) // TODO: these are broken because closures are not supported for @Requires( condition = {})
//        !context.containsBean(GitHubActionsBean2)

        cleanup:
        context.close()
    }

    void "test that a condition can be required for a bean when false"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        expect:
        context.containsBean(ABean)
//        !context.containsBean(GitHubActionsBean2) // TODO: these are broken because closures are not supported for @Requires( condition = {})

        cleanup:
        context.close()
    }

//    @Ignore("it doesn't matter whether TrueEnvCondition returns true or false, context never has TrueBean")
    void "test that a condition can be required for a bean when true"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        expect:
        context.containsBean(ABean)
        context.containsBean(TrueBean)

        cleanup:
        context.close()
    }

    void "test that a lambda condition can be required for a bean when true"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        expect:
        context.containsBean(ABean)
        context.containsBean(TrueLambdaBean)

        cleanup:
        context.close()
    }

    void "test requires property when not present"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        expect:
        !context.containsBean(RequiresProperty)

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
