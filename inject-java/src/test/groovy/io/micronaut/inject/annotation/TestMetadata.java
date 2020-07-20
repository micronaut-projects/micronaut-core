/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.inject.annotation;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationUtil;
import io.reactivex.Flowable;

public class TestMetadata extends DefaultAnnotationMetadata {
    public TestMetadata() {
    }

    static {
        if (!DefaultAnnotationMetadata.areAnnotationDefaultsRegistered("io.micronaut.context.annotation.Requires")) {
            DefaultAnnotationMetadata.registerAnnotationDefaults("io.micronaut.context.annotation.Requires", AnnotationUtil.internMapOf(new Object[]{"missing", new Object[0], "notEnv", new Object[0], "missingConfigurations", new Object[0], "entities", new Object[0], "missingBeans", new Object[0], "condition", $micronaut_load_class_value_2(), "env", new Object[0], "classes", new Object[0], "sdk", "MICRONAUT", "beans", new Object[0]}));
        }

    }

    static AnnotationClassValue $micronaut_load_class_value_2() {
        try {
            return new AnnotationClassValue(Flowable.class);
        } catch (Throwable e) {
            return new AnnotationClassValue("io.reactivex.Flowable");
        }
    }
}

