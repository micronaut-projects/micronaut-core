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
package org.particleframework.management.endpoint.routes;

import org.particleframework.http.MediaType;
import org.particleframework.inject.MethodExecutionHandle;
import org.particleframework.management.endpoint.Endpoint;
import org.particleframework.management.endpoint.Read;
import org.particleframework.web.router.Router;
import org.particleframework.web.router.UriRoute;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>Exposes an {@link Endpoint} to display application routes</p>
 *
 * @see org.particleframework.web.router.annotation.Action
 *
 * @author James Kleeh
 * @since 1.0
 */
@Endpoint("routes")
public class RoutesEndpoint {

    private Router router;

    @Inject
    public RoutesEndpoint(Router router) {
        this.router = router;
    }

    @Read
    Map<String, Map<String, String>> getRoutes() {
        return router.uriRoutes().collect(Collectors.toMap(this::getRouteKey, this::getRouteValue));
    }

    protected String getMethodString(MethodExecutionHandle targetMethod) {
        return new StringBuilder()
                .append(targetMethod.getReturnType().getType().getName())
                .append(" ")
                .append(targetMethod.getDeclaringType().getName())
                .append('.')
                .append(targetMethod.getMethodName())
                .append("(")
                .append(Arrays.stream(targetMethod.getArguments())
                        .map(argument -> argument.getType().getName() + " " + argument.getName())
                        .collect(Collectors.joining( ", " )))
                .append(")")
                .toString();
    }

    protected String getRouteKey(UriRoute route) {
        String produces = route.getProduces().stream()
                .map(MediaType::toString)
                .collect(Collectors.joining(" || "));

        return new StringBuilder("{[")
                .append(route.getUriMatchTemplate())
                .append("],method=[")
                .append(route.getHttpMethod().name())
                .append("],produces=[")
                .append(produces)
                .append("]}")
                .toString();
    }

    protected Map<String, String> getRouteValue(UriRoute route) {
        Map<String, String> values = new LinkedHashMap<>(1);

        values.put("method", getMethodString(route.getTargetMethod()));

        return values;
    }
}
