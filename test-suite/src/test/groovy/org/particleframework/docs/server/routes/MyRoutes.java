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
package org.particleframework.docs.server.routes;

// tag::imports[]
import org.particleframework.context.ExecutionHandleLocator;
import org.particleframework.web.router.DefaultRouteBuilder;

import javax.inject.*;
// end::imports[]

/**
 * @author Graeme Rocher
 * @since 1.0
 */

// tag::class[]
@Singleton
public class MyRoutes extends DefaultRouteBuilder { // <1>
    public MyRoutes(ExecutionHandleLocator executionHandleLocator, UriNamingStrategy uriNamingStrategy) {
        super(executionHandleLocator, uriNamingStrategy);
    }

    @Inject
    void messageRoutes(MessageController messageController) { // <2>
        GET("/hello/{name}", messageController, "hello", String.class); // <3>
    }
}
// end::class[]
