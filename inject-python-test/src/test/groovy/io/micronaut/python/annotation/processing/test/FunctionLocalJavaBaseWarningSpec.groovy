package io.micronaut.python.annotation.processing.test

import io.micronaut.python.compiler.PyronautCompiler

/**
 * A class defined inside a function that extends a Java class (or implements a Java interface
 * GraalPy adapts) has no generated Java class: GraalPy implements it with a host adapter, which
 * only a JVM supports. A native executable fails with "Java Class can be extended only in JVM
 * mode" when the class statement runs (micronaut-projects/pyronaut#125), so the compilation warns.
 *
 * <p>It is a warning rather than an error because the class works on a JVM.
 */
class FunctionLocalJavaBaseWarningSpec extends AbstractPythonTypeElementSpec {

    private String warningsFrom(String python) {
        // the compiler prints the processor's notes and warnings to stderr
        def captured = new ByteArrayOutputStream()
        def previous = System.err
        System.setErr(new PrintStream(captured, true))
        try {
            PyronautCompiler.builder()
                .pythonCode(python)
                .build()
                .buildClassLoader()
        } finally {
            System.setErr(previous)
        }
        return captured.toString()
    }

    void "a class defined inside a function extending a Java class warns"() {
        when:
        def reported = warningsFrom('''
from java.util import TimerTask

def create_task():
    class LocalTask(TimerTask):
        def run(self):
            pass
    return LocalTask()
''')

        then:
        reported.contains("Python class [LocalTask] (line 5) is defined inside a function and extends the Java class [java.util.TimerTask]")
        reported.contains("Java Class can be extended only in JVM mode")
        reported.contains("Define the class at module level")
    }

    void "a class at module level extending a Java class does not warn"() {
        when:
        def reported = warningsFrom('''
from java.util import TimerTask
from jakarta.inject import Singleton

@Singleton
class CountingTask(TimerTask):
    def run(self):
        pass
''')

        then:
        !reported.contains("is defined inside a function")
    }
}
