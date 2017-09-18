/*
 * Copyright 2017 original authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.particleframework.aop;

import org.particleframework.core.annotation.Internal;
import org.particleframework.core.convert.MutableConvertibleMultiValues;
import org.particleframework.core.convert.MutableConvertibleMultiValuesMap;
import org.particleframework.core.order.OrderUtil;
import org.particleframework.inject.Argument;
import org.particleframework.inject.ArgumentValue;
import org.particleframework.inject.ExecutionHandle;
import org.particleframework.inject.MutableArgumentValue;

import java.util.*;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class InterceptorChain<R> implements InvocationContext<R> {
    private final Interceptor<R>[] interceptors;
    private final Object target;
    private final ExecutionHandle<R> executionHandle;
    private final MutableConvertibleMultiValues<Object> attributes = new MutableConvertibleMultiValuesMap<>();
    private final Map<String, MutableArgumentValue<?>> parameters = new LinkedHashMap<>();
    private int index = 0;

    public InterceptorChain(Interceptor<R>[] interceptors,
                            Object target,
                            ExecutionHandle<R> executionHandle,
                            Object...originalParameters) {
        this.target = target;
        this.executionHandle = executionHandle;
        OrderUtil.sort(interceptors);
        this.interceptors = new Interceptor[interceptors.length+1];
        System.arraycopy(interceptors, 0, this.interceptors, 0, interceptors.length);
        this.interceptors[interceptors.length-1] = context -> executionHandle.invoke(
                getParameterValues()
        );
        Argument[] arguments = executionHandle.getArguments();
        for (int i = 0; i < arguments.length; i++) {
            Argument argument = executionHandle.getArguments()[i];
            parameters.put(argument.getName(), MutableArgumentValue.create(argument, originalParameters[i]));
        }
    }

    @Override
    public Class getDeclaringType() {
        return executionHandle.getDeclaringType();
    }

    @Override
    public <T> Optional<T> get(CharSequence name, Class<T> requiredType) {
        return attributes.get(name, requiredType);
    }

    @Override
    public List getAll(CharSequence name) {
        return attributes.getAll(name);
    }

    @Override
    public Set<String> getNames() {
        return attributes.getNames();
    }

    @Override
    public Argument[] getArguments() {
        return executionHandle.getArguments();
    }

    @Override
    public Object get(CharSequence name) {
        return attributes.getAll(name);
    }

    @Override
    public R invoke(Object... arguments) {
        return proceed();
    }

    @Override
    public Map<String, MutableArgumentValue<?>> getParameters() {
        return parameters;
    }

    @Override
    public Object[] getParameterValues() {
        return getParameters()
                .values()
                .stream()
                .map(ArgumentValue::getValue)
                .toArray();
    }

    @Override
    public Object getTarget() {
        return target;
    }

    @Override
    public R proceed() throws RuntimeException {
        Interceptor<R> interceptor;
        int len = this.interceptors.length;
        if(index == len) {
            interceptor = this.interceptors[len -1];
        }
        else {

            interceptor = this.interceptors[index++];
        }
        return interceptor.intercept(this);
    }

    @Override
    public InvocationContext<R> add(CharSequence key, Object value) {
        this.attributes.add(key, value);
        return this;
    }

    @Override
    public MutableConvertibleMultiValues<Object> put(CharSequence key, Object value) {
        this.attributes.put(key, value);
        return this;
    }

    @Override
    public MutableConvertibleMultiValues<Object> remove(CharSequence key, Object value) {
        this.attributes.remove(key, value);
        return this;
    }

    @Override
    public MutableConvertibleMultiValues<Object> clear(CharSequence key) {
        this.attributes.clear(key);
        return this;
    }

    @Override
    public MutableConvertibleMultiValues<Object> clear() {
        this.attributes.clear();
        return this;
    }
}
