/*
 * Copyright 2003-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.module.info.runtime;

import io.micronaut.module.info.AbstractMicronautModuleInfo;

import java.util.Set;

public class ServiceLoaderDefinedModule extends AbstractMicronautModuleInfo {
    public ServiceLoaderDefinedModule() {
        super("io.micronaut:micronaut-dummy",
            "dummy",
            "Dummy test module defined as a service",
            "1.0",
            null,
            null,
            Set.of()
        );
    }
}
