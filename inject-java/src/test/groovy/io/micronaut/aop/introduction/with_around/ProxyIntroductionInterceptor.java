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
package io.micronaut.aop.introduction.with_around;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;

import javax.inject.Singleton;

@Singleton
public class ProxyIntroductionInterceptor implements MethodInterceptor<Object, Object> {

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        // Only intercept CustomProxy
        if (context.getMethodName().equalsIgnoreCase("isProxy")) {
            // test introduced interface delegation
            CustomProxy customProxy = new CustomProxy() {
                @Override
                public boolean isProxy() {
                    return true;
                }
            };
            return context.getExecutableMethod().invoke(customProxy, context.getParameterValues());
        }
        return context.proceed();
    }
}
