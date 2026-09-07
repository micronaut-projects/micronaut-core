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
package io.micronaut.python.processing.annotation;

import io.micronaut.aop.Around;
import io.micronaut.aop.InterceptorBinding;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.Introduction;
import io.micronaut.context.annotation.Type;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.python.processing.model.DecoratorDef;
import io.micronaut.python.processing.model.ElementDef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Synthesises {@code @InterceptorBinding} metadata from the {@code @Around} and {@code @Introduction}
 * stereotypes of Python decorators.
 *
 * @since 5.2.0
 */
@Internal
final class PythonInterceptorBindings {

    private final AnnotationLookups lookups;
    private final PythonAnnotationValues annotationValues;

    PythonInterceptorBindings(AnnotationLookups lookups, PythonAnnotationValues values) {
        this.lookups = lookups;
        this.annotationValues = values;
    }

    /**
     * Adds the {@code @InterceptorBinding} entries implied by the element's decorators.
     *
     * @param annotationMetadata The metadata to update
     * @param element            The element
     */
    void apply(MutableAnnotationMetadata annotationMetadata, ElementDef element) {
        Map<String, BindingDefinition> bindingAnnotationNames = new LinkedHashMap<>();
        for (DecoratorDef decorator : element.decorators()) {
            collectBindingAnnotationNames(decorator, bindingAnnotationNames);
        }
        if (bindingAnnotationNames.isEmpty()) {
            return;
        }
        List<AnnotationValue<InterceptorBinding>> existingBindings = annotationMetadata.getAnnotationValuesByType(InterceptorBinding.class);
        List<AnnotationValue<InterceptorBinding>> updatedBindings = new ArrayList<>(existingBindings.size() + bindingAnnotationNames.size());
        Set<BindingKey> existingBindingsKeys = new LinkedHashSet<>();
        boolean changed = false;
        for (AnnotationValue<InterceptorBinding> binding : annotationMetadata.getAnnotationValuesByType(InterceptorBinding.class)) {
            String bindingAnnotationName = binding.stringValue().orElse(null);
            InterceptorKind kind = binding.enumValue("kind", InterceptorKind.class).orElse(InterceptorKind.AROUND);
            if (bindingAnnotationName != null) {
                existingBindingsKeys.add(new BindingKey(bindingAnnotationName, kind));
                BindingDefinition bindingDefinition = bindingAnnotationNames.get(bindingAnnotationName);
                if (bindingDefinition != null && bindingDefinition.kind() == kind && bindingDefinition.interceptorType() != null && !hasInterceptorType(binding, bindingDefinition.interceptorType())) {
                    updatedBindings.add(buildInterceptorBinding(bindingAnnotationName, bindingDefinition));
                    changed = true;
                    continue;
                }
            }
            updatedBindings.add(binding);
        }
        for (Map.Entry<String, BindingDefinition> entry : bindingAnnotationNames.entrySet()) {
            String bindingAnnotationName = entry.getKey();
            BindingDefinition bindingDefinition = entry.getValue();
            if (existingBindingsKeys.add(new BindingKey(bindingAnnotationName, bindingDefinition.kind()))) {
                updatedBindings.add(buildInterceptorBinding(bindingAnnotationName, bindingDefinition));
                changed = true;
            }
        }
        if (changed) {
            annotationMetadata.removeAnnotation(AnnotationUtil.ANN_INTERCEPTOR_BINDING);
            annotationMetadata.removeAnnotation(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS);
            for (AnnotationValue<InterceptorBinding> binding : updatedBindings) {
                annotationMetadata.addDeclaredRepeatable(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS, binding);
            }
        }
    }

    private boolean hasInterceptorType(AnnotationValue<InterceptorBinding> binding, AnnotationClassValue<?> interceptorType) {
        return binding.annotationClassValue("interceptorType")
            .map(existingType -> existingType.getName().equals(interceptorType.getName()))
            .orElse(false);
    }

    private AnnotationValue<InterceptorBinding> buildInterceptorBinding(
        String bindingAnnotationName,
        BindingDefinition bindingDefinition
    ) {
        AnnotationValueBuilder<InterceptorBinding> binding = AnnotationValue.builder(InterceptorBinding.class)
            .member(AnnotationMetadata.VALUE_MEMBER, new AnnotationClassValue<>(bindingAnnotationName))
            .member("kind", bindingDefinition.kind());
        AnnotationClassValue<?> interceptorType = bindingDefinition.interceptorType();
        if (interceptorType != null) {
            binding.member("interceptorType", interceptorType);
        }
        return binding.build();
    }

    private void collectBindingAnnotationNames(DecoratorDef decorator, Map<String, BindingDefinition> bindingAnnotationNames) {
        DecoratorDef resolvedDecorator = resolveDecoratorDefinition(decorator);
        if (hasDirectAroundStereotype(resolvedDecorator)) {
            bindingAnnotationNames.putIfAbsent(
                lookups.binaryClassName(decorator.annotationName()),
                new BindingDefinition(InterceptorKind.AROUND, interceptorType(resolvedDecorator))
            );
        }
        if (hasDirectIntroductionStereotype(resolvedDecorator)) {
            bindingAnnotationNames.putIfAbsent(
                lookups.binaryClassName(decorator.annotationName()),
                new BindingDefinition(InterceptorKind.INTRODUCTION, null)
            );
        }
        for (DecoratorDef stereotype : resolvedDecorator.stereotypes()) {
            collectBindingAnnotationNames(stereotype, bindingAnnotationNames);
        }
    }

    private @Nullable AnnotationClassValue<?> interceptorType(DecoratorDef decorator) {
        for (DecoratorDef stereotype : decorator.stereotypes()) {
            if (Type.class.getName().equals(lookups.binaryClassName(stereotype.annotationName()))) {
                AnnotationClassValue<?>[] values = annotationValues.classValues(stereotype.members().get(AnnotationMetadata.VALUE_MEMBER));
                if (values.length > 0) {
                    return values[0];
                }
            }
        }
        ClassElement javaAnnotationType = lookups.javaAnnotationType(decorator);
        if (javaAnnotationType != null) {
            AnnotationValue<Type> type = javaAnnotationType.getAnnotation(Type.class);
            if (type != null) {
                AnnotationClassValue<?>[] values = type.annotationClassValues(AnnotationMetadata.VALUE_MEMBER);
                if (values.length > 0) {
                    return values[0];
                }
            }
        }
        return null;
    }

    private DecoratorDef resolveDecoratorDefinition(DecoratorDef decorator) {
        String annotationName = lookups.binaryClassName(decorator.annotationName());
        DecoratorDef resolved = lookups.decoratorDef(annotationName);
        if (resolved != null) {
            return resolved;
        }
        Optional<ElementDef> annotationMirror = lookups.annotationMirror(annotationName);
        if (annotationMirror.isPresent()) {
            return new DecoratorDef(
                annotationName,
                annotationName,
                null,
                Map.of(),
                annotationMirror.get().decorators()
            );
        }
        return decorator;
    }

    private boolean hasDirectAroundStereotype(DecoratorDef decorator) {
        for (DecoratorDef stereotype : decorator.stereotypes()) {
            if (Around.class.getName().equals(lookups.binaryClassName(stereotype.annotationName()))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDirectIntroductionStereotype(DecoratorDef decorator) {
        for (DecoratorDef stereotype : decorator.stereotypes()) {
            if (Introduction.class.getName().equals(lookups.binaryClassName(stereotype.annotationName()))) {
                return true;
            }
        }
        return false;
    }

    private record BindingDefinition(InterceptorKind kind, @Nullable AnnotationClassValue<?> interceptorType) {
    }

    private record BindingKey(String annotationName, InterceptorKind kind) {
    }
}
