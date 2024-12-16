/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.context;

import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.PropertySourceLoader;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.naming.Named;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.TypeInformation;
import io.micronaut.core.util.AnsiColour;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.PropertyCatalog;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Allows enabling more verbose debugging on bean resolution.
 */
public enum BeanResolutionTraceMode {
    /**
     * No debug enabled (the default).
     */
    NONE,

    /**
     * When log mode is enabled output will be
     * written to a logger named {@code io.micronaut.context.resolution} at DEBUG level.
     */
    LOG,

    /**
     * With standard out debug output will be written to {@link System#out} avoiding any log formatting.
     */
    STANDARD_OUT,

    /**
     * Interactive mode will leverage both {@link System#out} and {@link System#in}
     * to allow pausing and resuming bean resolution and inspecting state.
     */
    INTERACTIVE;

    static final Logger LOGGER = LoggerFactory.getLogger("io.micronaut.context.resolution");
    private static final String MODE_SYS_PROP = "micronaut.inject.trace.mode";
    private static final String MODE_ENV_VAR = "MICRONAUT_INJECT_TRACE_MODE";
    private static final String CLASSES_SYS_PROP = "micronaut.inject.trace";
    private static final String CLASSES_ENV_VAR = "MICRONAUT_INJECT_TRACE";
    private static final Set<String> INTERNAL_PACKAGES = Set.of(
        "io.micronaut.context",
        "io.micronaut.aop",
        "io.micronaut.core.util",
        "org.codehaus.groovy.vmplugin",
        "java.util"
    );

    private static final String RIGHT_ARROW = AnsiColour.isSupported() ? " ➡️  " : " -> ";
    private static final String RIGHT_ARROW_LOOP = AnsiColour.isSupported() ? " ↪️  " : "\\---> ";
    private static final CharSequence START_TIME = "ResolutionDebug-start";

    /**
     * Obtains the default mode.
     *
     * @return The default mode
     */
    static BeanResolutionTraceMode getDefaultMode(Set<String> traceClasses) {
        String mode = Optional
            .ofNullable(System.getProperty(MODE_SYS_PROP))
            .orElseGet(() -> System.getenv(MODE_ENV_VAR));
        if (mode != null) {
            return BeanResolutionTraceMode
                .valueOf(NameUtils.environmentName(mode));
        }
        return traceClasses.isEmpty() ? NONE : BeanResolutionTraceMode.STANDARD_OUT;
    }

    static Set<String> getDefaultTraceClasses() {
        String classes = Optional
            .ofNullable(System.getProperty(CLASSES_SYS_PROP))
            .orElseGet(() -> System.getenv(CLASSES_ENV_VAR));
        if (classes != null) {
            return Set.of(classes.split(","));
        }
        return Set.of();
    }

    void startTrace(
        BeanResolutionContext resolutionContext,
        Argument<?> beanType,
        BeanDefinition<?> beanDefinition) {
        resolutionContext.setAttribute(START_TIME, System.currentTimeMillis());
        List<StackWalker.StackFrame> interestingFrames = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
            .walk(s ->
                s.dropWhile(f ->
                        (INTERNAL_PACKAGES.stream().anyMatch(p -> f.getClassName().startsWith(p)) || f.getDeclaringClass().isSynthetic()) &&
                        // capture startup beans
                        !(f.getClassName().equals(DefaultBeanContext.class.getName()) && f.getMethodName().equals("start")))
                    .limit(3)
                    .collect(Collectors.toList())
            );
        String beanName;
        if (beanType.getType().isSynthetic()) {
            beanName = beanDefinition.getTypeInformation().getBeanTypeString(TypeInformation.TypeFormat.ANSI_SIMPLE);
        } else {
            beanName = beanType
                .getBeanTypeString(TypeInformation.TypeFormat.ANSI_SIMPLE);
        }
        switch (this) {
            case STANDARD_OUT -> {
                System.out.println();
                String beanDescription = beanDefinition.getBeanDescription(TypeInformation.TypeFormat.ANSI_SHORTENED);
                System.out.println(beanName + RIGHT_ARROW + beanDescription + " at location:");
                for (StackWalker.StackFrame stackFrame : interestingFrames) {
                    StackTraceElement traceElement = stackFrame.toStackTraceElement();
                    StackTraceElement shortened = new StackTraceElement(
                        NameUtils.getShortenedName(traceElement.getClassName()),
                        traceElement.getMethodName(),
                        traceElement.getFileName(),
                        traceElement.getLineNumber()
                    );
                    System.out.println(shortened);
                }
                System.out.println();
            }

        }
    }

    <T> void traceBeanResolved(
        BeanResolutionContext resolutionContext,
        @NonNull Argument<T> beanType,
        @Nullable Qualifier<T> qualifier,
        @Nullable T bean) {
        String prefix = padLeft(resolutionContext, 1) + RIGHT_ARROW;

        // TODO: other output methods
        switch (this) {
            case STANDARD_OUT -> {
                System.out.print(prefix);
                System.out.print(bean != null ? "✅ " : "❌ ");
                if (qualifier != null) {
                    if (qualifier instanceof Named named) {
                        System.out.print(AnsiColour.yellow("@Named("));
                        System.out.print(AnsiColour.green("\"" + named.getName() + "\""));
                        System.out.print(AnsiColour.yellow(")"));
                    } else {
                        System.out.print(AnsiColour.yellow(qualifier.toString()));
                    }
                    System.out.print(" ");
                }
                System.out.print(AnsiColour.formatObject(bean));
                System.out.println();
            }
        }
    }

    void traceValueResolved(
        BeanResolutionContext resolutionContext,
        Argument<?> argument,
        String property,
        Object value) {
        BeanContext context = resolutionContext.getContext();
        if (context instanceof ApplicationContext applicationContext) {
            Environment environment = applicationContext.getEnvironment();
            PropertySource.Origin origin = environment.getPropertyEntry(property)
                .map(PropertySource.PropertyEntry::origin)
                .orElse(null);
            String prefix = padLeft(resolutionContext, 1) + RIGHT_ARROW;
            // TODO: other output methods
            switch (this) {
                case STANDARD_OUT -> {
                    System.out.print(prefix);
                    System.out.print(AnsiColour.formatObject(property));
                    System.out.print(" = ");
                    System.out.print(AnsiColour.formatObject(value));
                    if (origin != null) {
                        System.out.println(" (Origin: " + AnsiColour.brightYellow(origin.location()) + ")");
                    }
                    System.out.println();
                }
            }
        }
    }

    @NotNull
    private static String padLeft(BeanResolutionContext resolutionContext, int amount) {
        int size = resolutionContext.getPath().size() + amount;
        String prefix = "";
        if (size > 1) {
            prefix = "   ".repeat(size);
        }
        return prefix;
    }

    void finishTrace(BeanResolutionContext resolutionContext, BeanDefinition<?> rootDefinition) {
        Object v = resolutionContext.getAttribute(START_TIME);
        if (v instanceof Long start) {
            // TODO: other output methods
            switch (this) {
                case STANDARD_OUT -> {
                    System.out.println();
                    String beanName = rootDefinition.getBeanDescription(TypeInformation.TypeFormat.ANSI_SIMPLE, false);
                    long now = System.currentTimeMillis();
                    System.out.println("✅ Created " + beanName + " in " + (now - start) + "ms");
                    System.out.println("------------");
                }
            }
        }
    }

    void traceSegment(BeanResolutionContext context) {
        BeanResolutionContext.Path path = context.getPath();
        BeanResolutionContext.Segment<?, ?> segment = path.peek();
        if (segment != null) {
            BeanDefinition<?> declaringType = segment.getDeclaringType();
            if (declaringType == null || !declaringType.getBeanType().isSynthetic()) {

                int size = path.size();
                String prefix = "";
                if (size > 1) {
                    String spaces = "   ".repeat(size);
                    prefix = spaces + RIGHT_ARROW_LOOP;
                }
                String content = prefix + segment.toConsoleString(AnsiColour.isSupported());
                // TODO: other output methods
                switch (this) {
                    case STANDARD_OUT -> System.out.println(content);
                }
            }
        }
    }

    void traceBeanDisabled(BeanResolutionContext resolutionContext, String disabledBeanMessage) {
        String[] lines = disabledBeanMessage.split("\\r?\\n");
        String prefix = padLeft(resolutionContext, 3);
        // TODO: other output methods
        switch (this) {
            case STANDARD_OUT -> {
                for (String line : lines) {
                    if (StringUtils.isNotEmpty(line.trim())) {
                        System.out.print(prefix);
                        if (line.startsWith("*")) {
                            System.out.print("❌ ");
                            System.out.println(line.substring(1));
                        } else {
                            System.out.println(line);
                        }
                    }
                }
            }
        }
    }

    void traceConfiguration(
        Environment environment,
        @NonNull Collection<BeanDefinitionReference<?>> beanReferences,
        Collection<DisabledBean<?>> disabledBeans) {
        Collection<PropertySource> propertySources = environment.getPropertySources();
        Set<String> activeNames = environment.getActiveNames();

        System.out.println();
        System.out.println("Configuration Profile");
        System.out.println("---------------------");
        System.out.println(AnsiColour.brightBlue("Active Environment Names: ") + activeNames);
        System.out.println();
        System.out.println(AnsiColour.brightBlue("Available Property Sources (Priority Order Highest to Lowest): "));
        propertySources.stream().sorted(OrderUtil.REVERSE_ORDERED_COMPARATOR)
            .forEach(propertySource -> {
                System.out.print(" ✚ ");
                System.out.print(AnsiColour.formatObject(propertySource));
                System.out.print(" (");
                System.out.println(propertySource.getOrigin().location() + ")");
            });
        System.out.println();
        System.out.println(AnsiColour.brightBlue("Configurable Beans: "));
        List<BeanDefinitionReference<?>> configRefs = beanReferences.stream()
            .filter(ref -> ref.hasStereotype(ConfigurationReader.class) &&
                ref.stringValue(ConfigurationReader.class, "prefix").isPresent())
            .sorted((b1, b2) ->
                {
                    String p1 = b1.stringValue(ConfigurationReader.class, "prefix").get();
                    String p2 = b2.stringValue(ConfigurationReader.class, "prefix").get();
                    return p1.compareTo(p2);
                }
            ).toList();

        configRefs.forEach(ref -> {
                String prefix = ref.stringValue(ConfigurationReader.class, "prefix").orElse(null);
                if (prefix != null) {
                    Argument<?> argument = ref.asArgument();
                    System.out.print(" ✚ ");
                    System.out.print(AnsiColour.formatObject(prefix));
                    System.out.print(RIGHT_ARROW);
                    System.out.println(TypeInformation.TypeFormat.getTypeString(
                        TypeInformation.TypeFormat.ANSI_SHORTENED,
                        argument.getType(),
                        argument.getTypeVariables()
                    ));
                }
            });
        System.out.println();
        System.out.println(AnsiColour.brightBlue("Applicable Configuration Present: "));
        configRefs.stream()
            .flatMap(ref -> ref.stringValue(ConfigurationReader.class, "prefix").stream())
            .flatMap(prefix -> {
                if(prefix.endsWith(".*")) {
                    String eachProperty = prefix.substring(0, prefix.length() - 2);
                    return environment.getPropertyEntries(eachProperty).stream().flatMap(entry ->
                        {
                            String path = eachProperty + '.' + entry;
                            return environment.getPropertyEntries(
                                path
                            ).stream().map(p -> path + '.' + p);
                        }
                    );
                } else {
                    return environment.getPropertyEntries(prefix).stream().map(entry ->
                        prefix + '.' + entry
                    );
                }
            }).sorted().distinct().forEach(entry -> {
                System.out.print(" ✚ ");
                System.out.print(AnsiColour.formatObject(entry));
                PropertySource.PropertyEntry pe = environment.getPropertyEntry(entry).orElse(null);
                if (pe != null) {
                    String text = " Origin(" + AnsiColour.formatObject(pe.raw()) + " from " + AnsiColour.brightYellow(pe.origin().location()) + ")";
                    System.out.print(text);
                }
                System.out.println();
            });
        System.out.println("---------------------");
    }
}
