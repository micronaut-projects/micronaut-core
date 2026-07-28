from dataclasses import dataclass
from typing import Annotated

# tag::clazz[]
from jakarta.validation.constraints import NotBlank
from micronaut.core.annotation import Introspected, ReflectiveAccess


@ReflectiveAccess
@Introspected
@dataclass
class Email:
    subject: Annotated[str | None, NotBlank] = None  # <1>
    recipient: Annotated[str | None, NotBlank] = None  # <1>

    def getSubject(self) -> str | None:
        return self.subject

    def setSubject(self, subject: str) -> None:
        self.subject = subject

    def getRecipient(self) -> str | None:
        return self.recipient

    def setRecipient(self, recipient: str) -> None:
        self.recipient = recipient
# end::clazz[]
