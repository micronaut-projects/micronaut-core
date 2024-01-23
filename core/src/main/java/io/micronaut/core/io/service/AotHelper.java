/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.core.io.service;

import io.micronaut.core.annotation.Internal;

/**
 * AOT-specific helper.
 *
 * @since 4.2.0
 * @author Jonas Konrad
 */
@Internal
final class AotHelper {
    /**
     * If this setting is enabled, any attempts to use {@link io.micronaut.core.io.service.SoftServiceLoader} on a
     * service type that is not optimized by aot will produce an exception.
     */
    private static final String STRICT_SERVICE_LOADER_PROPERTY = "io.micronaut.aot.strict-service-loader";
    private static final boolean STRICT_SERVICE_LOADER = Boolean.getBoolean(STRICT_SERVICE_LOADER_PROPERTY);

    private AotHelper() {
    }

    /**
     * Check whether loading a dynamic service is allowed. Will throw an exception otherwise.
     *
     * @param name The name of the service, for the exception error message
     */
    public static void checkDynamicServiceLoad(String name) {
        if (STRICT_SERVICE_LOADER && !"buildtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))) {
            throw new UnsupportedOperationException("Dynamic service loading is forbidden, but there is no static service loader definition for " + name + ". Either add this service to the `service.types` micronaut-aot setting, or disable the `" + STRICT_SERVICE_LOADER_PROPERTY + "` system property.");
        }
    }
}
