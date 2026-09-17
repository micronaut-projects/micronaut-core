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
    Value pythonResult = PythonInvocation.invokePythonMethod(this.asPolyglotValue(), "size", new Object[]{});
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

        expect: 'the constructor creates the Python object; the Java base receives the argument of the Python super().__init__ call'
        assertGeneratedSourceContains(pythonCode, '''
public PythonConstructorBackedHandler(HandlerDependency dependency, HandlerDependency extra_dependency) {
    this(PythonContextRuntime.newInstance(PythonConstructorBackedHandler.__PYTHON_CLASS_REFERENCE, (Object) dependency, (Object) extra_dependency));
''')
        assertGeneratedSourceContains(pythonCode, '''
public PythonConstructorBackedHandler(Value value) {
    super((HandlerDependency) PythonConversion.convertValue(PythonJavaBases.argument(value, 0, "(io.micronaut.python.annotation.processing.test.HandlerDependency)"), io.micronaut.python.annotation.processing.test.HandlerDependency.class));
    this.graalpyInternalValue = value;
    PythonJavaBases.bind(value, this);
''')
    }
}
