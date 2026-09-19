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

import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Any;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Type;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.qualifiers.AnyQualifier;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Derives the qualifier of an injected argument from its resolved annotation metadata, with the rules the build-time
 * writer applies when it decides which {@link Qualifiers} factory to emit.
 *
 * @since 5.3.0
 */
@Internal
final class PythonQualifiers {

    private PythonQualifiers() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static @Nullable Qualifier<?> qualifier(Argument<?> argument, ClassLoader classLoader) throws ClassNotFoundException {
        AnnotationMetadata metadata = argument.getAnnotationMetadata();
        List<String> qualifierNames = metadata.getAnnotationNamesByStereotype(AnnotationUtil.QUALIFIER);
        if (!qualifierNames.isEmpty()) {
            // @Primary narrows nothing, so it is the same as no qualifier and takes no part in a composite either
            List<String> narrowing = qualifierNames.stream().filter(name -> !name.equals(Primary.NAME)).toList();
            if (narrowing.isEmpty()) {
                return null;
            }
            if (narrowing.size() == 1) {
                return forAnnotation(argument, metadata, narrowing.getFirst());
            }
            List<Qualifier> qualifiers = new ArrayList<>(narrowing.size());
            for (String name : narrowing) {
                qualifiers.add(forAnnotation(argument, metadata, name));
            }
            return Qualifiers.byQualifiers(qualifiers.toArray(Qualifier[]::new));
        }
        if (metadata.hasAnnotation(AnnotationUtil.ANN_INTERCEPTOR_BINDING_QUALIFIER)) {
            return Qualifiers.byInterceptorBinding(metadata);
        }
        String[] byType = metadata.hasDeclaredAnnotation(Type.NAME) ? metadata.stringValues(Type.NAME) : null;
        if (byType != null && byType.length > 0) {
            Class<?>[] types = new Class[byType.length];
            for (int i = 0; i < byType.length; i++) {
                types[i] = ModelTypes.resolve(byType[i], classLoader);
            }
            return Qualifiers.byType(types);
        }
        return null;
    }

    private static @Nullable Qualifier<?> forAnnotation(Argument<?> argument, AnnotationMetadata metadata, String annotationName) {
        if (annotationName.equals(Primary.NAME)) {
            return null;
        }
        if (annotationName.equals(AnnotationUtil.NAMED)) {
            String name = metadata.stringValue(AnnotationUtil.NAMED).orElseGet(argument::getName);
            if (!name.contains("$")) {
                return Qualifiers.byName(name);
            }
            return Qualifiers.forArgument(argument);
        }
        if (annotationName.equals(Any.NAME)) {
            return AnyQualifier.INSTANCE;
        }
        String repeatableContainer = metadata.findRepeatableAnnotation(annotationName).orElse(null);
        if (repeatableContainer != null) {
            return Qualifiers.byRepeatableAnnotation(metadata, repeatableContainer);
        }
        return Qualifiers.byAnnotation(metadata, annotationName);
    }
}
