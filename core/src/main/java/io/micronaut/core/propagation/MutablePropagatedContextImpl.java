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
package io.micronaut.core.propagation;

import io.micronaut.core.annotation.Internal;

/**
 * The default implementation of {@link MutablePropagatedContext}.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
final class MutablePropagatedContextImpl implements MutablePropagatedContext {

    private PropagatedContext propagatedContext;

    MutablePropagatedContextImpl(PropagatedContext propagatedContext) {
        this.propagatedContext = propagatedContext;
    }

    @Override
    public MutablePropagatedContext add(PropagatedContextElement element) {
        propagatedContext = propagatedContext.plus(element);
        return this;
    }

    @Override
    public MutablePropagatedContext remove(PropagatedContextElement element) {
        propagatedContext = propagatedContext.minus(element);
        return this;
    }

    @Override
    public MutablePropagatedContext replace(PropagatedContextElement oldElement, PropagatedContextElement newElement) {
        propagatedContext = propagatedContext.replace(oldElement, newElement);
        return this;
    }

    @Override
    public PropagatedContext getContext() {
        return propagatedContext;
    }
}
