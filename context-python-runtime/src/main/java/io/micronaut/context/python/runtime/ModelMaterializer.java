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

import io.micronaut.context.python.runtime.model.AnnotationMetadataModel;
import io.micronaut.context.python.runtime.model.AnnotationValueModel;
import io.micronaut.context.python.runtime.model.ArgumentModel;
import io.micronaut.context.python.runtime.model.ArrayValueModel;
import io.micronaut.context.python.runtime.model.ClassValueModel;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.annotation.AnnotationMetadataSupport;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Materializes runtime objects from the model: annotation metadata, arguments and typed annotation values. The
 * objects are the same ones the build-time writer's static initializers create.
 *
 * @since 5.3.0
 */
@Internal
public final class ModelMaterializer {

    private final ClassLoader classLoader;

    /**
     * @param classLoader The class loader of the described class
     */
    public ModelMaterializer(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * Materializes annotation metadata, registering its annotation defaults and repeatable containers first.
     *
     * @param model The model
     * @return The metadata
     */
    public AnnotationMetadata annotationMetadata(AnnotationMetadataModel model) {
        for (Map.Entry<String, Map<String, Object>> entry : model.annotationDefaults().entrySet()) {
            DefaultAnnotationMetadata.registerAnnotationDefaults(classValue(entry.getKey()), values(entry.getValue()));
        }
        if (!model.repeatableContainers().isEmpty()) {
            DefaultAnnotationMetadata.registerRepeatableAnnotations(model.repeatableContainers());
        }
        if (model.isEmpty()) {
            return AnnotationMetadata.EMPTY_METADATA;
        }
        return new DefaultAnnotationMetadata(
            annotations(model.declaredAnnotations()),
            annotations(model.declaredStereotypes()),
            annotations(model.allStereotypes()),
            annotations(model.allAnnotations()),
            new LinkedHashMap<>(model.annotationsByStereotype()),
            model.hasPropertyExpressions(),
            false
        );
    }

    /**
     * Materializes an argument.
     *
     * @param model The model
     * @return The argument
     * @throws ClassNotFoundException If the type cannot be loaded
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Argument<?> argument(ArgumentModel model) throws ClassNotFoundException {
        Class type = ModelTypes.resolve(model.typeName(), classLoader);
        if (model.annotationMetadata().isEmpty() && model.typeArguments().isEmpty()) {
            return Argument.of(type, model.name());
        }
        return Argument.of(type, model.name(), annotationMetadata(model.annotationMetadata()), arguments(model.typeArguments()));
    }

    /**
     * Materializes arguments.
     *
     * @param models The models
     * @return The arguments
     * @throws ClassNotFoundException If a type cannot be loaded
     */
    public Argument<?>[] arguments(List<ArgumentModel> models) throws ClassNotFoundException {
        if (models.isEmpty()) {
            return Argument.ZERO_ARGUMENTS;
        }
        Argument<?>[] arguments = new Argument[models.size()];
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = argument(models.get(i));
        }
        return arguments;
    }

    /**
     * Resolves a type name.
     *
     * @param typeName The type name
     * @return The class
     * @throws ClassNotFoundException If the type cannot be loaded
     */
    public Class<?> resolve(String typeName) throws ClassNotFoundException {
        return ModelTypes.resolve(typeName, classLoader);
    }

    private Map<String, Map<CharSequence, Object>> annotations(Map<String, Map<String, Object>> model) {
        if (model.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<CharSequence, Object>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : model.entrySet()) {
            result.put(entry.getKey(), values(entry.getValue()));
        }
        return result;
    }

    private Map<CharSequence, Object> values(Map<String, Object> model) {
        if (model.isEmpty()) {
            return Map.of();
        }
        Map<CharSequence, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : model.entrySet()) {
            values.put(entry.getKey(), value(entry.getValue()));
        }
        return values;
    }

    private Object value(Object value) {
        return switch (value) {
            case ClassValueModel classValue -> classValue(classValue.name());
            case AnnotationValueModel annotation -> new AnnotationValue<>(annotation.annotationName(), values(annotation.values()),
                AnnotationMetadataSupport.ANNOTATION_DEFAULT_VALUES_PROVIDER);
            case ArrayValueModel array -> array(array);
            default -> value;
        };
    }

    private Object array(ArrayValueModel array) {
        List<Object> elements = new ArrayList<>(array.elements().size());
        for (Object element : array.elements()) {
            elements.add(value(element));
        }
        Class<?> componentType = switch (array.componentKind()) {
            case STRING -> String.class;
            case BOOLEAN -> array.primitive() ? boolean.class : Boolean.class;
            case BYTE -> array.primitive() ? byte.class : Byte.class;
            case SHORT -> array.primitive() ? short.class : Short.class;
            case INT -> array.primitive() ? int.class : Integer.class;
            case LONG -> array.primitive() ? long.class : Long.class;
            case FLOAT -> array.primitive() ? float.class : Float.class;
            case DOUBLE -> array.primitive() ? double.class : Double.class;
            case CHAR -> array.primitive() ? char.class : Character.class;
            case CLASS -> AnnotationClassValue.class;
            case ANNOTATION -> AnnotationValue.class;
            case ARRAY, OBJECT -> Object.class;
        };
        Object result = Array.newInstance(componentType, elements.size());
        for (int i = 0; i < elements.size(); i++) {
            Array.set(result, i, elements.get(i));
        }
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private AnnotationClassValue<?> classValue(String name) {
        try {
            return new AnnotationClassValue(Class.forName(name, false, classLoader));
        } catch (ClassNotFoundException | LinkageError e) {
            // Same fallback as the generated class-value helpers: a name the application cannot load stays a name.
            return new AnnotationClassValue<>(name);
        }
    }
}
