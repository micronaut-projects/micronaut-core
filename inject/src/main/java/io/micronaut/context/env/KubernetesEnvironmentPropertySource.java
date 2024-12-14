/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.context.env;

import io.micronaut.core.annotation.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.micronaut.context.env.EnvironmentPropertySource.getEnv;

/**
 * A property source specific for the Kubernetes environment.
 *
 * It excludes Kubernetes-specific environment variables (like FOO_SERVICE_HOST, FOO_SERVICE_PORT, etc.) since they would
 * slow down application startup
 *
 * @author Nilson Pontello
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
public class KubernetesEnvironmentPropertySource extends MapPropertySource {

    /**
     * The name of this property source.
     */
    public static final String NAME = "k8s-env";

    static final List<String> VAR_SUFFIXES = Arrays.asList(
            "_TCP",
            "_TCP_PORT",
            "_TCP_PROTO",
            "_TCP_ADDR",
            "_UDP_PORT",
            "_UDP_PROTO",
            "_UDP_ADDR",
            "_SERVICE_PORT",
            "_SERVICE_PORT_HTTP",
            "_SERVICE_HOST"
    );

    /**
     * Default constructor.
     */
    public KubernetesEnvironmentPropertySource() {
        super(NAME, getEnv(getEnvNoK8s(), null, null));
    }

    /**
     * Allows for control over which environment variables are included.
     *
     * @param includes The environment variables to include in configuration
     * @param excludes The environment variables to exclude from configuration
     */
    public KubernetesEnvironmentPropertySource(@Nullable List<String> includes, @Nullable List<String> excludes) {
        super(NAME, getEnv(getEnvNoK8s(), includes, excludes));
    }

    @Override
    public Origin getOrigin() {
        return EnvironmentPropertySource.ORIGIN;
    }

    @Override
    public int getOrder() {
        return EnvironmentPropertySource.POSITION;
    }

    @Override
    public PropertyConvention getConvention() {
        return PropertyConvention.ENVIRONMENT_VARIABLE;
    }

    static Map<String, String> getEnvNoK8s() {
        Map<String, String> props = new HashMap<>(CachedEnvironment.getenv());
        props.entrySet().removeIf(entry -> VAR_SUFFIXES.stream().anyMatch(s -> entry.getKey().endsWith(s)));
        props.entrySet().removeIf(entry -> entry.getKey().endsWith("_PORT") && entry.getValue().startsWith("tcp://"));
        return props;
    }

}
