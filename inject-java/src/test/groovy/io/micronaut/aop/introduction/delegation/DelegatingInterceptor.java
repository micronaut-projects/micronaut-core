/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.aop.introduction.delegation;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.inject.ExecutableMethod;

import javax.inject.Singleton;

@Singleton
public class DelegatingInterceptor implements MethodInterceptor<Delegating, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Delegating, Object> context) {
        ExecutableMethod<Delegating, Object> executableMethod = context.getExecutableMethod();
        Object[] parameterValues = context.getParameterValues();
        if (executableMethod.getName().equals("test2")) {
            DelegatingIntroduced instance = new DelegatingIntroduced() {
                @Override
                public String test2() {
                    return "good";
                }

                @Override
                public String test() {
                    return "good";
                }
            };
            return executableMethod.invoke(instance, parameterValues);
        } else {
            return executableMethod
                    .invoke(new DelegatingImpl(), parameterValues);
        }

    }
}
