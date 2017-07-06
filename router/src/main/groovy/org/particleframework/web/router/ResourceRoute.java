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
package org.particleframework.web.router;

import org.particleframework.http.HttpMethod;
import org.particleframework.http.MediaType;

/**
 * <p>A resource route is a composite route to a REST endpoint</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ResourceRoute  {

    ResourceRoute accept(MediaType mediaType);

    ResourceRoute nest(Runnable nested);

    /**
     * Whether the route is read-only
     *
     * @param readOnly True if this resource route should be read-only
     * @return A new {@link ResourceRoute}
     */
    ResourceRoute readOnly(boolean readOnly);

    /**
     * Exclude a particular HTTP method from this resource route
     *
     * @param methods The methods to exclude
     * @return The resource route
     */
    ResourceRoute exclude(HttpMethod... methods);
}
