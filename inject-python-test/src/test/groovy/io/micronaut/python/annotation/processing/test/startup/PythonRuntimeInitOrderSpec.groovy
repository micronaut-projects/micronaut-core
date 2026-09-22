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
package io.micronaut.python.annotation.processing.test.startup

import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.context.BeanContext
import io.micronaut.context.RuntimeBeanDefinition
import io.micronaut.context.processor.ExecutableMethodProcessor
import io.micronaut.context.python.PythonContextRuntime
import io.micronaut.core.type.Argument
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec
import org.graalvm.polyglot.Context

import java.util.function.Function

/**
 * Python beans that are instantiated before the {@code @Context} GraalPy context bean is
 * initialized (type converters, beans of {@code processOnStartup} executable methods) must
 * initialize the runtime themselves instead of failing with "GraalPy context has not been
 * initialized".
 */
class PythonRuntimeInitOrderSpec extends AbstractPythonTypeElementSpec {

    void "test python TypeConverter bean is created before the GraalPy context bean"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from micronaut.core.convert import ConversionContext, TypeConverter
from java.util import Optional
from io.micronaut.python.annotation.processing.test.startup import Temperature

@Singleton
class TemperatureConverter(TypeConverter[str, Temperature]):
    def convert(self, source: str, target_type: type[Temperature], context: ConversionContext) -> Optional:
        return Optional.of(Temperature(float(source)))
''', true)

        expect:
        PythonContextRuntime.isInitialized()
        context.getConversionService().convert("21.5", Temperature).get().celsius() == 21.5d
        getBean(context, "python.TemperatureConverter").asPolyglotValue().getContext() == context.getBean(Context, Qualifiers.byName("python"))

        cleanup:
        context?.close()
    }

    void "test python bean of a processOnStartup executable method is created before the GraalPy context bean"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from io.micronaut.python.annotation.processing.test.startup import StartupProcessed

@Singleton
class StartupListener:
    @StartupProcessed
    def ping(self) -> str:
        return "pong"
''', true)

        expect:
        PythonContextRuntime.isInitialized()
        context.getBean(StartupMethodProcessor).results == [ping: "pong"]
        getBean(context, "python.StartupListener").asPolyglotValue().getContext() == context.getBean(Context, Qualifiers.byName("python"))

        cleanup:
        context?.close()
    }

    void "test the GraalPy context bean is created once when the runtime is initialized early"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from micronaut.core.convert import ConversionContext, TypeConverter
from java.util import Optional
from io.micronaut.python.annotation.processing.test.startup import StartupProcessed, Temperature

@Singleton
class TemperatureConverter(TypeConverter[str, Temperature]):
    def convert(self, source: str, target_type: type[Temperature], context: ConversionContext) -> Optional:
        return Optional.of(Temperature(float(source)))

@Singleton
class StartupListener:
    @StartupProcessed
    def ping(self) -> str:
        return "pong"
''', true)

        when:
        def graalPyContext = context.getBean(Context, Qualifiers.byName("python"))

        then:
        context.getBeansOfType(Context).size() == 1
        graalPyContext == PythonContextRuntime.getContext()
        context.getConversionService().convert("-3", Temperature).get().celsius() == -3d
        context.getBean(StartupMethodProcessor).results == [ping: "pong"]

        when:
        context.close()

        then:
        !PythonContextRuntime.isInitialized()

        when: "the stopped application context no longer provides its runtime"
        def bootstrapped = PythonContextRuntime.getContext()

        then: "a default context is bootstrapped instead, the one a platform entry point outside an application gets"
        bootstrapped != graalPyContext
        PythonContextRuntime.isInitialized()

        cleanup:
        PythonContextRuntime.resetContext()
        bootstrapped?.close()
    }

    void "test the bootstrap context of the application is never used to initialize the runtime"() {
        given: "an application with a bootstrap context and a Python bean created before the eager beans"
        bootstrapEnvironment = true
        def context = buildContext('''
from jakarta.inject import Singleton
from micronaut.core.convert import ConversionContext, TypeConverter
from java.util import Optional
from io.micronaut.python.annotation.processing.test.startup import Temperature

@Singleton
class TemperatureConverter(TypeConverter[str, Temperature]):
    def convert(self, source: str, target_type: type[Temperature], context: ConversionContext) -> Optional:
        return Optional.of(Temperature(float(source)))
''', true)

        expect: "the early bean initialized the runtime from the application context, not the bootstrap context"
        PythonContextRuntime.isInitialized()
        context.getConversionService().convert("21.5", Temperature).get().celsius() == 21.5d

        when: "a refresh recreates the bootstrap context while the application is running"
        def applicationGraalPyContext = context.getBean(Context, Qualifiers.byName("python"))
        context.environment.refresh()
        context.close()
        def bootstrapped = PythonContextRuntime.getContext()

        then: "the stopped application context cleared the record, which the fresh bootstrap context did not replace: a default context is bootstrapped, not one of the bootstrap context"
        bootstrapped != applicationGraalPyContext
        PythonContextRuntime.isInitialized()

        cleanup:
        PythonContextRuntime.resetContext()
        bootstrapped?.close()
        bootstrapEnvironment = false
    }

    private boolean bootstrapEnvironment

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        contextBuilder.bootstrapEnvironment(bootstrapEnvironment)
        // the processor is a Java bean; the bean context it processes is the one being started
        contextBuilder.beanDefinitions(
            RuntimeBeanDefinition.builder(StartupMethodProcessor, { RuntimeBeanDefinition.CreationContext creation ->
                new StartupMethodProcessor(creation.getBean(Argument.of(BeanContext)))
            } as Function)
                .singleton(true)
                .exposedTypes(ExecutableMethodProcessor, StartupMethodProcessor)
                .typeArguments(ExecutableMethodProcessor, Argument.of(StartupProcessed))
                .build()
        )
    }
}
