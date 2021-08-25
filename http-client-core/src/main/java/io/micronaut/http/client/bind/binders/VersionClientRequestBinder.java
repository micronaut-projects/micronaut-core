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
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.version.annotation.Version;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.bind.AnnotatedClientRequestBinder;
import io.micronaut.http.client.bind.ClientRequestUriContext;
import io.micronaut.http.client.interceptor.configuration.ClientVersioningConfiguration;
import io.micronaut.inject.qualifiers.Qualifiers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VersionClientRequestBinder implements AnnotatedClientRequestBinder<Version> {
    private final Map<String, ClientVersioningConfiguration> versioningConfigurations =
            new ConcurrentHashMap<>(5);
    private final BeanContext beanContext;

    public VersionClientRequestBinder(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public void bind(
            @NonNull MethodInvocationContext<Object, Object> context,
            @NonNull ClientRequestUriContext uriContext,
            @NonNull MutableHttpRequest<?> request
    ) {
        context.findAnnotation(Version.class)
                .flatMap(AnnotationValue::stringValue)
                .filter(StringUtils::isNotEmpty)
                .ifPresent(version -> {
                    ClientVersioningConfiguration configuration =
                            getVersioningConfiguration(context.getAnnotationMetadata());

                    configuration.getHeaders()
                            .forEach(header -> request.header(header, version));
                    configuration.getParameters()
                            .forEach(parameter -> uriContext.addQueryParameter(parameter, version));
                });
    }

    @Override
    @NonNull
    public Class<Version> getAnnotationType() {
        return Version.class;
    }

    private ClientVersioningConfiguration getVersioningConfiguration(AnnotationMetadata annotationMetadata) {
        return versioningConfigurations.computeIfAbsent(getClientId(annotationMetadata), clientId ->
                beanContext.findBean(ClientVersioningConfiguration.class, Qualifiers.byName(clientId))
                        .orElseGet(() -> beanContext.findBean(ClientVersioningConfiguration.class, Qualifiers.byName(ClientVersioningConfiguration.DEFAULT))
                                .orElseThrow(() -> new ConfigurationException("Attempt to apply a '@Version' to the request, but " +
                                        "versioning configuration found neither for '" + clientId + "' nor '" + ClientVersioningConfiguration.DEFAULT + "' provided.")
                                )));

    }

    private String getClientId(AnnotationMetadata clientAnn) {
        return clientAnn.stringValue(Client.class).orElse(null);
    }
}