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

    void "python subclass of host class can inject more constructor parameters than host super constructor"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
from micronaut.python.annotation.processing.test import ConstructorBackedHandler, HandlerDependency

@Singleton
class PythonConstructorBackedHandler(ConstructorBackedHandler):
    def __init__(self, dependency: HandlerDependency, extra_dependency: HandlerDependency):
        super().__init__(dependency)

    def handle(self) -> str:
        return self.dependencyName()
'''

        expect:
        assertGeneratedSourceContains(pythonCode, '''
public PythonConstructorBackedHandler(HandlerDependency dependency, HandlerDependency extra_dependency) {
    super(dependency);
''')
    }
}
