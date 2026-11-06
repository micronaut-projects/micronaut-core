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
package io.micronaut.python.compiler

/**
 * A stub that owns its Python object (created through its no-argument constructor) creates the object
 * again once the application it was created in has shut down, and only then.
 */
class OwnedPythonObjectSourceSpec extends GeneratedJavaSourceSpec {

    void "an owned Python object is replaced under the stub's monitor when its application is gone"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable


@Singleton
class Counter:
    def __init__(self):
        self.count = 0

    @Executable
    def increment(self) -> int:
        self.count += 1
        return self.count
'''

        expect: "the field is volatile, so the object a thread reads without the monitor is the one another thread stored"
        assertGeneratedSourceContains(pythonCode, '''
  protected transient volatile Value graalpyInternalValue;
''')

        and: "the live check is repeated under the monitor, so concurrent callers share one new object"
        assertGeneratedSourceContains(pythonCode, '''
  public Value asPolyglotValue() {
    if (this instanceof io.micronaut.aop.InterceptedProxy) {
      return PythonCoercion.interceptedTargetValue((InterceptedProxy) this);
    }
    Value value = this.graalpyInternalValue;
    if (this.graalpyInternalClassReference != null && !PythonContextRuntime.isLiveInstance(value)) {
      synchronized (this) {
        value = this.graalpyInternalValue;
        if (!PythonContextRuntime.isLiveInstance(value)) {
          value = PythonContextRuntime.newInstance(this.graalpyInternalClassReference);
          this.graalpyInternalValue = value;
        }
      }
    }
    return value;
  }
''')
    }
}
