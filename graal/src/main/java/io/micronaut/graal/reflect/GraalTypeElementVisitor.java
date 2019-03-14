/*
 * Copyright 2017-2019 original authors
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

package io.micronaut.graal.reflect;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.micronaut.core.annotation.*;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.GeneratedFile;
import org.reactivestreams.Publisher;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Future;

/**
 * Generates the GraalVM reflect.json file at compilation time.
 *
 * @author graemerocher
 * @since 1.1
 */
@Experimental
public class GraalTypeElementVisitor implements TypeElementVisitor<Object, Object> {

    /**
     * Beans are those requiring full reflective access to all public members.
     */
    protected static Set<String> beans = new HashSet<>();

    /**
     * Classes only require classloading access.
     */
    protected static Set<String> classes = new HashSet<>();

    /**
     * Arrays requiring reflective instantiation.
     */
    protected static Set<String> arrays = new HashSet<>();

    private static final String BASE_REFLECT_JSON = "src/main/graal/reflect.json";

    private static final String ALL_PUBLIC_METHODS = "allPublicMethods";

    private static final String NAME = "name";

    private static final String ALL_DECLARED_CONSTRUCTORS = "allDeclaredConstructors";

    private boolean isSubclass = getClass() != GraalTypeElementVisitor.class;

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (!isSubclass) {
            if (element.hasAnnotation(Introspected.class)) {
                beans.add(element.getName());
                final String[] introspectedClasses = element.getValue(Introspected.class, "classes", String[].class).orElse(StringUtils.EMPTY_STRING_ARRAY);
                Collections.addAll(beans, introspectedClasses);
            } else if (element.hasAnnotation(TypeHint.class)) {
                final String[] introspectedClasses = element.getValue(TypeHint.class, String[].class).orElse(StringUtils.EMPTY_STRING_ARRAY);
                final TypeHint.AccessType accessType = element.getValue(TypeHint.class, "accessType", TypeHint.AccessType.class)
                        .orElse(TypeHint.AccessType.REFLECTION_PUBLIC);

                processClasses(accessType, introspectedClasses);
                processClasses(accessType, element.getValue(
                        TypeHint.class,
                        "typeNames",
                        String[].class).orElse(StringUtils.EMPTY_STRING_ARRAY
                    )
                );
            }
        }
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (!isSubclass) {
            if (element.hasDeclaredStereotype(EntryPoint.class)) {
                final ClassElement returnType = element.getReturnType();
                possiblyReflectOnType(returnType);
                final ParameterElement[] parameters = element.getParameters();
                for (ParameterElement parameter : parameters) {
                    possiblyReflectOnType(parameter.getType());
                }
            }
        }
    }

    @Override
    public void visitConstructor(ConstructorElement element, VisitorContext context) {
        if (!isSubclass) {
            if (element.hasAnnotation(Creator.class)) {
                beans.add(element.getDeclaringType().getName());
            }
        }
    }

    private void possiblyReflectOnType(ClassElement type) {
        if (type == null || type.isPrimitive() || type.isAbstract() || type.isEnum() || type.getName().startsWith("java.lang")) {
            return;
        }

        boolean isWrapperType = type.isAssignable(Iterable.class) ||
                                type.isAssignable(Publisher.class) ||
                                type.isAssignable(Map.class) ||
                                type.isAssignable(Optional.class) ||
                                type.isAssignable(Future.class);
        if (!isWrapperType) {
            beans.add(type.getName());
        }
        
        final Map<String, ClassElement> typeArguments = type.getTypeArguments();
        for (ClassElement value : typeArguments.values()) {
            possiblyReflectOnType(value);
        }
    }

    private void processClasses(TypeHint.AccessType accessType, String... introspectedClasses) {
        if (accessType == TypeHint.AccessType.CLASS_LOADING) {
            Collections.addAll(classes, introspectedClasses);
        } else {
            Collections.addAll(beans, introspectedClasses);
        }
    }

    @Override
    public final void finish(VisitorContext visitorContext) {
        // don't do this for subclasses
        if (!isSubclass) {
            List<Map> json;
            ObjectMapper mapper = new ObjectMapper();
            ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());

            File f = new File(BASE_REFLECT_JSON);
            if (f.exists()) {
                try {
                    json = mapper.readValue(f, new TypeReference<List<Map>>() {
                    });
                } catch (Throwable e) {
                    visitorContext.fail("Error parsing base reflect.json: " + BASE_REFLECT_JSON, null);
                    return;
                }
            } else {
                json = new ArrayList<>();
            }


            final Iterable<URL> resources = visitorContext.getClasspathResources("META-INF/reflect.json");
            for (URL resource : resources) {
                try {
                    final List<Map> list = mapper.readValue(resource, new TypeReference<List<Map>>() {
                    });

                    if (list != null) {
                        for (Map map : list) {
                            if (map != null) {
                                final Object n = map.get(NAME);
                                if (n != null) {
                                    final Object o = map.get(ALL_PUBLIC_METHODS);
                                    if (o instanceof Boolean) {
                                        if (((Boolean) o)) {
                                            beans.add(n.toString());
                                        } else {
                                            classes.add(n.toString());
                                        }
                                    } else {
                                        classes.add(n.toString());
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable e) {
                    visitorContext.warn("Couldn't include reflect.json from library dependency: " + resource, null);
                }
            }

            if (CollectionUtils.isEmpty(beans) && CollectionUtils.isEmpty(classes) && CollectionUtils.isEmpty(arrays)) {
                return;
            }

            try {
                final Optional<GeneratedFile> generatedFile = visitorContext.visitMetaInfFile("reflect.json");
                generatedFile.ifPresent(generatedFile1 -> {


                    for (String aClass : classes) {
                        json.add(CollectionUtils.mapOf(
                                NAME, aClass,
                                ALL_DECLARED_CONSTRUCTORS, true
                        ));
                    }

                    for (String array : arrays) {
                        json.add(CollectionUtils.mapOf(
                                NAME, "[L" + array.substring(0, array.length() - 2) + ";",
                                ALL_DECLARED_CONSTRUCTORS, true
                        ));
                    }

                    for (String bean : beans) {
                        json.add(CollectionUtils.mapOf(
                                NAME, bean,
                                ALL_PUBLIC_METHODS, true,
                                ALL_DECLARED_CONSTRUCTORS, true
                        ));
                    }

                    try (Writer w = generatedFile1.openWriter()) {
                        visitorContext.info("Writing reflect.json file to destination: " + generatedFile1.getName());

                        writer.writeValue(w, json);
                    } catch (IOException e) {
                        visitorContext.fail("Error writing reflect.json: " + e.getMessage(), null);
                    }
                });
            } finally {
                beans.clear();
                classes.clear();
                arrays.clear();
            }
        }
    }
}
