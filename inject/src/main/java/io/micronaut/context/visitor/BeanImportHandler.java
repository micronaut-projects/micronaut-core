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
package io.micronaut.context.visitor;

import java.util.Collections;
import java.util.Set;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.beans.BeanElementBuilder;
import io.micronaut.inject.visitor.VisitorContext;

/**
 * Interface that allows extension of Bean import handling in other to support
 * other injection systems beyond JSR-330 in downstream modules.
 *
 * @author graemerocher
 * @since 3.0.0
 */
public interface BeanImportHandler {

    /**
     * @return The supported annotation names.
     */
    default @NonNull Set<String> getSupportedAnnotationNames() {
        return Collections.emptySet();
    }

    /**
     * Callback method invoked when a bean is added.
     * @param beanElementBuilder The bean element builder
     * @param context The visitor context
     */
    void beanAdded(BeanElementBuilder beanElementBuilder, VisitorContext context);
}
