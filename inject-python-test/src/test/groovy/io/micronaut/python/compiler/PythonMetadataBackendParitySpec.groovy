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
from enum import Enum
from typing import Annotated, Optional
from jakarta.inject import Singleton, Inject, Named
from jakarta.annotation import PostConstruct, PreDestroy
from jakarta.validation.constraints import NotBlank, Min
from micronaut.core.annotation import Introspected, Creator
from micronaut.context.annotation import Value, Requires, Primary, Prototype, Property, Executable, Factory, Bean, Context, ConfigurationProperties
from micronaut.http.annotation import Controller, Get, QueryValue
from micronaut.context import BeanRegistration
from java.util.stream import Stream

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
class Garage:
    def __init__(self, by_name: dict[str, Radio], stream: Stream[Radio], registration: BeanRegistration[Engine], registrations: list[BeanRegistration[Radio]], color: Annotated[str, Property(name="car.color")]):
        self.by_name = by_name
        self.stream_count = stream.count()
        self.registration = registration
        self.registrations = registrations
        self.color = color

    def describe(self) -> str:
        return ",".join(sorted(self.by_name.keySet())) + ":" + str(self.stream_count) + ":" + self.registration.getBeanDefinition().getBeanType().getSimpleName() + ":" + str(len(self.registrations)) + ":" + self.color

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

@Controller("/garage")
class GarageController:
    def __init__(self, car: Car):
        self.car = car

    @Get("/status")
    def status(self, detail: Annotated[str, QueryValue(defaultValue="none")]) -> str:
        return "open:" + self.car.name + ":" + detail

    @Executable(processOnStartup=True)
    def warm(self, times: int) -> int:
        return times * 2

    @Executable
    def sleep(self) -> None:
        pass

class Vehicle:
    def __init__(self, label: str):
        self.label = label
        self.parked = False

    def describe(self) -> str:
        return self.label + ":" + str(self.parked)

    def park(self):
        self.parked = True

@Factory
class Fleet:
    def __init__(self, engine: Engine):
        self.engine = engine

    @Bean
    @Named("van")
    def van(self, radio: Annotated[Radio, Named("backup")]) -> Vehicle:
        return Vehicle("van/" + radio.station())

    @Singleton
    @Bean(preDestroy="park")
    def truck(self) -> Vehicle:
        return Vehicle("truck")

    @Bean
    @Named
    def spare(self) -> Vehicle:
        return Vehicle("spare")

@Singleton
@Named
class SpareRadio(Radio):
    def __init__(self):
        pass

    def station(self) -> str:
        return "spare"

@ConfigurationProperties("garage")
class GarageConfig:
    name: Annotated[str, NotBlank]
    size: int
    tags: list[str]

    def __init__(self):
        self.name = "default"
        self.size = 0
        self.tags = []

    def describe(self) -> str:
        return self.name + "/" + str(self.size) + "/" + ",".join(self.tags)

@Context
@Requires(property="feature.enabled", value="true")
class Depot:
    def __init__(self, engine: Engine):
        self.count = engine.start()

    def describe(self) -> str:
        return "depot:" + str(self.count)

@Singleton
@Requires(property="feature.enabled", value="true")
class Feature:
    def __init__(self):
        pass

@Introspected
class Ticket:
    code: str

    def __init__(self, code: str):
        self.code = code

    @staticmethod
    @Creator
    def issue(code: str) -> "Ticket":
        return Ticket("issued-" + code)

@Introspected
class Fuel(Enum):
    PETROL = "petrol"
    DIESEL = "diesel"

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

    @Executable
    def greet(self, greeting: str) -> str:
        return greeting + " " + self.name

    @Executable
    def touch(self):
        self.age += 1
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
        def metadataClasses = { List<String> files -> files.findAll { it ==~ /garage\/[$](Engine|Radio|BackupRadio|Trip|Car|Garage|GarageController|Feature|Person|Fleet|Vehicle|SpareRadio|Depot|GarageConfig|Fuel|Ticket)[$]((Van|Truck|Spare)[0-9])?[$]?(Definition|Introspection|Definition[$]Exec)\.class/ } }
        def services = { List<String> files -> files.findAll { (it.startsWith('META-INF/micronaut/io.micronaut.inject.BeanDefinitionReference/') || it.startsWith('META-INF/micronaut/io.micronaut.core.beans.BeanIntrospectionReference/')) && !it.contains('TargetTypeMapping') } }
        def models = { List<String> files -> files.findAll { it.endsWith('.mpym') } }

        expect: 'the compiler and the build-time model backend emit the same definitions, introspections and service entries'
        metadataClasses(compiler) == metadataClasses(buildTime)
        metadataClasses(compiler).sort() == ['garage/$BackupRadio$Definition.class', 'garage/$Car$Definition.class', 'garage/$Depot$Definition.class', 'garage/$Engine$Definition.class',
                                             'garage/$Engine$Introspection.class', 'garage/$Feature$Definition.class', 'garage/$Fleet$Definition.class',
                                             'garage/$Fleet$Spare2$Definition.class', 'garage/$Fleet$Truck1$Definition.class', 'garage/$Fleet$Van0$Definition.class', 'garage/$Fuel$Introspection.class',
                                             'garage/$Garage$Definition.class', 'garage/$GarageConfig$Definition.class', 'garage/$GarageConfig$Introspection.class',
                                             'garage/$GarageController$Definition$Exec.class', 'garage/$GarageController$Definition.class',
                                             'garage/$Person$Introspection.class', 'garage/$Radio$Definition.class', 'garage/$SpareRadio$Definition.class', 'garage/$Ticket$Introspection.class',
                                             'garage/$Trip$Definition.class']
        services(compiler) == services(buildTime)
        models(compiler).isEmpty()
        models(buildTime) == models(runtime)
        models(runtime).sort() == ['garage.BackupRadio', 'garage.Car', 'garage.Depot', 'garage.Engine', 'garage.Feature', 'garage.Fleet', 'garage.Fuel', 'garage.Garage', 'garage.GarageConfig',
                                   'garage.GarageController',
                                   'garage.Person', 'garage.Radio', 'garage.SpareRadio', 'garage.Ticket', 'garage.Trip'].collect { "META-INF/micronaut/python/runtime/${it}.mpym".toString() }

        and: 'the runtime backend emits no metadata class of its own and no service entry, and a catalog instead'
        // Two artifacts are generated from Java classes that a visitor adds for a Python class, by the ordinary Java
        // pipeline in every backend, and are not described by the Python model: the introspection a validated
        // configuration class needs, and the converter of an introspected enum
        metadataClasses(runtime) == ['garage/$GarageConfig$Introspection.class']
        services(runtime) == ['META-INF/micronaut/io.micronaut.core.beans.BeanIntrospectionReference/garage.$GarageConfig$Introspection',
                              'META-INF/micronaut/io.micronaut.inject.BeanDefinitionReference/garage.$FuelTypeConverter$Definition']
        runtime.contains('META-INF/micronaut/python/runtime/catalog')
        !buildTime.contains('META-INF/micronaut/python/runtime/catalog')

        and: 'everything else (wrappers, Python sources, manifests) is identical across the three'
        def rest = compiler - metadataClasses(compiler) - services(compiler)
        def runtimeRest = runtime - models(runtime) - metadataClasses(runtime) - services(runtime) - ['META-INF/micronaut/python/runtime/catalog']
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
            model.beanDefinitions().each { definition ->
                def classFile = new File(outputs['model-build-time'], definition.definitionClassName().replace('.', '/') + '.class')
                assert classFile.bytes == PythonRuntimeMetadata.definitionBytes(model, definition): classFile
                if (!definition.executableMethods().isEmpty()) {
                    def execFile = new File(outputs['model-build-time'], definition.definitionClassName().replace('.', '/') + '$Exec.class')
                    assert execFile.bytes == PythonRuntimeMetadata.executableMethodsBytes(model, definition): execFile
                }
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

        when: 'the context starts: the references answer candidate selection from the models, and only the eagerly initialized bean is generated'
        def context = ApplicationContext.builder().classLoader(loader).properties(['feature.enabled': 'false']).start()
        def afterStart = PythonRuntimeMetadata.generatedClassCount() - before

        then: 'a @Context bean loads its definition to evaluate its condition, with the definition of the bean it needs; nothing else is generated'
        afterStart == 2
        ['garage.Depot', 'garage.Engine'].every { PythonRuntimeMetadata.isDefinitionGenerated(loader.loadClass(it)) }
        !PythonRuntimeMetadata.isDefinitionGenerated(loader.loadClass('garage.Car'))
        !PythonRuntimeMetadata.isIntrospectionGenerated(loader.loadClass('garage.Person'))

        when: 'a bean is resolved: its definition and the definitions of its candidates are generated, and nothing else'
        context.getBean(loader.loadClass('garage.Car'))

        then:
        PythonRuntimeMetadata.generatedClassCount() - before == 7
        ['garage.Car', 'garage.Engine', 'garage.Radio', 'garage.BackupRadio', 'garage.SpareRadio', 'garage.Trip'].every { PythonRuntimeMetadata.isDefinitionGenerated(loader.loadClass(it)) }
        !PythonRuntimeMetadata.isDefinitionGenerated(loader.loadClass('garage.GarageController'))
        !PythonRuntimeMetadata.isDefinitionGenerated(loader.loadClass('garage.Feature'))

        when:
        BeanIntrospector.forClassLoader(loader).getIntrospection(loader.loadClass('garage.Person'))
        context.getAllBeanDefinitions()

        then: 'the introspection, and every remaining definition (the controller with its executable methods companion) once all definitions are asked for'
        PythonRuntimeMetadata.generatedClassCount() - before == 17

        cleanup:
        context?.close()
        loader?.close()
    }

    private Map observe(String backend) {
        def loader = new URLClassLoader([outputs[backend].toURI().toURL()] as URL[], getClass().classLoader)
        def context = ApplicationContext.builder().classLoader(loader).properties(['car.name': 'parity', 'car.color': 'red', 'feature.enabled': 'true', 'garage.name': 'central', 'garage.size': 3, 'garage.tags': ['a', 'b'], 'micronaut.server.port': -1]).start()
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
            result['garage.describe'] = context.getBean(loader.loadClass('garage.Garage')).describe()
            def controllerDefinition = context.getBeanDefinition(loader.loadClass('garage.GarageController'))
            result['controller'] = definition(controllerDefinition)
            result['controller.executables'] = controllerDefinition.executableMethods.collect { [it.name, it.arguments*.name, it.arguments*.type*.name, it.returnType.type.name, it.annotationMetadata.annotationNames.sort(), it.arguments.collect { a -> a.annotationMetadata.annotationNames.sort() }] }
            result['controller.processing'] = controllerDefinition.executableMethodsForProcessing*.name
            result['controller.requiresProcessing'] = controllerDefinition.requiresMethodProcessing()
            def controller = context.getBean(loader.loadClass('garage.GarageController'))
            result['controller.warm'] = controllerDefinition.findMethod('warm', Integer.TYPE).get().invoke(controller, 21)
            result['controller.sleep'] = controllerDefinition.findMethod('sleep').get().invoke(controller)
            def server = context.getBean(io.micronaut.runtime.server.EmbeddedServer).start()
            def client = context.createBean(io.micronaut.http.client.HttpClient, server.URL)
            result['controller.http'] = client.toBlocking().retrieve('/garage/status?detail=full')
            result['controller.http.default'] = client.toBlocking().retrieve('/garage/status')
            client.close()
            result['garage.Garage'] = definition(context.getBeanDefinition(loader.loadClass('garage.Garage')))
            result['garage.Fleet'] = definition(context.getBeanDefinition(loader.loadClass('garage.Fleet')))
            Class vehicleType = loader.loadClass('garage.Vehicle')
            result['vehicles'] = context.getBeanDefinitions(vehicleType).collect { definition(it) }.sort { it.name }
            def van = context.getBean(vehicleType, io.micronaut.inject.qualifiers.Qualifiers.byName('van'))
            result['vehicle.van'] = van.describe()
            result['vehicle.spare'] = context.getBean(vehicleType, io.micronaut.inject.qualifiers.Qualifiers.byName('spare')).describe()
            result['radio.spare'] = context.getBean(loader.loadClass('garage.Radio'), io.micronaut.inject.qualifiers.Qualifiers.byName('spareRadio')).station()
            result['garage.SpareRadio'] = definition(context.getBeanDefinition(loader.loadClass('garage.SpareRadio')))
            result['vehicles.all'] = context.getBeansOfType(vehicleType)*.describe().sort()
            def truck = context.getBeansOfType(vehicleType).find { it.describe().startsWith('truck') }
            context.destroyBean(truck)
            result['vehicle.truck.parked'] = truck.describe()
            result['feature.present'] = context.findBean(loader.loadClass('garage.Feature')).present
            result['depot.describe'] = context.getBean(loader.loadClass('garage.Depot')).describe()
            def configType = loader.loadClass('garage.GarageConfig')
            result['garage.GarageConfig'] = definition(context.getBeanDefinition(configType))
            result['config.describe'] = context.getBean(configType).describe()
            result['config.validated'] = context.getBeanDefinition(configType) instanceof io.micronaut.inject.ValidatedBeanDefinition
            result['trip.prototype'] = !context.getBean(loader.loadClass('garage.Trip')).is(context.getBean(loader.loadClass('garage.Trip')))
            def introspector = BeanIntrospector.forClassLoader(loader)
            ['garage.Person', 'garage.Engine'].each { String name ->
                result[name + '.introspection'] = introspection(introspector.getIntrospection(loader.loadClass(name)))
            }
            def fuel = introspector.getIntrospection(loader.loadClass('garage.Fuel'))
            result['fuel'] = introspection(fuel)
            result['fuel.constants'] = fuel.constants.collect { [it.value.name(), it.annotationMetadata.annotationNames.sort()] }
            def ticket = introspector.getIntrospection(loader.loadClass('garage.Ticket'))
            result['ticket'] = introspection(ticket)
            result['ticket.instantiate'] = ticket.instantiate('abc').code
            result['ticket.staticCreator'] = ticket.constructor.arguments*.name
            result['introspected'] = introspector.findIntrospectedTypes { it.name.startsWith('garage.') }*.name.sort()
            result['introspections.enumerated'] = introspector.findIntrospections { it.name.startsWith('garage.') }*.beanType*.name.sort()
            def person = introspector.getIntrospection(loader.loadClass('garage.Person'))
            def instance = person.instantiate('parity')
            person.getRequiredProperty('age', Integer.TYPE).set(instance, 7)
            result['person.label'] = person.getRequiredProperty('label', String).get(instance)
            result['person.readOnly'] = person.getRequiredProperty('label', String).readOnly
            result['person.constraintIndex'] = person.getIndexedProperties(loader.loadClass('jakarta.validation.constraints.NotBlank'))*.name
            result['person.constraintStereotypeIndex'] = person.getIndexedProperties(loader.loadClass('jakarta.validation.Constraint'))*.name
            result['person.methods'] = person.beanMethods.collect { [it.name, it.returnType.type.name, it.arguments*.name, it.annotationMetadata.annotationNames.sort()] }
            result['person.greet'] = person.beanMethods.find { it.name == 'greet' }.invoke(instance, 'hello')
            person.beanMethods.find { it.name == 'touch' }.invoke(instance)
            result['person.touched'] = person.getRequiredProperty('age', Integer.TYPE).get(instance)
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
