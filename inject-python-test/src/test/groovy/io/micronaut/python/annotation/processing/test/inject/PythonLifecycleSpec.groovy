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

import io.micronaut.inject.DisposableBeanDefinition
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec
import spock.lang.PendingFeature

class PythonLifecycleSpec extends AbstractPythonTypeElementSpec {

    void "test post construct method alone does not create bean definition"() {
        when:
        def definition = buildBeanDefinition("python", "LifecycleService", '''
from jakarta.annotation import PostConstruct

class LifecycleService:
    @PostConstruct
    def initialize(self):
        pass
''')

        then:
        definition == null
    }

    void "test inject constructor without lifecycle method creates bean definition"() {
        when:
        def definition = buildBeanDefinition("python", "LifecycleService", '''
from jakarta.inject import Inject

class LifecycleService:
    @Inject
    def __init__(self):
        pass
''')

        then:
        definition != null
        definition.postConstructMethods.empty
        definition.preDestroyMethods.empty
    }

    void "test post construct and pre destroy metadata on inject constructor bean"() {
        when:
        def definition = buildBeanDefinition("python", "LifecycleService", '''
from jakarta.annotation import PostConstruct, PreDestroy
from jakarta.inject import Inject

class LifecycleService:
    @Inject
    def __init__(self):
        pass

    @PostConstruct
    def initialize(self):
        pass

    @PreDestroy
    def close(self):
        pass
''')

        then:
        definition != null
        definition.postConstructMethods.size() == 1
        definition.preDestroyMethods.size() == 1
        definition instanceof DisposableBeanDefinition
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

    void "test lifecycle hooks on class-level around advised bean"() {
        given:
        def pythonCode = '''
from micronaut.aop import Around, InterceptorBean, MethodInvocationContext
from micronaut.context.annotation import Executable
from jakarta.annotation import PostConstruct, PreDestroy
from jakarta.inject import Singleton
import java

@Around
def Mutating(target):
    return target

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@InterceptorBean(Mutating)
@Singleton
class MutatingInterceptor(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext):
        return context.proceed()

@Mutating
@Singleton
class LifecycleService:
    def __init__(self):
        self.count = 0

    def some_method(self) -> str:
        return "good"

    @Executable
    def get_count(self) -> int:
        return self.count

    @PostConstruct
    def created(self):
        self.count += 1

    @PreDestroy
    def destroyed(self):
        self.count -= 1
'''

        when:
        def context = buildContext(pythonCode)
        def definition = getBeanDefinition(context, "python.LifecycleService")
        def service = getBean(context, "python.LifecycleService")

        then:
        !definition.isAbstract()
        definition.postConstructMethods.size() == 1
        definition.preDestroyMethods.size() == 1
        service.some_method() == "good"
        service.get_count() == 1

        when:
        context.destroyBean(service)

        then:
        service.get_count() == 0

        cleanup:
        context?.close()
    }

    void "test lifecycle hooks on method-level around advised bean"() {
        given:
        def pythonCode = '''
from micronaut.aop import Around, InterceptorBean, MethodInvocationContext
from micronaut.context.annotation import Executable
from jakarta.annotation import PostConstruct, PreDestroy
from jakarta.inject import Singleton
import java

@Around
def Mutating(target):
    return target

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@InterceptorBean(Mutating)
@Singleton
class MutatingInterceptor(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext):
        return context.proceed()

@Singleton
class LifecycleService:
    def __init__(self):
        self.count = 0

    @Mutating
    def some_method(self) -> str:
        return "good"

    @Executable
    def get_count(self) -> int:
        return self.count

    @PostConstruct
    def created(self):
        self.count += 1

    @PreDestroy
    def destroyed(self):
        self.count -= 1
'''

        when:
        def context = buildContext(pythonCode)
        def definition = getBeanDefinition(context, "python.LifecycleService")
        def service = getBean(context, "python.LifecycleService")

        then:
        !definition.isAbstract()
        definition.postConstructMethods.size() == 1
        definition.preDestroyMethods.size() == 1
        service.some_method() == "good"
        service.get_count() == 1

        when:
        context.destroyBean(service)

        then:
        service.get_count() == 0

        cleanup:
        context?.close()
    }

    void "test post construct interceptor bindings on around advised bean and factory method"() {
        given:
        def pythonCode = '''
from micronaut.aop import Around, InterceptorBean, InterceptorBinding, MethodInvocationContext
from micronaut.context.annotation import Executable, Factory
from jakarta.annotation import PostConstruct
from jakarta.inject import Singleton
import java

InterceptorKind = java.type("io.micronaut.aop.InterceptorKind")
MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@Around
@InterceptorBinding(kind=InterceptorKind.POST_CONSTRUCT)
def LifecycleBinding(target):
    return target

@InterceptorBean(LifecycleBinding)
@Singleton
class AroundInterceptor(MethodInterceptor):
    count: int = 0

    def intercept(self, context: MethodInvocationContext):
        self.count += 1
        return context.proceed()

@InterceptorBinding(value=LifecycleBinding, kind=InterceptorKind.POST_CONSTRUCT)
@Singleton
class PostConstructInterceptor(MethodInterceptor):
    count: int = 0

    def intercept(self, context: MethodInvocationContext):
        self.count += 1
        return context.proceed()

@LifecycleBinding
@Singleton
class LifecycleService:
    def __init__(self):
        self.count = 0

    @Executable
    def call(self) -> str:
        return "ok"

    @Executable
    def get_count(self) -> int:
        return self.count

    @PostConstruct
    def init(self):
        self.count += 1

class Product:
    @Executable
    def call(self) -> str:
        return "product"

@Factory
class ProductFactory:
    @LifecycleBinding
    @Singleton
    def product(self) -> Product:
        return Product()
'''

        when:
        def context = buildContext(pythonCode)
        def aroundInterceptor = getBean(context, "python.AroundInterceptor")
        def postConstructInterceptor = getBean(context, "python.PostConstructInterceptor")
        def service = getBean(context, "python.LifecycleService")

        then:
        service.get_count() == 1
        aroundInterceptor.count == 2
        postConstructInterceptor.count == 1

        when:
        service.call()

        then:
        aroundInterceptor.count == 3
        postConstructInterceptor.count == 1

        when:
        def product = getBean(context, "python.Product")

        then:
        product.call() == "product"
        aroundInterceptor.count == 5
        postConstructInterceptor.count == 2

        cleanup:
        context?.close()
    }

    void "test lifecycle interceptor bindings without around advice"() {
        given:
        def pythonCode = '''
from micronaut.aop import InterceptorBinding, MethodInvocationContext
from micronaut.context.annotation import Executable
from jakarta.annotation import PostConstruct
from jakarta.inject import Singleton
import java

InterceptorKind = java.type("io.micronaut.aop.InterceptorKind")
MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@InterceptorBinding(kind=InterceptorKind.PRE_DESTROY)
@InterceptorBinding(kind=InterceptorKind.POST_CONSTRUCT)
def LifecycleBinding(target):
    return target

@InterceptorBinding(value=LifecycleBinding, kind=InterceptorKind.POST_CONSTRUCT)
@Singleton
class PostConstructInterceptor(MethodInterceptor):
    count: int = 0

    def intercept(self, context: MethodInvocationContext):
        self.count += 1
        return context.proceed()

@InterceptorBinding(value=LifecycleBinding, kind=InterceptorKind.PRE_DESTROY)
@Singleton
class PreDestroyInterceptor(MethodInterceptor):
    count: int = 0

    def intercept(self, context: MethodInvocationContext):
        self.count += 1
        return context.proceed()

@LifecycleBinding
@Singleton
class LifecycleService:
    def __init__(self):
        self.initialized = False

    @PostConstruct
    def init(self):
        self.initialized = True

    @Executable
    def is_initialized(self) -> bool:
        return self.initialized

    def call(self) -> str:
        return "ok"
'''

        when:
        def context = buildContext(pythonCode)
        def postConstructInterceptor = getBean(context, "python.PostConstructInterceptor")
        def preDestroyInterceptor = getBean(context, "python.PreDestroyInterceptor")
        def service = getBean(context, "python.LifecycleService")

        then:
        service.is_initialized()
        postConstructInterceptor.count == 1
        preDestroyInterceptor.count == 0

        when:
        context.destroyBean(service)

        then:
        preDestroyInterceptor.count == 1

        cleanup:
        context?.close()
    }

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0059")
    void "test pre destroy interceptor binding on around advised bean"() {
        given:
        def pythonCode = '''
from micronaut.aop import Around, InterceptorBinding, MethodInvocationContext
from jakarta.inject import Singleton
import java

InterceptorKind = java.type("io.micronaut.aop.InterceptorKind")
MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@Around
@InterceptorBinding(kind=InterceptorKind.PRE_DESTROY)
def LifecycleBinding(target):
    return target

@InterceptorBinding(value=LifecycleBinding, kind=InterceptorKind.PRE_DESTROY)
@Singleton
class PreDestroyInterceptor(MethodInterceptor):
    count: int = 0

    def intercept(self, context: MethodInvocationContext):
        self.count += 1
        return context.proceed()

@LifecycleBinding
@Singleton
class LifecycleService:
    def call(self) -> str:
        return "ok"
'''

        when:
        def context = buildContext(pythonCode)
        def preDestroyInterceptor = getBean(context, "python.PreDestroyInterceptor")
        def service = getBean(context, "python.LifecycleService")
        context.destroyBean(service)

        then:
        preDestroyInterceptor.count == 1

        cleanup:
        context?.close()
    }
}
