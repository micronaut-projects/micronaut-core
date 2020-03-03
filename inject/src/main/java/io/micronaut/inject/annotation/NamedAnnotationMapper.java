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

import io.micronaut.core.naming.Named;

import java.lang.annotation.Annotation;

/**
 * A named {@link AnnotationMapper} operates against any named annotation, and does not require the
 * annotation to be on the annotation processor classpath.
 *
 * @author graemerocher
 * @since 1.0
 */
@SuppressWarnings("WeakerAccess")
public interface NamedAnnotationMapper extends AnnotationMapper<Annotation>, Named {
}
