/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.python.processing.element;

import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.inject.ast.AnnotationElement;
import org.jspecify.annotations.Nullable;
import io.micronaut.python.processing.PythonProcessingEnvironment;
import io.micronaut.python.processing.model.ClassDef;
import io.micronaut.python.processing.annotation.PythonAnnotationMetadataBuilder;
import io.micronaut.python.processing.model.DecoratorDef;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Class element implementation for Python annotations, which are declared as decorators.
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Experimental
public final class PythonAnnotationElement extends PythonClassElement implements AnnotationElement {

    /**
     * @param classDef    The class definition synthesized for the decorator
     * @param environment The environment
     */
    public PythonAnnotationElement(ClassDef classDef, PythonProcessingEnvironment environment) {
        super(classDef, environment, 0, null, true);
    }

    private PythonAnnotationElement(ClassDef classDef, PythonProcessingEnvironment environment, boolean initializeClassMetadata) {
        super(classDef, environment, 0, null, initializeClassMetadata);
    }

    @Override
    protected PythonClassElement copyThis() {
        return new PythonAnnotationElement(getNativeType(), environment, false);
    }

    @Override
    public boolean isInherited() {
        // the decorators of the synthesized class definition are the decorators applied to the
        // decorator that declares the annotation
        for (DecoratorDef decorator : getNativeType().decorators()) {
            if (AnnotationUtil.ANN_INHERITED.equals(decorator.annotationName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Set<ElementType> getTargets() {
        // A Python declaration may carry java.lang.annotation.Target like it carries @Inherited
        for (DecoratorDef decorator : getNativeType().decorators()) {
            if (Target.class.getName().equals(decorator.annotationName())) {
                EnumSet<ElementType> targets = EnumSet.noneOf(ElementType.class);
                for (Object value : decorator.members().values()) {
                    addTargets(value, targets);
                }
                return Collections.unmodifiableSet(targets);
            }
        }
        // A Java annotation used from Python answers with its own declaration
        AnnotationElement javaAnnotation = javaAnnotation();
        if (javaAnnotation != null) {
            return javaAnnotation.getTargets();
        }
        return DEFAULT_TARGETS;
    }

    private static void addTargets(Object value, EnumSet<ElementType> targets) {
        if (value instanceof Iterable<?> values) {
            for (Object item : values) {
                addTargets(item, targets);
            }
        } else if (value instanceof Object[] values) {
            for (Object item : values) {
                addTargets(item, targets);
            }
        } else if (value instanceof ElementType elementType) {
            targets.add(elementType);
        } else if (value != null) {
            String name = value.toString();
            name = name.substring(name.lastIndexOf('.') + 1);
            for (ElementType elementType : ElementType.values()) {
                if (elementType.name().equals(name)) {
                    targets.add(elementType);
                    break;
                }
            }
        }
    }

    @Override
    public Optional<String> getRepeatableContainer() {
        // The repeated name of the Python declaration, or the @Repeatable of the Java annotation
        PythonAnnotationMetadataBuilder builder = environment.annotationMetadataBuilder();
        DecoratorDef declaration = builder.decoratorDef(getName());
        if (declaration != null && declaration.repeatedName() != null) {
            return Optional.of(builder.binaryClassName(declaration.repeatedName()));
        }
        return Optional.ofNullable(builder.getRepeatableContainerNameForType(getNativeType()));
    }

    @Override
    public RetentionPolicy getRetentionPolicy() {
        for (DecoratorDef decorator : getNativeType().decorators()) {
            if (Retention.class.getName().equals(decorator.annotationName())) {
                for (Object value : decorator.members().values()) {
                    if (value instanceof RetentionPolicy retentionPolicy) {
                        return retentionPolicy;
                    }
                    if (value != null) {
                        String name = value.toString();
                        name = name.substring(name.lastIndexOf('.') + 1);
                        for (RetentionPolicy retentionPolicy : RetentionPolicy.values()) {
                            if (retentionPolicy.name().equals(name)) {
                                return retentionPolicy;
                            }
                        }
                    }
                }
            }
        }
        return environment.annotationMetadataBuilder().getRetentionPolicy(getNativeType());
    }

    @Nullable
    private AnnotationElement javaAnnotation() {
        if (environment.javaVisitorContext() == null) {
            return null;
        }
        return environment.javaVisitorContext().getClassElement(getName())
            .filter(AnnotationElement.class::isInstance)
            .map(AnnotationElement.class::cast)
            .orElse(null);
    }
}
