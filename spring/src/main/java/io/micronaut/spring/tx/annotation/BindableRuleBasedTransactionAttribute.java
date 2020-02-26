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
package io.micronaut.spring.tx.annotation;

import io.micronaut.core.util.ArrayUtils;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Extends {@link org.springframework.transaction.interceptor.RuleBasedTransactionAttribute} so that it can be bound
 * from annotation metadata and defaults to rollback on all exception types apart from those configured.
 *
 * @author graemerocher
 * @since 1.0
 */
public class BindableRuleBasedTransactionAttribute extends DefaultTransactionAttribute {

    private final Set<Class<? extends Throwable>> noRollbackFor = new HashSet<>();
    private Set<Class<? extends Throwable>> rollbackFor = null;

    /**
     * Configures the exceptions to not rollback for.
     *
     * @param exceptions The exceptions not to rollback for
     */
    public final void setNoRollbackFor(Class<? extends Throwable>... exceptions) {
        if (ArrayUtils.isNotEmpty(exceptions)) {
            noRollbackFor.addAll(Arrays.asList(exceptions));
        }
    }

    /**
     * Configures the exceptions to rollback for.
     *
     * @param exceptions The exceptions to rollback for
     */
    public final void setRollbackFor(Class<? extends Throwable>... exceptions) {
        if (ArrayUtils.isNotEmpty(exceptions)) {
            if (rollbackFor == null) {
                rollbackFor = new HashSet<>();
            }
            rollbackFor.addAll(Arrays.asList(exceptions));
        }
    }

    /**
     * @return An unmodifiable set of exceptions to not rollback for.
     */
    public final Set<Class<? extends Throwable>> getNoRollbackFor() {
        return Collections.unmodifiableSet(noRollbackFor);
    }

    /**
     * @return An unmodifiable set of exceptions to rollback for.
     */
    public final Set<Class<? extends Throwable>> getRollbackFor() {
        if (rollbackFor != null) {
            return Collections.unmodifiableSet(rollbackFor);
        }
        return Collections.emptySet();
    }

    @Override
    public final boolean rollbackOn(Throwable ex) {
        if (ex == null) {
            return false;
        }

        for (Class<? extends Throwable> aClass : noRollbackFor) {
            if (aClass.isInstance(ex)) {
                return false;
            }
        }

        if (rollbackFor != null) {
            for (Class<? extends Throwable> aClass : rollbackFor) {
                if (aClass.isInstance(ex)) {
                    return true;
                }
            }

            return false;
        } else {
            return true;
        }
    }
}
