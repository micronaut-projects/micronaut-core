package io.micronaut.python.compiler

/**
 * A Python class extending a Java class is only serializable when the Java base is; the generated
 * source is checked, as the test runtime cannot instantiate Python subclasses of Java classes.
 */
class SerializableStubSourceSpec extends GeneratedJavaSourceSpec {

    void "a Python class on a serializable Java base is serializable"() {
        expect:
        assertGeneratedSourceContains('''
from micronaut.core.annotation import Introspected
from micronaut.python.annotation.processing.test.collections import SerializableBase

@Introspected
class OnSerializableBase(SerializableBase):
    def __init__(self, name: str):
        self.name = name
''', 'public class OnSerializableBase extends SerializableBase implements PooledValueCoercible, Serializable {')
    }

    void "a Python class on a Java base that is not serializable is not serializable"() {
        expect:
        assertGeneratedSourceContains('''
from micronaut.core.annotation import Introspected
from micronaut.python.annotation.processing.test.collections import PlainBase

@Introspected
class OnPlainBase(PlainBase):
    def __init__(self, name: str):
        self.name = name
''', 'public class OnPlainBase extends PlainBase implements PooledValueCoercible {')
    }
}
