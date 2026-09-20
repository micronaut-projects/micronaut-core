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
package io.micronaut.context.python.runtime;

import io.micronaut.context.python.runtime.model.ArgumentModel;
import io.micronaut.context.python.runtime.model.BeanMethodModel;
import io.micronaut.context.python.runtime.model.ClassModel;
import io.micronaut.context.python.runtime.model.IntrospectionModel;
import io.micronaut.context.python.runtime.model.PropertyModel;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.beans.AbstractInitializableBeanIntrospection;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The immutable objects a generated introspection class initializes itself with. The dispatch indices follow the
 * build-time writer: for each property in order, its read method, then its write method, or the index of the
 * exception thrown when an immutable property is mutated.
 *
 * @since 5.3.0
 */
@Internal
@UsedByGeneratedCode
public final class PythonIntrospectionState {

    private final AnnotationMetadata annotationMetadata;
    private final AnnotationMetadata constructorAnnotationMetadata;
    private final Argument<?> @Nullable [] constructorArguments;
    private final AbstractInitializableBeanIntrospection.BeanPropertyRef<Object>[] propertyRefs;
    private final AbstractInitializableBeanIntrospection.BeanMethodRef<Object> @Nullable [] methodRefs;

    private PythonIntrospectionState(AnnotationMetadata annotationMetadata,
                                     AnnotationMetadata constructorAnnotationMetadata,
                                     Argument<?> @Nullable [] constructorArguments,
                                     AbstractInitializableBeanIntrospection.BeanPropertyRef<Object>[] propertyRefs,
                                     AbstractInitializableBeanIntrospection.BeanMethodRef<Object> @Nullable [] methodRefs) {
        this.annotationMetadata = annotationMetadata;
        this.constructorAnnotationMetadata = constructorAnnotationMetadata;
        this.constructorArguments = constructorArguments;
        this.propertyRefs = propertyRefs;
        this.methodRefs = methodRefs;
    }

    /**
     * Materializes the state of an introspection.
     *
     * @param beanType The bean type
     * @param model    The class model, which must have an introspection
     * @return The state
     * @throws ClassNotFoundException If a property or constructor type cannot be loaded
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static PythonIntrospectionState of(Class<?> beanType, ClassModel model) throws ClassNotFoundException {
        IntrospectionModel introspection = model.introspection();
        if (introspection == null) {
            throw new IllegalArgumentException("The model of " + beanType.getName() + " has no introspection");
        }
        ModelMaterializer materializer = new ModelMaterializer(beanType.getClassLoader());
        AnnotationMetadata annotationMetadata = materializer.annotationMetadata(model.annotationMetadata());
        AnnotationMetadata constructorMetadata = materializer.annotationMetadata(introspection.constructorAnnotationMetadata());
        Argument<?>[] constructorArguments = introspection.constructorArguments().isEmpty() ? null
            : materializer.arguments(introspection.constructorArguments());
        List<PropertyModel> properties = introspection.properties();
        AbstractInitializableBeanIntrospection.BeanPropertyRef<Object>[] refs = new AbstractInitializableBeanIntrospection.BeanPropertyRef[properties.size()];
        int index = 0;
        for (int i = 0; i < refs.length; i++) {
            PropertyModel property = properties.get(i);
            Argument argument = materializer.argument(property.argument());
            int read = -1;
            if (property.readMethod() != null) {
                read = index++;
            }
            int write = -1;
            int with = -1;
            if (property.writeMethod() != null) {
                write = index++;
            } else if (property.readOnly()) {
                with = index++;
            }
            refs[i] = new AbstractInitializableBeanIntrospection.BeanPropertyRef(argument,
                read == -1 ? null : argument, write == -1 ? null : argument, read, write, with, property.readOnly(), !property.readOnly());
        }
        List<BeanMethodModel> beanMethods = introspection.methods();
        AbstractInitializableBeanIntrospection.BeanMethodRef<Object>[] methodRefs =
            new AbstractInitializableBeanIntrospection.BeanMethodRef[beanMethods.size()];
        for (int i = 0; i < methodRefs.length; i++) {
            BeanMethodModel beanMethod = beanMethods.get(i);
            List<ArgumentModel> parameters = beanMethod.method().parameters();
            // The dispatch indices of the methods continue the property indices, as the generated class numbers them
            methodRefs[i] = new AbstractInitializableBeanIntrospection.BeanMethodRef(materializer.argument(beanMethod.returnArgument()),
                beanMethod.method().name(), materializer.annotationMetadata(beanMethod.annotationMetadata()),
                parameters.isEmpty() ? null : materializer.arguments(parameters), index++);
        }
        return new PythonIntrospectionState(annotationMetadata, constructorMetadata, constructorArguments, refs,
            methodRefs.length == 0 ? null : methodRefs);
    }

    /**
     * @return The bean annotation metadata
     */
    public AnnotationMetadata annotationMetadata() {
        return annotationMetadata;
    }

    /**
     * @return The constructor annotation metadata
     */
    public AnnotationMetadata constructorAnnotationMetadata() {
        return constructorAnnotationMetadata;
    }

    /**
     * @return The constructor arguments, or null when the constructor has none
     */
    public Argument<?> @Nullable [] constructorArguments() {
        return constructorArguments;
    }

    /**
     * @return The method references, or null when the introspection exposes none
     */
    public AbstractInitializableBeanIntrospection.BeanMethodRef<Object> @Nullable [] methodRefs() {
        return methodRefs;
    }

    /**
     * @return The property references
     */
    public AbstractInitializableBeanIntrospection.BeanPropertyRef<Object>[] propertyRefs() {
        return propertyRefs;
    }
}
