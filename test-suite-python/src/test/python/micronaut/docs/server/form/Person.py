from dataclasses import dataclass

from micronaut.core.annotation import Introspected, ReflectiveAccess


@ReflectiveAccess
@Introspected
@dataclass
class Person:
    firstName: str | None = None
    lastName: str | None = None
    age: int = 0

    def getFirstName(self) -> str | None:
        return self.firstName

    def setFirstName(self, firstName: str) -> None:
        self.firstName = firstName

    def getLastName(self) -> str | None:
        return self.lastName

    def setLastName(self, lastName: str) -> None:
        self.lastName = lastName

    def getAge(self) -> int:
        return self.age

    def setAge(self, age: int) -> None:
        self.age = age
