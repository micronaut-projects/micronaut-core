from abc import ABC, abstractmethod

import java
# tag::imports[]
from micronaut.core.async_.annotation import SingleResult
from micronaut.core.version.annotation import Version
from micronaut.http.annotation import Get
from micronaut.http.client.annotation import Client
# end::imports[]

Publisher = java.type("org.reactivestreams.Publisher")


# tag::clazz[]
@Client("/hello")
@Version("1")  # <1>
class HelloClient(ABC):

    @Get("/greeting/{name}")
    @abstractmethod
    def sayHello(self, name: str) -> str:
        pass

    @Version("2")
    @Get("/greeting/{name}")
    @SingleResult
    @abstractmethod
    def sayHelloTwo(self, name: str) -> Publisher:  # <2>
        pass
# end::clazz[]
