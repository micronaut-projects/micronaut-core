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
import spock.lang.PendingFeature

class NullableElementSpec extends AbstractPythonTypeElementSpec {

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

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0030")
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
