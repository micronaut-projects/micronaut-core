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

import io.micronaut.context.ApplicationContext
import io.micronaut.context.python.PythonContextRuntime
import io.micronaut.python.annotation.processing.test.decorators.MethodOrder
import io.micronaut.python.annotation.processing.test.decorators.Orderers
import io.micronaut.python.compiler.InMemoryBeanDefinitionsProvider
import io.micronaut.python.compiler.PyronautCompiler
import org.graalvm.polyglot.Value

/**
 * Whether a decorator is applied bare ({@code @X}) or as a factory ({@code @X(...)}) follows from its
 * syntactic form alone, for generated Java annotation decorators and for custom annotation functions.
 */
class DecoratorFormSpec extends AbstractPythonTypeElementSpec {

    void "test a positional class-valued decorator argument is an annotation value"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from micronaut.python.annotation.processing.test.decorators import MethodOrder, Orderers

@Singleton
@MethodOrder(Orderers.Alphabetical)
class OrderedService:
    @Executable
    @MethodOrder(Orderers.Random)
    def positional(self) -> str:
        return "positional"

    @Executable
    @MethodOrder(value=Orderers.Random)
    def keyword(self) -> str:
        return "keyword"
''')

        when:
        def definition = getBeanDefinition(context, "python.OrderedService")
        Value bean = getBean(context, "python.OrderedService").asPolyglotValue()

        then:
        definition.classValue(MethodOrder).get() == Orderers.Alphabetical
        definition.findMethod("positional").get().classValue(MethodOrder).get() == Orderers.Random
        definition.findMethod("keyword").get().classValue(MethodOrder).get() == Orderers.Random
        bean.invokeMember("positional").asString() == "positional"
        bean.invokeMember("keyword").asString() == "keyword"

        cleanup:
        context?.close()
    }

    void "test a custom annotation function is applied bare and with parentheses"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from micronaut.core.bind.annotation import Bindable

@Bindable
def Marker(value: str = "default"):
    def decorator(target):
        return target
    return decorator

@Singleton
@Marker
class BareMarked:
    @Executable
    @Marker
    def bare(self) -> str:
        return "bare"

    @Executable
    @Marker()
    def empty(self) -> str:
        return "empty"

    @Executable
    @Marker("explicit")
    def explicit(self) -> str:
        return "explicit"

@Singleton
@Marker("explicit")
class ExplicitMarked:
    pass
''')

        when:
        def bare = getBeanDefinition(context, "python.BareMarked")
        def explicit = getBeanDefinition(context, "python.ExplicitMarked")
        Value bean = getBean(context, "python.BareMarked").asPolyglotValue()

        then: "a bare decorator is the same as one called without arguments"
        bare.hasAnnotation("python.Marker")
        !bare.stringValue("python.Marker").isPresent()
        bare.getAnnotation("python.Marker").getDefaultValues() == [value: "default"]
        bare.findMethod("bare").get().hasAnnotation("python.Marker")
        !bare.findMethod("bare").get().stringValue("python.Marker").isPresent()
        bare.findMethod("empty").get().hasAnnotation("python.Marker")
        !bare.findMethod("empty").get().stringValue("python.Marker").isPresent()
        bare.findMethod("explicit").get().stringValue("python.Marker").get() == "explicit"
        explicit.stringValue("python.Marker").get() == "explicit"
        bean.invokeMember("bare").asString() == "bare"
        bean.invokeMember("empty").asString() == "empty"
        bean.invokeMember("explicit").asString() == "explicit"

        cleanup:
        context?.close()
    }

    void "test an imported custom annotation function is applied bare and with parentheses"() {
        given:
        def tempDir = File.createTempDir("python-decorator-form", "")
        def annotationsDir = new File(tempDir, "annotations")
        def beansDir = new File(tempDir, "beans")
        annotationsDir.mkdirs()
        beansDir.mkdirs()

        new File(annotationsDir, "AdultMales.py").text = '''\
from micronaut.core.bind.annotation import Bindable

@Bindable
def AdultMales():
    def decorator(target):
        return target
    return decorator
'''
        new File(annotationsDir, "Marker.py").text = '''\
from micronaut.core.bind.annotation import Bindable

@Bindable
def Marker(value: str = "default"):
    def decorator(target):
        return target
    return decorator
'''
        new File(annotationsDir, "Views.py").text = '''\
from jakarta.inject import Singleton
from .AdultMales import AdultMales

@AdultMales
@Singleton
class AdultMalesView:
    pass
'''
        new File(beansDir, "Filters.py").text = '''\
from typing import Annotated
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from annotations.AdultMales import AdultMales
from annotations.Marker import Marker

@AdultMales
@Singleton
class AdultMalesFilter:
    @Executable
    @Marker
    def bare(self, value: Annotated[str, AdultMales]) -> str:
        return value

    @Executable
    @Marker("explicit")
    def explicit(self) -> str:
        return "explicit"

@AdultMales()
@Marker
@Singleton
class CalledAdultMalesFilter:
    pass
'''

        PythonContextRuntime.resetContext()
        def classLoader = PyronautCompiler.builder()
            .pythonSrc(tempDir.absolutePath)
            .build()
            .buildClassLoader()
        ApplicationContext context = ApplicationContext.builder()
            .classLoader(classLoader)
            .environments("test")
            .beanDefinitionsProvider(new InMemoryBeanDefinitionsProvider(false))
            .build()
            .start()

        when:
        def bare = getBeanDefinition(context, "beans.AdultMalesFilter")
        def called = getBeanDefinition(context, "beans.CalledAdultMalesFilter")
        def sibling = getBeanDefinition(context, "annotations.AdultMalesView")
        Value bean = getBean(context, "beans.AdultMalesFilter").asPolyglotValue()

        then: "a bare decorator is the same as one called without arguments"
        bare.hasAnnotation("annotations.AdultMales")
        bare.findMethod("bare", String).get().hasAnnotation("annotations.Marker")
        !bare.findMethod("bare", String).get().stringValue("annotations.Marker").isPresent()
        bare.findMethod("bare", String).get().arguments[0].annotationMetadata.hasAnnotation("annotations.AdultMales")
        bare.findMethod("explicit").get().stringValue("annotations.Marker").get() == "explicit"
        called.hasAnnotation("annotations.AdultMales")
        called.hasAnnotation("annotations.Marker")
        !called.stringValue("annotations.Marker").isPresent()
        sibling.hasAnnotation("annotations.AdultMales")
        getBean(context, "annotations.AdultMalesView") != null
        bean.invokeMember("bare", "value").asString() == "value"
        bean.invokeMember("explicit").asString() == "explicit"

        cleanup:
        context?.close()
        tempDir?.deleteDir()
        PythonContextRuntime.resetContext()
    }
}
