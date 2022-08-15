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

import io.micronaut.core.annotation.Internal;
import org.crac.Context;
import org.crac.Resource;

/**
 * The bridge between a Micronaut CRaC Resource and the CRaC api.
 * @author Tim Yates
 * @since 3.7.0
 */
@Internal
public class CracFacadeResource implements Resource {

    private final OrderedCracResource orderedCracResource;

    public CracFacadeResource(OrderedCracResource orderedCracResource) {
        this.orderedCracResource = orderedCracResource;
    }

    @Override
    public void beforeCheckpoint(Context<? extends Resource> context) throws Exception {
        orderedCracResource.beforeCheckpoint(createCracContext(context));
    }

    @Override
    public void afterRestore(Context<? extends Resource> context) throws Exception {
        orderedCracResource.afterRestore(createCracContext(context));
    }

    private CracContext createCracContext(Context<? extends Resource> context) {
        return new DefaultCracContext(() -> (Context<Resource>) context);
    }
}
