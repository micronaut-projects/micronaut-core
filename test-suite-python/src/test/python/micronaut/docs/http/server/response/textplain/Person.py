from dataclasses import dataclass


@dataclass
class Person:
    name: str
    age: int

    def __str__(self) -> str:
        return f"Person[name={self.name}, age={self.age}]"
