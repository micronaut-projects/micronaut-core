from typing import Annotated

from jakarta.inject import Inject
from jakarta.validation import ConstraintViolationException
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .PythonMessageSender import Message, PythonMessageSender


@MicronautTest
class PythonMessageSenderSpec:
    sender: Annotated[PythonMessageSender, Inject]

    @Test
    def validArgumentsReachThePythonOverride(self):
        assert self.sender.send("bob", Message("hello")) == "bob: hello"
        assert self.sender.broadcast(Message("hello")) == "everyone: hello"

    @Test
    def inheritedConstraintsAreEnforced(self):
        assert self._violation(lambda: self.sender.send("", Message("hello"))) == "send.recipient: must not be blank"
        assert self._violation(lambda: self.sender.send("bob", None)) == "send.message: must not be null"
        assert self._violation(lambda: self.sender.broadcast(None)) == "broadcast.message: must not be null"

    @Test
    def inheritedCascadingIsEnforced(self):
        assert self._violation(lambda: self.sender.send("bob", Message(""))) == "send.message.text: must not be blank"
        assert self._violation(lambda: self.sender.broadcast(Message(""))) == "broadcast.message.text: must not be blank"

    @staticmethod
    def _violation(call) -> str:
        try:
            call()
        except ConstraintViolationException as e:
            return e.getMessage()
        assert False, "expected a ConstraintViolationException"
