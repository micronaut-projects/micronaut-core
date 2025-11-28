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
import io.micronaut.context.env.Environment
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.PendingFeature

/**
 * Tests for Python @ConfigurationProperties annotation.
 *
 * @author Micronaut
 * @since 4.8.0
 */
class ConfigurationPropertiesSpec extends AbstractPythonTypeElementSpec {
    @PendingFeature(reason = "Fails with 'Non writable or non-existent member key 'cylinders' which is likely a GraalPy bug")
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
        ferrariBean.cylinders() == 8
        ferrariBean.enabled() == false
        fordBean.cylinders() == 6
        fordBean.enabled() == true
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

}
