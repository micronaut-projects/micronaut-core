from abc import ABC, abstractmethod
from dataclasses import dataclass

@dataclass
class Book:
    title : str

class BookService(ABC):
    @abstractmethod
    def find_book(self, title: str) -> Book:
        ...


