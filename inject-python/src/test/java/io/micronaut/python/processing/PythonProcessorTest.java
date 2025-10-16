package io.micronaut.python.processing;

import org.junit.jupiter.api.Test;

public class PythonProcessorTest {

    @Test
    void testProcess() {
        PythonProcessor pythonProcessor = new PythonProcessor();
        pythonProcessor.process("""
@scope
def singleton(type):
    return type

def scope(func):
    return func

def named(name = ""):
    def decorator_named(func):
        return func
    return decorator_named

@singleton
#@named("myName")
class MyClass:
    ""\"A simple example class""\"
    i = 12345

    def f(self):
        return 'hello world'


""");
    }
}
