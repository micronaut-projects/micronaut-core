/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.crac.support;

import io.micronaut.core.annotation.Experimental;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Registers all defined Resources for Coordinated Restore at Checkpoint.
 *
 * @author Tim Yates
 * @since 3.7.0
 */
@Experimental
@Singleton
public class OrderedCracResourceRegistrar implements CracResourceRegistrar {

    private final List<? extends OrderedCracResource> resources;
    private final CracContext context;

    /**
     * Collects together all available CRaC resources in the order specified.
     *
     * @param resources The ordered registered CRaC resources
     * @param context   The CRaC context
     */
    public OrderedCracResourceRegistrar(
        List<? extends OrderedCracResource> resources,
        CracContext context
    ) {
        this.resources = resources;
        this.context = context;
    }

    /**
     * For each known resource, register it with the CRaC context.
     */
    @Override
    public void registerResources() {
        resources.forEach(context::register);
    }
}
