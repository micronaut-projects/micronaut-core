/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import io.micronaut.inject.writer.GeneratedFile;
import io.micronaut.inject.writer.ServiceAggregatorWriter;

import javax.annotation.processing.Filer;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Collects the service descriptors of the module being compiled and writes them out as a single
 * {@link io.micronaut.core.io.service.ServiceAggregator}, in place of the
 * {@code META-INF/micronaut/<service>/<class>} marker file per implementation.
 *
 * <p>State is shared across the Micronaut processors of one compilation, because
 * {@link BeanDefinitionInjectProcessor} and {@link TypeElementVisitorProcessor} both contribute to
 * the same module while each holding its own output visitor. It is keyed on the {@link Filer}, which
 * is unique per javac task, so a reused compiler daemon does not carry state between builds.</p>
 *
 * <p>Both processors reach their final round independently, and javac does not say which of them
 * gets there last. The first one to finish writes the aggregator and its
 * {@code META-INF/services} entry; a resource may only be created once per compilation, so anything
 * generated after that point falls back to writing ordinary marker files. That tail is a handful of
 * entries at most, and the runtime unions aggregated and scanned services, so a module may mix the
 * two freely.</p>
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Internal
public final class ServiceAggregation {

    /**
     * Processor option enabling aggregation.
     */
    public static final String OPTION_ENABLED = "micronaut.serviceAggregator";

    /**
     * Processor option giving the aggregator an explicit fully qualified class name.
     *
     * <p>Without it the name is derived from the longest package prefix shared by the generated
     * implementations. That needs no configuration, but two modules sharing a root package would
     * derive the same name and shadow each other on the classpath, so a build producing several
     * modules under one package should set this.</p>
     */
    public static final String OPTION_CLASS_NAME = "micronaut.serviceAggregator.name";

    private static final Map<Filer, State> STATES = new WeakHashMap<>();

    private ServiceAggregation() {
    }

    /**
     * Enables or disables aggregation for the compilation identified by the filer.
     *
     * @param filer   The filer of the current javac task
     * @param options The processor options
     */
    public static synchronized void configure(Filer filer, Map<String, String> options) {
        state(filer).enabled = StringUtils.isTrue(options.get(OPTION_ENABLED));
    }

    /**
     * @param filer The filer of the current javac task
     * @return Whether service descriptors should be aggregated rather than written as marker files
     */
    public static synchronized boolean isEnabled(Filer filer) {
        return state(filer).enabled;
    }

    /**
     * Buffers one service implementation.
     *
     * @param filer              The filer of the current javac task
     * @param serviceName        The service name
     * @param implementation     The implementation class name
     * @param originatingElement The originating element, used to declare the aggregator's inputs
     * @return {@code true} if it was buffered, {@code false} if the aggregator has already been
     * written and the caller should fall back to writing a marker file
     */
    public static synchronized boolean add(Filer filer,
                                           String serviceName,
                                           String implementation,
                                           Element originatingElement) {
        State state = state(filer);
        if (!state.enabled || state.written) {
            return false;
        }
        state.services.computeIfAbsent(serviceName, s -> new ArrayList<>()).add(implementation);
        if (originatingElement != null) {
            state.originatingElements.putIfAbsent(implementation, originatingElement);
        }
        return true;
    }

    /**
     * Writes the aggregator for everything buffered so far, if this is the first call for the
     * compilation and anything was buffered.
     *
     * @param filer          The filer of the current javac task
     * @param visitorContext The visitor context
     * @param outputVisitor  The output visitor to write through
     */
    public static synchronized void write(Filer filer,
                                          VisitorContext visitorContext,
                                          ClassWriterOutputVisitor outputVisitor) {
        State state = state(filer);
        if (!state.enabled || state.written || state.services.isEmpty()) {
            return;
        }
        state.written = true;
        Map<String, List<String>> services = new LinkedHashMap<>(state.services);
        Element[] originatingElements = state.originatingElements.values().toArray(Element.EMPTY_ELEMENT_ARRAY);
        state.services.clear();
        state.originatingElements.clear();

        String className = className(visitorContext, services);
        try {
            new ServiceAggregatorWriter(className, services, originatingElements, visitorContext)
                .accept(outputVisitor);
            writeServicesEntry(outputVisitor, className, originatingElements);
        } catch (IOException e) {
            visitorContext.fail("Unable to write the service aggregator " + className + ": " + e.getMessage(), null);
        }
    }

    private static void writeServicesEntry(ClassWriterOutputVisitor outputVisitor,
                                           String className,
                                           Element[] originatingElements) throws IOException {
        Optional<GeneratedFile> file = outputVisitor.visitMetaInfFile(
            "services/" + io.micronaut.core.io.service.ServiceAggregator.SERVICE_NAME,
            originatingElements
        );
        if (file.isPresent()) {
            try (BufferedWriter writer = new BufferedWriter(file.get().openWriter())) {
                writer.write(className);
                writer.newLine();
            }
        }
    }

    private static State state(Filer filer) {
        if (filer == null) {
            return new State();
        }
        return STATES.computeIfAbsent(filer, f -> new State());
    }

    private static String className(VisitorContext visitorContext, Map<String, List<String>> services) {
        String configured = visitorContext.getOptions().get(OPTION_CLASS_NAME);
        if (StringUtils.isNotEmpty(configured)) {
            return configured;
        }
        String commonPackage = null;
        for (List<String> implementations : services.values()) {
            for (String implementation : implementations) {
                String packageName = packageOf(implementation);
                commonPackage = commonPackage == null ? packageName : commonPrefix(commonPackage, packageName);
            }
        }
        if (StringUtils.isEmpty(commonPackage)) {
            return "micronaut" + ServiceAggregatorWriter.CLASS_SUFFIX;
        }
        return commonPackage + '.' + ServiceAggregatorWriter.CLASS_SUFFIX;
    }

    private static String packageOf(String className) {
        int i = className.lastIndexOf('.');
        return i < 0 ? "" : className.substring(0, i);
    }

    /**
     * The shared prefix of two package names, cut at a package boundary so the result is always a
     * real package rather than a truncated segment.
     */
    private static String commonPrefix(String a, String b) {
        String[] left = a.split("\\.");
        String[] right = b.split("\\.");
        var builder = new StringBuilder();
        for (int i = 0; i < Math.min(left.length, right.length); i++) {
            if (!left[i].equals(right[i])) {
                break;
            }
            if (i > 0) {
                builder.append('.');
            }
            builder.append(left[i]);
        }
        return builder.toString();
    }

    private static final class State {
        private final Map<String, List<String>> services = new LinkedHashMap<>();
        private final Map<String, Element> originatingElements = new LinkedHashMap<>();
        private boolean enabled;
        private boolean written;
    }

}
