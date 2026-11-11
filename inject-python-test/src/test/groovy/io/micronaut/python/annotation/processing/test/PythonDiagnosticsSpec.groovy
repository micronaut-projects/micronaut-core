package io.micronaut.python.annotation.processing.test

class PythonDiagnosticsSpec extends AbstractPythonTypeElementSpec {

    void "every unresolved Java import is reported, located in the source"() {
        when:
        buildBeanDefinition("python", "PetService", '''
from jakarta.inject import Singleton
from java.util import NoSuchList
from io.nosuchpackage.annotations import *

@Singleton
class PetService:
    pass
''')

        then:
        def e = thrown(RuntimeException)
        def message = e.message
        // both problems are reported, not only the first
        message.contains('[python:unresolved-import] Cannot import [NoSuchList] from [java.util]')
        message.contains('[python:unresolved-import] Cannot resolve Java package [io.nosuchpackage.annotations]')
        // each at its own line, with the offending statement underlined
        message.contains('--> Unnamed:3:1')
        message.contains('--> Unnamed:4:1')
        message.contains(' 3 | from java.util import NoSuchList')
        message.contains('  | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^')
        // the concise compiler message does not add a second, guessed location, nor the Java one
        !message.contains('Python snippet:')
        !message.contains('Java snippet:')
    }

    void "an annotation member referencing a missing Java constant is reported at the member value"() {
        when:
        buildBeanDefinition("python", "MapperService", '''
from jakarta.inject import Named, Singleton
from io.micronaut.python.annotation.processing.test.constants import MapperNames

@Singleton
@Named(MapperNames.NO_SUCH_CONSTANT)
class MapperService:
    pass
''')

        then:
        def e = thrown(RuntimeException)
        def message = e.message
        message.contains('[python:unresolved-annotation-member] Cannot resolve the value [MapperNames.NO_SUCH_CONSTANT] of member [value] of @jakarta.inject.Named')
        message.contains('declares no constant or nested type named [NO_SUCH_CONSTANT]')
        message.contains('--> Unnamed:6:8')
        message.contains(' 6 | @Named(MapperNames.NO_SUCH_CONSTANT)')
        message.contains('  |        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^')
    }
}
