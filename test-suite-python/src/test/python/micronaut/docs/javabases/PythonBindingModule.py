from jakarta.inject import Singleton
from docs.javabases import AbstractBindingModule, EventBus


@Singleton
class PythonBindingModule(AbstractBindingModule):
    def configure(self) -> None:
        self.bind(EventBus.class_)
        self.bindValue(42)

    def recorded(self) -> list:
        return list(self.binder())

    def count(self) -> int:
        return self.bindingCount()
