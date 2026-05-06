from dataclasses import dataclass

from micronaut.core.annotation import Introspected, ReflectiveAccess


@ReflectiveAccess
@Introspected
@dataclass
class Message:
    text: str | None = None

    def getText(self) -> str | None:
        return self.text

    def setText(self, text: str) -> None:
        self.text = text

    def __str__(self) -> str:
        return "Message{text='" + str(self.text) + "'}"
