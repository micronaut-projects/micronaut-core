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
package io.micronaut.python.compiler

import io.micronaut.context.ApplicationContext
import io.micronaut.context.python.PythonContextRuntime
import io.micronaut.context.python.runtime.PythonMetadataCatalog
import io.micronaut.context.python.runtime.PythonRuntimeMetadata
import io.micronaut.core.beans.BeanIntrospector
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The runtime backend end to end: a compilation saves models and a catalog instead of definitions and
 * introspections, and an ordinary application discovers, generates and uses them.
 */
class PythonRuntimeMetadataSpec extends Specification {
    @Shared File source
    @Shared File output

    def setupSpec() {
        source = File.createTempDir('runtime-metadata-source', '')
        output = File.createTempDir('runtime-metadata-output', '')
        def file = new File(source, 'app/models.py')
        file.parentFile.mkdirs()
        file.text = '''
from typing import Annotated
from jakarta.inject import Singleton, Inject
from jakarta.annotation import PostConstruct
from micronaut.core.annotation import Introspected
from micronaut.context.annotation import Value

@Singleton
@Introspected
class RuntimeBean:
    def __init__(self):
        pass

    def greeting(self) -> str:
        return "hello from Python"

@Singleton
class RuntimeService:
    def __init__(self, bean: RuntimeBean, name: Annotated[str, Value("${runtime.name:default}")]):
        self.bean = bean
        self.name = name
        self.initialized = False
        self.other = None

    @Inject
    def set_other(self, other: RuntimeBean):
        self.other = other

    @PostConstruct
    def initialize(self):
        self.initialized = True

    def describe(self) -> str:
        return self.bean.greeting() + " for " + self.name + (" (initialized)" if self.initialized else "") + (" (injected)" if self.other is not None else "")

@Introspected
class RuntimePerson:
    name: str
    age: int
    active: bool

    def __init__(self):
        self.name = "before"
        self.age = 1
        self.active = True
'''
        PyronautCompiler.builder().pythonSrc(source.absolutePath).targetDir(output)
            .options(['-Amicronaut.python.metadata.backend=model-runtime'])
            .build().compile()
    }

    def cleanupSpec() {
        source?.deleteDir()
        output?.deleteDir()
    }

    def cleanup() {
        PythonContextRuntime.resetContext()
    }

    def "compilation emits models, a catalog and wrappers but no definitions or introspections"() {
        expect:
        new File(output, 'app/RuntimeBean.class').exists()
        new File(output, 'app/RuntimeService.class').exists()
        new File(output, 'app/RuntimePerson.class').exists()
        new File(output, 'META-INF/micronaut/python/runtime/app.RuntimeBean.mpym').exists()
        new File(output, 'META-INF/micronaut/python/runtime/app.RuntimeService.mpym').exists()
        new File(output, 'META-INF/micronaut/python/runtime/app.RuntimePerson.mpym').exists()
        def catalog = new File(output, 'META-INF/micronaut/python/runtime/catalog').readLines()
        catalog.first() == PythonMetadataCatalog.HEADER
        catalog.drop(1).collect { it.split('\t')[0] }.sort() == ['app.RuntimeBean', 'app.RuntimePerson', 'app.RuntimeService']
        catalog.drop(1).collect { it.split('\t')[2] }.sort() == ['B', 'BI', 'I']
        !new File(output, 'app/$RuntimeBean$Definition.class').exists()
        !new File(output, 'app/$RuntimeBean$Introspection.class').exists()
        !new File(output, 'app/$RuntimeService$Definition.class').exists()
        !new File(output, 'app/$RuntimePerson$Introspection.class').exists()
        !new File(output, 'META-INF/micronaut/io.micronaut.inject.BeanDefinitionReference/app.$RuntimeBean$Definition').exists()
        !new File(output, 'META-INF/micronaut/io.micronaut.core.beans.BeanIntrospectionReference/app.$RuntimePerson$Introspection').exists()
    }

    def "an ordinary application discovers, generates and uses the runtime metadata"() {
        given:
        def loader = newLoader()
        def beanType = loader.loadClass('app.RuntimeBean')
        def serviceType = loader.loadClass('app.RuntimeService')
        def personType = loader.loadClass('app.RuntimePerson')

        expect:
        !PythonRuntimeMetadata.isDefinitionGenerated(beanType)
        !PythonRuntimeMetadata.isIntrospectionGenerated(personType)
        PythonMetadataCatalog.of(loader).entries().size() == 3
        !PythonRuntimeMetadata.isDefinitionGenerated(beanType)

        when: 'no provider is wired by hand: the service-loaded configurer composes the runtime references'
        def context = ApplicationContext.builder().classLoader(loader).properties(['runtime.name': 'tests']).start()
        def bean = context.getBean(beanType)
        def service = context.getBean(serviceType)
        def introspector = BeanIntrospector.forClassLoader(loader)
        def introspection = introspector.getIntrospection(personType)
        def person = introspection.instantiate()
        introspection.getRequiredProperty('name', String).set(person, 'runtime')
        introspection.getRequiredProperty('age', Integer.TYPE).set(person, 42)
        introspection.getRequiredProperty('active', Boolean.TYPE).set(person, false)

        then:
        context.getBean(beanType).is(bean)
        context.getBeanDefinition(beanType).class.name == 'app.$RuntimeBean$Definition'
        context.getBeanDefinition(beanType).annotationMetadata.hasAnnotation('io.micronaut.core.annotation.Introspected')
        context.getBeanDefinition(beanType).annotationMetadata.hasDeclaredAnnotation('jakarta.inject.Singleton')
        context.getBeanDefinition(beanType).singleton
        context.getBeanDefinition(serviceType).constructor.arguments*.name == ['bean', 'name']
        introspection.class.name == 'app.$RuntimePerson$Introspection'
        introspection.class.classLoader.is(loader)
        introspection.getRequiredProperty('name', String).get(person) == 'runtime'
        introspection.getRequiredProperty('age', Integer.TYPE).get(person) == 42
        !introspection.getRequiredProperty('active', Boolean.TYPE).get(person)
        introspector.getIntrospection(personType).is(introspection)
        introspector.findIntrospectedTypes { it.name.startsWith('app.') }.sort { it.name }*.name == ['app.RuntimeBean', 'app.RuntimePerson']
        PythonRuntimeMetadata.isDefinitionGenerated(beanType)
        PythonRuntimeMetadata.isIntrospectionGenerated(personType)
        !PythonRuntimeMetadata.isIntrospectionGenerated(beanType)

        and: 'the runtime-generated definitions create usable Python-backed beans with injection, values and lifecycle'
        bean.greeting() == 'hello from Python'
        service.describe() == 'hello from Python for tests (initialized) (injected)'

        cleanup:
        context?.close()
        loader?.close()
    }

    def "concurrent lookup generates once and identical names in separate loaders stay isolated"() {
        given:
        def first = newLoader()
        def second = newLoader()
        def firstType = first.loadClass('app.RuntimePerson')
        def secondType = second.loadClass('app.RuntimePerson')
        def pool = Executors.newFixedThreadPool(8)
        def before = PythonRuntimeMetadata.generatedClassCount()

        when:
        def results = pool.invokeAll((1..16).collect {
            { BeanIntrospector.forClassLoader(first).getIntrospection(firstType) } as Callable
        }).collect { it.get() }
        def other = BeanIntrospector.forClassLoader(second).getIntrospection(secondType)

        then:
        results.every { it.is(results.first()) }
        other.class != results.first().class
        other.beanType.is(secondType)
        other.class.classLoader.is(second)
        PythonRuntimeMetadata.generatedClassCount() - before == 2

        cleanup:
        pool?.shutdownNow()
        first?.close()
        second?.close()
    }

    def "a fresh JVM generates from disk with no compiler, AST API or source generator on its classpath"() {
        given:
        def command = [new File(System.getProperty('java.home'), 'bin/java').absolutePath,
            '--limit-modules=java.se,jdk.unsupported,jdk.management,jdk.zipfs',
            '-Dpolyglot.engine.WarnInterpreterOnly=false', '-cp', System.getProperty('pythonApplicationClasspath'),
            'io.micronaut.context.python.runtime.PythonRuntimeMetadataRunner', output.absolutePath,
            'bean=app.RuntimeBean', 'method=greeting', 'introspection=app.RuntimePerson', 'property=name',
            'absent=app.$RuntimeBean$Definition,app.$RuntimePerson$Introspection,app.$RuntimeService$Definition']
        def log = File.createTempFile('runtime-metadata-process', '.log')

        when:
        def process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log).start()
        def completed = process.waitFor(120, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
        }
        def report = log.text

        then:
        completed
        assert process.exitValue() == 0: report
        report.contains('COMPILER_ABSENT=true')
        report.contains('JAVAC_ABSENT=true')
        report.contains('AST_API_ABSENT=true')
        report.contains('SOURCEGEN_ABSENT=true')
        report.contains('DEFINITION=app.$RuntimeBean$Definition')
        report.contains('METHOD_RESULT=hello from Python')
        report.contains('INTROSPECTION=app.$RuntimePerson$Introspection')
        report.contains('PROPERTY=generated at runtime')
        report.contains('ENUMERATED=true')
        report.contains('GENERATED_CLASSES=3')

        cleanup:
        log?.delete()
    }

    def "an unsupported construct fails compilation with a diagnostic naming it"() {
        given:
        def target = File.createTempDir('runtime-metadata-invalid', '')

        when:
        PyronautCompiler.builder().pythonCode('''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable

@Singleton
class Unsupported:
    def __init__(self):
        pass

    @Executable
    def run(self):
        pass
''').targetDir(target).options(['-Amicronaut.python.metadata.backend=model-runtime']).build().compile()

        then:
        def error = thrown(Exception)
        error.message.contains('Python metadata model backend')
        error.message.contains('an executable method (run) is not supported yet')
        error.message.contains('micronaut.python.metadata.backend=compiler')

        cleanup:
        target?.deleteDir()
    }

    private URLClassLoader newLoader() {
        new URLClassLoader([output.toURI().toURL()] as URL[], getClass().classLoader)
    }
}
