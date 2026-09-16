package io.micronaut.python.annotation.processing.test

import io.micronaut.aop.Interceptor
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.context.RuntimeBeanDefinition
import io.micronaut.runtime.server.EmbeddedServer
import pythontest.introduction.reflective.Prompt
import pythontest.introduction.reflective.ReflectiveService
import pythontest.introduction.reflective.ReflectiveServiceInterceptor
import pythontest.introduction.reflective.Var

import java.lang.reflect.Modifier

/**
 * A Python class with an introduction stereotype whose members are all abstract methods compiles to a
 * Java interface so that frameworks building the implementation reflectively from the interface
 * (java.lang.reflect.Proxy, Method.getAnnotation) can consume it.
 */
class InterfaceStubSpec extends AbstractPythonTypeElementSpec {

    void "test introduction with only abstract methods compiles to an annotated Java interface"() {
        given:
        def context = buildContext('''
from abc import ABC, abstractmethod
from typing import Annotated
from pythontest.introduction.reflective import Prompt, ReflectiveService, Var
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable


@ReflectiveService("assistant")
class Assistant(ABC):

    @Prompt("You are helpful.")
    @abstractmethod
    def chat(self, message: Annotated[str, Var("msg")]) -> str:
        ...

    @Prompt("Summarize.", priority=Prompt.Priority.HIGH)
    @abstractmethod
    def summarize(self, text: str, max_words: int) -> str:
        ...


@Singleton
class Caller:
    def __init__(self, assistant: Assistant):
        self.assistant = assistant

    @Executable
    def ask(self, message: str) -> str:
        return self.assistant.chat(message)
''')

        when:
        Class<?> assistantType = context.classLoader.loadClass("python.Assistant")
        def assistant = context.getBean(assistantType)
        def caller = getBean(context, "python.Caller")

        then: "the generated Java type is an interface carrying the runtime annotations"
        assistantType.isInterface()
        assistantType.getAnnotation(ReflectiveService).value() == "assistant"
        Modifier.isAbstract(assistantType.getMethod("chat", String).modifiers)
        assistantType.getMethod("chat", String).getAnnotation(Prompt).value() == "You are helpful."
        assistantType.getMethod("chat", String).parameterAnnotations[0].find { it instanceof Var }.value() == "msg"
        assistantType.getMethod("summarize", String, int).getAnnotation(Prompt).priority() == Prompt.Priority.HIGH

        and: "the bean is the introduction proxy of the interface and calls reach the reflective implementation"
        assistantType.isInstance(assistant)
        assistant.chat("hi") == "You are helpful. [assistant] msg=hi"
        assistant.summarize("some text", 3) == "Summarize. [assistant] priority=HIGH p0=some text p1=3"

        and: "Python code calls the introduced bean like any other injected bean"
        caller.ask("hello") == "You are helpful. [assistant] msg=hello"

        cleanup:
        context?.close()
    }

    void "test introduction interface bridges static functions and copies class-valued annotation members"() {
        given:
        def context = buildContext('''
from abc import ABC, abstractmethod
from typing import Annotated
from pythontest.introduction.reflective import Prompt, ReflectiveService, Var


class Calculator:
    pass


@ReflectiveService
class Planner(ABC):

    @Prompt("Plan the day.", tools=[Calculator])
    @abstractmethod
    def plan(self, topic: Annotated[str, Var("topic")]) -> str:
        ...

    @Prompt("Join the parts.")
    @staticmethod
    def aggregate(first: Annotated[str, Var("first")], second: Annotated[str, Var("second")]) -> str:
        return f"{first} | {second}"

    @staticmethod
    def budget(days: int) -> int:
        return days * 100
''')

        when:
        Class<?> plannerType = context.classLoader.loadClass("python.Planner")
        def planner = context.getBean(plannerType)
        def aggregate = plannerType.getMethod("aggregate", String, String)
        def budget = plannerType.getMethod("budget", int)

        then: "class-valued members reference the generated Java class of the Python type"
        plannerType.isInterface()
        plannerType.getMethod("plan", String).getAnnotation(Prompt).tools() == [context.classLoader.loadClass("python.Calculator")] as Class[]
        planner.plan("hiking") == "Plan the day. tool=Calculator topic=hiking"

        and: "static functions are static interface methods that run the Python function"
        Modifier.isStatic(aggregate.modifiers)
        aggregate.getAnnotation(Prompt).value() == "Join the parts."
        aggregate.parameterAnnotations[1].find { it instanceof Var }.value() == "second"
        aggregate.invoke(null, "music", "dinner") == "music | dinner"
        budget.invoke(null, 3) == 300

        cleanup:
        context?.close()
    }

    void "test declarative HTTP client compiles to an interface implemented by the client introduction"() {
        given:
        def context = buildContext('''
from abc import ABC, abstractmethod
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from micronaut.http.annotation import Controller, Get
from micronaut.http.client.annotation import Client


@Controller("/pets")
class PetController:
    @Get("/{name}")
    def pet(self, name: str) -> str:
        return f"pet {name}"


@Client("/pets")
class PetClient(ABC):
    @Get("/{name}")
    @abstractmethod
    def pet(self, name: str) -> str:
        ...


@Singleton
class PetShop:
    def __init__(self, client: PetClient):
        self.client = client

    @Executable
    def fetch(self, name: str) -> str:
        return self.client.pet(name)
''', true)
        def server = context.getBean(EmbeddedServer)
        server.start()

        when:
        Class<?> clientType = context.classLoader.loadClass("python.PetClient")
        def client = context.getBean(clientType)
        def shop = getBean(context, "python.PetShop")

        then:
        clientType.isInterface()
        client.getClass().name == 'python.PetClient$Intercepted'
        client.pet("dino") == "pet dino"
        shop.fetch("rex") == "pet rex"

        cleanup:
        context?.close()
    }

    void "test plain Python interface keeps the runtime annotations of its methods"() {
        given:
        def context = buildContext('''
from abc import ABC, abstractmethod
from jakarta.inject import Singleton
from pythontest.introduction.reflective import Prompt


class Translator(ABC):
    @Prompt("Translate.")
    @abstractmethod
    def translate(self, text: str) -> str:
        ...


@Singleton
class UpperTranslator(Translator):
    def translate(self, text: str) -> str:
        return text.upper()
''')

        when:
        Class<?> translatorType = context.classLoader.loadClass("python.Translator")
        def translator = context.getBean(translatorType)

        then:
        translatorType.isInterface()
        translatorType.getMethod("translate", String).getAnnotation(Prompt).value() == "Translate."
        translator.translate("hello") == "HELLO"

        cleanup:
        context?.close()
    }

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        contextBuilder.beanDefinitions(
            RuntimeBeanDefinition.builder(new ReflectiveServiceInterceptor())
                .singleton(true)
                .exposedTypes(ReflectiveServiceInterceptor, Interceptor)
                .build()
        )
    }
}
