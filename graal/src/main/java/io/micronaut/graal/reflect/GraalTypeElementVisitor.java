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
package io.micronaut.graal.reflect;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ast.*;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.GeneratedFile;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Generates the GraalVM reflect.json file at compilation time.
 *
 * @author graemerocher
 * @author Iván López
 * @since 1.1
 */
public class GraalTypeElementVisitor implements TypeElementVisitor<Object, Object> {
    /**
     * The position of the visitor.
     */
    public static final int POSITION = -200;

    /**
     * Beans are those requiring full reflective access to all public members.
     */
    protected static Set<String> packages = new HashSet<>();

    /**
     * Classes only require classloading access.
     */
    protected static Map<String, Map<String, Object>> classes = new HashMap<>();

    /**
     * Arrays requiring reflective instantiation.
     */
    protected static Set<String> arrays = new HashSet<>();

    private static final TypeHint.AccessType[] DEFAULT_ACCESS_TYPE = {TypeHint.AccessType.ALL_DECLARED_CONSTRUCTORS};
    private static final String REFLECTION_CONFIG_JSON = "reflect-config.json";

    private static final String BASE_REFLECT_JSON = "src/main/graal/reflect.json";

    private static final String ALL_PUBLIC_METHODS = "allPublicMethods";

    private static final String ALL_DECLARED_FIELDS = "allDeclaredFields";

    private static final String NAME = "name";

    private static final String ALL_DECLARED_CONSTRUCTORS = "allDeclaredConstructors";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private boolean isSubclass = getClass() != GraalTypeElementVisitor.class;

    private boolean executed = false;

    @Override
    public int getOrder() {
        return POSITION; // allow mutation of metadata
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (!isSubclass && !element.hasStereotype(Deprecated.class)) {
            if (element.hasAnnotation(Introspected.class)) {
                packages.add(element.getPackageName());
                final String beanName = element.getName();
                addBean(beanName);
                resolveClassData(beanName + "[]");
                final String[] introspectedClasses = element.getValue(Introspected.class, "classes", String[].class).orElse(StringUtils.EMPTY_STRING_ARRAY);
                for (String introspectedClass : introspectedClasses) {
                    addBean(introspectedClass);
                }
            } else if (element.hasAnnotation(TypeHint.class)) {
                packages.add(element.getPackageName());
                final String[] introspectedClasses = element.stringValues(TypeHint.class);
                final TypeHint typeHint = element.synthesize(TypeHint.class);
                TypeHint.AccessType[] accessTypes = DEFAULT_ACCESS_TYPE;

                if (typeHint != null) {
                    accessTypes = typeHint.accessType();
                }
                processClasses(accessTypes, introspectedClasses);
                processClasses(accessTypes, element.getValue(
                        TypeHint.class,
                        "typeNames",
                        String[].class).orElse(StringUtils.EMPTY_STRING_ARRAY
                        )
                );
            }
        }
    }

    private void addBean(String beanName) {
        resolveClassData(beanName).putAll(CollectionUtils.mapOf(
                ALL_PUBLIC_METHODS, true,
                ALL_DECLARED_CONSTRUCTORS, true,
                ALL_DECLARED_FIELDS, true
        ));
    }

    @Override
    public void visitField(FieldElement element, VisitorContext context) {
        if (element.hasStereotype(ReflectiveAccess.class)) {
            final ClassElement dt = element.getDeclaringType();
            packages.add(dt.getPackageName());
            final Map<String, Object> json = resolveClassData(resolveName(dt));
            final List<Map<String, Object>> fields = (List<Map<String, Object>>)
                    json.computeIfAbsent("fields", (Function<String, List<Map<String, Object>>>) s -> new ArrayList<>());

            fields.add(Collections.singletonMap(
                    "name", element.getName()
            ));
        }
    }

    private String resolveName(ClassElement classElement) {
        if (classElement.isArray()) {
            return classElement.getName() + "[]";
        }
        return classElement.getName();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (!isSubclass && element.hasDeclaredStereotype(ReflectiveAccess.class)) {
            processMethodElement(element);
        }
    }

    @Override
    public void visitConstructor(ConstructorElement element, VisitorContext context) {
        if (!isSubclass) {
            if (element.hasAnnotation(Creator.class)) {
                final ClassElement declaringType = element.getDeclaringType();
                packages.add(declaringType.getPackageName());
                addBean(declaringType.getName());
            } else if (element.hasAnnotation(ReflectiveAccess.class)) {
                processMethodElement(element);
            }
        }
    }

    private void processMethodElement(MethodElement element) {
        final String methodName = element.getName();
        packages.add(element.getDeclaringType().getPackageName());
        final Map<String, Object> json = resolveClassData(element.getDeclaringType().getName());
        final List<Map<String, Object>> methods = (List<Map<String, Object>>)
                json.computeIfAbsent("methods", (Function<String, List<Map<String, Object>>>) s -> new ArrayList<>());
        final List<String> params = Arrays.stream(element.getParameters())
                .map(ParameterElement::getType)
                .filter(Objects::nonNull)
                .map(this::resolveName).collect(Collectors.toList());
        Map newMap = CollectionUtils.mapOf(
                "name", methodName,
                "parameterTypes", params
        );
        if (!methods.contains(newMap)) {
            methods.add(newMap);
        }
    }

    private void processClasses(TypeHint.AccessType[] accessType, String... introspectedClasses) {
        for (String introspectedClass : introspectedClasses) {

            for (TypeHint.AccessType type : accessType) {
                if (type == TypeHint.AccessType.ALL_PUBLIC) {
                    for (String aClass : introspectedClasses) {
                        addBean(aClass);
                    }
                    return;
                }
                Map<String, Object> json = resolveClassData(introspectedClass);
                json.put(NameUtils.camelCase(type.name().toLowerCase()), true);
            }
        }
    }

    @Override
    public final void finish(VisitorContext visitorContext) {

        // Execute only once and never for subclasses
        if (!executed && !isSubclass) {
            executed = true;
            generateNativeImageProperties(visitorContext);
        }
    }

    private void generateNativeImageProperties(VisitorContext visitorContext) {
        List<Map> json;

        Optional<Path> projectDir = visitorContext.getProjectDir();

        File userReflectJsonFile = projectDir
                .map(projectPath -> Paths.get(projectPath.toString(), BASE_REFLECT_JSON).toFile())
                .orElse(null);

        if (userReflectJsonFile != null && userReflectJsonFile.exists()) {
            try {
                json = MAPPER.readValue(userReflectJsonFile, new TypeReference<List<Map>>() {
                });
            } catch (Throwable e) {
                visitorContext.fail("Error parsing base reflect.json: " + BASE_REFLECT_JSON, null);
                return;
            }
        } else {
            json = new ArrayList<>();
        }

        if (CollectionUtils.isEmpty(classes) && CollectionUtils.isEmpty(arrays) && CollectionUtils.isEmpty(json)) {
            return;
        }

        try {
            String path = buildNativeImagePath(visitorContext);
            String reflectFile = path + REFLECTION_CONFIG_JSON;
            final Optional<GeneratedFile> generatedFile = visitorContext.visitMetaInfFile(reflectFile, Element.EMPTY_ELEMENT_ARRAY);
            generatedFile.ifPresent(gf -> {
                for (Map<String, Object> value : classes.values()) {
                    json.add(value);
                }

                for (String array : arrays) {
                    json.add(CollectionUtils.mapOf(
                            NAME, "[L" + array.substring(0, array.length() - 2) + ";",
                            ALL_DECLARED_CONSTRUCTORS, true
                    ));
                }

                ObjectWriter writer = MAPPER.writer(new DefaultPrettyPrinter());
                try (Writer w = gf.openWriter()) {
                    visitorContext.info("Writing " + REFLECTION_CONFIG_JSON + " file to destination: " + gf.getName());

                    writer.writeValue(w, json);
                } catch (IOException e) {
                    visitorContext.fail("Error writing " + REFLECTION_CONFIG_JSON + ": " + e.getMessage(), null);
                }
            });
        } finally {
            classes.clear();
            arrays.clear();
        }
    }

    private String buildNativeImagePath(VisitorContext visitorContext) {

        String group = visitorContext.getOptions().get(VisitorContext.MICRONAUT_PROCESSING_GROUP);
        String module = visitorContext.getOptions().get(VisitorContext.MICRONAUT_PROCESSING_MODULE);

        if (group != null && module != null) {
            return "native-image/" + group + "/" + module + "/";
        }

        String basePackage = packages.stream()
                .distinct()
                .min(Comparator.comparingInt(String::length)).orElse("io.micronaut");

        if (basePackage.startsWith("io.micronaut.")) {
            module = basePackage.substring("io.micronaut.".length()).replace('.', '-');
            basePackage = "io.micronaut";
        } else {
            if (basePackage.contains(".")) {
                final int i = basePackage.lastIndexOf('.');
                module = basePackage.substring(i + 1);
                basePackage = basePackage.substring(0, i);
            } else {
                module = basePackage;
            }
        }

        return "native-image/" + basePackage + "/" + module + "/";
    }

    private Map<String, Object> resolveClassData(String introspectedClass) {
        return classes.computeIfAbsent(introspectedClass, s -> {
            final HashMap<String, Object> map = new HashMap<>(5);
            map.put(NAME, s);
            return map;
        });
    }
}
