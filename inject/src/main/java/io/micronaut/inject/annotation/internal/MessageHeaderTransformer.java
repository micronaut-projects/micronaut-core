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

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.inject.annotation.NamedAnnotationTransformer;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

/**
 * Transforms the message header annotation to the old format to backwards compatibility.
 *
 * @author graemerocher
 * @since 2.4.0
 */
public class MessageHeaderTransformer implements NamedAnnotationTransformer {
    @Override
    public String getName() {
        return "io.micronaut.messaging.annotation.MessageHeader";
    }

    @Override
    public List<AnnotationValue<?>> transform(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        final AnnotationValueBuilder<Annotation> builder = AnnotationValue.builder("io.micronaut.messaging.annotation.Header");
        annotation.stringValue().ifPresent(builder::value);
        annotation.stringValue("name").ifPresent(n -> builder.member("name", n));
        return Collections.singletonList(
                builder.build()
        );
    }
}
