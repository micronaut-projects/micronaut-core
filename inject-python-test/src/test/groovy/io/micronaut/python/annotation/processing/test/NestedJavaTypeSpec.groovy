/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.python.annotation.processing.test.nested.OuterHost
import io.micronaut.python.annotation.processing.test.nested.OuterMarker
import io.micronaut.python.compiler.PyronautCompiler
import io.micronaut.python.processing.PythonAnnotationProcessor

/**
 * Nested Java types ({@code Outer$Inner}) imported and used from Python: nested annotation types as
 * decorators and as {@code Annotated[...]} metadata, and nested classes, enums and interfaces imported
 * either as {@code from a.b import Outer} + {@code Outer.Inner} or as {@code from a.b.Outer import Inner}.
 */
class NestedJavaTypeSpec extends AbstractPythonTypeElementSpec {

    private static final String NESTED = 'micronaut.python.annotation.processing.test.nested'

    void "nested annotation imported from its outer type decorates a class"() {
        given:
        def context = buildContext("""
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from ${NESTED}.OuterMarker import Tagged, Plain, Level
from ${NESTED} import OuterMarker

@Singleton
@Plain
@Tagged("command", priority=3)
class Command:
    @Executable
    def levels(self) -> str:
        return Level.HIGH.name() + ":" + OuterMarker.Level.LOW.name()
""")

        when:
        def definition = getBeanDefinition(context, 'python.Command')

        then:
        definition.hasAnnotation(OuterMarker.Tagged)
        definition.stringValue(OuterMarker.Tagged).get() == 'command'
        definition.intValue(OuterMarker.Tagged, 'priority').getAsInt() == 3
        definition.hasAnnotation(OuterMarker.Plain)
        getBean(context, 'python.Command').levels() == 'HIGH:LOW'

        cleanup:
        context?.close()
    }

    void "nested annotation accessed as an attribute of its outer type decorates methods and fields"() {
        given:
        def context = buildContext("""
from typing import Annotated
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from micronaut.core.annotation import Introspected
from ${NESTED} import OuterMarker
from ${NESTED}.OuterMarker import Plain

@Singleton
@Introspected
@OuterMarker("outer")
class Service:
    label: Annotated[str, OuterMarker.Tagged("field", priority=1)] = "label"

    @Executable
    @OuterMarker.Tagged("method", priority=2)
    def run(self, argument: Annotated[str, Plain, OuterMarker.Tagged("parameter")]) -> str:
        return argument
""")

        when:
        def definition = getBeanDefinition(context, 'python.Service')
        def method = definition.getRequiredMethod('run', String)
        def label = getBeanIntrospection(context, 'python.Service').getRequiredProperty('label', String)

        then:
        definition.stringValue(OuterMarker).get() == 'outer'
        label.stringValue(OuterMarker.Tagged).get() == 'field'
        label.intValue(OuterMarker.Tagged, 'priority').getAsInt() == 1
        method.stringValue(OuterMarker.Tagged).get() == 'method'
        method.intValue(OuterMarker.Tagged, 'priority').getAsInt() == 2
        method.arguments[0].annotationMetadata.hasAnnotation(OuterMarker.Plain)
        method.arguments[0].annotationMetadata.stringValue(OuterMarker.Tagged).get() == 'parameter'
        getBean(context, 'python.Service').run('ok') == 'ok'

        cleanup:
        context?.close()
    }

    void "nested annotation imported under TYPE_CHECKING applies at compile time"() {
        given:
        def context = buildContext("""
from __future__ import annotations
from typing import TYPE_CHECKING, Annotated
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable

if TYPE_CHECKING:
    from ${NESTED}.OuterMarker import Plain, Tagged

@Singleton
@Tagged("guarded")
class Guarded:
    @Executable
    def run(self, argument: Annotated[str, Plain]) -> str:
        return argument
""")

        when:
        def definition = getBeanDefinition(context, 'python.Guarded')
        def method = definition.getRequiredMethod('run', String)

        then:
        definition.stringValue(OuterMarker.Tagged).get() == 'guarded'
        method.arguments[0].annotationMetadata.hasAnnotation(OuterMarker.Plain)
        getBean(context, 'python.Guarded').run('ok') == 'ok'

        cleanup:
        context?.close()
    }

    void "nested classes are importable both from the package and from the outer type"() {
        given:
        def context = buildContext("""
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from ${NESTED}.OuterHost import Inner, Mode, Builder
from ${NESTED}.OuterHost import OuterHost as SelfImported
from ${NESTED} import OuterHost

@Singleton
class NestedUser:
    @Executable
    def self_imported(self) -> str:
        return SelfImported.describe() if SelfImported == OuterHost else "different"

    @Executable
    def from_outer(self) -> str:
        return OuterHost.Inner("attribute").getName() + ":" + OuterHost.Mode.SLOW.name()

    @Executable
    def from_import(self) -> str:
        return Inner("imported").getName() + ":" + Mode.FAST.name()

    @Executable
    def same_types(self) -> bool:
        return Inner == OuterHost.Inner and Mode == OuterHost.Mode and Builder == OuterHost.Builder

    @Executable
    def outer_still_callable(self) -> str:
        return OuterHost.describe()

    @Executable
    def built(self) -> str:
        return Builder.create().name("built").build().getName() + ":" + Builder.Stage.FINAL.name()
""")

        when:
        def bean = getBean(context, 'python.NestedUser')

        then:
        bean.self_imported() == 'outer'
        bean.from_outer() == 'attribute:SLOW'
        bean.from_import() == 'imported:FAST'
        bean.same_types()
        bean.outer_still_callable() == 'outer'
        bean.built() == 'built:FINAL'

        cleanup:
        context?.close()
    }

    void "nested enum and interface types are usable in signatures"() {
        given:
        def context = buildContext("""
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from ${NESTED}.OuterHost import Mode, Contract

@Singleton
class ModeService(Contract):
    def describe(self) -> str:
        return "contract"

    @Executable
    def flip(self, mode: Mode) -> Mode:
        return Mode.SLOW if mode == Mode.FAST else Mode.FAST
""")

        when:
        def definition = getBeanDefinition(context, 'python.ModeService')
        def bean = getBean(context, 'python.ModeService')

        then:
        OuterHost.Contract.isAssignableFrom(bean.getClass())
        definition.getRequiredMethod('flip', OuterHost.Mode).returnType.type == OuterHost.Mode
        bean.flip(OuterHost.Mode.FAST) == OuterHost.Mode.SLOW
        bean.describe() == 'contract'

        cleanup:
        context?.close()
    }

    void "the module of a Java type exports the type and its imported nested types"() {
        given:
        def tempDir = File.createTempDir("python-nested-types", "")
        def srcPath = new File(tempDir, "META-INF/" + PythonAnnotationProcessor.APPLICATION_SRC_PATH)

        when:
        PyronautCompiler.builder()
            .pythonCode("""
from jakarta.inject import Singleton
from ${NESTED}.OuterHost import Inner
from ${NESTED}.OuterHost.Builder import Stage
from ${NESTED}.OuterMarker import Tagged
from ${NESTED} import OuterMarker

@Singleton
@Tagged("command")
@OuterMarker.Plain
class Command:
    def stage(self) -> str:
        return Inner("x").getName() + Stage.FINAL.name()
""")
            .targetDir(tempDir)
            .build()
            .compile()
        def packageInit = new File(srcPath, "micronaut/python/annotation/processing/test/nested/__init__.py").text
        def hostInit = new File(srcPath, "micronaut/python/annotation/processing/test/nested/OuterHost/__init__.py").text
        def builderInit = new File(srcPath, "micronaut/python/annotation/processing/test/nested/OuterHost/Builder/__init__.py").text
        def markerInit = new File(srcPath, "micronaut/python/annotation/processing/test/nested/OuterMarker/__init__.py").text
        def taggedModule = new File(srcPath, "micronaut/python/annotation/processing/test/nested/OuterMarker/Tagged.py").text

        then: "the package imports each type from the type's own module"
        packageInit.contains("from .OuterHost import OuterHost")
        packageInit.contains("from .OuterMarker import OuterMarker")
        !packageInit.contains("from . import OuterHost")
        !new File(srcPath, "micronaut/python/annotation/processing/test/nested/OuterMarker.py").exists()

        and: "a class module binds the class and the nested types imported from it"
        hostInit.contains("OuterHost = java.type('io.micronaut.python.annotation.processing.test.nested.OuterHost')")
        hostInit.contains("Inner = java.type('io.micronaut.python.annotation.processing.test.nested.OuterHost\$Inner')")
        hostInit.contains("from .Builder import Builder")
        builderInit.contains("Builder = java.type('io.micronaut.python.annotation.processing.test.nested.OuterHost\$Builder')")
        builderInit.contains("Stage = java.type('io.micronaut.python.annotation.processing.test.nested.OuterHost\$Builder\$Stage')")

        and: "an annotation module holds its decorator and exports the nested annotations"
        markerInit.contains('@micronaut_annotation("io.micronaut.python.annotation.processing.test.nested.OuterMarker"')
        markerInit.contains("def OuterMarker(")
        markerInit.contains("Plain = _OuterMarker_Plain")
        markerInit.contains("OuterMarker.Plain = _OuterMarker_Plain")
        markerInit.contains("from .Tagged import Tagged")
        taggedModule.contains('@micronaut_annotation("io.micronaut.python.annotation.processing.test.nested.OuterMarker$Tagged")')
        taggedModule.contains("def Tagged(")

        cleanup:
        tempDir.deleteDir()
    }

    void "nested JDK types are importable from the outer type"() {
        given:
        def context = buildContext("""
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from java.util import Map
from java.util.Map import Entry

@Singleton
class EntryService:
    @Executable
    def entry(self) -> Entry:
        return Map.entry("key", "value")

    @Executable
    def same_type(self) -> bool:
        return Entry == Map.Entry
""")

        when:
        def definition = getBeanDefinition(context, 'python.EntryService')
        def bean = getBean(context, 'python.EntryService')

        then:
        definition.getRequiredMethod('entry').returnType.type == Map.Entry
        bean.entry().key == 'key'
        bean.same_type()

        cleanup:
        context?.close()
    }

    void "importing a nested type that does not exist fails compilation"() {
        when:
        PyronautCompiler.builder()
            .pythonCode("""
from jakarta.inject import Singleton
from ${NESTED}.OuterHost import Missing

@Singleton
class Broken:
    pass
""")
            .build()
            .buildClassLoader()

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Missing')
        e.message.contains('io.micronaut.python.annotation.processing.test.nested.OuterHost')
    }
}
