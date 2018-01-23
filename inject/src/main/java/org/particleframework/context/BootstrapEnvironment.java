/*
 * Copyright 2018 original authors
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
package org.particleframework.context;

import org.particleframework.context.env.DefaultEnvironment;

/**
 * @author graemerocher
 * @since 1.0
 */
class BootstrapEnvironment extends DefaultEnvironment {

    public static final String PROPERTY_SOURCE_PREFIX = "bootstrap";

    @Override
    protected String getPropertySourceRootName() {
        return PROPERTY_SOURCE_PREFIX;
    }
}
