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
import io.micronaut.core.annotation.AnnotationUtil

class RepeatableBindingSelectionSpec extends AbstractTypeElementSpec {

    void 'repeatable bindings match every occurrence only when opted in: #all, #tags'() {
        given:
        def context = buildContext(source(all, tags))
        def targetType = context.classLoader.loadClass('repeatablebinding.TargetBean')
        def interceptorType = context.classLoader.loadClass('repeatablebinding.TagInterceptor')

        when:
        context.getBean(targetType).work()

        then:
        interceptorType.calls == expected
        context.getBeanDefinition(interceptorType).annotationMetadata
            .getAnnotationValuesByName(AnnotationUtil.ANN_INTERCEPTOR_BINDING)
            .find { it.stringValue().orElse('') == 'repeatablebinding.Tag' }
            .booleanValue('matchAllOccurrences').orElse(false) == all

        cleanup:
        context.close()

        where:
        all   | tags           | expected
        false | ['a']          | 1
        true  | ['a']          | 0
        true  | ['a', 'b']     | 1
        true  | ['a', 'b', 'c'] | 1
        true  | ['b', 'c']     | 0
    }

    void 'a method narrowing the group of its class loses an interceptor requiring the whole group: #all'() {
        given:
        def context = buildContext("""
package repeatablegroup;

import io.micronaut.aop.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@java.lang.annotation.Target({ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Repeatable(Tags.class)
@Around
@InterceptorBinding(bindMembers = true, matchAllOccurrences = $all)
@interface Tag {
    String value();
}

@Retention(RetentionPolicy.RUNTIME)
@java.lang.annotation.Target({ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@interface Tags {
    Tag[] value();
}

@Retention(RetentionPolicy.RUNTIME)
@java.lang.annotation.Target({ElementType.TYPE, ElementType.METHOD})
@Tag("a")
@Tag("b")
@interface TaggedAB {
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

    @Tag("c")
    @TaggedAB
    public void composed() {
    }
}
""")
        def targetType = context.classLoader.loadClass('repeatablegroup.TargetBean')
        def calls = context.classLoader.loadClass('repeatablegroup.TagInterceptor').calls
        def target = context.getBean(targetType)
        def binding = context.getBeanDefinition(targetType).getRequiredMethod('narrowed').annotationMetadata
            .getAnnotationValuesByName(AnnotationUtil.ANN_INTERCEPTOR_BINDING)
            .find { it.stringValue().orElse('') == 'repeatablegroup.Tag' }

        when:
        target.inherited()
        target.narrowed()
        target.widened()
        target.composed()

        then:
        calls == expected
        binding.booleanValue('matchAllOccurrences').orElse(false) == all

        cleanup:
        context.close()

        where:
        all   | expected
        false | ['inherited', 'narrowed', 'widened', 'composed']
        true  | ['inherited', 'widened', 'composed']
    }

    private static String source(boolean all, List<String> tags) {
        return """
package repeatablebinding;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.NonBinding;
import jakarta.inject.Singleton;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@java.lang.annotation.Target({ElementType.TYPE, ElementType.METHOD})
@Repeatable(Tags.class)
@Around
@InterceptorBinding(bindMembers = true, matchAllOccurrences = $all)
@interface Tag {
    String value();
    @NonBinding String description() default "";
}

@Retention(RetentionPolicy.RUNTIME)
@java.lang.annotation.Target({ElementType.TYPE, ElementType.METHOD})
@interface Tags {
    Tag[] value();
}

@Singleton
@Tag(value = "a", description = "interceptor a")
@Tag(value = "b", description = "interceptor b")
class TagInterceptor implements MethodInterceptor<Object, Object> {
    static int calls;

    public Object intercept(MethodInvocationContext<Object, Object> context) {
        calls++;
        return context.proceed();
    }
}

@Singleton
${tags.collect { '@Tag(value = "' + it + '", description = "target")' }.join('\n')}
class TargetBean {
    public String work() {
        return "done";
    }
}
"""
    }
}
