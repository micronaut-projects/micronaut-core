package io.micronaut.python.compiler

class ExceptionSubclassSourceSpec extends GeneratedJavaSourceSpec {

    void "the Value constructor of a Python exception subclass forwards the super constructor arguments"() {
        given:
        def pythonCode = '''
from micronaut.python.annotation.processing.test import AbstractProblemLike

TYPE = "https://example.org/not-found"

class TaskNotFoundProblem(AbstractProblemLike):
    def __init__(self, task_id: int):
        super().__init__(TYPE, "Not found", 404, f"Task {task_id} not found")
'''

        expect:
        assertGeneratedSourceContains(pythonCode, '''
public TaskNotFoundProblem(Value value) {
    super(PythonExceptions.argumentAsString(value, 0), PythonExceptions.argumentAsString(value, 1), PythonExceptions.argument(value, 2).asInt(), PythonExceptions.argumentAsString(value, 3));
    this.graalpyInternalValue = value;
    PythonExceptions.attachCause(this, value);
  }
''')
        assertGeneratedSourceContains(pythonCode, '''
public TaskNotFoundProblem(int task_id) {
    this(PythonContextRuntime.newInstance(TaskNotFoundProblem.__PYTHON_CLASS_REFERENCE, (Object) task_id));
  }
''')
    }

    void "a Python exception subclass with a message constructor forwards the message"() {
        given:
        def pythonCode = '''
from java.lang import RuntimeException

class NotEligibleException(RuntimeException):
    def __init__(self, message: str):
        super().__init__(message)
'''

        expect:
        assertGeneratedSourceContains(pythonCode, '''
public NotEligibleException(Value value) {
    super(PythonExceptions.argumentAsString(value, 0));
''')
    }

    void "a Python exception subclass without a constructor takes its message from the Python exception"() {
        given:
        def pythonCode = '''
from java.lang import RuntimeException

class OutOfStockException(RuntimeException):
    pass
'''

        expect:
        assertGeneratedSourceContains(pythonCode, '''
public OutOfStockException(Value value) {
    super(PythonExceptions.message(value));
''')
        assertGeneratedSourceContains(pythonCode, '''
public OutOfStockException() {
    this(PythonContextRuntime.newInstance(OutOfStockException.__PYTHON_CLASS_REFERENCE));
  }
''')
    }

    void "a super constructor call that matches no Java constructor fails compilation"() {
        given:
        def pythonCode = '''
from micronaut.python.annotation.processing.test import AbstractProblemLike

class BrokenProblem(AbstractProblemLike):
    def __init__(self, title: str):
        super().__init__(title, 404)
'''

        expect:
        assertCompilationFailsContaining(pythonCode, 'No constructor of the Java exception class [io.micronaut.python.annotation.processing.test.AbstractProblemLike] accepts the arguments of the super constructor call [super().__init__(title, 404)] of Python class [python.BrokenProblem]')
    }

    void "a super constructor call with keyword arguments fails compilation"() {
        given:
        def pythonCode = '''
from java.lang import RuntimeException

class KeywordException(RuntimeException):
    def __init__(self, message: str):
        super().__init__(message=message)
'''

        expect:
        assertCompilationFailsContaining(pythonCode, 'The super constructor call [super().__init__(message=message)] of Python class [python.KeywordException] passes [message=message]')
    }
}
