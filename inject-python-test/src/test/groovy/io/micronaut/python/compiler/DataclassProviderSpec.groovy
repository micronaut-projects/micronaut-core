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

import io.micronaut.context.python.PythonDataclassProvider
import io.micronaut.core.io.service.SoftServiceLoader
import spock.lang.Specification
import spock.lang.TempDir

class DataclassProviderSpec extends Specification {

    private static final String SERVICE_DIR = "META-INF/micronaut/io.micronaut.context.python.PythonDataclassProvider"

    @TempDir
    File output

    void "the compiler registers precomputed metadata for declared inherited and nested dataclasses"() {
        given:
        PyronautCompiler.builder().pythonCode('''
from dataclasses import dataclass


@dataclass
class Book:
    title: str


class InheritedBook(Book):
    pass


class Mixin:
    pass


class SecondBaseBook(Mixin, Book):
    pass


class Outer:
    @dataclass
    class Inner:
        title: str


class Plain:
    pass
''').targetDir(output).build().compile()
        def loader = new URLClassLoader([output.toURI().toURL()] as URL[], getClass().classLoader)

        when:
        def providers = SoftServiceLoader.load(PythonDataclassProvider, loader).collectAll()
        def entries = providers.collectMany { it.entries() }.collectEntries { [(it.type().name): it.pythonClass()] }

        then:
        new File(output, SERVICE_DIR).list().size() == 1
        providers.size() == 1
        providers[0].class.name.contains('PythonDataclasses$')
        providers[0].entries().is(providers[0].entries())
        entries.keySet() == ['python.Book', 'python.InheritedBook', 'python.SecondBaseBook', 'python.Outer$Inner'] as Set
        entries['python.Book'].packageName() == 'python'
        entries['python.Book'].rootName() == 'Book'
        entries['python.Book'].nestedMemberNames().length == 0
        entries['python.Outer$Inner'].rootName() == 'Outer'
        entries['python.Outer$Inner'].nestedMemberNames() == ['Inner'] as String[]

        cleanup:
        loader?.close()
    }

    void "no provider is generated for sources without dataclasses"() {
        when:
        PyronautCompiler.builder().pythonCode('''
class Plain:
    def greet(self) -> str:
        return "hello"
''').targetDir(output).build().compile()

        then:
        !new File(output, SERVICE_DIR).exists()
    }
}
