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
package io.micronaut.inject.configuration;

import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ast.ClassElement;

import java.util.Objects;
import java.util.Optional;

/**
 * An util class to calculate configuration paths.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public final class ConfigurationUtils {

    private static final String EACH_PROPERTY_LIST_SUFFIX = "[*]";
    private static final String EACH_PROPERTY_MAP_SUFFIX = ".*";

    private ConfigurationUtils() {
    }

    public static String buildPropertyPath(ClassElement owningType, ClassElement declaringType, String propertyName) {
        String typePath;
        if (declaringType.hasStereotype(ConfigurationReader.class)) {
            typePath = getRequiredTypePath(declaringType);
        } else {
            typePath = getRequiredTypePath(owningType);
        }
        return typePath + "." + propertyName;
    }

    public static String getRequiredTypePath(ClassElement classElement) {
        return getTypePath(classElement).orElseThrow(() -> new IllegalStateException("Prefix is required for " + classElement));
    }

    public static Optional<String> getTypePath(ClassElement classElement) {
        if (!classElement.hasStereotype(ConfigurationReader.class)) {
            return Optional.empty();
        }
        if (classElement.isTrue(ConfigurationReader.class, ConfigurationReader.PREFIX_CALCULATED)) {
            return classElement.stringValue(ConfigurationReader.class, ConfigurationReader.PREFIX);
        }
        String path = getPath(classElement);
        path = prependSuperclasses(classElement, path);
        path = prependInners(classElement, path);
        String finalPath = path;
        classElement.annotate(ConfigurationReader.class, builder ->
            builder.member(ConfigurationReader.PREFIX, finalPath)
                   .member(ConfigurationReader.PREFIX_CALCULATED, true)
        );
        return Optional.of(path);
    }

    private static String combinePaths(String p1, String p2) {
        if (StringUtils.isNotEmpty(p1) && StringUtils.isNotEmpty(p2)) {
            return p1 + "." + p2;
        }
        if (StringUtils.isNotEmpty(p1)) {
            return p1;
        }
        return p2;
    }

    private static String getPath(AnnotationMetadata annotationMetadata) {
        Optional<String> basePrefixOptional = annotationMetadata.stringValue(ConfigurationReader.class, ConfigurationReader.BASE_PREFIX);
        Optional<String> prefixOptional = annotationMetadata.stringValue(ConfigurationReader.class, ConfigurationReader.PREFIX);
        String prefix;
        if (basePrefixOptional.isPresent()) {
            if (prefixOptional.isEmpty()) {
                prefix = basePrefixOptional.get();
            } else {
                prefix = prefixOptional.map(p -> basePrefixOptional.get() + "." + p).orElse(null);
            }
        } else {
            prefix = prefixOptional.orElse(null);
        }
        if (annotationMetadata.hasDeclaredAnnotation(EachProperty.class)) {
            return computeIterablePrefix(annotationMetadata, prefix);
        }
        if (prefix == null) {
            return "";
        }
        return prefix;
    }

    @NonNull
    private static String computeIterablePrefix(AnnotationMetadata annotationMetadata, String prefix) {
        Objects.requireNonNull(prefix);
        if (annotationMetadata.booleanValue(EachProperty.class, "list").orElse(false)) {
            if (!prefix.endsWith(EACH_PROPERTY_LIST_SUFFIX)) {
                return prefix + EACH_PROPERTY_LIST_SUFFIX;
            } else {
                return prefix;
            }
        } else {
            if (!prefix.endsWith(EACH_PROPERTY_MAP_SUFFIX)) {
                return prefix + EACH_PROPERTY_MAP_SUFFIX;
            } else {
                return prefix;
            }
        }
    }

    private static String prependInners(ClassElement classElement, String path) {
        Optional<ClassElement> inner = classElement.getEnclosingType();
        while (classElement.isInner() && inner.isPresent()) {
            ClassElement enclosingType = inner.get();
            if (enclosingType.isTrue(ConfigurationReader.class, ConfigurationReader.PREFIX_CALCULATED)) {
                String parentPrefix = enclosingType.stringValue(ConfigurationReader.class, ConfigurationReader.PREFIX).orElse("");
                path = combinePaths(parentPrefix, path);
                break;
            } else {
                String parentPrefix = getPath(enclosingType);
                path = combinePaths(parentPrefix, path);
                path = prependSuperclasses(enclosingType, path);
            }
            inner = enclosingType.getEnclosingType();
        }
        return path;
    }

    private static String prependSuperclasses(ClassElement declaringType, String path) {
        if (declaringType.isInterface()) {
            path = prependInterfaces(declaringType, path);
        } else {
            Optional<ClassElement> optionalSuperType = declaringType.getSuperType();
            while (optionalSuperType.isPresent()) {
                ClassElement superType = optionalSuperType.get();
                if (superType.isTrue(ConfigurationReader.class, ConfigurationReader.PREFIX_CALCULATED)) {
                    String parentPrefix = superType.stringValue(ConfigurationReader.class, ConfigurationReader.PREFIX).orElse("");
                    path = combinePaths(parentPrefix, path);
                    break;
                } else {
                    String parentConfig = getPath(superType);
                    if (StringUtils.isNotEmpty(parentConfig)) {
                        path = combinePaths(parentConfig, path);
                    }
                    optionalSuperType = superType.getSuperType();
                }
            }
        }
        return path;
    }

    private static String prependInterfaces(ClassElement declaringType, String path) {
        ClassElement superInterface = resolveSuperInterface(declaringType);
        while (superInterface != null) {
            String parentConfig = getPath(superInterface);
            if (StringUtils.isNotEmpty(parentConfig)) {
                path = combinePaths(parentConfig, path);
            }
            superInterface = resolveSuperInterface(superInterface);
        }
        return path;
    }

    private static ClassElement resolveSuperInterface(ClassElement declaringType) {
        return declaringType.getInterfaces().stream().filter(tm -> tm.hasStereotype(ConfigurationReader.class)).findFirst().orElse(null);
    }

}
