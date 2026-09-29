package io.micronaut.python.compiler

class InterfaceStubSourceSpec extends GeneratedJavaSourceSpec {

    private static final List<String> ALLOW_REFLECTION = ["-Amicronaut.introspection.allowReflection=python.*"]

    private static final String ASSISTANT = '''
from abc import ABC, abstractmethod
from typing import Annotated
from pythontest.introduction.reflective import Prompt, ReflectiveService, Var


@ReflectiveService("assistant")
class Assistant(ABC):

    @Prompt("You are helpful.", priority=Prompt.Priority.HIGH)
    @abstractmethod
    def chat(self, message: Annotated[str, Var("msg")]) -> str:
        ...

    @Prompt("Join.")
    @staticmethod
    def join(first: str, second: str) -> str:
        return first + second
'''

    void "introduction interface is generated as a vetoed Java interface with its runtime annotations"() {
        expect:
        assertGeneratedSourceEquals(ASSISTANT, '''
package python;

import io.micronaut.context.python.PythonContextRuntime;
import io.micronaut.context.python.PythonConversion;
import io.micronaut.context.python.annotation.PythonClass;
import io.micronaut.core.annotation.Vetoed;
import java.lang.String;
import org.graalvm.polyglot.Value;
import pythontest.introduction.reflective.Prompt;
import pythontest.introduction.reflective.ReflectiveService;
import pythontest.introduction.reflective.Var;

@Vetoed
@PythonClass(
    packageName = "python",
    rootName = "Assistant",
    displayName = "Assistant",
    cacheKey = "class-instance:python.Assistant"
)
@ReflectiveService("assistant")
public interface Assistant {
  @Prompt(
      priority = Prompt.Priority.HIGH,
      value = "You are helpful."
  )
  String chat(@Var("msg") String message);

  @Prompt("Join.")
  static String join(String first, String second) {
    Value pythonResult = PythonContextRuntime.invokeStaticMethod(new io.micronaut.context.python.PythonContextRuntime.PythonClassReference("python", "Assistant", new String[]{}, "Assistant", "class-instance:python.Assistant"), "join", first, second);
    return PythonConversion.isNone(pythonResult) ? null : pythonResult.asString();
  }
}
''', "python.Assistant", ALLOW_REFLECTION)
    }

    void "the runtime annotations stay off the generated interface without the allowReflection option"() {
        expect:
        assertGeneratedSourceEquals(ASSISTANT, '''
package python;

import io.micronaut.context.python.PythonContextRuntime;
import io.micronaut.context.python.PythonConversion;
import io.micronaut.context.python.annotation.PythonClass;
import io.micronaut.core.annotation.Vetoed;
import java.lang.String;
import org.graalvm.polyglot.Value;

@Vetoed
@PythonClass(
    packageName = "python",
    rootName = "Assistant",
    displayName = "Assistant",
    cacheKey = "class-instance:python.Assistant"
)
public interface Assistant {
  String chat(String message);

  static String join(String first, String second) {
    Value pythonResult = PythonContextRuntime.invokeStaticMethod(new io.micronaut.context.python.PythonContextRuntime.PythonClassReference("python", "Assistant", new String[]{}, "Assistant", "class-instance:python.Assistant"), "join", first, second);
    return PythonConversion.isNone(pythonResult) ? null : pythonResult.asString();
  }
}
''', "python.Assistant")
    }

    void "introduction class with Python state stays a class"() {
        given:
        def pythonCode = '''
from abc import ABC, abstractmethod
from jakarta.inject import Inject, Singleton
from pythontest.introduction.reflective import ReflectiveService


@Singleton
class Clock:
    pass


@ReflectiveService
class Assistant(ABC):
    clock: Clock = None

    @Inject
    def set_clock(self, clock: Clock):
        self.clock = clock

    @abstractmethod
    def chat(self, message: str) -> str:
        ...
'''

        expect:
        assertGeneratedSourceContains(pythonCode, '''
public class Assistant implements ValueCoercible
''')
    }
}
