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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.visitor.ClassElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.GeneratedFile;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import java.io.File;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Visits the application class.
 *
 * @author graemerocher
 * @since 1.0
 */
@Experimental
public class OpenApiApplicationVisitor extends AbstractOpenApiVisitor implements TypeElementVisitor<OpenAPIDefinition, Object> {
    /**
     * System property that enables setting the target file to write to.
     */
    public static final String MICRONAUT_OPENAPI_TARGET_FILE = "micronaut.openapi.target.file";

    private ClassElement classElement;

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        context.info("Generating OpenAPI Documentation");
        Optional<OpenAPI> attr = context.get(ATTR_OPENAPI, OpenAPI.class);
        OpenAPI openAPI = readOpenAPI(element, context);
        if (attr.isPresent()) {
            OpenAPI existing = attr.get();
            existing.setInfo(openAPI.getInfo());
            existing.setTags(openAPI.getTags());
            existing.setServers(openAPI.getServers());
            existing.setSecurity(openAPI.getSecurity());
            existing.setExternalDocs(openAPI.getExternalDocs());
            existing.setExtensions(openAPI.getExtensions());

        } else {
            context.put(ATTR_OPENAPI, openAPI);
        }

        // handle type level tags
        List<io.swagger.v3.oas.models.tags.Tag> tagList = processOpenApiAnnotation(
                element,
                context,
                Tag.class,
                io.swagger.v3.oas.models.tags.Tag.class,
                openAPI.getTags()
        );
        openAPI.setTags(tagList);

        // handle type level security requirements
        List<io.swagger.v3.oas.models.security.SecurityRequirement> securityRequirements = processOpenApiAnnotation(
                element,
                context,
                SecurityRequirement.class,
                io.swagger.v3.oas.models.security.SecurityRequirement.class,
                openAPI.getSecurity()
        );
        openAPI.setSecurity(securityRequirements);

        // handle type level servers
        List<io.swagger.v3.oas.models.servers.Server> servers = processOpenApiAnnotation(
                element,
                context,
                Server.class,
                io.swagger.v3.oas.models.servers.Server.class,
                openAPI.getServers()
        );
        openAPI.setServers(servers);

        if (Boolean.getBoolean(ATTR_TEST_MODE)) {
            testReference = openAPI;
        }

        this.classElement = element;
    }

    private <T, A extends Annotation> List<T> processOpenApiAnnotation(ClassElement element, VisitorContext context, Class<A> annotationType, Class<T> modelType, List<T> tagList) {
        List<AnnotationValue<A>> annotations = element.getAnnotationValuesByType(annotationType);
        if (CollectionUtils.isNotEmpty(annotations)) {
            if (CollectionUtils.isEmpty(tagList)) {
                tagList = new ArrayList<>();

            }
            for (AnnotationValue<A> tag : annotations) {
                JsonNode jsonNode = toJson(tag.getValues());
                try {
                    T t = jsonMapper.treeToValue(jsonNode, modelType);
                    if (t != null) {
                        tagList.add(t);
                    }
                } catch (JsonProcessingException e) {
                    context.warn("Error reading OpenAPI" + annotationType + " annotation", element);
                }
            }
        }
        return tagList;
    }

    private OpenAPI readOpenAPI(ClassElement element, VisitorContext context) {
        return element.findAnnotation(OpenAPIDefinition.class).flatMap(o -> {
                    JsonNode jsonNode = toJson(o.getValues());

                    try {
                        return Optional.of(jsonMapper.treeToValue(jsonNode, OpenAPI.class));
                    } catch (JsonProcessingException e) {
                        context.warn("Error reading Swagger OpenAPI for element [" + element + "]: " + e.getMessage(), element);
                        return Optional.empty();
                    }
                }).orElse(new OpenAPI());
    }

    @Override
    public void finish(VisitorContext visitorContext) {

        if (classElement != null) {

            Optional<OpenAPI> attr = visitorContext.get(ATTR_OPENAPI, OpenAPI.class);

            attr.ifPresent(openAPI -> {
                String property = System.getProperty(MICRONAUT_OPENAPI_TARGET_FILE);
                if (StringUtils.isNotEmpty(property)) {
                    File f = new File(property);
                    visitorContext.info("Writing OpenAPI YAML to destination: " + f);
                    try {
                        f.getParentFile().mkdirs();
                        yamlMapper.writeValue(f, openAPI);
                    } catch (Exception e) {
                        visitorContext.warn("Unable to generate swagger.yml: " + e.getMessage() , classElement);
                    }
                } else {

                    String fileName = "swagger.yml";
                    Info info = openAPI.getInfo();
                    if (info != null) {
                        String version = info.getVersion();
                        if (version != null) {
                            fileName = "swagger-" + version + ".yml";
                        }
                    }
                    Optional<GeneratedFile> generatedFile = visitorContext.visitGeneratedFile(fileName);
                    if (generatedFile.isPresent()) {
                        GeneratedFile f = generatedFile.get();
                        try {
                            visitorContext.info("Writing OpenAPI YAML to destination: " + f.toURI());
                            Writer writer = f.openWriter();
                            yamlMapper.writeValue(writer, openAPI);
                        } catch (Exception e) {
                            visitorContext.warn("Unable to generate swagger.yml: " + e.getMessage() , classElement);
                        }
                    }
                }
            });
        }
    }
}
