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
package io.micronaut.inject.factory.nullreturn;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;

public class Test1 {

        public static final AnnotationMetadata $ANNOTATION_METADATA = new DefaultAnnotationMetadata(null, null, null, null, null);

    static {
        if (!DefaultAnnotationMetadata.areAnnotationDefaultsRegistered("io.micronaut.context.annotation.Requires")) {
            DefaultAnnotationMetadata.registerAnnotationDefaults(new AnnotationClassValue<Object>("Requires"), AnnotationUtil.internMapOf(new Object[]{"condition", new AnnotationClassValue<Object>("Requires"), "beans", new Object[0], "notEnv", new Object[0], "resources", new Object[0], "env", new Object[0], "entities", new Object[0], "missingConfigurations", new Object[0], "missing", new Object[0], "missingBeans", new Object[0], "classes", new Object[0], "missingClasses", new Object[0], "sdk", "MICRONAUT"}));
        }

    }
}
