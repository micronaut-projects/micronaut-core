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
package io.micronaut.annotation.processing;

import io.micronaut.annotation.processing.visitor.JavaPackageElement;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.util.Toggleable;
import io.micronaut.inject.visitor.PackageElementVisitor;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static io.micronaut.core.util.StringUtils.EMPTY_STRING;

/**
 * <p>The annotation processed used to execute package element visitors.</p>
 *
 * @author Denis Stepanov
 * @since 4.10
 */
@SupportedOptions({
    AbstractInjectAnnotationProcessor.MICRONAUT_PROCESSING_INCREMENTAL,
    AbstractInjectAnnotationProcessor.MICRONAUT_PROCESSING_ANNOTATIONS,
    VisitorContext.MICRONAUT_PROCESSING_PROJECT_DIR,
    VisitorContext.MICRONAUT_PROCESSING_GROUP,
    VisitorContext.MICRONAUT_PROCESSING_MODULE
})
public sealed class PackageElementVisitorProcessor extends AbstractInjectAnnotationProcessor permits AggregatingPackageElementVisitorProcessor {
    private static final Set<String> SUPPORTED_ANNOTATION_NAMES;

    static {
        var names = new HashSet<String>();
        for (PackageElementVisitor<?> packageElementVisitor : findPackageElementVisitors()) {
            try {
                Set<String> supportedAnnotationNames = packageElementVisitor.getSupportedAnnotationNames();
                if (!supportedAnnotationNames.equals(Collections.singleton("*"))) {
                    names.addAll(supportedAnnotationNames);
                }
            } catch (Throwable ignore) {
                // ignore if annotations are not on the classpath
            }
        }
        SUPPORTED_ANNOTATION_NAMES = names;
    }

    private List<PackageLoadedVisitor> packageVisitors;

    /**
     * The visited annotation names.
     *
     * @return The names of all the visited annotations.
     */
    static Set<String> getVisitedAnnotationNames() {
        return SUPPORTED_ANNOTATION_NAMES;
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        Collection<? extends PackageElementVisitor<?>> packageElementVisitors = findPackageElementVisitors();

        // set supported options as system properties to keep compatibility
        // in particular for micronaut-openapi
        processingEnv.getOptions().entrySet().stream()
            .filter(entry -> entry.getKey() != null && entry.getKey().startsWith(VisitorContext.MICRONAUT_BASE_OPTION_NAME))
            .forEach(entry -> System.setProperty(entry.getKey(), entry.getValue() == null ? EMPTY_STRING : entry.getValue()));

        this.packageVisitors = new ArrayList<>(packageElementVisitors.size());

        TypeElementVisitor.VisitorKind incrementalProcessorKind = getIncrementalProcessorKind();

        for (PackageElementVisitor<?> visitor : packageElementVisitors) {
            TypeElementVisitor.VisitorKind visitorKind = visitor.getVisitorKind();

            if (incrementalProcessorKind == visitorKind) {
                try {
                    packageVisitors.add(PackageLoadedVisitor.of(visitor, processingEnv));
                } catch (TypeNotPresentException | NoClassDefFoundError e) {
                    // ignored, means annotations referenced are not on the classpath
                }
            }
        }

        OrderUtil.reverseSort(packageVisitors);
    }

    /**
     * Does this process have any visitors.
     *
     * @return True if visitors are present.
     */
    protected boolean hasVisitors() {
        for (PackageElementVisitor<?> packageElementVisitor : findPackageElementVisitors()) {
            if (packageElementVisitor.getVisitorKind() == getVisitorKind()) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return The loaded visitors.
     */
    public List<PackageLoadedVisitor> getPackageVisitors() {
        return packageVisitors;
    }

    /**
     * @return The incremental processor type.
     * @see #GRADLE_PROCESSING_AGGREGATING
     * @see #GRADLE_PROCESSING_ISOLATING
     */
    protected TypeElementVisitor.VisitorKind getIncrementalProcessorKind() {
        String type = getIncrementalProcessorType();
        if (type.equals(GRADLE_PROCESSING_AGGREGATING)) {
            return TypeElementVisitor.VisitorKind.AGGREGATING;
        }
        return TypeElementVisitor.VisitorKind.ISOLATING;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        if (packageVisitors.isEmpty()) {
            return Collections.emptySet();
        } else {
            return super.getSupportedAnnotationTypes();
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (packageVisitors.isEmpty() || processingGeneratedAnnotation(annotations)) {
            return false;
        }

        var packageElements = new LinkedHashSet<PackageElement>();

        for (TypeElement annotation : annotations) {
            Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
            if (!packageVisitors.isEmpty()) {
                for (Element annotatedElement : annotatedElements) {
                    if (annotatedElement instanceof PackageElement packageElement) {
                        packageElements.add(packageElement);
                    }
                }
            }
        }

        if (packageElements.isEmpty()) {
            return false;
        }

        JavaElementAnnotationMetadataFactory elementAnnotationMetadataFactory = javaVisitorContext.getElementAnnotationMetadataFactory();

        List<JavaPackageElement> javaPackageElements = packageElements.stream()
            .map(element -> new JavaPackageElement(element, elementAnnotationMetadataFactory, javaVisitorContext))
            .collect(Collectors.toCollection(() -> new ArrayList<>(packageElements.size())));

        if (!javaPackageElements.isEmpty()) {
            for (PackageLoadedVisitor packageVisitor : packageVisitors) {
                for (JavaPackageElement javaPackageElement : javaPackageElements) {
                    if (packageVisitor.matches(javaPackageElement)) {
                        packageVisitor.visitor().visitPackage(javaPackageElement, javaVisitorContext);
                    }
                }
            }
        }

        if (roundEnv.processingOver()) {
            javaVisitorContext.finish();
        }
        return false;
    }

    @NonNull
    private static Collection<? extends PackageElementVisitor<?>> findPackageElementVisitors() {
        return SoftServiceLoader.load(PackageElementVisitor.class, PackageElementVisitorProcessor.class.getClassLoader())
            .disableFork()
            .collectAll(Toggleable::isEnabled).stream()
            .filter(Objects::nonNull)
            .<PackageElementVisitor<?>>map(e -> e)
            // remove duplicate classes
            .collect(Collectors.toMap(Object::getClass, v -> v, (a, b) -> a)).values();
    }
}
