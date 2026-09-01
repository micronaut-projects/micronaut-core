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
package io.micronaut.core.io.service;

import io.micronaut.core.annotation.Experimental;

import java.util.List;

/**
 * The single generated entry point to every {@link ServiceAggregator} on an application's classpath.
 *
 * <p>A {@link ServiceAggregator} removes reflection from loading the services inside a module, but
 * finding the module aggregators themselves would still mean one {@code Class.forName} per module.
 * That is not free: it is about a millisecond per module, enough to cancel out the saving for
 * applications built from many small modules.</p>
 *
 * <p>A root is generated where the whole classpath is known — the application build — and simply
 * constructs each module aggregator directly, so the only class resolved by name at runtime is the
 * root itself. A root must cover every aggregator on the application classpath, because the runtime
 * stops looking at the per-module entries once it finds one.</p>
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Experimental
public interface ServiceAggregatorRoot {

    /**
     * The service name roots are advertised under.
     */
    String SERVICE_NAME = "io.micronaut.core.io.service.ServiceAggregatorRoot";

    /**
     * @return Every module aggregator on the application classpath, freshly constructed
     */
    List<ServiceAggregator> getAggregators();

}
