/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.views;

import io.micronaut.context.annotation.Requires;
import io.micronaut.security.filters.SecurityFilter;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Ensures the views filter is applied after the security filter.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Singleton
@Requires(beans = SecurityFilter.class)
public class SecuredViewsFilterOrderProvider implements ViewsFilterOrderProvider {

    @Inject
    protected SecurityFilter securityFilter;

    @Override
    public int getOrder() {
        return securityFilter.getOrder() + 100;
    }
}
