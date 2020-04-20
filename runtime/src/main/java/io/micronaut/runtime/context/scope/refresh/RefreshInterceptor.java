/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.runtime.context.scope.refresh;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import javax.inject.Singleton;
import java.util.concurrent.locks.Lock;

/**
 * <p>A {@link MethodInterceptor} that will lock the bean preventing it from being destroyed by a
 * {@link RefreshEvent} until the method completes.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Requires(notEnv = {Environment.FUNCTION, Environment.ANDROID})
public class RefreshInterceptor implements MethodInterceptor {

    private final RefreshScope refreshScope;

    /**
     * @param refreshScope To allow target beans to be refreshed
     */
    public RefreshInterceptor(RefreshScope refreshScope) {
        this.refreshScope = refreshScope;
    }

    @Override
    public Object intercept(MethodInvocationContext context) {
        Object target = context.getTarget();
        Lock lock = refreshScope.getLock(target).readLock();
        try {
            lock.lock();
            return context.proceed();
        } finally {
            lock.unlock();
        }
    }
}
