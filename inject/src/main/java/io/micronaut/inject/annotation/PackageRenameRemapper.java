/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.inject.annotation;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.visitor.VisitorContext;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.List;

/**
 * A {@link AnnotationRemapper} that simply renames packages retaining the original simple class
 * names as is.
 *
 * @author graemerocher
 * @since 1.1.0
 */
public interface PackageRenameRemapper extends AnnotationRemapper {

    /**
     * @return The target package name.
     */
    @NonNull String getTargetPackage();

    @Override
    default List<AnnotationValue<?>> remap(AnnotationValue<?> annotation, VisitorContext visitorContext) {
        String simpleName = NameUtils.getSimpleName(annotation.getAnnotationName());
        return Collections.singletonList(
                new AnnotationValue<>(getTargetPackage() + '.' + simpleName, annotation.getValues())
        );
    }
}
