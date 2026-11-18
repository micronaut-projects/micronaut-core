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
package io.micronaut.retry.intercept

import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.inject.MethodExecutionHandle
import io.micronaut.retry.annotation.Fallback
import io.micronaut.retry.annotation.Recoverable
import jakarta.inject.Singleton
import spock.lang.Issue
import spock.lang.Specification

@Issue("https://github.com/micronaut-projects/micronaut-core/issues/6238")
class CustomRecoveryInterceptorSpec extends Specification {

    void "a recovery interceptor that replaces the default one and is bound to @Recoverable resolves the fallbacks"() {
        given:
        ApplicationContext context = ApplicationContext.run(['spec.name': 'CustomRecoveryInterceptorSpec'])

        when:
        String result = context.getBean(GreetingService).greet()

        then:
        result == "fallback"
        context.getBean(RecoveryInterceptor) instanceof CustomRecoveryInterceptor
        context.getBean(CustomRecoveryInterceptor).fallbackLookups == 1

        cleanup:
        context.close()
    }

    @Requires(property = "spec.name", value = "CustomRecoveryInterceptorSpec")
    @InterceptorBean(Recoverable)
    @Replaces(RecoveryInterceptor)
    static class CustomRecoveryInterceptor extends RecoveryInterceptor {

        int fallbackLookups

        CustomRecoveryInterceptor(BeanContext beanContext) {
            super(beanContext)
        }

        @Override
        Optional<? extends MethodExecutionHandle<?, Object>> findFallbackMethod(MethodInvocationContext<Object, Object> context) {
            fallbackLookups++
            return super.findFallbackMethod(context)
        }
    }

    static interface GreetingApi {
        String greet()
    }

    @Requires(property = "spec.name", value = "CustomRecoveryInterceptorSpec")
    @Singleton
    @Recoverable(api = GreetingApi)
    static class GreetingService implements GreetingApi {

        @Override
        String greet() {
            throw new IllegalStateException("greeting failed")
        }
    }

    @Requires(property = "spec.name", value = "CustomRecoveryInterceptorSpec")
    @Fallback
    static class GreetingFallback implements GreetingApi {

        @Override
        String greet() {
            return "fallback"
        }
    }
}
