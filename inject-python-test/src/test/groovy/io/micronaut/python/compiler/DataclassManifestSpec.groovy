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

import spock.lang.Specification
import spock.lang.TempDir

class DataclassManifestSpec extends Specification {

    @TempDir
    File output

    void "the Java imports manifest records the wrappers of declared, inherited and nested dataclasses"() {
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

        when:
        def dataclasses = JavaImportsManifest.read(output).dataclasses

        then: "each wrapper with the package and dotted name of its Python class; no generated provider"
        dataclasses == [
            'python.Book'          : ['python', 'Book'],
            'python.InheritedBook' : ['python', 'InheritedBook'],
            'python.Outer$Inner'   : ['python', 'Outer.Inner'],
            'python.SecondBaseBook': ['python', 'SecondBaseBook'],
        ]
        !new File(output, 'META-INF/micronaut/io.micronaut.context.python.PythonDataclassProvider').exists()
    }

    void "sources without dataclasses record none"() {
        when:
        PyronautCompiler.builder().pythonCode('''
class Plain:
    def greet(self) -> str:
        return "hello"
''').targetDir(output).build().compile()

        then:
        JavaImportsManifest.read(output).dataclasses.isEmpty()
    }
}
