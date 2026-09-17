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
package io.micronaut.python.annotation.processing.test.messaging

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Executable
import io.micronaut.context.annotation.Factory
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.ExecutableMethod
import io.micronaut.messaging.annotation.MessageListener
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

/**
 * Messaging listener annotations ({@code @KafkaListener}, {@code @PulsarConsumer}, {@code @CoherenceTopicListener},
 * {@code @Subject(queue=...)}) are meta-annotated with {@link MessageListener}, a {@code @Bean} stereotype, and are
 * legitimately placed on methods of ordinary beans. Such methods must be bridged as executable methods and not be
 * mistaken for {@code @Factory} bean methods.
 */
class MessageListenerMethodSpec extends AbstractPythonTypeElementSpec {

    void "test a method annotated with a @MessageListener stereotype is an executable method of an ordinary bean"() {
        given:
        def context = buildContext('''\
from jakarta.inject import Singleton
from io.micronaut.python.annotation.processing.test.messaging import TestConsumer

@Singleton
class OrderConsumer:

    def __init__(self):
        self.received = []

    @TestConsumer(topic="orders")
    def on_order(self, message: str):
        self.received.append(message)
        return len(self.received)
''')

        when:
        def definition = getBeanDefinition(context, "python.OrderConsumer")
        ExecutableMethod method = definition.findMethod("on_order", String).get()
        def bean = getBean(context, "python.OrderConsumer")

        then:
        definition.hasDeclaredAnnotation(AnnotationUtil.SINGLETON)
        !definition.hasStereotype(Factory)
        definition.getExecutableMethods().size() == 1
        method.hasDeclaredAnnotation(TestConsumer)
        method.stringValue(TestConsumer, "topic").get() == "orders"
        method.hasStereotype(MessageListener)
        method.hasStereotype(Bean)
        method.hasStereotype(Executable)
        method.booleanValue(Executable, "processOnStartup").orElse(false)
        bean.on_order("first") == 1
        bean.on_order("second") == 2

        cleanup:
        context?.close()
    }

    void "test a @MessageListener stereotype method returning None on a @Singleton"() {
        given:
        def context = buildContext('''\
from jakarta.inject import Singleton
from io.micronaut.python.annotation.processing.test.messaging import TestConsumer

@Singleton
class MessagePrinter:

    def __init__(self):
        self.messages = []

    @TestConsumer(topic="messages")
    def message_printer(self, message: str) -> None:
        self.messages.append(message)
''')

        when:
        def definition = getBeanDefinition(context, "python.MessagePrinter")
        ExecutableMethod method = definition.findMethod("message_printer", String).get()
        def bean = getBean(context, "python.MessagePrinter")
        bean.message_printer("hello")

        then:
        !definition.hasStereotype(Factory)
        method.returnType.type == void
        method.hasDeclaredAnnotation(TestConsumer)
        method.stringValue(TestConsumer, "topic").get() == "messages"
        method.booleanValue(Executable, "processOnStartup").orElse(false)
        bean.asPolyglotValue().getMember("messages").getArraySize() == 1

        cleanup:
        context?.close()
    }

    void "test a method annotation member aliasing a @MessageListener stereotype member is copied to the executable method"() {
        given:
        def context = buildContext('''\
from io.micronaut.python.annotation.processing.test.messaging import TestListener, TestSubject

@TestListener
class ProductListener:

    @TestSubject(value="product", queue="product-queue")
    def receive(self, data: str) -> None:
        pass
''')

        when:
        def definition = getBeanDefinition(context, "python.ProductListener")
        ExecutableMethod method = definition.findMethod("receive", String).get()

        then:
        definition.hasStereotype(MessageListener)
        !definition.hasStereotype(Factory)
        method.hasDeclaredAnnotation(TestSubject)
        method.stringValue(TestSubject).get() == "product"
        method.stringValue(TestSubject, "queue").get() == "product-queue"
        method.hasStereotype(TestListener)
        method.stringValue(TestListener, "queue").get() == "product-queue"
        method.hasStereotype(Bean)
        method.hasStereotype(Executable)

        cleanup:
        context?.close()
    }

    void "test a @Bean method without a return type on a @Factory is still rejected"() {
        when:
        buildContext('''\
from micronaut.context.annotation import Factory, Bean

class Bar:
    pass

@Factory
class BarFactory:

    @Bean
    def bar(self):
        return Bar()
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Factory methods declared with @Bean must specify a return type")
        e.message.contains("def bar(self)")
    }

    void "test a @MessageListener stereotype method without a return type on a @Factory is a factory method"() {
        when:
        buildContext('''\
from micronaut.context.annotation import Factory
from io.micronaut.python.annotation.processing.test.messaging import TestConsumer

@Factory
class ConsumerFactory:

    @TestConsumer(topic="orders")
    def consumer(self):
        return None
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Factory methods declared with @Bean must specify a return type")
    }

    void "test a @Bean method inherited from a non-@Factory base is bridged for the @Factory subclass"() {
        given:
        def context = buildContext('''\
from micronaut.context.annotation import Factory, Bean

class Bar:

    def __init__(self):
        self.name = "inherited"

class BaseFactory:

    @Bean
    def bar(self) -> Bar:
        return Bar()

@Factory
class BarFactory(BaseFactory):
    pass
''')

        when:
        def bar = getBean(context, "python.Bar")
        def baseStub = context.classLoader.loadClass("python.BaseFactory")
        def factoryStub = context.classLoader.loadClass("python.BarFactory")

        then:
        bar.asPolyglotValue().getMember("name").asString() == "inherited"
        baseStub.getDeclaredMethod("bar").returnType.name == "python.Bar"
        factoryStub.getMethod("bar").declaringClass == baseStub

        cleanup:
        context?.close()
    }

    void "test @Bean functions of a module-level Factory() script are factory methods"() {
        given:
        def context = buildContext('''\
from micronaut.context.annotation import Factory, Bean

Factory()

class Bar:

    def __init__(self, name: str):
        self.name = name

@Bean
def bar() -> Bar:
    return Bar("module")

def not_a_bean() -> Bar:
    return Bar("plain")
''')

        when:
        def bar = getBean(context, "python.Bar")
        def script = context.classLoader.loadClass("python.Script")

        then:
        bar.asPolyglotValue().getMember("name").asString() == "module"
        script.getDeclaredMethod("bar").returnType.name == "python.Bar"
        !script.declaredMethods*.name.contains("not_a_bean")

        cleanup:
        context?.close()
    }

    void "test a @Bean function without a return type on a module-level Factory() script is rejected"() {
        when:
        buildContext('''\
from micronaut.context.annotation import Factory, Bean

Factory()

class Bar:
    pass

@Bean
def bar():
    return Bar()
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Factory methods declared with @Bean must specify a return type")
    }
}
