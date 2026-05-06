from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .Person import Person

BigDecimal = java.type("java.math.BigDecimal")
Calendar = java.type("java.util.Calendar")
Long = java.type("java.lang.Long")


@Property(name="spec.name", value="TextPlainControllerTest")
@MicronautTest
class TextPlainControllerTest:
    httpClient: Annotated[HttpClient, Inject, Client("/txt")]

    @Test
    def text_plain_boolean(self):
        self.assert_text_result("/boolean", "true")

    @Test
    def text_plain_mono_boolean(self):
        self.assert_text_result("/boolean/mono", "true")

    @Test
    def text_plain_flux_boolean(self):
        self.assert_text_result("/boolean/flux", "true")

    @Test
    def text_plain_big_decimal(self):
        self.assert_text_result("/bigdecimal", BigDecimal.valueOf(Long.MAX_VALUE).toString())

    @Test
    def text_plain_date(self):
        self.assert_text_result("/date", Calendar.Builder().setDate(2023, 7, 4).build().toString())

    @Test
    def text_plain_person(self):
        self.assert_text_result("/person", str(Person("Dean Wette", 65)))

    def assert_text_result(self, url: str, expected_result: str):
        result = self.httpClient.toBlocking().retrieve(url)
        assert result == expected_result
