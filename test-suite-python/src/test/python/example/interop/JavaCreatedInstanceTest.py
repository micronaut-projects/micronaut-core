from micronaut.context import ApplicationContext
from micronaut.json import JsonMapper
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .Greeting import Greeting


@MicronautTest
class JavaCreatedInstanceTest:
    """An instance of a Python-backed type that Java created is the Python object in Python.

    Deserialization (``ObjectMapper.readValue(json, PyDataclass)``, ``client.retrieve(request,
    PyDataclass)``) builds the generated Java class of the Python type, never the Python object
    itself, and the wrapper creates its Python object in the primary context of the application
    whose runtime is installed. A test that runs a nested ``ApplicationContext.run()`` is in the
    context of the enclosing application, so the object the wrapper created in the nested
    application's context reached it as a foreign object: ``isinstance``, ``==`` and ``repr``
    worked on the foreign object rather than on the Python one.
    """

    @Test
    def a_java_created_instance_is_the_python_object_of_the_calling_context(self) -> None:
        nested = ApplicationContext.run()
        try:
            mapper = nested.getBean(JsonMapper)
            greeting = mapper.readValue('{"text":"Hello John"}', Greeting)

            assert greeting.text == "Hello John"
            assert isinstance(greeting, Greeting), f"not a Greeting: {type(greeting)}"
            assert greeting == Greeting("Hello John"), f"not equal: {greeting!r}"
            assert repr(greeting) == repr(Greeting("Hello John")), f"repr is {greeting!r}"
        finally:
            nested.close()

