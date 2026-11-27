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
package io.micronaut.context.python;

import io.micronaut.aop.InterceptedProxy;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.jspecify.annotations.Nullable;

/**
 * The arguments of a Python method call from a generated bridge, with every introspected wrapper (a
 * {@code @Serdeable} or {@code @Introspected} dataclass that Java created or deserialized) replaced
 * by its Python object in the context of the call.
 * <p>
 * Python code then receives an instance of its own class rather than the Java proxy of one:
 * {@code type(body)} is the dataclass, {@code dataclasses.is_dataclass(body)} and
 * {@code dataclasses.asdict(body)} work, and an attribute that is {@code None} is the {@code None}
 * of the context. A pooled bridge already passes such arguments this way
 * ({@link PythonCoercion#coerceArgumentsToContext(Context, Object[])}).
 * <p>
 * The Java fields of an introspected wrapper hold its state for Java (getters read them). Python
 * code that assigns an attribute of the object it received does not go through the wrapper, so
 * once the call returns, the properties whose attribute now holds another object are written back
 * to the fields, as an assignment made through the wrapper would have been. The object the call
 * received is the wrapper's own, which the wrapper keeps handing to Python, so later calls see the
 * same object and the changes made to it.
 */
final class PythonObjectArguments {

    private final Object[] arguments;
    private final ValueCoercible.@Nullable GeneratedPropertyMembers[] wrappers;
    private final Value[][] propertyValues;

    private PythonObjectArguments(Object[] arguments,
                                  ValueCoercible.@Nullable GeneratedPropertyMembers[] wrappers,
                                  Value[][] propertyValues) {
        this.arguments = arguments;
        this.wrappers = wrappers;
        this.propertyValues = propertyValues;
    }

    /**
     * Replaces the introspected wrappers among the arguments of a call by their Python objects.
     *
     * @param context The context of the Python method
     * @param arguments The arguments of the bridge
     * @return The arguments to pass to Python
     */
    static PythonObjectArguments of(Context context, Object[] arguments) {
        int first = firstWrapper(arguments);
        if (first < 0) {
            return new PythonObjectArguments(arguments, new ValueCoercible.GeneratedPropertyMembers[0], new Value[0][]);
        }
        Object[] pythonArguments = arguments.clone();
        ValueCoercible.GeneratedPropertyMembers[] wrappers = new ValueCoercible.GeneratedPropertyMembers[arguments.length];
        Value[][] propertyValues = new Value[arguments.length][];
        for (int i = first; i < arguments.length; i++) {
            if (!isPassedAsPythonObject(arguments[i])) {
                continue;
            }
            PooledValueCoercible wrapper = (PooledValueCoercible) arguments[i];
            Value pythonObject = PythonCoercion.coercePooledValue(wrapper, context);
            pythonArguments[i] = pythonObject;
            // the attributes of an object of another context (a pooled copy) are not the wrapper's
            if (wrapper instanceof ValueCoercible.GeneratedPropertyMembers members && pythonObject.equals(wrapper.asPolyglotValue())) {
                String[] names = members.micronautValueCoerciblePropertyNames();
                Value[] values = new Value[names.length];
                for (int j = 0; j < names.length; j++) {
                    values[j] = pythonObject.getMember(names[j]);
                }
                wrappers[i] = members;
                propertyValues[i] = values;
            }
        }
        return new PythonObjectArguments(pythonArguments, wrappers, propertyValues);
    }

    private static int firstWrapper(Object[] arguments) {
        for (int i = 0; i < arguments.length; i++) {
            if (isPassedAsPythonObject(arguments[i])) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Whether an argument is passed as its Python object: an introspected wrapper, not an AOP proxy,
     * whose Python object would bypass the interceptors.
     */
    private static boolean isPassedAsPythonObject(@Nullable Object argument) {
        return argument instanceof PooledValueCoercible && !(argument instanceof InterceptedProxy<?>);
    }

    /**
     * @return The arguments to pass to the Python method
     */
    Object[] arguments() {
        return arguments;
    }

    /**
     * Writes the attributes the Python method reassigned back to the fields of the wrappers it
     * received the objects of.
     */
    void syncWrappers() {
        for (int i = 0; i < wrappers.length; i++) {
            ValueCoercible.GeneratedPropertyMembers members = wrappers[i];
            Value[] before = propertyValues[i];
            if (members == null || before == null) {
                continue;
            }
            Value pythonObject = (Value) arguments[i];
            String[] names = members.micronautValueCoerciblePropertyNames();
            for (int j = 0; j < names.length && j < before.length; j++) {
                Value after = pythonObject.getMember(names[j]);
                if (after != null && !after.equals(before[j])) {
                    members.micronautValueCoerciblePutMember(names[j], after);
                }
            }
        }
    }
}
