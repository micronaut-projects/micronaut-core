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

import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer

class PythonDecimalSpec extends AbstractPythonTypeElementSpec {

    void "an application class named Decimal is not mapped to BigDecimal"() {
        given:
        def context = buildContext('''
from dataclasses import dataclass
from decimal import Decimal as NativeDecimal
from micronaut.core.annotation import Introspected

@Introspected
@dataclass
class Decimal:
    label: str

@Introspected
@dataclass
class Prices:
    native_amount: NativeDecimal
    custom: Decimal
''')
        def introspection = getBeanIntrospection(context, "python.Prices")

        expect:
        introspection.getRequiredProperty("native_amount", BigDecimal).type == BigDecimal
        introspection.getProperty("custom").get().type.name == "python.Decimal"

        cleanup:
        context?.close()
    }

    void "introspection maps imported qualified and nullable Decimal properties to BigDecimal"() {
        given:
        def context = buildContext('''
import decimal
from decimal import Decimal as Money
from dataclasses import dataclass
from micronaut.core.annotation import Introspected

@Introspected
@dataclass
class Prices:
    amount: decimal.Decimal
    discount: Money | None = None
''')
        def introspection = getBeanIntrospection(context, "python.Prices")

        expect:
        introspection.getRequiredProperty("amount", BigDecimal).type == BigDecimal
        introspection.getRequiredProperty("discount", BigDecimal).type == BigDecimal
        introspection.getRequiredProperty("discount", BigDecimal).asArgument().nullable
        introspection.constructorArguments*.type == [BigDecimal, BigDecimal]

        cleanup:
        context?.close()
    }

    void "executable Decimal arguments and return values preserve native type precision scale and null"() {
        given:
        def context = buildContext('''
from decimal import Decimal
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable

@Singleton
class PriceService:
    @Executable
    def echo(self, value: Decimal) -> Decimal:
        assert isinstance(value, Decimal)
        return value

    @Executable
    def nullable(self, value: Decimal | None) -> Decimal | None:
        assert value is None or isinstance(value, Decimal)
        return value

    @Executable
    def from_text(self, value: str) -> Decimal:
        return Decimal(value)
''')
        def definition = getBeanDefinition(context, "python.PriceService")
        def bean = getBean(context, "python.PriceService")

        expect:
        definition.getRequiredMethod("echo", BigDecimal).returnType.type == BigDecimal
        definition.getRequiredMethod("nullable", BigDecimal).arguments[0].nullable
        bean.nullable(null) == null
        ["14.50", "-123.4500", "1E+30", "12345678901234567890.12345678901234567890"].every { text ->
            def expected = new BigDecimal(text)
            def returned = bean.echo(expected)
            def constructed = bean.from_text(text)
            returned instanceof BigDecimal && returned.equals(expected) && constructed.equals(expected)
        }

        cleanup:
        context?.close()
    }

    void "HTTP JSON binds Decimal fields as native values and writes exact numeric JSON"() {
        given:
        def context = buildContext('''
from dataclasses import dataclass
from decimal import Decimal
from typing import Annotated
from micronaut.serde.annotation import Serdeable
from micronaut.http.annotation import Body, Controller, Post

@Serdeable
@dataclass
class Quote:
    amount: Decimal
    discount: Decimal | None = None

@Controller("/decimal")
class PriceController:
    @Post
    def echo(self, quote: Annotated[Quote, Body]) -> Quote:
        assert isinstance(quote.amount, Decimal)
        assert quote.discount is None or isinstance(quote.discount, Decimal)
        return quote
''', true)
        def server = context.getBean(EmbeddedServer).start()
        def client = context.createBean(HttpClient, server.URL)

        expect:
        ["14.50", "-123.4500", "12345678901234567890.12345678901234567890"].every { text ->
            def request = HttpRequest.POST("/decimal", '{"amount":' + text + ',"discount":null}')
                .contentType(MediaType.APPLICATION_JSON_TYPE)
            def response = client.toBlocking().retrieve(request, String)
            response.contains('"amount":' + text)
        }

        cleanup:
        client?.close()
        context?.close()
    }
}
