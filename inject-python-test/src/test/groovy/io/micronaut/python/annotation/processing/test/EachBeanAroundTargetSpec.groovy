package io.micronaut.python.annotation.processing.test

import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Unroll

class EachBeanAroundTargetSpec extends AbstractPythonTypeElementSpec {

    @Unroll
    void "test each bean interceptor proceeds to the target of its qualifier #description"() {
        given:
        def context = buildContext("""\
from typing import Annotated
from micronaut.aop import Around, InterceptorBean
from micronaut.context.annotation import EachBean, EachProperty, Executable, Parameter, Prototype

import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@EachProperty($eachProperty)
class ChannelConfiguration:
    sender: str = None

    def __init__(self, name: Annotated[str, Parameter]):
        self.name = name

    def set_sender(self, sender: str) -> None:
        self.sender = sender

@Executable
@Around
def Labelled(func):
    return func

@EachBean(ChannelConfiguration.__qualname__)
@Labelled
class Channel:
    def __init__(self, configuration: ChannelConfiguration):
        self.configuration = configuration

    def send(self, message: str) -> str:
        return f"{message} from {self.configuration.sender}"

@Prototype
@InterceptorBean(Labelled)
class LabelInterceptor(MethodInterceptor):
    def intercept(self, context):
        return f"[x] {context.proceed()}"
""", false, [
            "labelled-channels.sms.sender"  : "Fred",
            "labelled-channels.email.sender": "Bob"
        ])
        def channelClass = context.classLoader.loadClass("python.Channel")

        expect:
        context.getBean(channelClass, Qualifiers.byName("sms")).send("Hello") == "[x] Hello from Fred"
        context.getBean(channelClass, Qualifiers.byName("email")).send("Hello") == "[x] Hello from Bob"
        context.getBean(channelClass, Qualifiers.byName("sms")).send("Hi") == "[x] Hi from Fred"

        cleanup:
        context?.close()

        where:
        description          | eachProperty
        "without a primary"  | '"labelled-channels"'
        "with a primary"     | 'value="labelled-channels", primary="sms"'
    }
}
