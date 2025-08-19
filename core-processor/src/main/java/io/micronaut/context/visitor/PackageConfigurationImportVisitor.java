/*
 * Copyright 2017-2025 original authors
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

import io.micronaut.context.annotation.Configuration;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.PackageElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.PackageElementVisitor;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.BeanConfigurationWriter;

import java.io.IOException;

/**
 * Implementation of {@link io.micronaut.context.annotation.Configuration} package visitor.
 *
 * @author Denis Stepanov
 * @since 4.10
 */
@Internal
public final class PackageConfigurationImportVisitor implements PackageElementVisitor<Configuration> {

    @Override
    public void visitPackage(PackageElement packageElement, VisitorContext context) throws ProcessingException {
        var writer = new BeanConfigurationWriter(
            packageElement.getName(),
            packageElement,
            packageElement.getAnnotationMetadata(),
            context
        );
        try {
            writer.accept(context);
        } catch (IOException e) {
            throw new ProcessingException(packageElement, "I/O error occurred writing Configuration for package [" + packageElement.getName() + "]: " + e.getMessage(), e);
        }
    }

    @NonNull
    @Override
    public TypeElementVisitor.VisitorKind getVisitorKind() {
        return TypeElementVisitor.VisitorKind.ISOLATING;
    }

    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE;
    }
}
