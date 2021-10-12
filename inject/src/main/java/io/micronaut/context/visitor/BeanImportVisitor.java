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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Import;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.beans.BeanElementBuilder;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

/**
 * Implementation of {@link io.micronaut.context.annotation.Import}.
 *
 * @author graemerocher
 * @since 3.0.0
 */
public class BeanImportVisitor implements TypeElementVisitor<Import, Object> {

    private static final List<BeanImportHandler> BEAN_IMPORT_HANDLERS;

    static {
        final ServiceLoader<BeanImportHandler> handlers = ServiceLoader.load(BeanImportHandler.class);
        List<BeanImportHandler> beanImportHandlers = new ArrayList<>();
        for (BeanImportHandler handler : handlers) {
            beanImportHandlers.add(handler);
        }
        OrderUtil.sort(beanImportHandlers);
        BEAN_IMPORT_HANDLERS = Collections.unmodifiableList(beanImportHandlers);
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        List<ClassElement> beanElements = collectInjectableElements(element, context);

        for (ClassElement beanElement : beanElements) {
            final BeanElementBuilder beanElementBuilder =
                    element.addAssociatedBean(beanElement)
                    .inject();
            for (BeanImportHandler beanImportHandler : BEAN_IMPORT_HANDLERS) {
                beanImportHandler.beanAdded(beanElementBuilder, context);
            }
        }
    }

    @NonNull
    public static List<ClassElement> collectInjectableElements(ClassElement element, VisitorContext context) {
        List<ClassElement> beanElements = new ArrayList<>();
        final String[] classNames = element.getAnnotationMetadata().stringValues(Import.class, "classes");
        if (ArrayUtils.isNotEmpty(classNames)) {
            for (String className : classNames) {
                context.getClassElement(className).ifPresent(beanElements::add);
            }
        }

        String[] annotations = element.getAnnotationMetadata().stringValues(Import.class, "annotated");
        Set<String> annotationSet;
        if (ArrayUtils.isEmpty(annotations)) {
            annotationSet = CollectionUtils.setOf(AnnotationUtil.SCOPE, Bean.class.getName(), AnnotationUtil.QUALIFIER);
        } else {
            annotationSet = new HashSet<>(Arrays.asList(annotations));
        }
        if (!annotationSet.contains("*")) {
            for (BeanImportHandler beanImportHandler : BEAN_IMPORT_HANDLERS) {
                annotationSet.addAll(beanImportHandler.getSupportedAnnotationNames());
            }
        }
        final String[] packages = element.getAnnotationMetadata().stringValues(Import.class, "packages");

        if (ArrayUtils.isNotEmpty(packages)) {
            for (String aPackage : packages) {
                final ClassElement[] classElements = context
                            .getClassElements(aPackage, annotationSet.toArray(new String[0]));
                for (ClassElement classElement : classElements) {
                    if (!classElement.isAbstract()) {
                        beanElements.add(classElement);
                    }
                }
            }
        }
        return beanElements;
    }

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Collections.singleton(Import.class.getName());
    }

    @NonNull
    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
