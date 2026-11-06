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
package io.micronaut.python.annotation.processing.test

import io.micronaut.context.ApplicationContext
import io.micronaut.python.annotation.processing.test.javabases.CheckedBase
import org.graalvm.polyglot.PolyglotException

import java.util.concurrent.TimeoutException

/**
 * Python classes overriding Java methods that declare checked exceptions: the generated override
 * and the base method dispatcher declare the same exceptions, and a checked exception raised by
 * the Python override surfaces to Java as that exception.
 */
class JavaBaseCheckedExceptionSpec extends AbstractPythonTypeElementSpec {

    void "an override of a Java method declaring checked exceptions compiles and rethrows them"() {
        given:
        ApplicationContext ctx = buildContext('''
from java.io import IOException
from java.util.concurrent import TimeoutException
from jakarta.inject import Singleton
from micronaut.python.annotation.processing.test.javabases import CheckedBase


class Unreadable(IOException):
    def __init__(self, key: str):
        super().__init__("unreadable " + key)
        self.key = key


@Singleton
class PythonLoader(CheckedBase):
    def initialize(self, channel: str, name: str) -> None:
        super().initialize(channel, name)
        self.mark("python:" + name)

    def load(self, key: str) -> str:
        if key == "io":
            raise IOException("boom " + key)
        if key == "timeout":
            raise TimeoutException("slow " + key)
        if key == "unreadable":
            raise Unreadable(key)
        if key == "value":
            raise ValueError("bad " + key)
        return "loaded " + key
''', true)
        CheckedBase loader = ctx.getBean(CheckedBase)
        Class<?> generatedClass = ctx.classLoader.loadClass('python.PythonLoader')

        expect: 'the generated overrides declare the checked exceptions of the Java methods'
        generatedClass.getMethod('initialize', String, String).exceptionTypes.toList() == [IOException]
        generatedClass.getMethod('load', String).exceptionTypes.toList() == [IOException, TimeoutException]

        when: 'the Python override delegates to the base method through super()'
        loader.initialize('c', 'n')

        then:
        loader.initialized() == ['c:n', 'python:n']

        when: 'the base method called through super() throws its checked exception'
        loader.initialize(null, 'x')

        then: 'it reaches the Java caller as is'
        def io = thrown(IOException)
        io.message == 'no channel for x'

        when: 'the Python override returns normally'
        def loaded = loader.load('ok')

        then:
        loaded == 'loaded ok'

        and: 'a Java exception raised in Python reaches the catch of the Java base'
        loader.run('io') == 'io:boom io'
        loader.run('timeout') == 'timeout:slow timeout'

        and: 'a Python exception class extending the checked exception is the generated exception'
        loader.run('unreadable') == 'io:unreadable unreadable'

        when:
        loader.load('io')

        then:
        def e = thrown(IOException)
        e.message == 'boom io'

        when:
        loader.load('unreadable')

        then:
        def unreadable = thrown(IOException)
        unreadable.class.name == 'python.Unreadable'
        unreadable.message == 'unreadable unreadable'

        when: 'a Python exception that is not a declared one is not unwrapped'
        loader.load('value')

        then:
        def polyglot = thrown(PolyglotException)
        polyglot.message.contains('bad value')

        cleanup:
        ctx?.close()
    }
}
