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
import io.micronaut.context.annotation.ConfigurationReader
import io.micronaut.context.annotation.Property
import io.micronaut.context.env.Environment
import io.micronaut.inject.ValidatedBeanDefinition
import io.micronaut.inject.qualifiers.Qualifiers

/**
 * Tests for Python @ConfigurationProperties annotation.
 *
 * @author Micronaut
 * @since 4.8.0
 */
class ConfigurationPropertiesSpec extends AbstractPythonTypeElementSpec {
    void "test configuration properties with constraints is validating bean definition"() {
        given:
        def definition = buildBeanDefinition("python", "ServiceConfig", '''
from typing import Annotated
from micronaut.context.annotation import ConfigurationProperties
from jakarta.validation.constraints import NotBlank

@ConfigurationProperties("service")
class ServiceConfig:
    name: Annotated[str, NotBlank]
''')

        expect:
        definition instanceof ValidatedBeanDefinition
    }

    void "test validated configuration properties can be injected into another bean"() {
        given:
        def pythonCode = '''
from typing import Annotated
from jakarta.inject import Inject, Singleton
from jakarta.validation.constraints import NotBlank
from micronaut.context.annotation import ConfigurationProperties

@ConfigurationProperties("service")
class ServiceConfig:
    name: Annotated[str, NotBlank]

@Singleton
class Service:
    def __init__(self, config: ServiceConfig):
        self.config = config

    def get_name(self) -> str:
        return self.config.name

@Singleton
class FieldInjectedService:
    config: Annotated[ServiceConfig, Inject]

    def get_name(self) -> str:
        return self.config.name
'''

        when:
        def context = buildContext(pythonCode, false, ["service.name": "catalog"])
        def service = getBean(context, "python.Service")
        def fieldInjectedService = getBean(context, "python.FieldInjectedService")

        then:
        service.asPolyglotValue().invokeMember("get_name").asString() == "catalog"
        fieldInjectedService.asPolyglotValue().invokeMember("get_name").asString() == "catalog"

        cleanup:
        context?.close()
    }

    void "test each property on Python class tht implements Java interface"() {
        given:
        def pythonCode = '''
from micronaut.context.annotation import EachProperty
from micronaut.core.util import Toggleable

@EachProperty("engines")
class EngineConfiguration(Toggleable):
    cylinders : int
    enabled : bool = True
'''

        when:
        def properties = [
                "engines.ferrari.cylinders": "8",
                "engines.ferrari.enabled": false,
                "engines.ford.cylinders": "6"
        ]
        def context = buildContext(pythonCode, false, properties)
        def ferrariBean = getBean(context, "python.EngineConfiguration", Qualifiers.byName("ferrari"))
        def fordBean = getBean(context, "python.EngineConfiguration", Qualifiers.byName("ford"))

        then:
        ferrariBean != null
        ferrariBean.getCylinders() == 8
        ferrariBean.isEnabled() == false
        fordBean.getCylinders() == 6
        fordBean.isEnabled() == true
    }

    void "test each property on Python class"() {
        given:
        def pythonCode = '''
from micronaut.context.annotation import EachProperty
#from micronaut.core.util import Toggleable

@EachProperty("engines")
class EngineConfiguration:
    cylinders : int
    enabled : bool = True
'''

        when:
        def properties = [
                "engines.ferrari.cylinders": "8",
                "engines.ferrari.enabled": false,
                "engines.ford.cylinders": "6"
        ]
        def context = buildContext(pythonCode, false, properties)
        def ferrariBean = getBean(context, "python.EngineConfiguration", Qualifiers.byName("ferrari"))
        def fordBean = getBean(context, "python.EngineConfiguration", Qualifiers.byName("ford"))

        then:
        ferrariBean != null
        ferrariBean.cylinders == 8
        ferrariBean.enabled == false
        fordBean.cylinders == 6
        fordBean.enabled == true
    }

    void "test each property with each bean on Python factory"() {
        given:
        def pythonCode = '''
from micronaut.context.annotation import EachProperty, EachBean, Executable, Factory
#from micronaut.core.util import Toggleable

@EachProperty("engines")
class EngineConfiguration:
    cylinders : int
    enabled : bool = True

class Engine:
    def __init__(self, config : EngineConfiguration):
        self.config = config

    def enabled(self) -> bool:
        return self.config.enabled

    def cylinders(self) -> int:
        return self.config.cylinders

@Factory
class EngineFactory:
    @EachBean(EngineConfiguration.__qualname__)
    def engine(self, config : EngineConfiguration) -> Engine:
        return Engine(config)

'''

        when:
        def properties = [
                "engines.ferrari.cylinders": "8",
                "engines.ferrari.enabled": false,
                "engines.ford.cylinders": "6"
        ]
        def context = buildContext(pythonCode, false, properties)
        def ferrariBean = getBean(context, "python.Engine", Qualifiers.byName("ferrari")).asPolyglotValue()
        def fordBean = getBean(context, "python.Engine", Qualifiers.byName("ford")).asPolyglotValue()

        then:
        ferrariBean != null
        ferrariBean.invokeMember("cylinders").asInt() == 8
        ferrariBean.invokeMember("enabled").asBoolean() == false
        fordBean.invokeMember("cylinders").asInt() == 6
        fordBean.invokeMember("enabled").asBoolean() == true
    }

    void "test each property with each bean on Python class"() {
        given:
        def pythonCode = '''
from micronaut.context.annotation import EachProperty, EachBean, Executable
#from micronaut.core.util import Toggleable

@EachProperty("engines")
class EngineConfiguration:
    cylinders : int
    enabled : bool = True

@EachBean(EngineConfiguration.__qualname__)
class Engine:
    def __init__(self, config : EngineConfiguration):
        self.config = config

    @Executable
    def enabled(self) -> bool:
        return self.config.enabled

    @Executable
    def cylinders(self) -> int:
        return self.config.cylinders
'''

        when:
        def properties = [
                "engines.ferrari.cylinders": "8",
                "engines.ferrari.enabled": false,
                "engines.ford.cylinders": "6"
        ]
        def context = buildContext(pythonCode, false, properties)
        def ferrariBean = getBean(context, "python.Engine", Qualifiers.byName("ferrari"))
        def fordBean = getBean(context, "python.Engine", Qualifiers.byName("ford"))

        then:
        ferrariBean != null
        ferrariBean.cylinders() == 8
        ferrariBean.enabled() == false
        fordBean.cylinders() == 6
        fordBean.enabled() == true
    }

    void "test each bean injects configuration name parameter"() {
        given:
        def pythonCode = '''
from typing import Annotated
from micronaut.context.annotation import EachProperty, EachBean, Executable, Parameter

@EachProperty("engines")
class EngineConfiguration:
    cylinders : int

@EachBean(EngineConfiguration.__qualname__)
class Engine:
    def __init__(self, config: EngineConfiguration, name: Annotated[str, Parameter]):
        self.config = config
        self.name = name

    @Executable
    def engine_name(self) -> str:
        return self.name

    @Executable
    def cylinders(self) -> int:
        return self.config.cylinders
'''

        when:
        def properties = [
                "engines.ferrari.cylinders": "8",
                "engines.ford.cylinders": "6"
        ]
        def context = buildContext(pythonCode, false, properties)
        def ferrariBean = getBean(context, "python.Engine", Qualifiers.byName("ferrari"))
        def fordBean = getBean(context, "python.Engine", Qualifiers.byName("ford"))

        then:
        ferrariBean.engine_name() == "ferrari"
        ferrariBean.cylinders() == 8
        fordBean.engine_name() == "ford"
        fordBean.cylinders() == 6

        cleanup:
        context?.close()
    }

    void "test each bean with empty replaces does not replace itself"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
from micronaut.context.annotation import EachBean, EachProperty, Replaces

@EachProperty(value="mydatasources", primary="default")
class MyDataSource:
    xyz: str

@EachBean(MyDataSource.__qualname__)
@Singleton
@Replaces
class MyService:
    def __init__(self, data_source: MyDataSource):
        self.data_source = data_source
'''

        when:
        def context = buildContext(pythonCode, false, [
                "mydatasources.default.xyz": "111",
                "mydatasources.foo.xyz": "111",
                "mydatasources.bar.xyz": "111"
        ])
        def serviceType = context.classLoader.loadClass("python.MyService")
        def services = context.getBeansOfType(serviceType)

        then:
        services.size() == 3

        cleanup:
        context?.close()
    }

    void "test @ConfigurationProperties on Python @dataclass"() {
        given:
        def pythonCode = '''
from micronaut.context.annotation import ConfigurationProperties
from dataclasses import dataclass

@ConfigurationProperties("myapp")
@dataclass
class AppConfig:
    name: str
    port: int = 8080
    enabled: bool = True
'''

        when:
        def properties = [
            "myapp.name": "MyApp",
            "myapp.port": "9090",
            "myapp.enabled": "false"
        ]
        def context = buildContext(pythonCode, false, properties)
        def configBean = getBean(context, "python.AppConfig")

        then:
        configBean != null
        configBean.name == "MyApp"
        configBean.port == 9090
        configBean.enabled == false

        cleanup:
        context?.close()
    }

    void "test @ConfigurationProperties on regular Python class with attributes"() {
        given:
        def pythonCode = '''
from micronaut.context.annotation import ConfigurationProperties, Executable
from micronaut.context.env import Environment

@ConfigurationProperties("database")
class DatabaseConfig:
    host: str = "localhost"
    port: int = 5432

    def __init__(self, env: Environment):
        self.env = env

    @Executable
    def has_env(self) -> bool:
        return self.env is not None
'''

        when:
        def properties = [
            "database.host": "db.example.com"
        ]
        def context = buildContext(pythonCode, false, properties)
        def configBean = getBean(context, "python.DatabaseConfig")

        then:
        configBean != null
        configBean.host == "db.example.com"
        configBean.port == 5432
        configBean.has_env()

        cleanup:
        context?.close()
    }

    void "test configuration property names with numbers"() {
        given:
        def pythonCode = '''
from micronaut.context.annotation import ConfigurationProperties

@ConfigurationProperties("aws")
class AwsConfig:
    disable_ec2_metadata: str
    disable_ec_metadata: str
    disable_ec2instance_metadata: str
'''

        when:
        def context = buildContext(pythonCode, false, [
                "aws.disable-ec2-metadata": "disabled",
                "aws.disable-ec-metadata": "enabled",
                "aws.disable-ec2instance-metadata": "instance-disabled"
        ])
        def configBean = getBean(context, "python.AwsConfig")

        then:
        configBean.disable_ec2_metadata == "disabled"
        configBean.disable_ec_metadata == "enabled"
        configBean.disable_ec2instance_metadata == "instance-disabled"

        cleanup:
        context?.close()
    }

    void "test configuration property includes and excludes"() {
        given:
        def pythonCode = '''
from micronaut.context.annotation import ConfigurationProperties

@ConfigurationProperties(value="include", includes=["public_field"])
class IncludedConfig:
    public_field: str = "default"
    another_public_field: str = "default"

@ConfigurationProperties(value="exclude", excludes=["another_public_field"])
class ExcludedConfig:
    public_field: str = "default"
    another_public_field: str = "default"
'''

        when:
        def context = buildContext(pythonCode, false, [
                "include.public-field": "configured",
                "include.another-public-field": "ignored",
                "exclude.public-field": "configured",
                "exclude.another-public-field": "ignored"
        ])
        def includedConfig = getBean(context, "python.IncludedConfig")
        def excludedConfig = getBean(context, "python.ExcludedConfig")

        then:
        includedConfig.public_field == "configured"
        excludedConfig.public_field == "configured"

        when:
        includedConfig.another_public_field

        then:
        thrown(MissingPropertyException)

        when:
        excludedConfig.another_public_field

        then:
        thrown(MissingPropertyException)

        cleanup:
        context?.close()
    }

    void "test configuration reader prefix aliases from Java stereotypes"() {
        given:
        def pythonCode = '''
from micronaut.python.compiler import TestEndpoint2, TestEndpoint4

@TestEndpoint2("simple")
class SimpleEndpoint:
    my_value: str | None = None

@TestEndpoint4("simple")
class BaseEndpoint:
    my_value: str | None = None
'''

        when:
        def context = buildContext(pythonCode, false, [
                "simple.my-value": "simple-value",
                "endpoints.simple.my-value": "base-value"
        ])
        def simpleEndpoint = getBean(context, "python.SimpleEndpoint")
        def baseEndpoint = getBean(context, "python.BaseEndpoint")

        then:
        simpleEndpoint.my_value == "simple-value"
        baseEndpoint.my_value == "base-value"

        cleanup:
        context?.close()
    }

    void "test configuration properties post construct sees bound values"() {
        given:
        def pythonCode = '''
from jakarta.annotation import PostConstruct
from micronaut.context.annotation import ConfigurationProperties

@ConfigurationProperties("app.entity")
class EntityProperties:
    prop: str
    initialized_prop: str = None

    @PostConstruct
    def init(self):
        self.initialized_prop = self.prop
'''

        when:
        def context = buildContext(pythonCode, false, [
                "app.entity.prop": "configured"
        ])
        def configBean = getBean(context, "python.EntityProperties")

        then:
        configBean.prop == "configured"
        configBean.initialized_prop == "configured"

        cleanup:
        context?.close()
    }

    void "test configuration inject constructor with beans and other configs"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
from micronaut.context.annotation import ConfigurationInject, ConfigurationProperties, Executable

@ConfigurationProperties("xyz")
class OtherConfig:
    name: str

@Singleton
class OtherSingleton:
    pass

@ConfigurationProperties("foo.bar")
class ConfigWithConstructor:
    @ConfigurationInject
    def __init__(
        self,
        host: str,
        server_port: int,
        other_config: OtherConfig,
        other_singleton: OtherSingleton
    ):
        self.host = host
        self.server_port = server_port
        self.other_config = other_config
        self.other_singleton = other_singleton

    @Executable
    def get_host(self) -> str:
        return self.host

    @Executable
    def get_server_port(self) -> int:
        return self.server_port

    @Executable
    def get_other_config(self) -> OtherConfig:
        return self.other_config

    @Executable
    def get_other_singleton(self) -> OtherSingleton:
        return self.other_singleton
'''

        when:
        def context = buildContext(pythonCode, false, [
                "foo.bar.host": "test",
                "foo.bar.server-port": "123",
                "xyz.name": "other"
        ])
        def configBean = getBean(context, "python.ConfigWithConstructor")

        then:
        configBean.get_host() == "test"
        configBean.get_server_port() == 123
        configBean.get_other_config().name == "other"
        configBean.get_other_singleton() != null

        cleanup:
        context?.close()
    }

    void "test configuration inject constructor argument metadata"() {
        given:
        def pythonCode = '''
from micronaut.context.annotation import ConfigurationInject, ConfigurationProperties, Executable

@ConfigurationProperties("foo.bar")
class ConfigWithConstructor:
    @ConfigurationInject
    def __init__(self, host: str, server_port: int):
        self.host = host
        self.server_port = server_port

    @Executable
    def get_host(self) -> str:
        return self.host

    @Executable
    def get_server_port(self) -> int:
        return self.server_port
'''

        when:
        def definition = buildBeanDefinition("python", "ConfigWithConstructor", pythonCode)
        def arguments = definition.constructor.arguments

        then:
        arguments.length == 2
        arguments[0].synthesize(Property).name() == "foo.bar.host"
        arguments[1].synthesize(Property).name() == "foo.bar.server-port"

        when:
        def context = buildContext(pythonCode, false, [
                "foo.bar.host": "test",
                "foo.bar.server-port": "123"
        ])
        def configBean = getBean(context, "python.ConfigWithConstructor")

        then:
        configBean.get_host() == "test"
        configBean.get_server_port() == 123

        cleanup:
        context?.close()
    }

    void "test each property configuration inject constructor argument metadata"() {
        given:
        def pythonCode = '''
from micronaut.context.annotation import ConfigurationInject, EachProperty, Executable

@EachProperty("engines")
class EngineConfiguration:
    @ConfigurationInject
    def __init__(self, cylinders: int, manufacturer: str):
        self.cylinders = cylinders
        self.manufacturer = manufacturer

    @Executable
    def get_cylinders(self) -> int:
        return self.cylinders

    @Executable
    def get_manufacturer(self) -> str:
        return self.manufacturer
'''

        when:
        def definition = buildBeanDefinition("python", "EngineConfiguration", pythonCode)
        def arguments = definition.constructor.arguments

        then:
        arguments.length == 2
        arguments[0].synthesize(Property).name() == "engines.*.cylinders"
        arguments[1].synthesize(Property).name() == "engines.*.manufacturer"

        when:
        def context = buildContext(pythonCode, false, [
                "engines.ferrari.cylinders": "8",
                "engines.ferrari.manufacturer": "Ferrari",
                "engines.ford.cylinders": "6",
                "engines.ford.manufacturer": "Ford"
        ])
        def ferrariBean = getBean(context, "python.EngineConfiguration", Qualifiers.byName("ferrari"))
        def fordBean = getBean(context, "python.EngineConfiguration", Qualifiers.byName("ford"))

        then:
        ferrariBean.get_cylinders() == 8
        ferrariBean.get_manufacturer() == "Ferrari"
        fordBean.get_cylinders() == 6
        fordBean.get_manufacturer() == "Ford"

        cleanup:
        context?.close()
    }

    void "test each property nested configuration reader prefix"() {
        given:
        def pythonCode = '''
from micronaut.context.annotation import ConfigurationProperties, EachProperty

@EachProperty("foo.bar")
class MyConfig:
    host: str

    @ConfigurationProperties("baz")
    class ChildConfig:
        stuff: str
'''

        when:
        def definition = buildBeanDefinition("python", "MyConfig\$ChildConfig", pythonCode)

        then:
        definition.synthesize(ConfigurationReader).prefix() == "foo.bar.*.baz"
    }

    void "test each property nested configuration property metadata"() {
        given:
        def pythonCode = '''
from micronaut.context.annotation import ConfigurationProperties, EachProperty

@EachProperty("foo.bar")
class MyConfig:
    host: str

    @ConfigurationProperties("baz")
    class ChildConfig:
        stuff: str
'''

        when:
        def definition = buildBeanDefinition("python", "MyConfig\$ChildConfig", pythonCode)
        def propertyInjection = definition.injectedMethods.find { method ->
            method.arguments.length == 1 && method.arguments[0].annotationMetadata.hasAnnotation(Property)
        }

        then:
        propertyInjection != null
        propertyInjection.arguments[0].annotationMetadata.synthesize(Property).name() == "foo.bar.*.baz.stuff"
    }

    void "test nested configuration properties bind nested classes"() {
        given:
        def pythonCode = '''
from micronaut.context.annotation import ConfigurationProperties

@ConfigurationProperties("test")
class OuterConfig:
    @ConfigurationProperties("inner")
    class InnerConfig:
        @ConfigurationProperties("nested")
        class ThirdLevel:
            num: int

        foo: str
        third_level: ThirdLevel

    name: str
    age: int
    inner: InnerConfig
'''

        when:
        def context = buildContext(pythonCode, false, [
                "test.name": "test1",
                "test.age": "10",
                "test.inner.foo": "test2",
                "test.inner.nested.num": "20"
        ])
        def configBean = getBean(context, "python.OuterConfig")

        then:
        configBean.name == "test1"
        configBean.age == 10
        configBean.inner.foo == "test2"
        configBean.inner.third_level.num == 20

        cleanup:
        context?.close()
    }

    void "test each property nested configuration properties bind nested each property list"() {
        given:
        def pythonCode = '''
from typing import Annotated
from micronaut.context.annotation import ConfigurationInject, ConfigurationProperties, EachProperty, Parameter

@EachProperty(value="test", primary="one")
class OuterConfig:
    @ConfigurationProperties("inner")
    class InnerConfig:
        @EachProperty("inners")
        class InnerEachConfig:
            @ConfigurationInject
            def __init__(self, name: Annotated[str, Parameter]):
                self.name = name

            count: int

        foo: str
        inners: list[InnerEachConfig]

    @ConfigurationInject
    def __init__(self, name: Annotated[str, Parameter]):
        self.name = name

    age: int
    inner: InnerConfig
'''

        when:
        def context = buildContext(pythonCode, false, [
                "test.one.age": "10",
                "test.one.inner.foo": "test2",
                "test.one.inner.inners.a.count": "20",
                "test.two.age": "30",
                "test.two.inner.foo": "test3",
                "test.two.inner.inners.1st.count": "30",
                "test.two.inner.inners.2nd.count": "40"
        ])
        def configBean = getBean(context, "python.OuterConfig")
        def configBean2 = getBean(context, "python.OuterConfig", Qualifiers.byName("two"))

        then:
        configBean.name == "one"
        configBean.age == 10
        configBean.inner.foo == "test2"
        configBean.inner.inners.size() == 1
        configBean.inner.inners[0].name == "a"
        configBean.inner.inners[0].count == 20
        configBean2.name == "two"
        configBean2.age == 30
        configBean2.inner.foo == "test3"
        configBean2.inner.inners.size() == 2
        configBean2.inner.inners.find { it.name == "1st" }.count == 30
        configBean2.inner.inners.find { it.name == "2nd" }.count == 40

        cleanup:
        context?.close()
    }

    void "test configuration inject method with beans and other configs"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
from micronaut.context.annotation import ConfigurationInject, ConfigurationProperties, Executable

@ConfigurationProperties("xyz")
class OtherConfig:
    name: str

@Singleton
class OtherSingleton:
    pass

@ConfigurationProperties("foo.bar")
class ConfigWithMethod:
    def __init__(self):
        self.host = None
        self.server_port = None
        self.other_config = None
        self.other_singleton = None

    @ConfigurationInject
    def inject(
        self,
        host: str,
        server_port: int,
        other_config: OtherConfig,
        other_singleton: OtherSingleton
    ):
        self.host = host
        self.server_port = server_port
        self.other_config = other_config
        self.other_singleton = other_singleton

    @Executable
    def get_host(self) -> str:
        return self.host

    @Executable
    def get_server_port(self) -> int:
        return self.server_port

    @Executable
    def get_other_config(self) -> OtherConfig:
        return self.other_config

    @Executable
    def get_other_singleton(self) -> OtherSingleton:
        return self.other_singleton
'''

        when:
        def context = buildContext(pythonCode, false, [
                "foo.bar.host": "test",
                "foo.bar.server-port": "123",
                "xyz.name": "other"
        ])
        def configBean = getBean(context, "python.ConfigWithMethod")

        then:
        configBean.get_host() == "test"
        configBean.get_server_port() == 123
        configBean.get_other_config().name == "other"
        configBean.get_other_singleton() != null

        cleanup:
        context?.close()
    }

    void "test @ConfigurationProperties on Python class with @property decorator"() {
        given:
        def pythonCode = '''
from micronaut.context.annotation import ConfigurationProperties

@ConfigurationProperties("cache")
class CacheConfig:
    def __init__(self):
        self._ttl_seconds = 300
        self._max_size = 1000

    @property
    def ttl_seconds(self) -> int:
        return self._ttl_seconds

    @ttl_seconds.setter
    def ttl_seconds(self, value: int):
        self._ttl_seconds = value

    @property
    def max_size(self) -> int:
        return self._max_size

    @max_size.setter
    def max_size(self, value: int):
        self._max_size = value
'''

        when:
        def properties = [
            "cache.ttl-seconds": "600",
            "cache.max-size": "2000"
        ]
        def context = buildContext(pythonCode, false, properties)
        def configBean = getBean(context, "python.CacheConfig")

        then:
        configBean != null
        configBean.ttl_seconds() == 600
        configBean.max_size() == 2000

        cleanup:
        context?.close()
    }

    void "test configuration property map field uses raw key format annotation"() {
        given:
        def pythonCode = '''
from typing import Annotated
import java

from micronaut.context.annotation import ConfigurationProperties
from micronaut.core.convert.format import MapFormat

StringConvention = java.type("io.micronaut.core.naming.conventions.StringConvention")

@ConfigurationProperties("conf")
class AnnotatedField:
    animals: Annotated[dict[str, str], MapFormat(keyFormat=StringConvention.RAW)] = {}
'''

        when:
        def context = buildContext(pythonCode, false, [
                "conf.animals.VERY_FAST": "rabbit"
        ])
        def configBean = getBean(context, "python.AnnotatedField")

        then:
        configBean.animals.containsKey("VERY_FAST")
        configBean.animals.get("VERY_FAST") == "rabbit"

        cleanup:
        context?.close()
    }
}
