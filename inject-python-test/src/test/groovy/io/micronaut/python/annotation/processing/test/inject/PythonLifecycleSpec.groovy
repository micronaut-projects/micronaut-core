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
package io.micronaut.python.annotation.processing.test.inject

import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

class PythonLifecycleSpec extends AbstractPythonTypeElementSpec {
    def setup() {
        ShutdownStateHolder.reset()
    }

    void "test @PostConstruct method is called during bean initialization"() {
        given: "Python code with a singleton bean that has a @PostConstruct method"
        def pythonCode = '''
from jakarta.inject import Singleton
from jakarta.annotation import PostConstruct
from micronaut.context.annotation import Executable

@Singleton
class LifecycleService:
    def __init__(self):
        self.initialized = False
        self.message = "not initialized"

    @PostConstruct
    def initialize(self):
        self.initialized = True
        self.message = "initialized by PostConstruct"

    @Executable
    def get_message(self) -> str:
        return self.message

    @Executable
    def is_initialized(self) -> bool:
        return self.initialized
'''

        when: "Building ApplicationContext and getting the bean"
        def context = buildContext(pythonCode)
        def service = getBean(context, "python.LifecycleService")

        then: "@PostConstruct method should have been called during bean initialization"
        service.is_initialized() == true
        service.get_message() == "initialized by PostConstruct"

        cleanup: "Ensure context is properly closed"
        context?.close()
    }

    void "test @PreDestroy method is called during context shutdown"() {
        given: "Python code with a singleton bean that has both @PostConstruct and @PreDestroy methods"
        def pythonCode = '''
from jakarta.inject import Singleton
from jakarta.annotation import PostConstruct, PreDestroy
from micronaut.context.annotation import Executable
import java
StateHolder = java.type("io.micronaut.python.annotation.processing.test.inject.ShutdownStateHolder")

@Singleton
class LifecycleService:
    def __init__(self):
        self.initialized = False
        self.destroyed = False
        self.lifecycle_events = []

    @PostConstruct
    def initialize(self):
        self.initialized = True
        StateHolder.getEvents().add("post_construct")

    @PreDestroy
    def cleanup(self):
        self.destroyed = True
        StateHolder.setDestroyed(True)
        StateHolder.getEvents().add("pre_destroy")

    @Executable
    def get_lifecycle_events(self) -> list:
        return StateHolder.getEvents()

    @Executable
    def is_initialized(self) -> bool:
        return self.initialized

    @Executable
    def is_destroyed(self) -> bool:

        return self.destroyed
'''

        when: "Building ApplicationContext and getting the bean"
        def context = buildContext(pythonCode)
        def service = getBean(context, "python.LifecycleService")

        then: "@PostConstruct should have been called but @PreDestroy should not yet"
        service.is_initialized() == true
        service.is_destroyed() == false
        service.get_lifecycle_events() == ["post_construct"]

        when: "Closing the context"
        context.close()

        then: "@PreDestroy should have been called during shutdown"
        ShutdownStateHolder.destroyed
        ShutdownStateHolder.events == ["post_construct", "pre_destroy"]
    }

}

class ShutdownStateHolder {
    static boolean destroyed = false
    static List<String> events = []
    static void reset() {
        destroyed = false
        events = []
    }
}
