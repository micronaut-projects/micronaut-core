/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.views.model.security;

import io.micronaut.core.util.Toggleable;
import javax.annotation.Nonnull;

/**
 * Configuration for {@link SecurityViewModelProcessor}.
 *
 * @author Sergio del Amo
 * @since 1.1.0
 */
public interface SecurityViewModelProcessorConfiguration extends Toggleable {

    /**
     *
     * @return the key name which will be used in the model map.
     */
    @Nonnull
    String getSecurityKey();

    /**
     *
     * @return the key for the principal name property which is used in the nested security map.
     */
    @Nonnull
    String getPrincipalNameKey();

    /**
     *
     * @return the key for the attributes property which is used in the nested security map.
     */
    @Nonnull
    String getAttributesKey();
}
