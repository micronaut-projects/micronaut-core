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
package io.micronaut.context.python.aop;

import io.micronaut.core.annotation.Internal;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import static io.micronaut.context.python.GraalPyRuntimeUtil.PYTHON;

/**
 * Utility method for Python AOP.
 */
@Internal
final class PythonAopUtil {
    static final String FUNCTION_WRAP_METHOD = "_wrap_micronaut_aop_method";

    /**
     * Support method for decorating functions.
     * @param context The context
     * @return The wrapped function.
     */
    static Value setupWrapFunction(Context context) {
        Value contextBindings = context.getBindings(PYTHON);
        if (!contextBindings.hasMember(FUNCTION_WRAP_METHOD)) {
            context.eval(PYTHON, """
import functools

def _wrap_micronaut_aop_method(method, java_target_method):
    @functools.wraps(method)
    def wrapper(*args, **kwargs):
        return java_target_method(method, args, kwargs)
    return wrapper
""");

        }

        return contextBindings.getMember(FUNCTION_WRAP_METHOD);
    }
}
