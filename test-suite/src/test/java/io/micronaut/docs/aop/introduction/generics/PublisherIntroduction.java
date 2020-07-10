/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.docs.aop.introduction.generics;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import java.lang.reflect.Method;
import javax.inject.Singleton;

@Singleton
public final class PublisherIntroduction implements MethodInterceptor<GenericPublisher<?>, Object> {

    @Override
    public Object intercept(final MethodInvocationContext<GenericPublisher<?>, Object> context) {
        final Method method = context.getTargetMethod();
        if (isEqualsMethod(method)) {
            // Only consider equal when proxies are identical.
            return context.getTarget() == context.getParameterValues()[0];

        } else if (isHashCodeMethod(method)) {
            return hashCode();

        } else if (isToStringMethod(method)) {
            return toString();

        } else {
            return context.getParameterValues()[0].getClass().getSimpleName();
        }
    }

    private static boolean isEqualsMethod(final Method method) {
        if ((method == null) || !"equals".equals(method.getName())) {
            return false;
        }
        final Class<?>[] paramTypes = method.getParameterTypes();
        return (paramTypes.length == 1) && (paramTypes[0] == Object.class);
    }

    private static boolean isHashCodeMethod(final Method method) {
        return (method != null) && "hashCode".equals(method.getName()) && (method.getParameterTypes().length == 0);
    }

    private static boolean isToStringMethod(final Method method) {
        return (method != null) && "toString".equals(method.getName()) && (method.getParameterTypes().length == 0);
    }

}
