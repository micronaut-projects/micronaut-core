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
package io.micronaut.python.annotation.processing.test.inject.factory.beanmethod

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Prototype
import io.micronaut.context.exceptions.BeanContextException
import io.micronaut.context.exceptions.NonUniqueBeanException
import io.micronaut.context.python.ContextHolder
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.type.Argument
import io.micronaut.core.type.TypeInformation
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec
import io.micronaut.python.annotation.processing.test.client.ExecutableClient
import io.micronaut.python.compiler.InMemoryBeanDefinitionsProvider
import io.micronaut.python.compiler.PyronautCompiler
import jakarta.inject.Singleton
import reactor.core.publisher.Flux
import spock.lang.PendingFeature
import spock.lang.Unroll

import java.time.DayOfWeek
import java.util.function.Function

class FactoryBeanMethodSpec extends AbstractPythonTypeElementSpec {
    void "test factory bean with preDestroy"() {
        given:
        def context = buildContext('''\
from micronaut.context.annotation import Factory, Bean, Prototype
from jakarta.inject import Singleton

class Bar1:
    stopped : bool = False

    def close(self):
        self.stopped = True
        pass
    pass

@Factory
class TestFactory:

    @Bean(preDestroy="close")
    @Singleton
    def bar(self) -> Bar1:
        return Bar1()


''')

        when:
        def bar1BeanDefinition = context.getBeanDefinitions(context.classLoader.loadClass('python.Bar1'))
                .find { it.getDeclaringType().get().simpleName.contains("TestFactory") }


        def wrapper = getBean(context, 'python.Bar1')
        def bar1 = wrapper.asPolyglotValue()

        then:
        bar1 != null
        bar1.getMember('stopped').asBoolean() == false

        when:
        context.destroyBean(wrapper)

        then:
        bar1.getMember('stopped').asBoolean() == true

        cleanup:
        context.close()
    }

    void "test factory bean with inherited preDestroy method"() {
        given:
        def context = buildContext('''\
from micronaut.context.annotation import Factory, Bean
from jakarta.inject import Singleton

class AbstractBar:
    stopped: bool = False

    def close(self):
        self.stopped = True

class Bar1(AbstractBar):
    pass

@Factory
class TestFactory:

    @Bean(preDestroy="close")
    @Singleton
    def bar(self) -> Bar1:
        return Bar1()
''')

        when:
        def wrapper = getBean(context, 'python.Bar1')
        def bar1 = wrapper.asPolyglotValue()

        then:
        bar1.getMember('stopped').asBoolean() == false

        when:
        context.destroyBean(wrapper)

        then:
        bar1.getMember('stopped').asBoolean() == true

        cleanup:
        context.close()
    }

    void "test a factory bean with attribute"() {
        given:
        def context = buildContext('''\
from micronaut.context.annotation import Factory, Bean, Prototype
from typing import Annotated

class Bar1:
    pass

@Factory
class TestFactory:
    bar : Annotated[Bar1, Bean, Prototype] = Bar1()


''')

        when:
        def bar1BeanDefinition = context.getBeanDefinitions(context.classLoader.loadClass('python.Bar1'))
                .find { it.getDeclaringType().get().simpleName.contains("TestFactory") }

        def bar1 = getBean(context, 'python.Bar1')

        then:
        bar1BeanDefinition.getBeanDescription(TypeInformation.TypeFormat.SHORTENED) == '@i.m.c.a.Prototype p.Bar1 p.TestFactory.getBar()'
        bar1 != null
        bar1BeanDefinition.getScope().get() == Prototype.class

        cleanup:
        context?.close()
    }

    void "test a factory bean with method"() {
        given:
        def context = buildContext('''\
from micronaut.context.annotation import Factory, Bean, Prototype

class Bar1:
    pass

@Factory
class TestFactory:

    @Bean
    @Prototype
    def bar(self) -> Bar1:
        return Bar1()


''')

        when:
        def bar1BeanDefinition = context.getBeanDefinitions(context.classLoader.loadClass('python.Bar1'))
                .find { it.getDeclaringType().get().simpleName.contains("TestFactory") }

        def bar1 = getBean(context, 'python.Bar1')

        then:
        bar1BeanDefinition.getBeanDescription(TypeInformation.TypeFormat.SHORTENED) == '@i.m.c.a.Prototype p.Bar1 p.TestFactory.bar()'
        bar1 != null
        bar1BeanDefinition.getScope().get() == Prototype.class

        cleanup:
        context.close()
    }

    void "test executable annotation on factory method returning function"() {
        given:
        def context = buildContext('''\
from micronaut.context.annotation import Factory, Bean, Executable
from java.util.function import Function

class EchoFunction(Function[str, str]):
    def apply(self, value: str) -> str:
        return value

@Factory
class FunctionFactory:

    @Bean
    @Executable
    def my_func(self) -> Function[str, str]:
        return EchoFunction()
''')

        when:
        def definition = context.getBeanDefinition(Function)
        def bean = context.getBean(Function)

        then:
        definition.findMethod("apply", String).isPresent()
        definition.getTypeArguments(Function).size() == 2
        definition.getTypeArguments(Function)[0].name == "T"
        definition.getTypeArguments(Function)[1].name == "R"
        definition.getTypeArguments(Function)[0].type == String
        definition.getTypeArguments(Function)[1].type == String
        bean.apply("ok") == "ok"

        cleanup:
        context.close()
    }

    void "test executable factory method with inherited client interface methods"() {
        given:
        def context = buildContext('''\
from micronaut.context.annotation import Factory, Bean, Executable
import java

ExecutableClient = java.type("io.micronaut.python.annotation.processing.test.client.ExecutableClient")

@Factory
class ClientFactory:

    @Bean
    @Executable
    def my_client(self) -> ExecutableClient:
        return None
''')

        when:
        BeanDefinition<?> definition = context.getBeanDefinition(ExecutableClient)
        def retrieveMethod = definition.getRequiredMethod("retrieve")
        def blockingMethod = definition.getRequiredMethod("blocking")
        def streamMethod = definition.getRequiredMethod("stream")

        then:
        retrieveMethod.returnType.type == Flux
        retrieveMethod.returnType.typeParameters.length == 1
        retrieveMethod.returnType.typeParameters[0].type == Object
        streamMethod.returnType.type == Flux
        streamMethod.returnType.typeParameters[0].type == byte[].class
        blockingMethod.returnType.type == byte[].class

        cleanup:
        context.close()
    }

    void "test factory method context scope"() {
        given:
        def context = buildContext('''\
from micronaut.context.annotation import Factory, Bean, Context

class BootstrapBean:
    created: bool = False

    def __init__(self):
        self.created = True

@Factory
class BootstrapFactory:

    @Bean
    @Context
    def bootstrap(self) -> BootstrapBean:
        return BootstrapBean()
''')

        when:
        def definition = context.getBeanDefinition(context.classLoader.loadClass("python.BootstrapBean"))
        def bean = context.getBean(context.classLoader.loadClass("python.BootstrapBean"))

        then:
        definition.isContextScope()
        bean.asPolyglotValue().getMember("created").asBoolean()

        cleanup:
        context.close()
    }

    void "test each bean factory method definition metadata"() {
        given:
        def context = buildContext('''\
from jakarta.inject import Singleton
from micronaut.context.annotation import EachBean, Factory, Bean

@Singleton
class ProductConfiguration:
    pass

class Product:
    def shutdown(self):
        pass

@Factory
class ProductFactory:
    @EachBean(ProductConfiguration.__qualname__)
    @Bean(preDestroy="shutdown")
    def product(self, configuration: ProductConfiguration) -> Product:
        return Product()
''')

        when:
        def productType = context.classLoader.loadClass("python.Product")
        BeanDefinition<?> definition = context.getBeanDefinitions(productType)
            .find { it.declaringType.isPresent() && it.declaringType.get().simpleName.contains("ProductFactory") }

        then:
        definition != null
        definition.hasStereotype(Factory)
        !definition.hasDeclaredAnnotation(Factory)
        !definition.hasDeclaredAnnotation(AnnotationUtil.SINGLETON)
        definition.hasDeclaredAnnotation(Bean)
        definition.hasDeclaredAnnotation(EachBean)

        cleanup:
        context.close()
    }

    void "test factory method definition inherits returned type metadata"() {
        given:
        def context = buildContext('''\
from jakarta.inject import Singleton
from micronaut.context.annotation import Factory, Bean

@Singleton
class Product:
    pass

@Factory
class ProductFactory:
    @Bean
    def product(self) -> Product:
        return Product()
''')

        when:
        def productType = context.classLoader.loadClass("python.Product")
        BeanDefinition<?> definition = context.getBeanDefinitions(productType)
            .find { it.declaringType.isPresent() && it.declaringType.get().simpleName.contains("ProductFactory") }

        then:
        definition != null
        definition.hasAnnotation(AnnotationUtil.SINGLETON)
        definition.hasDeclaredAnnotation(Bean)

        cleanup:
        context.close()
    }

    void "test factory method can return enum"() {
        given:
        def context = buildContext('''\
from micronaut.context.annotation import Factory
from jakarta.inject import Singleton
from java.time import DayOfWeek

@Factory
class DateFactory:

    @Singleton
    def start_day(self) -> DayOfWeek:
        return DayOfWeek.MONDAY
''')

        expect:
        context.containsBean(DayOfWeek)
        context.getBean(DayOfWeek) == DayOfWeek.MONDAY

        cleanup:
        context.close()
    }

    void "test inherited factory method is produced by child factory"() {
        given:
        def context = buildContext('''\
from micronaut.context.annotation import Factory, Bean
from jakarta.inject import Singleton

class Product:
    def __init__(self, name: str):
        self.name = name

class BaseProductFactory:

    @Bean
    @Singleton
    def product(self) -> Product:
        return Product("inherited")

@Factory
class ProductFactory(BaseProductFactory):
    pass
''')
        def productType = context.classLoader.loadClass("python.Product")

        when:
        def definition = context.getBeanDefinition(productType)
        def product = context.getBean(productType)

        then:
        definition.getDeclaringType().get().simpleName.contains("ProductFactory")
        product.asPolyglotValue().getMember("name").asString() == "inherited"

        cleanup:
        context.close()
    }

    void "test a factory bean with method and no return type"() {
        when:
        def context = buildContext('''\
from micronaut.context.annotation import Factory, Bean, Prototype

class Bar1:
    pass

@Factory
class TestFactory:

    @Bean
    @Prototype
    def bar(self):
        return Bar1()


''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Factory methods declared with @Bean must specify a return type")
        e.message.contains("def bar(self)")
    }

    void "test around advice on factory method can proxy return type from another package"() {
        given:
        def tempDir = File.createTempDir("pyronaut-aop-factory", "")
        def adviceDir = new File(tempDir, "advice")
        def methodDir = new File(adviceDir, "method")
        methodDir.mkdirs()

        new File(adviceDir, "Timed.py").text = '''\
from micronaut.aop import Around

@Around
def Timed(func):
    return func
'''

        new File(adviceDir, "MyBean.py").text = '''\
class MyBean:
    def do_work(self) -> str:
        return "Done"
'''

        new File(methodDir, "MyFactory.py").text = '''\
from micronaut.context.annotation import Factory, Prototype
from advice.MyBean import MyBean
from advice.Timed import Timed

@Factory
class MyFactory:
    @Prototype
    @Timed
    def my_bean(self) -> MyBean:
        return MyBean()
'''

        ContextHolder.resetContext()
        def classLoader = PyronautCompiler.builder()
            .pythonSrc(tempDir.absolutePath)
            .build()
            .buildClassLoader()

        ApplicationContext context = ApplicationContext.builder()
            .classLoader(classLoader)
            .environments("test")
            .beanDefinitionsProvider(new InMemoryBeanDefinitionsProvider(false))
            .build()
            .start()

        when:
        def myBeanType = classLoader.loadClass("advice.MyBean")
        def beans = context.getBeansOfType(myBeanType)

        then:
        beans.size() == 1
        beans.first().asPolyglotValue().invokeMember("do_work").asString() == "Done"

        cleanup:
        context?.close()
        tempDir?.deleteDir()
        ContextHolder.resetContext()
    }

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0038")
    void "test exposed factory method type with around advice"() {
        given:
        def context = buildContext('''\
from micronaut.aop import Around
from micronaut.context.annotation import Bean, Factory, Prototype

import java

Runnable = java.type("java.lang.Runnable")

@Around
def Logged(func):
    return func

class Task(Runnable):
    def __init__(self):
        self.ran = False

    def run(self):
        self.ran = True

@Factory
class TaskFactory:
    @Prototype
    @Bean(typed=[Runnable])
    @Logged
    def task(self) -> Task:
        return Task()
''')

        when:
        def definition = context.getBeanDefinition(Runnable)
        def task = context.getBean(Runnable)

        then:
        definition.exposedTypes == [Runnable] as Set
        task != null

        cleanup:
        context?.close()
        ContextHolder.resetContext()
    }

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0044")
    void "test invalid exposed factory method type fails compilation"() {
        given:
        ApplicationContext context = null

        when:
        context = buildContext('''\
from micronaut.context.annotation import Bean, Factory, Prototype

class ExposedType:
    pass

class Product:
    pass

@Factory
class ProductFactory:
    @Prototype
    @Bean(typed=[ExposedType])
    def product(self) -> Product:
        return Product()
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Bean defines an exposed type [python.ExposedType] that is not implemented by the bean type")

        cleanup:
        context?.close()
        ContextHolder.resetContext()
    }

    @Unroll
    void "test factory method can produce multiple #scopeName beans from list return"() {
        given:
        def context = buildContext("""\
from typing import Annotated, List
from jakarta.inject import Singleton, Qualifier
from micronaut.context.annotation import Factory, Bean, Prototype, Executable

import java

BeanProvider = java.type("io.micronaut.context.BeanProvider")

@Qualifier
def WishList(func):
    return func

@Qualifier
def All(func):
    return func

class Product:
    def __init__(self, name: str):
        self.name = name

@Singleton
class Catalogue:
    def __init__(
        self,
        all_products: Annotated[List[Product], All],
        wishlist: Annotated[List[Product], WishList],
        provider: BeanProvider[Product]
    ):
        self.all_products = all_products
        self.wishlist = wishlist
        self.provider = provider

    @Executable
    def all_count(self) -> int:
        return len(self.all_products)

    @Executable
    def all_names(self) -> str:
        return ",".join(sorted([product.name for product in self.all_products]))

    @Executable
    def wishlist_count(self) -> int:
        return len(self.wishlist)

    @Executable
    def wishlist_names(self) -> str:
        return ",".join(sorted([product.name for product in self.wishlist]))

    @Executable
    def provider_count(self) -> int:
        return self.provider.stream().count()

@Factory
class Shop:
    @Bean
    @${scopeAnnotation}
    @All
    def all_products(self) -> List[Product]:
        return [Product("one"), Product("two"), Product("three")]

    @Bean
    @${scopeAnnotation}
    @WishList
    def wishlist(self) -> List[Product]:
        return [Product("four"), Product("five")]
""")

        when:
        def productClass = context.classLoader.loadClass("python.Product")
        def shopClass = context.classLoader.loadClass("python.Shop")
        def catalogue = getBean(context, "python.Catalogue")

        then:
        context.getBeanDefinitions(shopClass).size() == 1
        context.getBeanDefinitions(shopClass, Qualifiers.byStereotype("python.WishList")).size() == 0
        catalogue.all_count() == 3
        catalogue.all_names() == "one,three,two"
        catalogue.wishlist_count() == 2
        catalogue.wishlist_names() == "five,four"
        catalogue.provider_count() == 5
        context.containsBean(productClass)

        when:
        context.getBean(productClass)

        then:
        thrown(NonUniqueBeanException)

        when:
        context.getBean(productClass, Qualifiers.byStereotype("python.WishList"))

        then:
        thrown(NonUniqueBeanException)

        when:
        List beans = context.getBean(Argument.listOf(productClass), Qualifiers.byStereotype("python.WishList"))

        then:
        beans.size() == 2
        beans.collect { it.asPolyglotValue().getMember("name").asString() }.sort() == ["five", "four"]
        if (sameContainerInstance) {
            assert beans.is(context.getBean(Argument.listOf(productClass), Qualifiers.byStereotype("python.WishList")))
        }

        cleanup:
        context.close()

        where:
        scopeName   | scopeAnnotation | sameContainerInstance
        "prototype" | "Prototype"     | false
        "singleton" | "Singleton"     | true
    }

    void "test factory method can produce named primitive value bean"() {
        given:
        def context = buildContext('''\
from typing import Annotated
from jakarta.inject import Inject, Named, Singleton
from micronaut.context.annotation import Factory, Bean, Executable

@Factory
class IntFactory:
    @Bean
    @Named("total")
    def total(self) -> int:
        return 10

@Singleton
class MyBean:
    total_from_field: Annotated[int, Inject, Named("total")] = 0

    def __init__(self, total: Annotated[int, Named("total")]):
        self.total = total

    @Executable
    def total_value(self) -> int:
        return self.total

    @Executable
    def total_field_value(self) -> int:
        return self.total_from_field
''')

        when:
        def bean = getBean(context, "python.MyBean")

        then:
        bean.total_value() == 10
        bean.total_field_value() == 10

        cleanup:
        context.close()
    }

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0046")
    void "test factory method named qualifier from constant"() {
        given:
        def context = buildContext('''\
from jakarta.inject import Named, Singleton
from micronaut.context.annotation import Factory, Bean

class Product:
    pass

@Factory
class ProductFactory:
    NAME = "testing123"

    @Bean
    @Singleton
    @Named(NAME)
    def product(self) -> Product:
        return Product()
''')

        when:
        def productType = context.classLoader.loadClass("python.Product")
        def definition = context.getBeanDefinition(productType, Qualifiers.byName("testing123"))

        then:
        definition.stringValue(AnnotationUtil.NAMED).get() == "testing123"

        cleanup:
        context.close()
    }

    void "test factory method returning none fails bean creation"() {
        given:
        def context = buildContext('''\
from typing import Annotated
from micronaut.context.annotation import Factory, Parameter, Prototype

class Product:
    pass

@Factory
class ProductFactory:
    @Prototype
    def product(self, name: Annotated[str, Parameter]) -> Product:
        return None
''')
        def productClass = context.classLoader.loadClass("python.Product")

        when:
        context.createBean(productClass, "test")

        then:
        thrown(BeanContextException)

        cleanup:
        context.close()
    }

    void "test each bean factory can disable individual candidates"() {
        given:
        def context = buildContext('''\
from micronaut.context.annotation import EachBean, EachProperty, Factory
import java

DisabledBeanException = java.type("io.micronaut.context.exceptions.DisabledBeanException")

@EachProperty("engines")
class EngineConfiguration:
    cylinders: int
    enabled: bool = True

class Engine:
    def __init__(self, cylinders: int):
        self.cylinders = cylinders

@Factory
class EngineFactory:
    @EachBean(EngineConfiguration.__qualname__)
    def build_engine(self, config: EngineConfiguration) -> Engine:
        if not config.enabled:
            raise DisabledBeanException("Engine configuration disabled")
        return Engine(config.cylinders)
''', false, [
                "engines.subaru.cylinders": "4",
                "engines.ford.cylinders": "8",
                "engines.ford.enabled": false,
                "engines.lamborghini.cylinders": "12"
        ])
        def engineClass = context.classLoader.loadClass("python.Engine")

        when:
        def engines = context.getBeansOfType(engineClass)

        then:
        engines.size() == 2
        engines.collect { it.asPolyglotValue().getMember("cylinders").asInt() }.sort() == [4, 12]

        cleanup:
        context.close()
    }

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0020")
    void "test a factory bean with static method"() {
        given:
        def context = buildContext('''\
from micronaut.context.annotation import Factory, Bean, Prototype
from jakarta.inject import Singleton

class Bar1:
    pass

@Factory
class TestFactory:

    @Singleton
    @staticmethod
    def bar() -> Bar1:
        return Bar1()
''')

        when:
        def bar1BeanDefinition = context.getBeanDefinitions(context.classLoader.loadClass('python.Bar1'))
                .find { it.getDeclaringType().get().simpleName.contains("TestFactory") }

        def bar1 = getBean(context, 'python.Bar1')

        then:
        bar1BeanDefinition.getBeanDescription(TypeInformation.TypeFormat.SHORTENED) == '@i.m.c.a.Prototype python.Bar1 python.TestFactory.bar()'
        bar1 != null
        bar1BeanDefinition.getScope().get() == Prototype.class

        cleanup:
        context.close()
    }
}
