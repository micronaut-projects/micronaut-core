/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.python.compiler

import spock.lang.Specification

/**
 * The Java source generated for Python classes extending Java classes, and the compile errors of
 * super constructor calls that cannot be resolved.
 */
class JavaBaseClassSourceSpec extends Specification {

    def "the generated class calls the Java super constructor with the recorded super arguments and binds the Python object"() {
        given:
        def tempDir = File.createTempDir("python-java-base", "")
        def compiler = PyronautCompiler.builder()
            .pythonCode('''
from micronaut.python.annotation.processing.test.javabases import GreetingBase


class NamedGreeter(GreetingBase):
    def __init__(self, name: str, count: int):
        super().__init__(name.upper(), count + 1)

    def greet(self) -> str:
        return "named:" + super().greet()
''')
            .targetDir(tempDir)
            .build()

        when:
        compiler.compile()
        def javaCode = new File(tempDir, "python/NamedGreeter.java").text

        then: 'the generated class extends the Java base and exposes its members to the Python object'
        javaCode.contains('public class NamedGreeter extends GreetingBase implements ValueCoercible, ValueCoercible.JavaBaseMembers')

        and: 'the Value constructor marks the object as under construction, calls the resolved (String, int) constructor with the recorded arguments and binds'
        javaCode.contains('this(value, PythonJavaBases.constructing(value));')
        javaCode.contains('super(PythonConversion.isNone(PythonJavaBases.argument(value, 0, "(java.lang.String, int)")) ? null : PythonJavaBases.argument(value, 0, "(java.lang.String, int)").asString(), PythonJavaBases.argument(value, 1, "(java.lang.String, int)").asInt());')
        javaCode.contains('construction.finished();')
        javaCode.contains('PythonJavaBases.bind(value, this)')

        and: 'a bridge called by the super constructor reaches the object under construction'
        javaCode.contains('PythonJavaBases.underConstruction()')

        and: 'the constructor of the Python parameters creates the Python object and delegates to it'
        javaCode.contains('public NamedGreeter(String name, int count) {\n    this(PythonContextRuntime.newInstance(NamedGreeter.__PYTHON_CLASS_REFERENCE, (Object) name, (Object) count));')

        and: 'a Python object already bound to a Java instance keeps it'
        javaCode.contains('NamedGreeter bound = PythonJavaBases.bound(arg1, NamedGreeter.class);')

        and: 'the Python override is bridged and the base implementation reachable non-virtually'
        javaCode.contains('public String greet() {')
        javaCode.contains('public Object micronautInvokeJavaBaseMethod(String name, List<Value> arguments) {')
        javaCode.contains('if ("greet".equals(name)) {')
        javaCode.contains('return super.greet();')
        javaCode.contains('if (arguments.size() == 1 && ValueCoercibles.matchesArgument(arguments.get(0), java.lang.String.class)) {\n        return super.join(PythonConversion.isNone(arguments.get(0)) ? null : arguments.get(0).asString());')
        javaCode.contains('if (arguments.size() == 1 && ValueCoercibles.matchesArgument(arguments.get(0), int.class)) {\n        return super.join(arguments.get(0).asInt());')
        javaCode.contains('if (arguments.size() == 2) {\n        return super.join(PythonConversion.isNone(arguments.get(0)) ? null : arguments.get(0).asString(), arguments.get(1).asInt());')
        javaCode.contains('return super.protectedHook();')
        javaCode.contains('throw PythonJavaBases.noSuchMethod("io.micronaut.python.annotation.processing.test.javabases.GreetingBase", name, arguments);')
        !javaCode.contains('super.staticHelper(')

        cleanup:
        tempDir.deleteDir()
    }

    def "a super constructor call that no Java constructor accepts is a compile error"() {
        when:
        PyronautCompiler.builder()
            .pythonCode('''
from micronaut.python.annotation.processing.test.javabases import GreetingBase


class Wrong(GreetingBase):
    def __init__(self):
        super().__init__("only-a-name")
''')
            .build()
            .buildClassLoader()

        then:
        def e = thrown(RuntimeException)
        e.message.contains('No constructor of the Java class [io.micronaut.python.annotation.processing.test.javabases.GreetingBase] accepts the arguments of the super constructor call [super().__init__(\'only-a-name\')] of Python class [Wrong]')
        e.message.contains('(java.lang.String, int)')
    }

    def "a missing super constructor call needs a no-argument constructor of the base"() {
        when:
        PyronautCompiler.builder()
            .pythonCode('''
from micronaut.python.annotation.processing.test.javabases import GreetingBase


class NoSuperCall(GreetingBase):
    def __init__(self):
        self.value = 1
''')
            .build()
            .buildClassLoader()

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Python class [NoSuperCall] extends the Java class [io.micronaut.python.annotation.processing.test.javabases.GreetingBase], which has no no-argument constructor')
    }

    def "super constructor calls that disagree are a compile error, calls in nested scopes are not the constructor's"() {
        when: 'two branches call the super constructor differently'
        PyronautCompiler.builder()
            .pythonCode('''
from micronaut.python.annotation.processing.test.javabases import GreetingBase


class Cond(GreetingBase):
    def __init__(self, flag: bool):
        if flag:
            super().__init__("yes", 1)
        else:
            super().__init__("no")
''')
            .build()
            .buildClassLoader()

        then:
        def e = thrown(RuntimeException)
        e.message.contains('The __init__ method of Python class [Cond] calls super().__init__() in more than one way [super().__init__(\'yes\', 1)] and [super().__init__(\'no\')]')

        when: 'the same call on both branches, and a nested function calling another super constructor'
        def tempDir = File.createTempDir("python-java-base", "")
        def compiler = PyronautCompiler.builder()
            .pythonCode('''
from micronaut.python.annotation.processing.test.javabases import GreetingBase


class Agreeing(GreetingBase):
    def __init__(self, flag: bool):
        if flag:
            super().__init__("yes", 1)
        else:
            super().__init__("yes", 1)

        class Helper:
            def __init__(self):
                super().__init__()

        self.helper = Helper()
''')
            .targetDir(tempDir)
            .build()
        compiler.compile()
        def javaCode = new File(tempDir, "python/Agreeing.java").text

        then: 'the (String, int) constructor of the base is called'
        javaCode.contains('"(java.lang.String, int)"')

        cleanup:
        tempDir?.deleteDir()
    }

    def "keyword arguments of the super constructor call are rejected"() {
        when:
        PyronautCompiler.builder()
            .pythonCode('''
from micronaut.python.annotation.processing.test.javabases import GreetingBase


class Keywords(GreetingBase):
    def __init__(self):
        super().__init__("name", count=2)
''')
            .build()
            .buildClassLoader()

        then:
        def e = thrown(RuntimeException)
        e.message.contains('must pass positional arguments only')
    }
}
