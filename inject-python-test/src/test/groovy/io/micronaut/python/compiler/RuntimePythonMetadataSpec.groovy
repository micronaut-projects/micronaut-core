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
import io.micronaut.context.python.runtime.RuntimePythonBeanDefinitionsProvider
import io.micronaut.context.python.runtime.RuntimePythonMetadata
import io.micronaut.core.beans.BeanIntrospector
import io.micronaut.inject.BeanDefinitionReference
import groovy.transform.CompileStatic
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RuntimePythonMetadataSpec extends Specification {
    @Shared File source
    @Shared File output

    def setupSpec() {
        source = File.createTempDir('runtime-metadata-source', '')
        output = File.createTempDir('runtime-metadata-output', '')
        def file = new File(source, 'app/models.py')
        file.parentFile.mkdirs()
        file.text = '''
from jakarta.inject import Singleton
from micronaut.core.annotation import Introspected

@Singleton
@Introspected
class RuntimeBean:
    def __init__(self):
        pass

    def greeting(self) -> str:
        return "hello from Python"

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
            .options(['-Amicronaut.python.runtimeMetadata=app.RuntimeBean,app.RuntimePerson'])
            .build().compile()
    }

    def cleanupSpec() {
        source?.deleteDir()
        output?.deleteDir()
    }

    def cleanup() {
        PythonContextRuntime.resetContext()
    }

    def "compilation emits models and wrappers but no selected definitions or introspections"() {
        expect:
        new File(output, 'app/RuntimeBean.class').exists()
        new File(output, 'app/RuntimePerson.class').exists()
        new File(output, 'META-INF/micronaut/python/runtime/index').readLines().sort() == ['app.RuntimeBean', 'app.RuntimePerson']
        !new File(output, 'app/$RuntimeBean$Definition.class').exists()
        !new File(output, 'app/$RuntimeBean$Introspection.class').exists()
        !new File(output, 'app/$RuntimePerson$Introspection.class').exists()
        !new File(output, 'app/$RuntimeBean$RuntimeDefinition.class').exists()
        !new File(output, 'app/$RuntimePerson$RuntimeIntrospection.class').exists()
    }

    def "discovery stays lightweight and a running context uses the generated singleton and introspection"() {
        given:
        def loader = newLoader()
        def beanType = loader.loadClass('app.RuntimeBean')
        def personType = loader.loadClass('app.RuntimePerson')
        def provider = new RuntimePythonBeanDefinitionsProvider()
        def reference = findReference(provider, loader, beanType)

        expect:
        reference
        reference.singleton
        !RuntimePythonMetadata.isDefinitionGenerated(beanType)
        !RuntimePythonMetadata.isIntrospectionGenerated(personType)

        when:
        def context = ApplicationContext.builder().classLoader(loader).beanDefinitionsProvider(provider).start()
        def bean = context.getBean(beanType)
        def introspector = BeanIntrospector.forClassLoader(loader)
        def introspection = introspector.getIntrospection(personType)
        def person = introspection.instantiate()
        introspection.getRequiredProperty('name', String).set(person, 'runtime')
        introspection.getRequiredProperty('age', Integer.TYPE).set(person, 42)
        introspection.getRequiredProperty('active', Boolean.TYPE).set(person, false)

        then:
        context.getBean(beanType).is(bean)
        context.getBeanDefinition(beanType).class.name == 'app.$RuntimeBean$RuntimeDefinition'
        context.getBeanDefinition(beanType).getBeanDefinitionName() == reference.getBeanDefinitionName()
        context.getBeanDefinition(beanType).annotationMetadata.hasAnnotation('io.micronaut.core.annotation.Introspected')
        introspection.class.name == 'app.$RuntimePerson$RuntimeIntrospection'
        introspection.class.classLoader.is(loader)
        introspection.getRequiredProperty('name', String).get(person) == 'runtime'
        introspection.getRequiredProperty('age', Integer.TYPE).get(person) == 42
        !introspection.getRequiredProperty('active', Boolean.TYPE).get(person)
        introspector.getIntrospection(personType).is(introspection)
        RuntimePythonMetadata.isDefinitionGenerated(beanType)
        RuntimePythonMetadata.isIntrospectionGenerated(personType)
        !RuntimePythonMetadata.isIntrospectionGenerated(beanType)

        and: 'the runtime-generated definition creates a usable Python-backed bean'
        bean.greeting() == 'hello from Python'

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

        cleanup:
        pool?.shutdownNow()
        first?.close()
        second?.close()
    }

    def "a fresh JVM generates from disk with no Python compiler or annotation processor on its classpath"() {
        given:
        def command = [new File(System.getProperty('java.home'), 'bin/java').absolutePath,
            '--limit-modules=java.se,jdk.unsupported,jdk.management,jdk.zipfs',
            '-Dpolyglot.engine.WarnInterpreterOnly=false', '-cp', System.getProperty('runtimeMetadataPrototypeClasspath'),
            'io.micronaut.context.python.runtime.RuntimePythonMetadataDemo', output.absolutePath,
            'app.RuntimeBean', 'app.RuntimePerson']
        def log = File.createTempFile('runtime-metadata-process', '.log')

        when:
        def process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log).start()
        def completed = process.waitFor(90, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
        }
        def report = log.text

        then:
        completed
        assert process.exitValue() == 0: report
        report.contains('COMPILER_ABSENT=true')
        report.contains('JAVAC_ABSENT=true')
        report.contains('DEFINITION=app.$RuntimeBean$RuntimeDefinition')
        report.contains('INTROSPECTION=app.$RuntimePerson$RuntimeIntrospection')
        report.contains('PROPERTY=generated at runtime')

        cleanup:
        log?.delete()
    }

    def "unsupported injection and lifecycle metadata fails compilation rather than being dropped"() {
        given:
        def target = File.createTempDir('runtime-metadata-invalid', '')

        when:
        PyronautCompiler.builder().pythonCode('''
from micronaut.core.annotation import Introspected
from jakarta.annotation import PostConstruct

@Introspected
class Unsupported:
    def __init__(self):
        pass

    @PostConstruct
    def initialize(self):
        pass
''').targetDir(target).options(['-Amicronaut.python.runtimeMetadata=python.Unsupported']).build().compile()

        then:
        def error = thrown(Exception)
        error.message.contains('Runtime metadata prototype')
        error.message.contains('annotated methods')

        cleanup:
        target?.deleteDir()
    }

    private URLClassLoader newLoader() {
        new URLClassLoader([output.toURI().toURL()] as URL[], getClass().classLoader)
    }

    @CompileStatic
    private static BeanDefinitionReference<?> findReference(RuntimePythonBeanDefinitionsProvider provider, ClassLoader loader, Class<?> type) {
        // Do not make Groovy introspect unrelated optional-dependency bean reference classes.
        for (BeanDefinitionReference<?> reference : provider.provide(loader)) {
            if (reference.getName() == type.getName()) {
                return reference
            }
        }
        throw new AssertionError('Missing runtime bean reference')
    }
}
