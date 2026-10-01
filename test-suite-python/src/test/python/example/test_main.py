from typing import Annotated

from jakarta.inject import Inject
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest

MicronautTest()

client: Annotated[HttpClient, Inject, Client("/")]

def client_for():
    return client

def test_root():
    response = client_for().toBlocking().retrieve("/module/")
    assert "World" in response
