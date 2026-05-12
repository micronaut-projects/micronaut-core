/*
 * Copyright 2017-2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.python.annotation.processing.test

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext

class RequiresSpec extends AbstractPythonTypeElementSpec {

    void "test requires property"() {
        given:
        def definition = buildBeanDefinition("python", "MyBean", '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Requires

@Requires(property="feature.enabled")
@Singleton
class MyBean:
    pass
''')

        when:
        def context = startContext(["feature.enabled": true])

        then:
        definition.isEnabled(context)

        when:
        context.close()
        context = startContext()

        then:
        !definition.isEnabled(context)

        cleanup:
        context?.close()
    }

    void "test requires property value and pattern"() {
        given:
        def definition = buildBeanDefinition("python", "MyBean", '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Requires

@Requires(property="feature.mode", value="active")
@Requires(property="feature.port", pattern="\\\\d+")
@Singleton
class MyBean:
    pass
''')

        when:
        def context = startContext(["feature.mode": "active", "feature.port": "8080"])

        then:
        definition.isEnabled(context)

        when:
        context.close()
        context = startContext(["feature.mode": "disabled", "feature.port": "8080"])

        then:
        !definition.isEnabled(context)

        when:
        context.close()
        context = startContext(["feature.mode": "active", "feature.port": "http"])

        then:
        !definition.isEnabled(context)

        cleanup:
        context?.close()
    }

    void "test requires missing property"() {
        given:
        def definition = buildBeanDefinition("python", "MyBean", '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Requires

@Requires(missingProperty="feature.disabled")
@Singleton
class MyBean:
    pass
''')

        when:
        def context = startContext()

        then:
        definition.isEnabled(context)

        when:
        context.close()
        context = startContext(["feature.disabled": true])

        then:
        !definition.isEnabled(context)

        cleanup:
        context?.close()
    }

    void "test requires beans"() {
        given:
        def definition = buildBeanDefinition("python", "MyBean", '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Requires

@Requires(beans=str)
@Singleton
class MyBean:
    pass
''')

        when:
        def context = startContext()

        then:
        !definition.isEnabled(context)

        when:
        context.registerSingleton(String, "present")

        then:
        definition.isEnabled(context)

        cleanup:
        context?.close()
    }

    void "test requires missing beans"() {
        given:
        def definition = buildBeanDefinition("python", "MyBean", '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Requires

@Requires(missingBeans=str)
@Singleton
class MyBean:
    pass
''')

        when:
        def context = startContext()

        then:
        definition.isEnabled(context)

        when:
        context.registerSingleton(String, "present")

        then:
        !definition.isEnabled(context)

        cleanup:
        context?.close()
    }

    void "test requires environment"() {
        given:
        def definition = buildBeanDefinition("python", "MyBean", '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Requires

@Requires(env="feature-test")
@Singleton
class MyBean:
    pass
''')

        when:
        def context = startContext([:], "feature-test")

        then:
        definition.isEnabled(context)

        when:
        context.close()
        context = startContext()

        then:
        !definition.isEnabled(context)

        cleanup:
        context?.close()
    }

    void "test requires not environment"() {
        given:
        def definition = buildBeanDefinition("python", "MyBean", '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Requires

@Requires(notEnv="feature-test")
@Singleton
class MyBean:
    pass
''')

        when:
        def context = startContext()

        then:
        definition.isEnabled(context)

        when:
        context.close()
        context = startContext([:], "feature-test")

        then:
        !definition.isEnabled(context)

        cleanup:
        context?.close()
    }

    void "test requires classes and missing classes"() {
        given:
        def requiresClassDefinition = buildBeanDefinition("python", "RequiresClassBean", '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Requires

@Requires(classes=str)
@Singleton
class RequiresClassBean:
    pass
''')
        def missingPresentClassDefinition = buildBeanDefinition("python", "MissingPresentClassBean", '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Requires

@Requires(missingClasses="java.lang.String")
@Singleton
class MissingPresentClassBean:
    pass
''')
        def missingAbsentClassDefinition = buildBeanDefinition("python", "MissingAbsentClassBean", '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Requires

@Requires(missingClasses="example.DoesNotExist")
@Singleton
class MissingAbsentClassBean:
    pass
''')

        expect:
        requiresClassDefinition.isEnabled(BeanContext.build())
        !missingPresentClassDefinition.isEnabled(BeanContext.build())
        missingAbsentClassDefinition.isEnabled(BeanContext.build())
    }

    private static ApplicationContext startContext(Map properties = [:], String... environments) {
        def builder = environments.length ? ApplicationContext.builder(environments) : ApplicationContext.builder()
        builder.properties(properties)
        builder.beanDefinitionsProvider { [] }
        def context = builder.build()
        context.environment.start()
        context
    }
}
