package io.micronaut.python.compiler

class NestedHostTypeSourceSpec extends GeneratedJavaSourceSpec {

    void "nested host return types use Java source names in class literals"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from java.util import Map

@Singleton
class EntryService:
    @Executable
    def entry(self) -> Map.Entry:
        return None
'''

        expect:
        assertGeneratedSourceContains(pythonCode, "Map.Entry.class")
    }
}
