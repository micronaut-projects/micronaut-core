/*
 * Copyright 2017-2025 original authors
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

class CustomScopeProxySpec extends AbstractPythonTypeElementSpec {

    void "test the scoped proxy of a Python class resolves its target lazily"() {
        given:
        ScopedProxyInitCounter.reset()
        def pythonCode = '''
import java
from micronaut.context.annotation import Executable
from micronaut.runtime.context.scope import Refreshable

Counter = java.type("io.micronaut.python.annotation.processing.test.ScopedProxyInitCounter")

@Refreshable
class Test:
    def __init__(self):
        Counter.increment()

    @Executable
    def test(self) -> str:
        return "good"
'''

        when:
        def context = buildContext(pythonCode)
        def bean = getBean(context, "python.Test")

        then: "creating the scoped proxy did not create the bean of the scope"
        ScopedProxyInitCounter.count() == 0

        when:
        def result = bean.test()

        then: "the proxy resolves the target of the scope when a method is called"
        result == "good"
        ScopedProxyInitCounter.count() == 1

        cleanup:
        context?.close()
    }

    void "test a Python bean of a scoped proxy scope is injected as a lazy proxy"() {
        given:
        ScopedProxyInitCounter.reset()
        def pythonCode = '''
import java
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from micronaut.runtime.context.scope import Refreshable

Counter = java.type("io.micronaut.python.annotation.processing.test.ScopedProxyInitCounter")

@Refreshable
class Metadata:
    def __init__(self):
        Counter.increment()

    @Executable
    def value(self) -> str:
        return "good"

@Singleton
class Listener:
    def __init__(self, metadata: Metadata):
        self.metadata = metadata

    @Executable
    def value(self) -> str:
        return self.metadata.value()
'''

        when:
        def context = buildContext(pythonCode)
        def listener = getBean(context, "python.Listener")

        then: "the injected bean is the proxy, so the scoped bean was not created with the listener"
        ScopedProxyInitCounter.count() == 0

        when:
        def result = listener.value()

        then:
        result == "good"
        ScopedProxyInitCounter.count() == 1

        cleanup:
        context?.close()
    }
}
