from micronaut.context import ApplicationContext
from micronaut.json import JsonMapper
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .Greeting import Greeting
from .Note import Note


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

    @Test
    def a_java_created_instance_is_one_python_object_per_context(self) -> None:
        nested = ApplicationContext.run()
        try:
            mapper = nested.getBean(JsonMapper)
            note = mapper.readValue('{"text":"Hello John"}', Note)

            assert isinstance(note, Note), f"not a Note: {type(note)}"
            # Note has no __eq__, so it is equal only to itself: the Python view of the wrapper has to
            # answer with one object per context for any of this to hold
            assert note == note, f"not equal to itself: {note!r}"
            assert hash(note) == hash(note), "the hash of the wrapper is not stable"
            assert {note: "value"}[note] == "value", "the wrapper cannot be a dictionary key"
            assert note in {note}, "the wrapper cannot be a set member"
        finally:
            nested.close()

    @Test
    def the_public_as_polyglot_value_is_the_object_of_the_wrapper(self) -> None:
        nested = ApplicationContext.run()
        try:
            mapper = nested.getBean(JsonMapper)
            note = mapper.readValue('{"text":"Hello John"}', Note)

            # asPolyglotValue() is a public member Python calls to reach the live object of a wrapper
            # (a bean held across a RefreshEvent, for one): a write through it must reach the wrapper
            note.asPolyglotValue().text = "Hello Jane"

            assert note.text == "Hello Jane", "a write through asPolyglotValue() did not reach the wrapper"
        finally:
            nested.close()

