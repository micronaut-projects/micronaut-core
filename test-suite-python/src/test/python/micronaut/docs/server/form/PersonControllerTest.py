from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest, MediaType
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

Person = java.type("micronaut.docs.server.form.Person")


@Property(name="spec.name", value="PersonControllerFormTest")
@MicronautTest
class PersonControllerTest:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def test_save(self):
        self.invoke(HttpRequest.POST(
            "/people",
            "firstName=Fred&lastName=Flintstone&age=45",
        ).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE))

    @Test
    def test_save_with_args(self):
        self.invoke(HttpRequest.POST(
            "/people/saveWithArgs",
            "firstName=Fred&lastName=Flintstone&age=45",
        ).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE))

    def invoke(self, request):
        response = self.client.toBlocking().exchange(request, Person)
        person = response.getBody().get()

        assert person.getFirstName() == "Fred"
        assert person.getLastName() == "Flintstone"
        assert person.getAge() == 45
