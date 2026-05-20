/*
 * Copyright 2017-2025 original authors
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

import io.micronaut.inject.ast.ClassElement

class NullableElementSpec extends AbstractPythonTypeElementSpec {

    void "test nullable and non null annotations on method parameters"() {
        expect:
        buildClassElement('''
from typing import Annotated
from micronaut.core.annotation import NonNull, Nullable

class NullableMethods:
    def annotated_nullable(self, name: Annotated[str, Nullable]) -> Annotated[str, Nullable]:
        pass

    def pep604_nullable(self, name: str | None) -> str | None:
        pass

    def annotated_non_null(self, name: Annotated[str, NonNull]) -> Annotated[str, NonNull]:
        pass

    def plain(self, name: str) -> str:
        pass
''') { ClassElement element ->
            def annotatedNullable = element.findMethod("annotated_nullable").get()
            assert annotatedNullable.parameters[0].isNullable()
            assert !annotatedNullable.parameters[0].isNonNull()

            def pep604Nullable = element.findMethod("pep604_nullable").get()
            assert pep604Nullable.parameters[0].isNullable()
            assert !pep604Nullable.parameters[0].isNonNull()

            def annotatedNonNull = element.findMethod("annotated_non_null").get()
            assert !annotatedNonNull.parameters[0].isNullable()
            assert annotatedNonNull.parameters[0].isNonNull()

            def plain = element.findMethod("plain").get()
            assert !plain.parameters[0].isNullable()
            assert !plain.parameters[0].isNonNull()
            return element
        }
    }

    void "test nullable and non null annotations on method return types"() {
        expect:
        buildClassElement('''
from typing import Annotated
from micronaut.core.annotation import NonNull, Nullable

class NullableMethods:
    def annotated_nullable(self, name: str) -> Annotated[str, Nullable]:
        pass

    def pep604_nullable(self, name: str) -> str | None:
        pass

    def annotated_non_null(self, name: str) -> Annotated[str, NonNull]:
        pass

    def plain(self, name: str) -> str:
        pass
''') { ClassElement element ->
            def annotatedNullable = element.findMethod("annotated_nullable").get()
            assert annotatedNullable.isNullable()
            assert !annotatedNullable.isNonNull()
            assert annotatedNullable.returnType.isNullable()
            assert !annotatedNullable.returnType.isNonNull()
            assert annotatedNullable.genericReturnType.isNullable()
            assert !annotatedNullable.genericReturnType.isNonNull()

            def pep604Nullable = element.findMethod("pep604_nullable").get()
            assert pep604Nullable.isNullable()
            assert !pep604Nullable.isNonNull()
            assert pep604Nullable.returnType.isNullable()
            assert !pep604Nullable.returnType.isNonNull()
            assert pep604Nullable.genericReturnType.isNullable()
            assert !pep604Nullable.genericReturnType.isNonNull()

            def annotatedNonNull = element.findMethod("annotated_non_null").get()
            assert !annotatedNonNull.isNullable()
            assert annotatedNonNull.isNonNull()
            assert !annotatedNonNull.returnType.isNullable()
            assert annotatedNonNull.returnType.isNonNull()
            assert !annotatedNonNull.genericReturnType.isNullable()
            assert annotatedNonNull.genericReturnType.isNonNull()

            def plain = element.findMethod("plain").get()
            assert !plain.isNullable()
            assert !plain.isNonNull()
            assert !plain.returnType.isNullable()
            assert !plain.returnType.isNonNull()
            assert !plain.genericReturnType.isNullable()
            assert !plain.genericReturnType.isNonNull()
            return element
        }
    }

    void "test nullable and non null annotations on property elements"() {
        expect:
        buildClassElement('''
from typing import Annotated
from micronaut.core.annotation import NonNull, Nullable

class NullableProperties:
    annotated_nullable: Annotated[str, Nullable]
    pep604_nullable: str | None
    annotated_non_null: Annotated[str, NonNull]
    plain: str
''') { ClassElement element ->
            def properties = element.beanProperties.collectEntries { [it.name, it] }

            assert properties["annotated_nullable"].isNullable()
            assert properties["pep604_nullable"].isNullable()
            assert properties["annotated_non_null"].isNonNull()
            assert !properties["plain"].isNullable()
            assert !properties["plain"].isNonNull()
            return element
        }
    }

    void "test nullable and non null annotations propagate to property types"() {
        expect:
        buildClassElement('''
from typing import Annotated
from micronaut.core.annotation import NonNull, Nullable

class NullableProperties:
    annotated_nullable: Annotated[str, Nullable]
    pep604_nullable: str | None
    annotated_non_null: Annotated[str, NonNull]
''') { ClassElement element ->
            def properties = element.beanProperties.collectEntries { [it.name, it] }

            assert properties["annotated_nullable"].type.isNullable()
            assert properties["annotated_nullable"].genericType.isNullable()
            assert properties["pep604_nullable"].type.isNullable()
            assert properties["pep604_nullable"].genericType.isNullable()
            assert properties["annotated_non_null"].type.isNonNull()
            assert properties["annotated_non_null"].genericType.isNonNull()
            return element
        }
    }
}
