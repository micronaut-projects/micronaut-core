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
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Singleton;

import java.util.HashSet;
import java.util.Set;

@Singleton
public class MyPrototypeRepoIntroducer implements MethodInterceptor<Object, Object> {

    public final Set<ExecutableMethod> methods = new HashSet<>();
    public final Set<RepoKey> repoMethods = new HashSet<>();

    @Override
    public int getOrder() {
        return 0;
    }

    @Nullable
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        methods.add(context.getExecutableMethod());
        repoMethods.add(new RepoKey(context.getTarget().getClass(), context.getExecutableMethod()));
        return null;
    }

    record RepoKey(Class<?> type, ExecutableMethod<?, ?> method) {}

}
