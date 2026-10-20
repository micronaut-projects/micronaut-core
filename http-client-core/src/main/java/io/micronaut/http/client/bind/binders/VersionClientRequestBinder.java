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
package io.micronaut.http.client.bind.binders;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.version.annotation.Version;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.bind.AnnotatedClientRequestBinder;
import io.micronaut.http.client.bind.ClientRequestUriContext;
import io.micronaut.http.client.interceptor.configuration.ClientVersioningConfiguration;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An {@link AnnotatedClientRequestBinder} implementation for {@link Version}.
 */
public class VersionClientRequestBinder implements AnnotatedClientRequestBinder<Version> {
    private final Map<String, ClientVersioningConfiguration> versioningConfigurations =
            new ConcurrentHashMap<>(5);
    private final BeanContext beanContext;

    public VersionClientRequestBinder(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public void bind(MethodInvocationContext<Object, Object> context,
                     ClientRequestUriContext uriContext,
                     MutableHttpRequest<?> request) {
        // The version and the client of the method are kept by the method
        Plan plan = context.getCachedValue(Plan.class, Plan::of);
        String version = plan.version();
        if (version == null) {
            return;
        }
        ClientVersioningConfiguration configuration = getVersioningConfiguration(plan.clientId());
        configuration.getHeaders()
                .forEach(header -> request.header(header, version));
        configuration.getParameters()
                .forEach(parameter -> uriContext.addQueryParameter(parameter, version));
    }

    @Override
    public Class<Version> getAnnotationType() {
        return Version.class;
    }

    private ClientVersioningConfiguration getVersioningConfiguration(@Nullable String clientId) {
        return versioningConfigurations.computeIfAbsent(clientId, id ->
                beanContext.findBean(ClientVersioningConfiguration.class, Qualifiers.byName(id))
                        .orElseGet(() -> beanContext.findBean(ClientVersioningConfiguration.class, Qualifiers.byName(ClientVersioningConfiguration.DEFAULT))
                                .orElseThrow(() -> new ConfigurationException("Attempt to apply a '@Version' to the request, but " +
                                        "versioning configuration found neither for '" + id + "' nor '" + ClientVersioningConfiguration.DEFAULT + "' provided.")
                                )));

    }

    /**
     * What the binder reads from the annotations of a client method.
     *
     * @param version  The version, {@code null} when the method has none or it is empty
     * @param clientId The client of the method, if any
     */
    private record Plan(@Nullable String version, @Nullable String clientId) {

        static Plan of(ExecutableMethod<?, ?> method) {
            AnnotationValue<Version> version = method.getAnnotation(Version.class);
            String value = version != null ? version.stringValue().filter(StringUtils::isNotEmpty).orElse(null) : null;
            return new Plan(value, method.stringValue(Client.class).orElse(null));
        }
    }
}
