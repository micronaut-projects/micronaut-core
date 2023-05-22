/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.inject.visitor;

import java.util.Collections;
import java.util.List;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.order.OrderUtil;

/**
 * Loads the {@link io.micronaut.inject.visitor.BeanElementVisitor} instances.
 *
 * @author graemerocher
 * @since 3.0.0
 */
final class BeanElementVisitorLoader {
    /**
     * @return The loaded visitors
     */
    @SuppressWarnings("unchecked")
    static @NonNull List<BeanElementVisitor<?>> load() {
        List<? extends BeanElementVisitor<?>> visitors = (List) SoftServiceLoader.load(BeanElementVisitor.class)
            .disableFork()
            .collectAll(BeanElementVisitor::isEnabled);

        if (visitors.isEmpty()) {
            return Collections.emptyList();
        } else {
            OrderUtil.sort(visitors);
            return Collections.unmodifiableList(visitors);
        }
    }
}
