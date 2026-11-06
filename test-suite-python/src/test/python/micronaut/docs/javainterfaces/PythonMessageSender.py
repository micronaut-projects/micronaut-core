# tag::imports[]
from dataclasses import dataclass
from typing import Annotated

from jakarta.inject import Singleton
from jakarta.validation.constraints import NotBlank
from micronaut.core.annotation import Introspected
from docs.javainterfaces import MessageSender
# end::imports[]


# tag::class[]
@Introspected
@dataclass
class Message:
    text: Annotated[str, NotBlank]  # <1>


@Singleton
class PythonMessageSender(MessageSender[Message]):  # <2>

    def send(self, recipient: str, message: Message) -> str:  # <3>
        return f"{recipient}: {message.text}"
# end::class[]
