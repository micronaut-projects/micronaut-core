/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.client.bind;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.format.Format;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.uri.UriMatchTemplate;

import java.util.List;
import java.util.Map;

/**
 * Handles arguments relating to the URI or partial body.
 *
 * @author James Kleeh
 * @since 2.1.0
 */
@Internal
public class DefaultClientBinder implements ClientArgumentRequestBinder<Object> {

    private final Map<String, Object> paramMap;
    private final Map<String, String> queryParams;
    private final List<Argument> bodyArguments;
    private final UriMatchTemplate uriTemplate;
    private final ConversionService<?> conversionService;

    public DefaultClientBinder(Map<String, Object> paramMap,
                               Map<String, String> queryParams,
                               List<Argument> bodyArguments,
                               UriMatchTemplate uriTemplate,
                               ConversionService<?> conversionService) {
        this.paramMap = paramMap;
        this.queryParams = queryParams;
        this.bodyArguments = bodyArguments;
        this.uriTemplate = uriTemplate;
        this.conversionService = conversionService;
    }

    @Override
    public void bind(@NonNull ArgumentConversionContext<Object> context, @NonNull Object value, @NonNull MutableHttpRequest<?> request) {
        AnnotationMetadata metadata = context.getAnnotationMetadata();
        Argument argument = context.getArgument();
        String argumentName = argument.getName();
        ArgumentConversionContext<String> stringConversion = ConversionContext.of(String.class).with(metadata);
        if (metadata.isAnnotationPresent(QueryValue.class)) {
            String parameterName = metadata.stringValue(QueryValue.class).orElse(argumentName);

            conversionService.convert(value, stringConversion)
                    .filter(StringUtils::isNotEmpty)
                    .ifPresent(o -> {
                queryParams.put(parameterName, o);
            });
        } else if (metadata.isAnnotationPresent(PathVariable.class)) {
            String parameterName = metadata.stringValue(PathVariable.class).orElse(argumentName);
            if (!(value instanceof String)) {
                conversionService.convert(value, stringConversion)
                        .filter(StringUtils::isNotEmpty)
                        .ifPresent(param -> paramMap.put(parameterName, param));
            }
        } else if (uriTemplate.getVariableNames().contains(context.getArgument().getName())) {
            if (paramMap.containsKey(argumentName) && argument.getAnnotationMetadata().hasStereotype(Format.class)) {
                final Object v = paramMap.get(argumentName);
                if (v != null) {
                    paramMap.put(argumentName, conversionService.convert(v, stringConversion));
                }
            }
        } else {
            bodyArguments.add(context.getArgument());
        }
    }
}
