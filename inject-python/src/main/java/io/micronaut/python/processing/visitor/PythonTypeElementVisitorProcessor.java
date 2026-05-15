/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.python.processing.visitor;

import io.micronaut.aop.InterceptorBinding;
import io.micronaut.aop.runtime.RuntimeProxy;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.version.VersionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.EnumConstantElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.TypeElementQuery;
import io.micronaut.python.processing.PythonProcessingEnvironment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

public class PythonTypeElementVisitorProcessor {
    private final ClassLoader classLoader;

    private Collection<? extends TypeElementVisitor<?, ?>> typeElementVisitors;
    private List<LoadedVisitor> loadedVisitors;

    public PythonTypeElementVisitorProcessor(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * Initialise the processor.
     *
     * @param environment The processing environment
     */
    public synchronized void init(PythonProcessingEnvironment environment) {

        Collection<? extends TypeElementVisitor<?, ?>> typeElementVisitors = findTypeElementVisitors();

        this.loadedVisitors = new ArrayList<>(typeElementVisitors.size());

        for (TypeElementVisitor<?, ?> visitor : typeElementVisitors) {
            TypeElementVisitor.VisitorKind visitorKind = visitor.getVisitorKind();
            TypeElementVisitor.VisitorKind incrementalProcessorKind = getIncrementalProcessorKind();

            if (incrementalProcessorKind == visitorKind) {
                try {
                    loadedVisitors.add(new LoadedVisitor(visitor));
                } catch (TypeNotPresentException | NoClassDefFoundError e) {
                    // ignored, means annotations referenced are not on the classpath
                }
            }
        }

        OrderUtil.reverseSort(loadedVisitors);

        PythonVisitorContext visitorContext = environment.visitorContext();
        for (LoadedVisitor loadedVisitor : loadedVisitors) {
            try {
                loadedVisitor.getVisitor().start(visitorContext);
            } catch (Throwable e) {
                visitorContext.fail(String.format("Error initializing type visitor [%s]: %s", loadedVisitor.getVisitor(), e.getMessage()), null);
            }
        }
    }

    /**
     * Process the given elements.
     *
     * @param environment The processing environment
     */
    public void process(PythonProcessingEnvironment environment) {
        PythonVisitorContext pythonVisitorContext = environment.visitorContext();
        for (LoadedVisitor loadedVisitor : loadedVisitors) {
            loadedVisitor.getVisitor().start(pythonVisitorContext);
        }

        Map<String, ClassElement> classes = environment.classes();
        Map<String, ClassElement> scripts = environment.scripts();
        for (LoadedVisitor loadedVisitor : loadedVisitors) {
            for (ClassElement element : classes.values()) {
                if (loadedVisitor.matchesClass(element)) {
                    if (isAopProxy(element)) {
                        element.annotate(RuntimeProxy.class, builder ->
                            builder.value("io.micronaut.context.python.aop.PythonProxyCreator")
                                .member("proxyTarget", true)
                        );
                    }
                    visitClass(loadedVisitor, element, pythonVisitorContext);
                }
            }
            // Also process script elements
            for (ClassElement scriptElement : scripts.values()) {
                if (loadedVisitor.matchesClass(scriptElement)) {
                    if (isAopProxy(scriptElement)) {
                        scriptElement.annotate(RuntimeProxy.class, builder ->
                            builder.value("io.micronaut.context.python.aop.PythonProxyCreator")
                                .member("proxyTarget", true)
                        );
                    }
                    visitClass(loadedVisitor, scriptElement, pythonVisitorContext);
                }
            }
        }

        for (LoadedVisitor loadedVisitor : loadedVisitors) {
            loadedVisitor.getVisitor().finish(pythonVisitorContext);
        }
    }

    private void visitClass(LoadedVisitor loadedVisitor, ClassElement element, PythonVisitorContext pythonVisitorContext) {
        TypeElementVisitor<?, ?> visitor = loadedVisitor.getVisitor();
        TypeElementQuery query = visitor.query();
        visitor.visitClass(element, pythonVisitorContext);

        if (query.includesConstructors()) {
            for (ConstructorElement constructorElement : element.getEnclosedElements(ElementQuery.CONSTRUCTORS)) {
                visitConstructor(loadedVisitor, constructorElement, pythonVisitorContext);
            }
        }

        boolean includesFields = query.includesFields() || query.includesEnumConstants();
        boolean includesMethods = query.includesMethods();
        List<? extends MemberElement> elements;
        if (includesMethods && includesFields) {
            elements = element.getEnclosedElements(ElementQuery.ALL_FIELD_AND_METHODS);
        } else if (includesMethods) {
            elements = element.getEnclosedElements(ElementQuery.ALL_METHODS);
        } else if (includesFields) {
            elements = element.getEnclosedElements(ElementQuery.ALL_FIELDS);
        } else {
            elements = List.of();
        }

        for (MemberElement memberElement : elements) {
            if (memberElement instanceof EnumConstantElement enumConstantElement) {
                if (query.includesEnumConstants()) {
                    visitEnumConstant(loadedVisitor, enumConstantElement, pythonVisitorContext);
                }
            } else if (memberElement instanceof FieldElement fieldElement) {
                if (query.includesFields()) {
                    visitField(loadedVisitor, fieldElement, pythonVisitorContext);
                }
            } else if (memberElement instanceof MethodElement methodElement) {
                if (includesMethods) {
                    visitMethod(loadedVisitor, methodElement, pythonVisitorContext);
                }
            }
        }
    }

    private void visitConstructor(LoadedVisitor visitor, ConstructorElement constructorElement, PythonVisitorContext pythonVisitorContext) {
        if (visitor.matchesElement(constructorElement.getAnnotationMetadata())) {
            visitor.getVisitor().visitConstructor(constructorElement, pythonVisitorContext);
        }
    }

    private void visitMethod(LoadedVisitor visitor, MethodElement methodElement, PythonVisitorContext pythonVisitorContext) {
        if (visitor.matchesElement(methodElement.getAnnotationMetadata())) {
            visitor.getVisitor().visitMethod(methodElement, pythonVisitorContext);
        }
    }

    private void visitEnumConstant(LoadedVisitor visitor, EnumConstantElement enumConstantElement, PythonVisitorContext pythonVisitorContext) {
        if (visitor.matchesElement(enumConstantElement.getAnnotationMetadata())) {
            visitor.getVisitor().visitEnumConstant(enumConstantElement, pythonVisitorContext);
        }
    }

    private void visitField(LoadedVisitor visitor, FieldElement fieldElement, PythonVisitorContext pythonVisitorContext) {
        if (visitor.matchesElement(fieldElement.getAnnotationMetadata())) {
            visitor.getVisitor().visitField(fieldElement, pythonVisitorContext);
        }
    }

    private boolean isAopProxy(ClassElement element) {
        if (element.hasStereotype(InterceptorBinding.class)) {
            return true;
        }
        for (MethodElement method : element.getMethods()) {
            if (method.hasStereotype(InterceptorBinding.class)) {
                return true;
            }
        }
        return false;
    }

    private TypeElementVisitor.VisitorKind getIncrementalProcessorKind() {
        return TypeElementVisitor.VisitorKind.ISOLATING;
    }

    /**
     * Discovers the {@link TypeElementVisitor} instances that are available.
     *
     * @return A collection of type element visitors.
     */
    @NonNull
    protected synchronized Collection<? extends TypeElementVisitor<?, ?>> findTypeElementVisitors() {
        if (typeElementVisitors == null) {
            HashSet<String> warnings = new HashSet<>();
            typeElementVisitors = findCoreTypeElementVisitors(warnings);
            for (String visitorWarning : warnings) {
                warning(visitorWarning);
            }
        }
        return typeElementVisitors;
    }

    private void warning(String visitorWarning) {
        System.err.println("WARNING: " + visitorWarning);
    }

    @SuppressWarnings("rawtypes")
    @NonNull
    private Collection<? extends TypeElementVisitor<?, ?>> findCoreTypeElementVisitors(@Nullable Set<String> warnings) {
        List visitors = SoftServiceLoader.load(TypeElementVisitor.class, classLoader)
            .disableFork()
            .collectAll(visitor -> {
                if (!visitor.isEnabled()) {
                    return false;
                }

                final Requires requires = visitor.getClass().getAnnotation(Requires.class);
                if (requires != null) {
                    final Requires.Sdk sdk = requires.sdk();
                    if (sdk == Requires.Sdk.MICRONAUT) {
                        final String version = requires.version();
                        if (StringUtils.isNotEmpty(version) && !VersionUtils.isAtLeastMicronautVersion(version)) {
                            try {
                                if (warnings != null) {
                                    warnings.add("TypeElementVisitor [" + visitor.getClass().getName() + "] will be ignored because Micronaut version [" + VersionUtils.MICRONAUT_VERSION + "] must be at least " + version);
                                }
                                return false;
                            } catch (IllegalArgumentException e) {
                                // shouldn't happen, thrown when invalid version encountered
                            }
                        }
                    }
                }
                return true;
            }).stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(Object::getClass, v -> v, (a, b) -> a)).values().stream().toList();
        if (visitors.isEmpty()) {
            visitors = new ArrayList<>();
            Iterator<ServiceLoader.Provider<TypeElementVisitor>> it = ServiceLoader.load(TypeElementVisitor.class, classLoader).stream().iterator();
            while (it.hasNext()) {
                Class<? extends TypeElementVisitor> type = null;
                try {
                    ServiceLoader.Provider<TypeElementVisitor> provider = it.next();
                    type = provider.type();
                    visitors.add(provider.get());
                } catch (Throwable e) {
                    if (e instanceof VirtualMachineError virtualMachineError) {
                        throw virtualMachineError;
                    } else {
                        if (warnings != null) {
                            warnings.add("Error loading TypeElementVisitor " + (type != null ? type.getSimpleName() : "") + ": " + e.getMessage());
                        }
                    }
                }
            }
        }
        return (Collection<? extends TypeElementVisitor<?, ?>>) visitors;
    }
}
