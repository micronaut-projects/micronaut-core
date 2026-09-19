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
import io.micronaut.context.python.runtime.PythonRuntimeMetadata
import io.micronaut.context.python.runtime.codec.PythonMetadataCodec
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanIntrospector
import io.micronaut.inject.BeanDefinition
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Path

/**
 * The parity harness: the same Python fixtures compiled by the three backends, compared on what the compilation
 * produced, on the observable metadata of every definition and introspection, and on application behaviour.
 */
class PythonMetadataBackendParitySpec extends Specification {

    static final String FIXTURE = '''
from typing import Annotated, Optional
from jakarta.inject import Singleton, Inject, Named
from jakarta.annotation import PostConstruct, PreDestroy
from jakarta.validation.constraints import NotBlank, Min
from micronaut.core.annotation import Introspected
from micronaut.context.annotation import Value, Requires, Primary, Prototype

@Singleton
@Introspected
class Engine:
    def __init__(self):
        self.started = 0

    def start(self) -> int:
        self.started += 1
        return self.started

@Singleton
@Primary
class Radio:
    def __init__(self):
        pass

    def station(self) -> str:
        return "primary"

@Singleton
@Named("backup")
class BackupRadio(Radio):
    def __init__(self):
        pass

    def station(self) -> str:
        return "backup"

@Prototype
class Trip:
    def __init__(self):
        self.count = 0

    def advance(self) -> int:
        self.count += 1
        return self.count

@Singleton
class Car:
    def __init__(self, engine: Engine, radio: Annotated[Radio, Named("backup")], name: Annotated[str, Value("${car.name:unnamed}")], radios: list[Radio], trip: Optional[Trip]):
        self.engine = engine
        self.radio = radio
        self.name = name
        self.radios = radios
        self.trip = trip
        self.spare = None
        self.state = "new"

    @Inject
    def set_spare(self, spare: Engine):
        self.spare = spare

    @PostConstruct
    def initialize(self):
        self.state = "initialized"

    @PreDestroy
    def stop(self):
        self.state = "stopped"

    def describe(self) -> str:
        return self.name + ":" + self.radio.station() + ":" + str(len(self.radios)) + ":" + self.state + ":" + str(self.spare is not None) + ":" + str(self.trip.get().advance() if self.trip.isPresent() else -1) + ":" + str(self.engine.start())

    def current_state(self) -> str:
        return self.state

@Singleton
@Requires(property="feature.enabled", value="true")
class Feature:
    def __init__(self):
        pass

@Introspected
class Person:
    name: Annotated[str, NotBlank]
    age: Annotated[int, Min(0)]
    active: bool
    tags: list[str]

    def __init__(self, name: str = "anonymous"):
        self.name = name
        self.age = 1
        self.active = True
        self.tags = []

    @property
    def label(self) -> str:
        return self.name + "/" + str(self.age)
'''

    static final Map<String, String> BACKENDS = [
        'compiler': 'compiler',
        'model-build-time': 'model-build-time',
        'model-runtime': 'model-runtime',
    ]

    @Shared File source
    @Shared Map<String, File> outputs = [:]

    def setupSpec() {
        source = File.createTempDir('parity-source', '')
        def file = new File(source, 'garage/models.py')
        file.parentFile.mkdirs()
        file.text = FIXTURE
        BACKENDS.each { name, option ->
            File output = File.createTempDir("parity-$name", '')
            PyronautCompiler.builder().pythonSrc(source.absolutePath).targetDir(output)
                .options(["-Amicronaut.python.metadata.backend=$option".toString()])
                .build().compile()
            outputs[name] = output
        }
    }

    def cleanupSpec() {
        source?.deleteDir()
        outputs.values().each { it.deleteDir() }
    }

    def cleanup() {
        PythonContextRuntime.resetContext()
    }

    static List<String> inventory(File output) {
        Path root = output.toPath()
        Files.walk(root).filter { Files.isRegularFile(it) }.map { root.relativize(it).toString().replace(File.separatorChar, '/' as char) }.sorted().toList()
    }

    def "the compilation inventories differ only in the metadata artifacts"() {
        given:
        def compiler = inventory(outputs.compiler)
        def buildTime = inventory(outputs['model-build-time'])
        def runtime = inventory(outputs['model-runtime'])
        def metadataClasses = { List<String> files -> files.findAll { it ==~ /garage\/\$(Engine|Radio|BackupRadio|Trip|Car|Feature|Person)\$(Definition|Introspection)\.class/ } }
        def services = { List<String> files -> files.findAll { (it.startsWith('META-INF/micronaut/io.micronaut.inject.BeanDefinitionReference/') || it.startsWith('META-INF/micronaut/io.micronaut.core.beans.BeanIntrospectionReference/')) && !it.contains('TargetTypeMapping') } }
        def models = { List<String> files -> files.findAll { it.endsWith('.mpym') } }

        expect: 'the compiler and the build-time model backend emit the same definitions, introspections and service entries'
        metadataClasses(compiler) == metadataClasses(buildTime)
        metadataClasses(compiler).sort() == ['garage/$BackupRadio$Definition.class', 'garage/$Car$Definition.class', 'garage/$Engine$Definition.class',
                                             'garage/$Engine$Introspection.class', 'garage/$Feature$Definition.class', 'garage/$Person$Introspection.class',
                                             'garage/$Radio$Definition.class', 'garage/$Trip$Definition.class']
        services(compiler) == services(buildTime)
        models(compiler).isEmpty()
        models(buildTime) == models(runtime)
        models(runtime).sort() == ['META-INF/micronaut/python/runtime/garage.BackupRadio.mpym', 'META-INF/micronaut/python/runtime/garage.Car.mpym',
                                   'META-INF/micronaut/python/runtime/garage.Engine.mpym', 'META-INF/micronaut/python/runtime/garage.Feature.mpym',
                                   'META-INF/micronaut/python/runtime/garage.Person.mpym', 'META-INF/micronaut/python/runtime/garage.Radio.mpym',
                                   'META-INF/micronaut/python/runtime/garage.Trip.mpym']

        and: 'the runtime backend emits no metadata class and no service entry for them, and a catalog instead'
        metadataClasses(runtime).isEmpty()
        services(runtime).isEmpty()
        runtime.contains('META-INF/micronaut/python/runtime/catalog')
        !buildTime.contains('META-INF/micronaut/python/runtime/catalog')

        and: 'everything else (wrappers, Python sources, manifests) is identical across the three'
        def rest = compiler - metadataClasses(compiler) - services(compiler)
        def runtimeRest = runtime - models(runtime) - ['META-INF/micronaut/python/runtime/catalog']
        def buildTimeRest = buildTime - metadataClasses(buildTime) - services(buildTime) - models(buildTime)
        (rest - runtimeRest) + (runtimeRest - rest) == []
        (rest - buildTimeRest) + (buildTimeRest - rest) == []
    }

    def "the saved models are identical across the model backends and the build-time classes are what the runtime generates"() {
        expect:
        inventory(outputs['model-runtime']).findAll { it.endsWith('.mpym') }.each { String resource ->
            byte[] runtimeModel = new File(outputs['model-runtime'], resource).bytes
            byte[] buildTimeModel = new File(outputs['model-build-time'], resource).bytes
            assert runtimeModel == buildTimeModel: resource
            def model = PythonMetadataCodec.decode(runtimeModel, resource).classModel()
            if (model.beanDefinition() != null) {
                def classFile = new File(outputs['model-build-time'], model.beanDefinition().definitionClassName().replace('.', '/') + '.class')
                assert classFile.bytes == PythonRuntimeMetadata.definitionBytes(model): classFile
            }
            if (model.introspection() != null) {
                def classFile = new File(outputs['model-build-time'], model.introspection().introspectionClassName().replace('.', '/') + '.class')
                assert classFile.bytes == PythonRuntimeMetadata.introspectionBytes(model): classFile
            }
        }
    }

    @Unroll
    def "the #backend backend exposes the same bean definitions, introspections and behaviour as the compiler"() {
        given:
        def expected = observe('compiler')
        PythonContextRuntime.resetContext()
        def actual = observe(backend)

        expect:
        differences(expected, actual) == [:]

        where:
        backend << ['model-build-time', 'model-runtime']
    }

    def "the runtime backend generates only what the application asks for"() {
        given:
        def loader = new URLClassLoader([outputs['model-runtime'].toURI().toURL()] as URL[], getClass().classLoader)
        def before = PythonRuntimeMetadata.generatedClassCount()

        when: 'the context starts: the references answer candidate selection from the models, nothing is generated for this fixture'
        def context = ApplicationContext.builder().classLoader(loader).properties(['feature.enabled': 'false']).start()
        def afterStart = PythonRuntimeMetadata.generatedClassCount() - before

        then:
        afterStart == 0
        !PythonRuntimeMetadata.isDefinitionGenerated(loader.loadClass('garage.Car'))
        !PythonRuntimeMetadata.isIntrospectionGenerated(loader.loadClass('garage.Person'))

        when: 'a bean is resolved: its definition and the definitions of its candidates are generated, and nothing else'
        context.getBean(loader.loadClass('garage.Car'))

        then:
        PythonRuntimeMetadata.generatedClassCount() - before == 5
        ['garage.Car', 'garage.Engine', 'garage.Radio', 'garage.BackupRadio', 'garage.Trip'].every { PythonRuntimeMetadata.isDefinitionGenerated(loader.loadClass(it)) }
        !PythonRuntimeMetadata.isDefinitionGenerated(loader.loadClass('garage.Feature'))

        when:
        BeanIntrospector.forClassLoader(loader).getIntrospection(loader.loadClass('garage.Person'))
        context.getAllBeanDefinitions()

        then: 'the introspection, and every remaining definition once all definitions are asked for'
        PythonRuntimeMetadata.generatedClassCount() - before == 7

        cleanup:
        context?.close()
        loader?.close()
    }

    private Map observe(String backend) {
        def loader = new URLClassLoader([outputs[backend].toURI().toURL()] as URL[], getClass().classLoader)
        def context = ApplicationContext.builder().classLoader(loader).properties(['car.name': 'parity', 'feature.enabled': 'true']).start()
        try {
            Map result = [:]
            ['garage.Engine', 'garage.Radio', 'garage.BackupRadio', 'garage.Trip', 'garage.Car', 'garage.Feature'].each { String name ->
                Class type = loader.loadClass(name)
                result[name] = definition(context.getBeanDefinition(type))
            }
            def car = context.getBean(loader.loadClass('garage.Car'))
            result['car.describe'] = car.describe()
            result['car.describe.again'] = car.describe()
            result['radio.primary'] = context.getBean(loader.loadClass('garage.Radio')).station()
            result['feature.present'] = context.findBean(loader.loadClass('garage.Feature')).present
            result['trip.prototype'] = !context.getBean(loader.loadClass('garage.Trip')).is(context.getBean(loader.loadClass('garage.Trip')))
            def introspector = BeanIntrospector.forClassLoader(loader)
            ['garage.Person', 'garage.Engine'].each { String name ->
                result[name + '.introspection'] = introspection(introspector.getIntrospection(loader.loadClass(name)))
            }
            result['introspected'] = introspector.findIntrospectedTypes { it.name.startsWith('garage.') }*.name.sort()
            result['introspections.enumerated'] = introspector.findIntrospections { it.name.startsWith('garage.') }*.beanType*.name.sort()
            def person = introspector.getIntrospection(loader.loadClass('garage.Person'))
            def instance = person.instantiate('parity')
            person.getRequiredProperty('age', Integer.TYPE).set(instance, 7)
            result['person.label'] = person.getRequiredProperty('label', String).get(instance)
            result['person.readOnly'] = person.getRequiredProperty('label', String).readOnly
            result['person.constraintIndex'] = person.getIndexedProperties(loader.loadClass('jakarta.validation.constraints.NotBlank'))*.name
            result['person.constraintStereotypeIndex'] = person.getIndexedProperties(loader.loadClass('jakarta.validation.Constraint'))*.name
            context.destroyBean(loader.loadClass('garage.Car'))
            result['car.stopped'] = car.current_state()
            return result
        } finally {
            if (context.running) {
                context.close()
            }
            loader.close()
        }
    }

    private static Map differences(Map expected, Map actual) {
        Map diff = [:]
        (expected.keySet() + actual.keySet()).each { key ->
            def e = expected[key]
            def a = actual[key]
            if (e instanceof Map && a instanceof Map) {
                Map nested = differences(e, a)
                if (nested) {
                    diff[key] = nested
                }
            } else if (e != a) {
                diff[key] = [expected: e, actual: a]
            }
        }
        diff
    }

    private static Map definition(BeanDefinition definition) {
        [
            name: definition.beanDefinitionName,
            singleton: definition.singleton,
            scope: definition.scopeName.orElse(null),
            primary: definition.primary,
            annotations: definition.annotationMetadata.annotationNames.sort(),
            declared: definition.annotationMetadata.declaredAnnotationNames.sort(),
            stereotypes: definition.annotationMetadata.stereotypeAnnotationNames.sort(),
            qualifier: definition.declaredQualifier?.toString(),
            constructorArguments: definition.constructor.arguments.collect { [it.name, it.type.name, it.annotationMetadata.annotationNames.sort(), it.typeParameters*.type*.name] },
            injectedMethods: definition.injectedMethods.collect { [it.name, it.arguments*.name] },
            postConstruct: definition.postConstructMethods*.name,
            preDestroy: definition.preDestroyMethods*.name,
            exposedTypes: definition.exposedTypes*.name.sort(),
            executableMethods: definition.executableMethods*.name,
            requiredComponents: definition.requiredComponents*.name.sort(),
            typeArguments: definition.typeArguments*.type*.name,
        ]
    }

    private static Map introspection(BeanIntrospection introspection) {
        [
            beanType: introspection.beanType.name,
            annotations: introspection.annotationMetadata.annotationNames.sort(),
            declared: introspection.annotationMetadata.declaredAnnotationNames.sort(),
            constructorArguments: introspection.constructorArguments.collect { [it.name, it.type.name] },
            properties: introspection.beanProperties.collect { [it.name, it.type.name, it.readOnly, it.annotationMetadata.annotationNames.sort(), it.asArgument().typeParameters*.type*.name] },
            beanMethods: introspection.beanMethods*.name,
            buildable: introspection.buildable,
            hasBuilder: introspection.hasBuilder(),
        ]
    }
}
