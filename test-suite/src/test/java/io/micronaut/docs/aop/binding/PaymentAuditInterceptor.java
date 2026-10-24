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
package io.micronaut.docs.aop.binding;

// tag::imports[]
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
// end::imports[]

// tag::interceptor[]
@Singleton
@Audit("security") // <1>
@Audit("billing")
public class PaymentAuditInterceptor implements MethodInterceptor<Object, Object> {

    private final List<String> audited = new CopyOnWriteArrayList<>();

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        audited.add(context.getMethodName());
        return context.proceed();
    }

    public List<String> getAudited() {
        return audited;
    }
}
// end::interceptor[]
