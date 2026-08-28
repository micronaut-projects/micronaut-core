from typing import Annotated
from dataclasses import dataclass

import java

# tag::imports[]
from micronaut.context.annotation import Requires
from micronaut.core.annotation import Introspected, ReflectiveAccess
from micronaut.http.annotation import Body, Controller, Post

LoggerFactory = java.type("org.slf4j.LoggerFactory")
# end::imports[]


@Requires(property="spec.name", value="BodyLogFilterSpec")
# tag::clazz[]
@ReflectiveAccess
@Introspected
@dataclass
class Person:
    firstName: str | None = None
    lastName: str | None = None

    def getFirstName(self) -> str | None:
        return self.firstName

    def setFirstName(self, firstName: str) -> None:
        self.firstName = firstName

    def getLastName(self) -> str | None:
        return self.lastName

    def setLastName(self, lastName: str) -> None:
        self.lastName = lastName

    def __str__(self) -> str:
        return f"Person[firstName={self.firstName}, lastName={self.lastName}]"


@Controller("/person")
class BodyLogController:
    LOG = LoggerFactory.getLogger("micronaut.docs.server.body.BodyLogController")

    @Post()
    def create(self, person: Annotated[Person, Body]) -> None:  # <1>
        self.LOG.info("Creating person {}", str(person))
# end::clazz[]
