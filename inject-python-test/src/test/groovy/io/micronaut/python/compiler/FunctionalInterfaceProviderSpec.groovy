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

import io.micronaut.context.python.PythonFunctionalInterfaceProvider
import io.micronaut.core.io.service.SoftServiceLoader
import io.micronaut.python.annotation.processing.test.FunctionalOverloads
import io.micronaut.python.annotation.processing.test.specifications.PredicateSpec
import io.micronaut.python.annotation.processing.test.specifications.QuerySpec
import io.micronaut.python.annotation.processing.test.specifications.UpdateSpec
import spock.lang.Specification

import java.util.function.Consumer

class FunctionalInterfaceProviderSpec extends Specification {

    private static final String SERVICE_DIR = "META-INF/micronaut/io.micronaut.context.python.PythonFunctionalInterfaceProvider"

    def "the compiler registers the functional interfaces of the Java types the Python sources reference"() {
        given:
        def pythonCode = '''
from typing import Annotated

from jakarta.inject import Inject, Singleton
from java.lang import Comparable, Iterable
from java.util.function import Consumer
from micronaut.python.annotation.processing.test import FunctionalOverloads
from micronaut.python.annotation.processing.test.specifications import PredicateSpec, SpecificationAdvice, SpecificationRepository, UpdateSpec


@SpecificationAdvice
@Singleton
class PersonRepository(SpecificationRepository[str]):

    def updateAll(self, spec: UpdateSpec[str]) -> int: ...

    def deleteAll(self, spec: PredicateSpec[str]) -> int: ...


@Singleton
class Overloads:

    repository: Annotated[PersonRepository, Inject]

    def overloads(self) -> FunctionalOverloads:
        return FunctionalOverloads()

    def names(self) -> Iterable[str]:
        return ["a"]
'''
        def tempDir = File.createTempDir("python-functional-interfaces", "")
        def compiler = PyronautCompiler.builder()
            .pythonCode(pythonCode)
            .targetDir(tempDir)
            .build()

        when:
        compiler.compile()
        def services = new File(tempDir, SERVICE_DIR).list() as List
        def loader = new URLClassLoader([tempDir.toURI().toURL()] as URL[], getClass().classLoader)
        def providers = SoftServiceLoader.load(PythonFunctionalInterfaceProvider, loader).collectAll()
        def entries = providers.collectMany { it.entries() }.collectEntries { [(it.typeName()): [it.arity(), it.returnsValue()]] }

        then: "one provider per compilation, named by the hash of its entries"
        services.size() == 1
        services[0].contains('$PythonFunctionalInterfaces$')
        providers.size() == 1

        and: "the parameters of the inherited and the declared repository methods"
        entries[PredicateSpec.name] == [2, true]
        entries[QuerySpec.name] == [3, true]
        entries[UpdateSpec.name] == [3, true]

        and: "the binary names, nested types included, of the interfaces of a type a Python method returns"
        entries[FunctionalOverloads.CustomCallback.name] == [1, true]
        entries[FunctionalOverloads.CustomBiCallback.name] == [2, true]

        and: "the functional interfaces of the JDK that are imported or accepted, but not its single-method collection and comparison types"
        entries[Consumer.name] == [1, false]
        entries[Runnable.name] == [0, false]
        !entries.containsKey(Iterable.name)
        !entries.containsKey(Comparable.name)

        cleanup:
        loader.close()
        tempDir.deleteDir()
    }

    def "no provider is generated when the sources reference no functional interface"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton


@Singleton
class Plain:

    def greet(self, name: str) -> str:
        return "hello " + name
'''
        def tempDir = File.createTempDir("python-functional-interfaces-none", "")
        def compiler = PyronautCompiler.builder()
            .pythonCode(pythonCode)
            .targetDir(tempDir)
            .build()

        when:
        compiler.compile()

        then:
        !new File(tempDir, SERVICE_DIR).exists()

        cleanup:
        tempDir.deleteDir()
    }
}
