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

class ConfigurationBuilderSpec extends AbstractPythonTypeElementSpec {

    void "test configuration builder writes to Java builder attribute"() {
        given:
        def pythonCode = '''
from typing import Annotated
import java
from micronaut.context.annotation import ConfigurationBuilder, ConfigurationProperties

Engine = java.type("io.micronaut.python.annotation.processing.test.ConfigBuilderEngine")
EngineBuilder = java.type("io.micronaut.python.annotation.processing.test.ConfigBuilderEngine$Builder")

@ConfigurationProperties("test.props")
class TestProps:
    builder: Annotated[EngineBuilder, ConfigurationBuilder(prefixes=["with"])] = Engine.builder()
'''

        when:
        def context = buildContext(pythonCode, false, [
                "test.props.manufacturer": "Toyota"
        ])
        def testProps = getBean(context, "python.TestProps")

        then:
        testProps.builder.build().manufacturer == "Toyota"

        cleanup:
        context?.close()
    }

    void "test multiple configuration builders use configured prefixes"() {
        given:
        def pythonCode = '''
from typing import Annotated
import java
from micronaut.context.annotation import ConfigurationBuilder, ConfigurationProperties

Engine = java.type("io.micronaut.python.annotation.processing.test.ConfigBuilderEngine")
EngineBuilder = java.type("io.micronaut.python.annotation.processing.test.ConfigBuilderEngine$Builder")

@ConfigurationProperties("engines")
class EngineConfig:
    primary: Annotated[EngineBuilder, ConfigurationBuilder(value="primary", prefixes=["with"])] = Engine.builder()
    backup: Annotated[EngineBuilder, ConfigurationBuilder(value="backup", prefixes=["with"])] = Engine.builder()
'''

        when:
        def context = buildContext(pythonCode, false, [
                "engines.primary.manufacturer": "Toyota",
                "engines.backup.manufacturer": "Honda"
        ])
        def engineConfig = getBean(context, "python.EngineConfig")

        then:
        engineConfig.primary.build().manufacturer == "Toyota"
        engineConfig.backup.build().manufacturer == "Honda"

        cleanup:
        context?.close()
    }

    void "test configuration builder with includes"() {
        given:
        def pythonCode = '''
from typing import Annotated
import java
from micronaut.context.annotation import ConfigurationBuilder, ConfigurationProperties

Engine = java.type("io.micronaut.python.annotation.processing.test.ConfigBuilderEngine")
EngineBuilder = java.type("io.micronaut.python.annotation.processing.test.ConfigBuilderEngine$Builder")

@ConfigurationProperties("test.props")
class TestProps:
    builder: Annotated[EngineBuilder, ConfigurationBuilder(prefixes=["with"], includes=["manufacturer"])] = Engine.builder()
'''

        when:
        def context = buildContext(pythonCode, false, [
                "test.props.manufacturer": "Toyota",
                "test.props.model": "Supra"
        ])
        def engine = getBean(context, "python.TestProps").builder.build()

        then:
        engine.manufacturer == "Toyota"
        engine.model == null

        cleanup:
        context?.close()
    }

    void "test configuration builder with configuration prefix"() {
        given:
        def pythonCode = '''
from typing import Annotated
import java
from micronaut.context.annotation import ConfigurationBuilder, ConfigurationProperties

Engine = java.type("io.micronaut.python.annotation.processing.test.ConfigBuilderEngine")
EngineBuilder = java.type("io.micronaut.python.annotation.processing.test.ConfigBuilderEngine$Builder")

@ConfigurationProperties("test.props")
class TestProps:
    builder: Annotated[EngineBuilder, ConfigurationBuilder(prefixes=["with"], configurationPrefix="engine")] = Engine.builder()
'''

        when:
        def context = buildContext(pythonCode, false, [
                "test.props.engine.manufacturer": "Honda",
                "test.props.engine.model": "NSX",
                "test.props.manufacturer": "ignored"
        ])
        def engine = getBean(context, "python.TestProps").builder.build()

        then:
        engine.manufacturer == "Honda"
        engine.model == "NSX"

        cleanup:
        context?.close()
    }

    void "test configuration builder uses inherited builder methods"() {
        given:
        def pythonCode = '''
from typing import Annotated
import java
from micronaut.context.annotation import ConfigurationBuilder, ConfigurationProperties

HierarchyBuilder = java.type("io.micronaut.python.annotation.processing.test.ConfigBuilderHierarchy$RealizedBuilder")

@ConfigurationProperties("hierarchy")
class HierarchyProps:
    builder: Annotated[HierarchyBuilder, ConfigurationBuilder(prefixes=["with"])] = HierarchyBuilder()
'''

        when:
        def context = buildContext(pythonCode, false, [
                "hierarchy.name": "Tim Yates"
        ])
        def hierarchyProps = getBean(context, "python.HierarchyProps")

        then:
        hierarchyProps.builder.build().name == "Tim Yates"

        cleanup:
        context?.close()
    }
}
