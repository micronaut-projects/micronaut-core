/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.openapi.visitor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.openapi.util.Yaml;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.annotations.servers.ServerVariable;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;

import java.util.*;

/**
 * Abstract base class for OpenAPI visitors.
 *
 * @author graemerocher
 * @since 1.0
 */
abstract class AbstractOpenApiVisitor  {

    static final String ATTR_TEST_MODE = "io.micronaut.OPENAPI_TEST";
    static final String ATTR_OPENAPI = "io.micronaut.OPENAPI";
    static OpenAPI testReference;

    /**
     * The JSON mapper.
     */
    ObjectMapper jsonMapper = Json.mapper().enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
    /**
     * The YAML mapper.
     */
    ObjectMapper yamlMapper = Yaml.mapper();

    /**
     * Convert the given map to a JSON node.
     *
     * @param values The values
     * @return The node
     */
    JsonNode toJson(Map<CharSequence, Object> values) {
        Map<CharSequence, Object> newValues = toValueMap(values);
        return jsonMapper.valueToTree(newValues);
    }

    /**
     * Resolve the PathItem for the given {@link UriMatchTemplate}.
     *
     * @param context The context
     * @param matchTemplate The match template
     * @return The {@link PathItem}
     */
    PathItem resolvePathItem(VisitorContext context, UriMatchTemplate matchTemplate) {
        OpenAPI openAPI = resolveOpenAPI(context);
        Paths paths = openAPI.getPaths();
        if (paths == null) {
            paths = new Paths();
            openAPI.setPaths(paths);
        }


        PathItem pathItem = paths.get(matchTemplate.toString());
        if (pathItem == null) {
            pathItem = new PathItem();
            paths.put(matchTemplate.toString(), pathItem);
        }
        return pathItem;
    }

    /**
     * Resolve the {@link OpenAPI} instance.
     *
     * @param context The context
     * @return The {@link OpenAPI} instance
     */
    OpenAPI resolveOpenAPI(VisitorContext context) {
        OpenAPI openAPI = context.get(ATTR_OPENAPI, OpenAPI.class).orElse(null);
        if (openAPI == null) {
            openAPI = new OpenAPI();
            context.put(ATTR_OPENAPI, openAPI);
            if (Boolean.getBoolean(ATTR_TEST_MODE)) {
                testReference = openAPI;
            }
        }
        return openAPI;
    }

    /**
     * Convert the values to a map.
     * @param values The values
     * @return The map
     */
    protected Map<CharSequence, Object> toValueMap(Map<CharSequence, Object> values) {
        Map<CharSequence, Object> newValues = new HashMap<>(values.size());
        for (Map.Entry<CharSequence, Object> entry : values.entrySet()) {
            CharSequence key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof AnnotationValue) {
                AnnotationValue<?> av = (AnnotationValue<?>) value;
                newValues.put(key, toValueMap(av.getValues()));
            } else if (value != null) {
                if (value.getClass().isArray()) {
                    Object[] a = (Object[]) value;
                    if (ArrayUtils.isNotEmpty(a)) {
                        Object first = a[0];
                        boolean areAnnotationValues = first instanceof AnnotationValue;

                        if (areAnnotationValues) {
                            String annotationName = ((AnnotationValue) first).getAnnotationName();
                            if (ServerVariable.class.getName().equals(annotationName)) {
                                Map variables = new LinkedHashMap();
                                for (Object o : a) {
                                    AnnotationValue<ServerVariable> sv = (AnnotationValue<ServerVariable>) o;
                                    Optional<String> n = sv.get("name", String.class);
                                    n.ifPresent(name -> {
                                        Map<CharSequence, Object> map = toValueMap(sv.getValues());
                                        Object dv = map.get("defaultValue");
                                        if (dv != null) {
                                            map.put("default", dv);
                                        }
                                        variables.put(name, map);
                                    });
                                }
                                newValues.put(key, variables);
                            } else {
                                List list = new ArrayList();
                                for (Object o : a) {
                                    if (o instanceof AnnotationValue) {
                                        list.add(toValueMap(((AnnotationValue<?>) o).getValues()));
                                    } else {
                                        list.add(o);
                                    }
                                }
                                newValues.put(key, list);
                            }
                        } else {
                            newValues.put(key, value);
                        }
                    }
                } else {
                    newValues.put(key, value);
                }
            }
        }
        return newValues;
    }
}
