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
package io.micronaut.aop.binding

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class NestedNonBindingSelectionSpec extends AbstractTypeElementSpec {

    void 'a non-binding member of an annotation nested in a binding member takes no part in selecting interceptors'() {
        given:
        def context = buildContext('''
package nestednonbinding;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.NonBinding;
import jakarta.inject.Singleton;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@interface Scope {
    String value();
    @NonBinding String note() default "";
}

@Retention(RetentionPolicy.RUNTIME)
@java.lang.annotation.Target({ElementType.TYPE, ElementType.METHOD})
@Around
@InterceptorBinding(bindMembers = true)
@interface Scoped {
    Scope[] value();
}

@Singleton
@Scoped(@Scope(value = "x", note = "interceptor"))
class ScopedInterceptor implements MethodInterceptor<Object, Object> {
    static java.util.List<String> calls = new java.util.ArrayList<>();

    public Object intercept(MethodInvocationContext<Object, Object> context) {
        calls.add(context.getMethodName());
        return context.proceed();
    }
}

@Singleton
class TargetBean {
    @Scoped(@Scope(value = "x", note = "target"))
    public void sameScope() {
    }

    @Scoped(@Scope(value = "y", note = "interceptor"))
    public void otherScope() {
    }
}
''')
        def target = context.getBean(context.classLoader.loadClass('nestednonbinding.TargetBean'))
        def calls = context.classLoader.loadClass('nestednonbinding.ScopedInterceptor').calls

        when:
        target.sameScope()
        target.otherScope()

        then: "the nested non-binding note differs, the nested binding value decides"
        calls == ['sameScope']

        cleanup:
        context.close()
    }
}
