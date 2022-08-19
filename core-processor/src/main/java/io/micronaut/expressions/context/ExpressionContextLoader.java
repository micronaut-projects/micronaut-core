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
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * This class is responsible for providing expression evaluation context
 * from classes annotated with {@link io.micronaut.context.annotation.EvaluatedExpressionContext}.
 *
 * Classes from external modules are loaded through {@link ExpressionContextReference}
 * provided by those modules. Context classes that are compiled in the same module need to be
 * explicitly added to context loader. Once all context classes are added, expression context
 * needs to be initialized. After the context is initialized, it can be used by code responsible
 * for expressions compilation
 *
 * @since 4.0.0
 * @author Sergey Gavrilov
 */
@Internal
public final class ExpressionContextLoader {
    private static final Set<String> LOADED_CONTEXTS = ConcurrentHashMap.newKeySet();

    private static volatile BeanContextExpressionEvaluationContext expressionContext = new BeanContextExpressionEvaluationContext();

    static {
        SoftServiceLoader.load(
                ExpressionContextReference.class,
                ExpressionContextLoader.class.getClassLoader())
            .disableFork()
            .collectAll()
            .stream()
            .map(ExpressionContextReference::getType)
            .forEach(LOADED_CONTEXTS::add);
    }

    /**
     * Adds evaluated expression context class element to context loader
     * at compilation time.
     *
     * @param contextClass context class element
     * @param visitorContext visitor context
     */
    public static void addContextClass(@NonNull ClassElement contextClass,
                                       @NonNull VisitorContext visitorContext) {
        ClassElement[] contextElements =
            Stream.concat(
                Stream.concat(expressionContext.getContextTypes().stream(), Stream.of(contextClass)),
                LOADED_CONTEXTS.stream()
                    .map(visitorContext::getClassElement)
                    .filter(Optional::isPresent)
                    .map(Optional::get))
                  .toArray(ClassElement[]::new);

        expressionContext = new BeanContextExpressionEvaluationContext(contextElements);
    }

    /**
     * Resets expression evaluation context.
     */
    public static void reset() {
        expressionContext = new BeanContextExpressionEvaluationContext();
    }

    /**
     * @return expressions evaluation context.
     */
    @NonNull
    public static ExpressionEvaluationContext getExpressionContext() {
        return expressionContext;
    }
}
