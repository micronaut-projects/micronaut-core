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
package io.micronaut.docs.annotation.retry;

import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.retry.annotation.Recoverable;
import io.micronaut.retry.intercept.RecoveryInterceptor;

import java.util.Optional;

@Requires(property = "spec.name", value = "CustomRecoveryInterceptorSpec")
// tag::class[]
@InterceptorBean(Recoverable.class) // <1>
@Replaces(RecoveryInterceptor.class) // <2>
public class CustomRecoveryInterceptor extends RecoveryInterceptor {

    public CustomRecoveryInterceptor(BeanContext beanContext) {
        super(beanContext);
    }

    @Override
    public Optional<? extends MethodExecutionHandle<?, Object>> findFallbackMethod(MethodInvocationContext<Object, Object> context) {
        // look up the fallback differently, or delegate to the default lookup
        return super.findFallbackMethod(context);
    }
}
// end::class[]
