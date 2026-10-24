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

class RepeatableBindingWrappedMetadataSpec extends AbstractTypeElementSpec {

    void 'every occurrence is matched when the metadata of the target is wrapped: #label'() {
        given:
        def context = buildContext("""
package wrappedgroup;

import io.micronaut.aop.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@java.lang.annotation.Target({ElementType.TYPE, ElementType.METHOD})
@Repeatable(Tags.class)
@Around(proxyTarget = true)
@InterceptorBinding(bindMembers = true, matchAllOccurrences = true)
@interface Tag {
    String value();
}

@Retention(RetentionPolicy.RUNTIME)
@java.lang.annotation.Target({ElementType.TYPE, ElementType.METHOD})
@interface Tags {
    Tag[] value();
}

@Retention(RetentionPolicy.RUNTIME)
@java.lang.annotation.Target({ElementType.TYPE})
@interface Label {
    String value();
}

@Singleton
@Tag("a")
@Tag("b")
class TagInterceptor implements MethodInterceptor<Object, Object> {
    static java.util.List<String> calls = new java.util.ArrayList<>();

    public Object intercept(MethodInvocationContext<Object, Object> context) {
        calls.add(context.getMethodName());
        return context.proceed();
    }
}

@Singleton
@Label("$label")
@Tag("a")
@Tag("b")
class TargetBean {
    public void inherited() {
    }

    @Tag("b")
    public void narrowed() {
    }

    @Tag("a")
    @Tag("b")
    @Tag("c")
    public void widened() {
    }
}
""")
        def target = context.getBean(context.classLoader.loadClass('wrappedgroup.TargetBean'))
        def calls = context.classLoader.loadClass('wrappedgroup.TagInterceptor').calls

        when:
        target.inherited()
        target.narrowed()
        target.widened()

        then:
        calls == ['inherited', 'widened']

        cleanup:
        context.close()

        where:
        label << ['constant', '${label:constant}', '#{true}']
    }
}
