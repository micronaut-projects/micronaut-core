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
package io.micronaut.aop.introduction;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class MyRepoIntroducer implements MethodInterceptor<Object, Object> {

    public static final List<Method> EXECUTED_METHODS = new ArrayList<>();

    @Override
    public int getOrder() {
        return 0;
    }

    @Nullable
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        EXECUTED_METHODS.add(context.getExecutableMethod().getTargetMethod());
        return null;
    }
}
