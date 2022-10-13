/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.internal.AddVirtualThreadsExecutor;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.inject.Named;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Adds a virtual threads executor bean.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public class AddVirtualThreadsExecutorVisitor implements TypeElementVisitor<AddVirtualThreadsExecutor, Object> {

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        ClassElement executorsClassElement = context.getClassElement(Executors.class).get();
        ClassElement executorClassElement = context.getClassElement(Executor.class).get();

        element.addAssociatedBean(executorClassElement)
            .annotate(Named.class, builder -> builder.value("VTHREADS_IO"))
            .annotate(Requires.class, builder -> builder.member("sdk", Requires.Sdk.JAVA).member("version", "19"))
            .createWith(MethodElement.of(
                executorsClassElement,
                executorsClassElement,
                new AnnotationMetadataProvider() {
                    @Override
                    public AnnotationMetadata getAnnotationMetadata() {
                        return AnnotationMetadata.EMPTY_METADATA;
                    }
                },
                context.getAnnotationMetadataBuilder(),
                executorClassElement,
                executorClassElement,
                "newVirtualThreadPerTaskExecutor",
                true,
                true));
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

}
