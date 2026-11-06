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
package io.micronaut.python.annotation.processing.test.visitorintegration

import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

/**
 * An annotation mapper that resolves a class named by an annotation member through the visitor context, as
 * Micronaut Data's {@code MappedPropertyMapper} does for {@code MappedProperty(converter = ...)}, must be able to
 * look up Python classes while the Python class registry is being built.
 */
class AnnotationMapperClassLookupSpec extends AbstractPythonTypeElementSpec {

    void "test a mapper can resolve a Python converter class named by a property annotation"() {
        given:
        def context = buildContext('''
from dataclasses import dataclass
from typing import Annotated

from jakarta.inject import Singleton
from micronaut.core.annotation import Introspected
from micronaut.core.convert import ConversionContext
from micronaut.data.model.runtime.convert import AttributeConverter
from io.micronaut.python.annotation.processing.test.visitorintegration import ConvertedProperty


@dataclass
class Quantity:
    amount: int


@Singleton
class QuantityAttributeConverter(AttributeConverter[Quantity, int]):

    def convertToPersistedValue(self, quantity: Quantity | None, context: ConversionContext) -> int | None:
        return None if quantity is None else quantity.amount

    def convertToEntityValue(self, value: int | None, context: ConversionContext) -> Quantity | None:
        return None if value is None else Quantity(value)


@Introspected
@dataclass
class Sale:
    quantity: Annotated[Quantity, ConvertedProperty(converter=QuantityAttributeConverter)]
    product: str = "apple"
''')

        when:
        def introspection = getBeanIntrospection(context, "python.Sale")
        def quantity = introspection.getRequiredProperty("quantity", Object)

        then:
        quantity.hasAnnotation(ConvertedProperty)
        quantity.getAnnotation(ConvertedProperty).annotationClassValue("converter").get().name == "python.QuantityAttributeConverter"
        quantity.hasAnnotation(PersistedAs)
        quantity.getAnnotation(PersistedAs).stringValue("converter").get() == "python.QuantityAttributeConverter"
        quantity.getAnnotation(PersistedAs).annotationClassValue("value").get().name == Integer.name

        cleanup:
        context?.close()
    }
}
