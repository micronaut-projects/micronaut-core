/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.expressions.context;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ClassElement;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This class is responsible for assembling expression evaluation context
 * from classes annotated with {@link io.micronaut.context.annotation.EvaluatedExpressionContext}.
 * The assembled context is considered as shared because elements from this context can
 * be referenced in any expression compiled within the same module. It can later be extended
 * by annotation level, annotation member level context classes or method element.
 *
 * @since 4.0.0
 * @author Sergey Gavrilov
 */
@Internal
public final class ExpressionCompilationContextRegistry {

    private static Collection<ClassElement> contextTypes = ConcurrentHashMap.newKeySet();

    /**
     * Adds evaluated expression context class element to context loader
     * at compilation time.
     *
     * @param contextClass context class element
     */
    public static void registerContextClass(@NonNull ClassElement contextClass) {
        contextTypes.add(contextClass);
    }

    /**
     * Resets expression evaluation context.
     */
    public static void reset() {
        contextTypes.clear();
    }

    /**
     * @return shared expression evaluation context.
     */
    @NonNull
    static ExtendableExpressionCompilationContext getSharedContext() {
        return new DefaultExpressionCompilationContext(contextTypes.toArray(ClassElement[]::new));
    }
}
