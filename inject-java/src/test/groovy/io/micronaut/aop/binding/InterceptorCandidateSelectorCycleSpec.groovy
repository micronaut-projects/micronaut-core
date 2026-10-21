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
import io.micronaut.context.exceptions.BeanContextException

class InterceptorCandidateSelectorCycleSpec extends AbstractTypeElementSpec {

    void 'a selector that needs the interceptor registry while it is created fails the context: #mode'() {
        given:
        def context = buildContext(source(mode))

        when:
        context.getBean(context.classLoader.loadClass('candidatecycle.Subject')).work()

        then:
        def e = thrown(BeanContextException)
        causes(e).any { it.message?.contains('An InterceptorCandidateSelector bean must not be intercepted') }

        cleanup:
        context.close()

        where:
        mode << ['advised', 'injectsAdvised']
    }

    void 'a selector that depends on a bean that is not intercepted is applied'() {
        given:
        def context = buildContext(source('injectsPlain'))

        when:
        def result = context.getBean(context.classLoader.loadClass('candidatecycle.Subject')).work()

        then:
        result == 'done'
        context.classLoader.loadClass('candidatecycle.Selector').getDeclaredField('calls').with { accessible = true; it.get(null) } > 0

        cleanup:
        context.close()
    }

    private static List<Throwable> causes(Throwable e) {
        List<Throwable> all = []
        for (Throwable t = e; t != null && !all.contains(t); t = t.cause) {
            all << t
        }
        all
    }

    private static String source(String mode) {
        """
package candidatecycle;

import io.micronaut.aop.*;
import io.micronaut.context.*;
import io.micronaut.core.type.Executable;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@InterceptorBinding(kind = InterceptorKind.AROUND)
@interface Tracked {}

@Singleton @InterceptorBean(Tracked.class)
class AAdvice implements MethodInterceptor<Object, Object> {
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return context.proceed();
    }
}

@Singleton
class AdvisedHelper {
    @Tracked
    public String help() { return "help"; }
}

@Singleton
class PlainHelper {
}

@Singleton ${mode == 'advised' ? '@Tracked' : ''}
class Selector implements InterceptorCandidateSelector {
    public static int calls;

    ${mode == 'injectsAdvised' ? 'Selector(AdvisedHelper helper) {}' : mode == 'injectsPlain' ? 'Selector(PlainHelper helper) {}' : ''}

    @Override
    public <T> void selectInterceptors(Executable<T, ?> executable, InterceptorKind kind,
            List<BeanRegistration<Interceptor<T, ?>>> candidates) {
        calls++;
    }
}

@Singleton @Tracked
class Subject {
    public String work() { return "done"; }
}
"""
    }
}
