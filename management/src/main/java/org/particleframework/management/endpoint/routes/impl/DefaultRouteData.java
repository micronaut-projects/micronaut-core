package org.particleframework.management.endpoint.routes.impl;

import org.particleframework.context.annotation.Requires;
import org.particleframework.inject.MethodExecutionHandle;
import org.particleframework.management.endpoint.routes.RouteData;
import org.particleframework.management.endpoint.routes.RoutesEndpoint;
import org.particleframework.web.router.UriRoute;

import javax.inject.Singleton;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
@Requires(beans = RoutesEndpoint.class)
public class DefaultRouteData implements RouteData<Map<String, String>> {

    @Override
    public Map<String, String> getData(UriRoute route) {
        Map<String, String> values = new LinkedHashMap<>(1);

        values.put("method", getMethodString(route.getTargetMethod()));

        return values;
    }

    protected String getMethodString(MethodExecutionHandle targetMethod) {
        return new StringBuilder()
                .append(targetMethod.getReturnType().asArgument().getTypeString(false))
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
}
