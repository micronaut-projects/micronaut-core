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

@Singleton
class LifecycleService:
    def __init__(self):
        self.initialized = False
        self.destroyed = False
        self.lifecycle_events = []

    @PostConstruct
    def initialize(self):
        self.initialized = True
        self.lifecycle_events.append("post_construct")

    @PreDestroy
    def cleanup(self):
        self.destroyed = True
        self.lifecycle_events.append("pre_destroy")

    @Executable
    def get_lifecycle_events(self) -> list:
        return self.lifecycle_events

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
        context.destroyBean(service)

        then: "@PreDestroy should have been called during shutdown"
        service.is_destroyed() == true
        service.get_lifecycle_events() == ["post_construct", "pre_destroy"]

        cleanup:
        context.close()
    }

    void "test multiple beans with lifecycle methods"() {
        given: "Python code with multiple singleton beans having lifecycle methods"
        def pythonCode = '''
from jakarta.inject import Singleton
from jakarta.annotation import PostConstruct, PreDestroy
from micronaut.context.annotation import Executable

@Singleton
class ServiceA:
    def __init__(self):
        self.name = "ServiceA"
        self.events = []

    @PostConstruct
    def init_a(self):
        self.events.append("A_init")

    @PreDestroy
    def destroy_a(self):
        self.events.append("A_destroy")

    @Executable
    def get_events(self) -> list:
        return self.events

@Singleton
class ServiceB:
    def __init__(self):
        self.name = "ServiceB"
        self.events = []

    @PostConstruct
    def init_b(self):
        self.events.append("B_init")

    @PreDestroy
    def destroy_b(self):
        self.events.append("B_destroy")

    @Executable
    def get_events(self) -> list:
        return self.events
'''

        when: "Building ApplicationContext and getting the beans"
        def context = buildContext(pythonCode)
        def serviceA = getBean(context, "python.ServiceA")
        def serviceB = getBean(context, "python.ServiceB")

        then: "Both services should have their @PostConstruct methods called"
        serviceA.get_events() == ["A_init"]
        serviceB.get_events() == ["B_init"]

        when: "Closing the context"
        context.destroyBean(serviceA)
        context.destroyBean(serviceB)

        then: "Both services should have their @PreDestroy methods called"
        serviceA.get_events() == ["A_init", "A_destroy"]
        serviceB.get_events() == ["B_init", "B_destroy"]

        cleanup:
        context.close()
    }

    void "test lifecycle methods with dependency injection"() {
        given: "Python code with beans that have dependencies and lifecycle methods"
        def pythonCode = '''
from jakarta.inject import Singleton, Inject
from jakarta.annotation import PostConstruct, PreDestroy
from typing import Annotated
from micronaut.context.annotation import Executable

@Singleton
class DependencyService:
    def __init__(self):
        self.events = []

    @PostConstruct
    def init_dependency(self):
        self.events.append("dependency_init")

    @PreDestroy
    def destroy_dependency(self):
        self.events.append("dependency_destroy")

    @Executable
    def get_events(self) -> list:
        return self.events

@Singleton
class MainService:
    def __init__(self):
        self.events = []

    dependency: Annotated[DependencyService, Inject] = None

    @PostConstruct
    def init_main(self):
        self.events.append("main_init")
        if self.dependency is not None:
            self.events.append("main_has_dependency")

    @PreDestroy
    def destroy_main(self):
        self.events.append("main_destroy")

    @Executable
    def get_events(self) -> list:
        return self.events

    @Executable
    def get_dependency_events(self) -> list:
        if self.dependency is None:
            return []
        return self.dependency.get_events()
'''

        when: "Building ApplicationContext and getting the main service"
        def context = buildContext(pythonCode)
        def mainService = getBean(context, "python.MainService")
        def depService = getBean(context, "python.DependencyService")

        then: "Both services should be initialized in dependency order"
        mainService.get_events() == ["main_init", "main_has_dependency"]
        mainService.get_dependency_events() == ["dependency_init"]

        when: "Closing the context"
        context.destroyBean(mainService)
        context.destroyBean(depService)

        then: "Both services should be destroyed"
        mainService.get_events() == ["main_init", "main_has_dependency", "main_destroy"]
        mainService.get_dependency_events() == ["dependency_init", "dependency_destroy"]

        cleanup:
        context.close()
    }
}
