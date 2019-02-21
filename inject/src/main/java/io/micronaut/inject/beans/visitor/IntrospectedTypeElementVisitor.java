/*
 * Copyright 2017-2019 original authors
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

package io.micronaut.inject.beans.visitor;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.ClassGenerationException;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.*;

/**
 * A {@link TypeElementVisitor} that visits classes annotated with {@link Introspected} and produces
 * {@link io.micronaut.core.beans.BeanIntrospectionReference} instances at compilation time.
 *
 * @author graemerocher
 * @since 1.1
 */
@Internal
public class IntrospectedTypeElementVisitor implements TypeElementVisitor<Introspected, Object> {

    private List<BeanIntrospectionWriter> writers = new ArrayList<>(10);

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        final Introspected introspected = element.synthesize(Introspected.class);
        if (introspected != null) {
            final boolean metadata = introspected.annotationMetadata();
            final BeanIntrospectionWriter writer = new BeanIntrospectionWriter(
                    element.getName(),
                    metadata ? element.getAnnotationMetadata() : null
            );
            final List<PropertyElement> beanProperties = element.getBeanProperties();
            final Set<String> includes = CollectionUtils.setOf(introspected.includes());
            final Set<String> excludes = CollectionUtils.setOf(introspected.excludes());
            final Set<Class<? extends Annotation>> ignored = CollectionUtils.setOf(introspected.ignored());

            for (PropertyElement beanProperty : beanProperties) {
                final ClassElement type = beanProperty.getType();
                if (type != null) {

                    final String name = beanProperty.getName();
                    if (!includes.isEmpty() && !includes.contains(name)) {
                        continue;
                    }
                    if (!excludes.isEmpty() && excludes.contains(name)) {
                        continue;
                    }

                    if (!ignored.isEmpty() && ignored.stream().anyMatch(beanProperty::hasAnnotation)) {
                        continue;
                    }

                    writer.visitProperty(
                            type,
                            name,
                            beanProperty.isReadOnly(),
                            metadata ? beanProperty.getAnnotationMetadata() : null,
                            beanProperty.getType().getTypeArguments()
                    );
                }
            }
            writers.add(writer);
        }
    }

    @Override
    public void finish(VisitorContext visitorContext) {

        for (BeanIntrospectionWriter writer : writers) {
            try {
                writer.accept(visitorContext);
            } catch (IOException e) {
                throw new ClassGenerationException("I/O error occurred during class generation: " + e.getMessage(), e);
            }
        }
    }

    private Map<String, Object> toNestedMap(Map<String, ClassElement> typeArguments) {
        if (typeArguments.isEmpty()) {
            return null;
        } else {
            Map<String, Object> args = new LinkedHashMap<>(typeArguments.size());
            for (Map.Entry<String, ClassElement> entry : typeArguments.entrySet()) {
                final ClassElement ce = entry.getValue();
                if (ce.getTypeArguments().isEmpty()) {
                    args.put(entry.getKey(), ce.getName());
                } else {
                    args.put(entry.getKey(), toNestedMap(ce.getTypeArguments()));
                }
            }
            return args;
        }
    }
}
