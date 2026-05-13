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

class ReplacesSpec extends AbstractPythonTypeElementSpec {

    void "test bean can replace another bean"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable, Replaces

class Engine:
    @Executable
    def start(self) -> str:
        return "base"

@Singleton
class V6Engine(Engine):
    @Executable
    def start(self) -> str:
        return "v6"

@Singleton
@Replaces(V6Engine)
class V8Engine(Engine):
    @Executable
    def start(self) -> str:
        return "v8"
''')

        when:
        def engineType = context.classLoader.loadClass("python.Engine")
        def engines = context.getBeansOfType(engineType)
        def engine = engines.first().asPolyglotValue()

        then:
        engines.size() == 1
        engine.invokeMember("start").asString() == "v8"

        cleanup:
        context?.close()
    }

    void "test named bean can replace another named bean"() {
        given:
        def context = buildContext('''
from jakarta.inject import Named, Singleton
from micronaut.context.annotation import Executable, Replaces

class Engine:
    @Executable
    def start(self) -> str:
        return "base"

@Singleton
@Named("primary")
class PrimaryEngine(Engine):
    @Executable
    def start(self) -> str:
        return "primary"

@Singleton
@Named("secondary")
class SecondaryEngine(Engine):
    @Executable
    def start(self) -> str:
        return "secondary"

@Singleton
@Named("primary")
@Replaces(value=PrimaryEngine, named="primary")
class ReplacementPrimaryEngine(Engine):
    @Executable
    def start(self) -> str:
        return "replacement-primary"
''')

        when:
        def engineType = context.classLoader.loadClass("python.Engine")
        def engines = context.getBeansOfType(engineType)
        def names = engines.collect { it.asPolyglotValue().invokeMember("start").asString() } as Set

        then:
        engines.size() == 2
        names == ["replacement-primary", "secondary"] as Set

        cleanup:
        context?.close()
    }

    void "test factory can replace another factory"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from micronaut.context.annotation import Factory, Replaces

class Engine:
    def name(self) -> str:
        return "base"

class V6Engine(Engine):
    def name(self) -> str:
        return "v6"

class V8Engine(Engine):
    def name(self) -> str:
        return "v8"

@Factory
class EngineFactory:
    @Singleton
    def engine(self) -> Engine:
        return V6Engine()

@Factory
@Replaces(factory=EngineFactory)
class ReplacementEngineFactory:
    @Singleton
    def engine(self) -> Engine:
        return V8Engine()
''')

        when:
        def engineType = context.classLoader.loadClass("python.Engine")
        def engines = context.getBeansOfType(engineType)
        def engine = engines.first().asPolyglotValue()

        then:
        engines.size() == 1
        engine.invokeMember("name").asString() == "v8"

        cleanup:
        context?.close()
    }

    void "test factory method can replace another factory method"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from micronaut.context.annotation import Factory, Replaces

class Engine:
    def name(self) -> str:
        return "base"

class V6Engine(Engine):
    def name(self) -> str:
        return "v6"

class V8Engine(Engine):
    def name(self) -> str:
        return "v8"

@Factory
class EngineFactory:
    @Singleton
    def engine(self) -> Engine:
        return V6Engine()

@Factory
class ReplacementEngineFactory:
    @Singleton
    @Replaces(value=Engine, factory=EngineFactory)
    def engine(self) -> Engine:
        return V8Engine()
''')

        when:
        def engineType = context.classLoader.loadClass("python.Engine")
        def engines = context.getBeansOfType(engineType)
        def engine = engines.first().asPolyglotValue()

        then:
        engines.size() == 1
        engine.invokeMember("name").asString() == "v8"

        cleanup:
        context?.close()
    }
}
