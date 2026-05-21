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
import io.micronaut.context.annotation.Requires
import io.micronaut.context.condition.OperatingSystem
import io.micronaut.context.exceptions.NoSuchBeanException

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

    void "test requires property not equals"() {
        given:
        def definition = buildBeanDefinition("python", "MyBean", '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Requires

@Requires(property="feature.mode", notEquals="disabled")
@Singleton
class MyBean:
    pass
''')

        when:
        def context = startContext(["feature.mode": "active"])

        then:
        definition.isEnabled(context)

        when:
        context.close()
        context = startContext(["feature.mode": "disabled"])

        then:
        !definition.isEnabled(context)

        when:
        context.close()
        context = startContext()

        then:
        definition.isEnabled(context)

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

    void "test requires bean property presence"() {
        given:
        def context = buildContext(requiresBeanPropertyCode(), false, [
            "config.property": "anyValue"
        ])

        expect:
        getBean(context, "python.PresentPropertyDependantBean") != null

        cleanup:
        context?.close()
    }

    void "test requires bean property with absent property"() {
        given:
        def context = buildContext(requiresBeanPropertyCode())

        when:
        getBean(context, "python.PresentPropertyDependantBean")

        then:
        thrown(NoSuchBeanException)

        cleanup:
        context?.close()
    }

    void "test requires bean property not equals with value not set"() {
        given:
        def context = buildContext(requiresBeanPropertyNotEqualsCode())

        expect:
        getBean(context, "python.NotConcreteValueBean") != null

        cleanup:
        context?.close()
    }

    void "test requires bean property not equals with value set"() {
        given:
        def context = buildContext(requiresBeanPropertyNotEqualsCode(), false, [
            "config.property": "concreteValue"
        ])

        when:
        getBean(context, "python.NotConcreteValueBean")

        then:
        thrown(NoSuchBeanException)

        cleanup:
        context?.close()
    }

    void "test meta requires condition"() {
        given:
        def definition = buildBeanDefinition("python", "MyBean", '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Requires

@Requires(property="feature.meta", value="enabled")
@Requires(beans=str)
def MetaRequires(cls):
    return cls

@MetaRequires
@Singleton
class MyBean:
    pass
''')

        when:
        def context = startContext(["feature.meta": "enabled"])

        then:
        !definition.isEnabled(context)

        when:
        context.registerSingleton(String, "present")

        then:
        definition.isEnabled(context)

        when:
        context.close()
        context = startContext(["feature.meta": "disabled"])
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

    void "test requires java sdk"() {
        given:
        def supportedDefinition = buildBeanDefinition("python", "SupportedBean", '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Requires

@Requires(sdk=Requires.Sdk.JAVA, version="17")
@Singleton
class SupportedBean:
    pass
''')
        def unsupportedDefinition = buildBeanDefinition("python", "UnsupportedBean", '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Requires

@Requires(sdk=Requires.Sdk.JAVA, version="800")
@Singleton
class UnsupportedBean:
    pass
''')
        def context = startContext()

        expect:
        supportedDefinition.isEnabled(context)
        !unsupportedDefinition.isEnabled(context)

        cleanup:
        context?.close()
    }

    void "test requires operating system"() {
        given:
        def osDefinition = buildBeanDefinition("python", "OsBean", '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Requires

@Requires(os=[Requires.Family.WINDOWS, Requires.Family.MAC_OS])
@Singleton
class OsBean:
    pass
''')
        def notOsDefinition = buildBeanDefinition("python", "NotOsBean", '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Requires

@Requires(notOs=[Requires.Family.WINDOWS, Requires.Family.MAC_OS])
@Singleton
class NotOsBean:
    pass
''')

        when:
        OperatingSystem.instance = new OperatingSystem(Requires.Family.LINUX)
        def context = startContext()

        then:
        !osDefinition.isEnabled(context)
        notOsDefinition.isEnabled(context)

        when:
        context.close()
        OperatingSystem.instance = new OperatingSystem(Requires.Family.WINDOWS)
        context = startContext()

        then:
        osDefinition.isEnabled(context)
        !notOsDefinition.isEnabled(context)

        cleanup:
        OperatingSystem.instance = null
        context?.close()
    }

    private static ApplicationContext startContext(Map properties = [:], String... environments) {
        def builder = environments.length ? ApplicationContext.builder(environments) : ApplicationContext.builder()
        builder.properties(properties)
        builder.beanDefinitionsProvider { [] }
        def context = builder.build()
        context.environment.start()
        context
    }

    private static String requiresBeanPropertyCode() {
        '''
from jakarta.inject import Singleton
from micronaut.context.annotation import ConfigurationProperties, Requires

@ConfigurationProperties("config")
class Config:
    property: str = None

@Requires(bean=Config, beanProperty="property")
@Singleton
class PresentPropertyDependantBean:
    pass
'''
    }

    private static String requiresBeanPropertyNotEqualsCode() {
        '''
from jakarta.inject import Singleton
from micronaut.context.annotation import ConfigurationProperties, Requires

@ConfigurationProperties("config")
class Config:
    property: str = None

@Requires(bean=Config, beanProperty="property", notEquals="concreteValue")
@Singleton
class NotConcreteValueBean:
    pass
'''
    }
}
