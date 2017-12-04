/*
 * Copyright 2017 original authors
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
package org.particleframework.management.endpoint;

import org.particleframework.context.annotation.Argument;
import org.particleframework.context.annotation.EachProperty;
import org.particleframework.core.util.Toggleable;

/**
 * An {@link Endpoint} configuration
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@EachProperty(value = "endpoints", primary = "all")
public class EndpointConfiguration implements Toggleable {

    protected boolean enabled = true;
    protected boolean sensitive;
    protected String id;

    public EndpointConfiguration(@Argument String id) {
        this.id = id;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @return Does the endpoint expose sensitive information
     */
    public boolean isSensitive() {
        return sensitive;
    }

    /**
     * @return The ID of the endpoint
     * @see Endpoint#value()
     */
    public String getId() {
        return id;
    }
}
