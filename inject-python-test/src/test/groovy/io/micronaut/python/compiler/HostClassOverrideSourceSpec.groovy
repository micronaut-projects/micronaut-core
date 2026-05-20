package io.micronaut.python.compiler

class HostClassOverrideSourceSpec extends GeneratedJavaSourceSpec {

    void "python subclasses bridge concrete host class overrides with host signature"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
from java.util import HashMap

@Singleton
class MyMap(HashMap):
    def size(self) -> int:
        return 42
'''

        expect:
        assertGeneratedSourceContains(pythonCode, '''
public int size() {
    Value pythonResult = GraalPyRuntimeUtil.invokePythonMethod(this.asPolyglotValue(), "size", new Object[]{});
    return pythonResult.asInt();
  }
''')
    }
}
