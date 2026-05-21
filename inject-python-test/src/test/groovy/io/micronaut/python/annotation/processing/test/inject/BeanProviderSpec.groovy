package io.micronaut.python.annotation.processing.test.inject

import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.context.exceptions.NonUniqueBeanException
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

class BeanProviderSpec extends AbstractPythonTypeElementSpec {

    boolean allowEmptyProviders = false

    void "test bean definition reference uses bean type for jakarta provider implementation"() {
        given:
        def reference = buildBeanDefinitionReference("python", "Test", '''
from jakarta.inject import Singleton

import java

Provider = java.type("jakarta.inject.Provider")

class Foo:
    pass

@Singleton
class Test(Provider[Foo]):
    def get(self) -> Foo:
        return Foo()
''')

        expect:
        reference != null
        reference.beanType.name == "python.Test"
    }

    void "test bean definition reference uses bean type for bean provider implementation"() {
        given:
        def reference = buildBeanDefinitionReference("python", "Test", '''
from jakarta.inject import Singleton

import java

BeanProvider = java.type("io.micronaut.context.BeanProvider")

class Foo:
    pass

@Singleton
class Test(BeanProvider[Foo]):
    def get(self) -> Foo:
        return Foo()
''')

        expect:
        reference != null
        reference.beanType.name == "python.Test"
    }

    void "test bean provider find and get use injection point qualifier"() {
        given:
        def context = buildContext('''
from typing import Annotated
from jakarta.inject import Singleton, Qualifier
from micronaut.context.annotation import Executable

import java

BeanProvider = java.type("io.micronaut.context.BeanProvider")

@Qualifier
def OneQualifier(func):
    return func

@Qualifier
def TwoQualifier(func):
    return func

class BeanNumber:
    @Executable
    def name(self) -> str:
        return "base"

@Singleton
@OneQualifier
class BeanOne(BeanNumber):
    @Executable
    def name(self) -> str:
        return "one"

@Singleton
@OneQualifier
@TwoQualifier
class BeanOneTwo(BeanNumber):
    @Executable
    def name(self) -> str:
        return "one-two"

@Singleton
class Test:
    def __init__(
        self,
        provider: Annotated[BeanProvider[BeanNumber], OneQualifier],
        provider_two: Annotated[BeanProvider[BeanNumber], TwoQualifier]
    ):
        self.provider = provider
        self.provider_two = provider_two

    @Executable
    def provider_present(self) -> bool:
        return self.provider.isPresent()

    @Executable
    def provider_two_find_name(self) -> str:
        return self.provider_two.find(None).get().name()

    @Executable
    def provider_two_get_name(self) -> str:
        return self.provider_two.get(None).name()

    @Executable
    def provider_find_without_qualifier(self):
        return self.provider.find(None).get()

    @Executable
    def provider_get_without_qualifier(self):
        return self.provider.get(None)
''')

        when:
        def bean = getBean(context, "python.Test")

        then:
        bean.provider_present()
        bean.provider_two_find_name() == "one-two"
        bean.provider_two_get_name() == "one-two"

        when:
        bean.provider_find_without_qualifier()

        then:
        thrown(NonUniqueBeanException)

        when:
        bean.provider_get_without_qualifier()

        then:
        thrown(NonUniqueBeanException)

        cleanup:
        context?.close()
    }

    void "test inject missing bean provider"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable

import java

BeanProvider = java.type("io.micronaut.context.BeanProvider")

@Singleton
class Foo:
    @Executable
    def name(self) -> str:
        return "foo"

class Bar:
    pass

@Singleton
class Test:
    def __init__(self, provider: BeanProvider[Foo], bar_provider: BeanProvider[Bar]):
        self.provider = provider
        self.bar_provider = bar_provider

    @Executable
    def provider_present(self) -> bool:
        return self.provider.isPresent()

    @Executable
    def bar_provider_present(self) -> bool:
        return self.bar_provider.isPresent()

    @Executable
    def provider_name(self) -> str:
        return self.provider.get().name()

    @Executable
    def provider_definition_name(self) -> str:
        return self.provider.getDefinition().getBeanType().getName()

    @Executable
    def bar_provider_get(self):
        return self.bar_provider.get()

    @Executable
    def bar_provider_definition(self):
        return self.bar_provider.getDefinition()
''')

        when:
        def bean = getBean(context, "python.Test")

        then:
        bean.provider_present()
        !bean.bar_provider_present()
        bean.provider_name() == "foo"
        bean.provider_definition_name() == "python.Foo"

        when:
        bean.bar_provider_get()

        then:
        thrown(NoSuchBeanException)

        when:
        bean.bar_provider_definition()

        then:
        thrown(NoSuchBeanException)

        cleanup:
        context?.close()
    }

    void "test jakarta provider triggers containsBean during injection"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable

import java

Provider = java.type("jakarta.inject.Provider")

class BeanNumber:
    @Executable
    def name(self) -> str:
        return "base"

@Singleton
class BeanNumberImpl(BeanNumber):
    @Executable
    def name(self) -> str:
        return "impl"

@Singleton
class Test:
    def __init__(self, provider: Provider[BeanNumber]):
        self.provider = provider

    @Executable
    def provider_name(self) -> str:
        return self.provider.get().name()
''')
        def containsBeanCache = containsBeanCache(context)

        when:
        int mapSize = containsBeanCache.size()
        def bean = getBean(context, "python.Test")

        then:
        containsBeanCache.size() == mapSize + 1
        bean.provider_name() == "impl"

        cleanup:
        context?.close()
    }

    void "test bean provider defers containsBean until queried"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable

import java

BeanProvider = java.type("io.micronaut.context.BeanProvider")

class BeanNumber:
    pass

@Singleton
class Test:
    def __init__(self, provider: BeanProvider[BeanNumber]):
        self.provider = provider

    @Executable
    def provider_present(self) -> bool:
        return self.provider.isPresent()
''')
        def containsBeanCache = containsBeanCache(context)

        when:
        int mapSize = containsBeanCache.size()
        def bean = getBean(context, "python.Test")

        then:
        containsBeanCache.size() == mapSize

        when:
        def present = bean.provider_present()

        then:
        !present
        containsBeanCache.size() == mapSize + 1

        cleanup:
        context?.close()
    }

    void "test missing jakarta provider does not fail when empty providers are allowed"() {
        given:
        allowEmptyProviders = true
        def context = buildContext(missingJakartaProviderCode())

        when:
        def bean = getBean(context, "python.Test")

        then:
        bean.provider_injected()

        when:
        bean.provider_get()

        then:
        thrown(NoSuchBeanException)

        cleanup:
        context?.close()
    }

    void "test missing jakarta provider fails when empty providers are disabled"() {
        given:
        allowEmptyProviders = false
        def context = buildContext(missingJakartaProviderCode())

        when:
        getBean(context, "python.Test")

        then:
        thrown(BeanInstantiationException)

        cleanup:
        context?.close()
    }

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        contextBuilder.allowEmptyProviders(allowEmptyProviders)
    }

    private static Map containsBeanCache(context) {
        def containsBeanCacheField = context.getClass().superclass.declaredFields.find { it.name == "containsBeanCache" }
        containsBeanCacheField.accessible = true
        containsBeanCacheField.get(context) as Map
    }

    private static String missingJakartaProviderCode() {
        '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable

import java

Provider = java.type("jakarta.inject.Provider")

@Singleton
class Test:
    def __init__(self, string_provider: Provider[str]):
        self.string_provider = string_provider

    @Executable
    def provider_injected(self) -> bool:
        return self.string_provider is not None

    @Executable
    def provider_get(self):
        return self.string_provider.get()
'''
    }
}
