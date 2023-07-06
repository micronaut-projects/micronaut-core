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
package io.micronaut.management.endpoint.management.impl;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.hateoas.AbstractResource;
import io.micronaut.management.endpoint.management.ManagementController;

/**
 * <p>Allow {@link ManagementController} to provide a response in HATEOAS architecture.</p>
 *
 * @author Hernán Cervera
 * @since 3.0.0
 */
@Introspected
public class AvailableEndpoints extends AbstractResource<AvailableEndpoints> {
}
