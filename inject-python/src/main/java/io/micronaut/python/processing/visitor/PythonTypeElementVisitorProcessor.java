package io.micronaut.python.processing.visitor;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.version.VersionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.EnumConstantElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.ElementPostponedToNextRoundException;
import io.micronaut.inject.visitor.TypeElementQuery;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.python.processing.PythonEnvironment;
import io.micronaut.python.processing.PythonProcessingEnvironment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class PythonTypeElementVisitorProcessor {
    private static final Set<String> VISITOR_WARNINGS;
    private static final Set<String> SUPPORTED_ANNOTATION_NAMES;

    static {
        final var warnings = new HashSet<String>();
        var names = new HashSet<String>();
        for (TypeElementVisitor<?, ?> typeElementVisitor : findCoreTypeElementVisitors(warnings)) {
            final Set<String> supportedAnnotationNames;
            try {
                supportedAnnotationNames = typeElementVisitor.getSupportedAnnotationNames();
            } catch (Throwable e) {
                // ignore if annotations are not on the classpath
                continue;
            }
            if (!supportedAnnotationNames.equals(Collections.singleton("*"))) {
                names.addAll(supportedAnnotationNames);
            }
        }
        SUPPORTED_ANNOTATION_NAMES = names;

        if (warnings.isEmpty()) {
            VISITOR_WARNINGS = Collections.emptySet();
        } else {
            VISITOR_WARNINGS = Collections.unmodifiableSet(warnings);
        }
    }

    private Collection<? extends TypeElementVisitor<?, ?>> typeElementVisitors;
    private List<LoadedVisitor> loadedVisitors;

    /**
     * Initialise the processor.
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
     * @param environment The processing environment
     */
    public void process(PythonProcessingEnvironment environment) {
        PythonVisitorContext pythonVisitorContext = environment.visitorContext();
        for (LoadedVisitor loadedVisitor : loadedVisitors) {
            loadedVisitor.getVisitor().start(pythonVisitorContext);
        }

        Map<String, ClassElement> classes = environment.classes();
        for (LoadedVisitor loadedVisitor : loadedVisitors) {
            TypeElementVisitor<?, ?> visitor = loadedVisitor.getVisitor();
            for (ClassElement element : classes.values()) {
                visitor.visitClass(element, pythonVisitorContext);
            }
        }

        for (LoadedVisitor loadedVisitor : loadedVisitors) {
            loadedVisitor.getVisitor().finish(pythonVisitorContext);
        }
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
            for (String visitorWarning : VISITOR_WARNINGS) {
                warning(visitorWarning);
            }
            typeElementVisitors = findCoreTypeElementVisitors(null);
        }
        return typeElementVisitors;
    }

    private void warning(String visitorWarning) {
        System.err.println("WARNING: " + visitorWarning);
    }

    @NonNull
    private static Collection<? extends TypeElementVisitor<?, ?>> findCoreTypeElementVisitors(@Nullable Set<String> warnings) {
        return SoftServiceLoader.load(TypeElementVisitor.class, PythonTypeElementVisitorProcessor.class.getClassLoader())
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
            .<TypeElementVisitor<?, ?>>map(e -> e)
            // remove duplicate classes
            .collect(Collectors.toMap(Object::getClass, v -> v, (a, b) -> a)).values();
    }
}
