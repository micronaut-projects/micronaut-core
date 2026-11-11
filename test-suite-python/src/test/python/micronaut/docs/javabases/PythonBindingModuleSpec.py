from typing import Annotated

from jakarta.inject import Inject
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .PythonBindingModule import PythonBindingModule


@MicronautTest
class PythonBindingModuleSpec:
    module: Annotated[PythonBindingModule, Inject]

    @Test
    def javaCallsThePythonOverrideOfAProtectedAbstractMethod(self):
        # install() is the only public entry point: it calls the protected abstract configure(),
        # which Python implements with calls of the protected members of the base
        assert list(self.module.install()) == ["EventBus", "42"]

    @Test
    def pythonCallsAnInheritedProtectedMethodWithoutTypeVariables(self):
        self.module.install()
        assert self.module.recorded() == ["EventBus", "42"]

    @Test
    def aPackagePrivateMethodOfAnotherPackageIsNotVisible(self):
        try:
            self.module.count()
            assert False, "expected the package-private method to be invisible"
        except AttributeError:
            pass
