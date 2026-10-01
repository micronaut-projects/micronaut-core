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
package io.micronaut.python.annotation.processing.test.dataclass;

import java.util.List;

/**
 * A Java interface read through JavaBean getters, like the Kubernetes client's {@code KubernetesObject}: a dataclass
 * implements it through attributes of the same names.
 */
public interface VersionedObject {

    String getApiVersion();

    String getKind();

    boolean isEnabled();

    List<? extends CharSequence> getLabels();

    default String describe() {
        return getKind() + "/" + getApiVersion();
    }
}
