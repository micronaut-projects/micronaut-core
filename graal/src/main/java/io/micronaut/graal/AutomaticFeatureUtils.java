package io.micronaut.graal;

import com.oracle.svm.core.jdk.proxy.DynamicProxyRegistry;
import io.micronaut.core.annotation.Internal;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature.BeforeAnalysisAccess;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


/**
 * Utility methods for implementing Graal's {@link com.oracle.svm.core.annotate.AutomaticFeature}.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.0
 */
@Internal
public final class AutomaticFeatureUtils {

    /**
     * Marks the given class to be initialized at build time, only if it is present.
     *
     * @param access the {@link BeforeAnalysisAccess} instance
     * @param className the class name
     */
    public static void initializeAtBuildTime(BeforeAnalysisAccess access, String className) {
        findClass(access, className).ifPresent(RuntimeClassInitialization::initializeAtBuildTime);
    }

    /**
     * Marks the given class to be initialized at runtime, only if it is present.
     *
     * @param access the {@link BeforeAnalysisAccess} instance
     * @param className the class name
     */
    public static void initializeAtRunTime(BeforeAnalysisAccess access, String className) {
        findClass(access, className).ifPresent(RuntimeClassInitialization::initializeAtRunTime);
    }

    /**
     * Allows reflection usage for all fields and methods of the given class, only if it is present.
     *
     * @param access the {@link BeforeAnalysisAccess} instance
     * @param className the class name
     */
    public static void registerForRuntimeReflection(BeforeAnalysisAccess access, String className) {
        findClass(access, className).ifPresent(AutomaticFeatureUtils::registerAllAccess);
    }

    /**
     * Allows reflection instantiation of the given class, only if it is present.
     *
     * @param access the {@link BeforeAnalysisAccess} instance
     * @param className the class name
     */
    public static void registerClassForRuntimeReflection(BeforeAnalysisAccess access, String className) {
        findClass(access, className).ifPresent(AutomaticFeatureUtils::registerClassForRuntimeReflection);
    }

    /**
     * Allows reflection usage for all methods of the given class, only if it is present.
     *
     * @param access the {@link BeforeAnalysisAccess} instance
     * @param className the class name
     */
    public static void registerMethodsForRuntimeReflection(BeforeAnalysisAccess access, String className) {
        findClass(access, className).ifPresent(AutomaticFeatureUtils::registerMethodsForRuntimeReflection);
    }

    /**
     * Allows reflection usage for all fields of the given class, only if it is present.
     *
     * @param access the {@link BeforeAnalysisAccess} instance
     * @param className the class name
     */
    public static void registerFieldsForRuntimeReflection(BeforeAnalysisAccess access, String className) {
        findClass(access, className).ifPresent(AutomaticFeatureUtils::registerFieldsForRuntimeReflection);
    }

    /**
     * Registers the given interfaces for dynamic proxy generation.
     *
     * @param access the {@link BeforeAnalysisAccess} instance
     * @param interfaces the list of interfaces that the generated proxy can implement
     */
    public static void addProxyClass(BeforeAnalysisAccess access, String... interfaces) {
        List<Class<?>> classList = new ArrayList<>();
        for (String anInterface : interfaces) {
            Class<?> clazz = access.findClassByName(anInterface);
            if (clazz != null) {
                classList.add(clazz);
            }
        }
        if (classList.size() == interfaces.length) {
            ImageSingletons.lookup(DynamicProxyRegistry.class).addProxyClass(classList.toArray(new Class<?>[interfaces.length]));
        }
    }

    private static void registerAllAccess(Class<?> clazz) {
        registerClassForRuntimeReflection(clazz);
        registerFieldsForRuntimeReflection(clazz);
        registerMethodsForRuntimeReflection(clazz);
    }

    private static void registerClassForRuntimeReflection(Class<?> clazz) {
        RuntimeReflection.register(clazz);
        RuntimeReflection.registerForReflectiveInstantiation(clazz);

    }

    private static void registerMethodsForRuntimeReflection(Class<?> clazz) {
        for (Method method : clazz.getMethods()) {
            RuntimeReflection.register(method);
        }
    }

    private static void registerFieldsForRuntimeReflection(Class<?> clazz) {
        for (Field field : clazz.getFields()) {
            RuntimeReflection.register(field);
        }
    }

    private static Optional<Class<?>> findClass(BeforeAnalysisAccess access, String className) {
        return Optional.ofNullable(access.findClassByName(className));
    }

}
