/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.netty.channel;

import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.Internal;
import io.netty.channel.uring.IoUring;

/**
 * Checks if io-uring is available.
 *
 * @author Jonas Konrad
 * @since 4.0.0
 */
@Internal
public class IoUringAvailabilityCondition implements Condition {

    /**
     * Checks if netty's io-uring native transport is available.
     *
     * @param context The ConditionContext.
     * @return true if the io-uring native transport is available.
     */
    @Override
    public boolean matches(ConditionContext context) {
        return IoUring.isAvailable();
    }
}
