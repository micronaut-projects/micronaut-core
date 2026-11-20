# A Java imports manifest as the Python compiler writes it, for GraalPyContextFactoryTest: the wrapper of a
# Python dataclass, registered at bootstrap although nothing imports Java.
PACKAGES = {}
TYPES = {}
MEMBERS = {}
DATACLASSES = {
    "io.micronaut.context.python.GraalPyContextFactoryTest$DataclassWrapper": ["python", "Item"]
}
