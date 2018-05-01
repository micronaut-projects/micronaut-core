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

package io.micronaut.security.session;

import io.micronaut.security.filters.SecurityFilterOrderProvider;
import io.micronaut.session.http.HttpSessionFilter;
import javax.inject.Singleton;

/**
 * {@link SecurityFilterOrderProvider} implementation for Session-Based Authentication.
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
public class SessionSecurityFilterOrderProvider implements SecurityFilterOrderProvider {

    private static final int ORDER_PADDING = 100;

    @Override
    public Integer getSecurityFilterOrder() {
        return HttpSessionFilter.ORDER + ORDER_PADDING;
    }
}
