package io.micronaut.context;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.TypeInformation;
import io.micronaut.core.util.AnsiColour;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.BeanDefinition;
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

    void startResolve(
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
                System.out.println(beanName + RIGHT_ARROW + beanDefinition.getBeanDescription(TypeInformation.TypeFormat.ANSI_SHORTENED) + " at location:");
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

    <T> void beanResolved(
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
                    System.out.print(AnsiColour.yellow(qualifier.toString()));
                    System.out.print(" ");
                }
                System.out.print(AnsiColour.formatObject(bean));
                System.out.println();
            }
        }
    }

    void valueResolved(
        BeanResolutionContext resolutionContext,
        Argument<?> argument,
        String property,
        Object value) {
        String prefix = padLeft(resolutionContext, 1) + RIGHT_ARROW;
        // TODO: other output methods
        switch (this) {
            case STANDARD_OUT -> {
                System.out.print(prefix);
                System.out.print(AnsiColour.formatObject(property));
                System.out.print(" = ");
                System.out.print(AnsiColour.formatObject(value));
                System.out.println();
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

    void finishResolve(BeanResolutionContext resolutionContext, BeanDefinition<?> rootDefinition) {
        Object v = resolutionContext.getAttribute(START_TIME);
        if (v instanceof Long start) {
            // TODO: other output methods
            switch (this) {
                case STANDARD_OUT -> {
                    System.out.println();
                    String beanName = rootDefinition.getTypeInformation().getBeanTypeString(TypeInformation.TypeFormat.ANSI_SIMPLE);
                    long now = System.currentTimeMillis();
                    System.out.println("✅ Created " + beanName + " in " + (now - start) + "ms");
                    System.out.println("------------");
                }
            }
        }
    }

    void debugSegment(BeanResolutionContext context) {
        BeanResolutionContext.Path path = context.getPath();
        int size = path.size();
        String prefix = "";
        if (size > 1) {
            String spaces = "   ".repeat(size);
            prefix = spaces + RIGHT_ARROW_LOOP;
        }
        String content = prefix + path.peek().toConsoleString(AnsiColour.isSupported());
        // TODO: other output methods
        switch (this) {
            case STANDARD_OUT -> System.out.println(content);
        }
    }

    void beansDisabled(BeanResolutionContext resolutionContext, String disabledBeanMessage) {
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
}
