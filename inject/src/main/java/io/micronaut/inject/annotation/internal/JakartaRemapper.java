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
package io.micronaut.inject.annotation.internal;

import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.annotation.AnnotationRemapper;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Internal
public final class JakartaRemapper implements AnnotationRemapper {

    private static final Pattern JAKARTA = Pattern.compile("^jakarta");

    @Override
    @NonNull
    public String getPackageName() {
        return "jakarta.inject";
    }

    @Override
    @NonNull public List<AnnotationValue<?>> remap(AnnotationValue<?> annotation, VisitorContext visitorContext) {
        String name = annotation.getAnnotationName();
        Matcher matcher = JAKARTA.matcher(name);

        AnnotationValue<?> stereotype = null;
        if (name.equals(Named.class.getName())) {
            stereotype = AnnotationValue.builder(AnnotationUtil.QUALIFIER).build();
        } else if (name.equals(Singleton.class.getName())) {
            stereotype = AnnotationValue.builder(AnnotationUtil.SCOPE).build();
        }

        return Collections.singletonList(
                AnnotationValue.builder(matcher.replaceFirst("javax"))
                        .members(annotation.getValues())
                        .stereotype(stereotype)
                        .build()
        );
    }
}
