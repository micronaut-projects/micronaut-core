package org.particleframework.core.annotation;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.particleframework.core.reflect.ReflectionUtils;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;

/**
 * Utility methods for annotations.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class AnnotationUtil {

    private static final Cache<AnnotationKey, Set<Annotation>> ANNOTATIONS_BY_STEREOTYPE_CACHE = Caffeine.newBuilder()
            .maximumSize(200)
            .weakKeys()
            .build();

    /**
     * Constant indicating an zero annotation
     */
    public static final Annotation[] ZERO_ANNOTATIONS = new Annotation[0];

    /**
     * Finds an annotation on the given class for the given stereotype
     *
     * @param cls The class
     * @param stereotype The stereotype
     * @return The annotation
     */
    public static <T extends Annotation> T findAnnotationWithStereoType(AnnotatedElement cls, Class stereotype) {
        Annotation[] annotations = cls.getAnnotations();
        return findAnnotationWithStereoType(stereotype, annotations);
    }

    /**
     * Finds an annotation on the given class for the given stereotype
     *
     * @param method The method
     * @param type The stereotype
     * @return The annotation
     */
    public static <T extends Annotation> T findAnnotation(Method method, Class type) {
        Optional<Annotation> result = findAnnotationsWithStereoType(method, type)
                .stream()
                .filter(annotation -> annotation.annotationType() == type)
                .findFirst();
        return (T) result.orElseGet(() ->
            findAnnotationsWithStereoType(method.getDeclaringClass(), type)
                .stream()
                .filter(annotation -> annotation.annotationType() == type)
                .findFirst().orElse(null)
        );
    }


    /**
     * Finds an annotation on the given class for the given stereotype
     *
     * @param stereotype The stereotype
     * @param annotations The annotations to search
     * @return The annotation
     */
    public static <T extends Annotation> T findAnnotationWithStereoType(Class stereotype, Annotation... annotations) {
        for(Annotation ann : annotations) {
            if(stereotype.isInstance(ann)) {
                return (T) ann;
            }
            else if(isNotInternalAnnotation(ann)) {
                if(findAnnotationWithStereoType(ann.annotationType(), stereotype) != null) {
                    return (T) ann;
                }
            }
        }
        return null;
    }

    /**
     * Finds an annotation on the given class for the given stereotype
     *
     * @param stereotype The stereotype
     * @param annotations The annotations to search
     * @return The annotation
     */
    public static Set<Annotation> findAnnotationsWithStereoType(Class<?> stereotype, Annotation... annotations) {
        Set<Annotation> annotationSet = new HashSet<>();
        for(Annotation ann : annotations) {
            if(stereotype.isInstance(ann)) {
                annotationSet.add(  ann);
            }
            else if(isNotInternalAnnotation(ann)) {
                if(findAnnotationWithStereoType(ann.annotationType(), stereotype) != null) {
                    annotationSet.add( ann);
                }
            }
        }
        return Collections.unmodifiableSet(annotationSet);
    }

    /**
     * Finds an annotation on the given class for the given stereotype
     *
     * @param classToSearch The class to search
     * @param stereotype The stereotype
     * @return The annotations
     */
    public static Set<Annotation> findAnnotationsWithStereoType(Class classToSearch, Class<?> stereotype) {
        if(classToSearch == null) {
            return Collections.emptySet();
        }
        AnnotationKey key = new AnnotationKey(classToSearch, stereotype);
        Set<Annotation> annotationSet = ANNOTATIONS_BY_STEREOTYPE_CACHE.getIfPresent(key);
        if(annotationSet == null) {
            annotationSet = new HashSet<>();
            while(classToSearch != Object.class && classToSearch != null) {
                Annotation[] annotations = classToSearch.getAnnotations();
                annotationSet.addAll(findAnnotationsWithStereoType(stereotype, annotations));
                Set<Class> allInterfaces = ReflectionUtils.getAllInterfaces(classToSearch);
                for (Class itfe : allInterfaces) {
                    annotationSet.addAll(findAnnotationsWithStereoType(stereotype, itfe.getAnnotations()));
                }
                classToSearch = classToSearch.getSuperclass();
            }
            annotationSet = Collections.unmodifiableSet(annotationSet);
            ANNOTATIONS_BY_STEREOTYPE_CACHE.put(key, annotationSet);
        }
        return annotationSet;
    }

    /**
     * Find annotations for the given stereotype on the given method. The result of this method is cached in a weak hashmap.
     * This method should not be used in runtime code that is executed repeatedly. Consumers of this method should be aware
     * that code that utilizes the method should be executed once upon startup.
     *
     *
     * @param method The method
     * @param stereotype The stereotype
     * @return The annotation set
     */
    public static Set<Annotation> findAnnotationsWithStereoType(Method method, Class<?> stereotype) {
        AnnotationKey key = new AnnotationKey(method, stereotype);
        return ANNOTATIONS_BY_STEREOTYPE_CACHE.get(key, annotationKey -> findAnnotationsWithStereoTypeNoCache(method, stereotype));
    }

    private static Set<Annotation> findAnnotationsWithStereoTypeNoCache(Method method, Class<?> stereotype) {
        Set<Annotation> annotationSet = new HashSet<>(findAnnotationsWithStereoType(stereotype, method.getAnnotations()));
        Class<?> classToSearch = method.getDeclaringClass().getSuperclass();
        while(classToSearch != Object.class && classToSearch != null) {
            Optional<Method> declaredMethod = ReflectionUtils.getDeclaredMethod(classToSearch, method.getName(), method.getParameterTypes());
            declaredMethod.ifPresent(superMethod ->
                    annotationSet.addAll( findAnnotationsWithStereoTypeNoCache(superMethod, stereotype))
            );
            classToSearch = classToSearch.getSuperclass();
        }
        Set<Class> allInterfaces = ReflectionUtils.getAllInterfaces(method.getDeclaringClass());
        for (Class itfe : allInterfaces) {
            Optional<Method> declaredMethod = ReflectionUtils.getDeclaredMethod(itfe, method.getName(), method.getParameterTypes());
            declaredMethod.ifPresent(interfaceMethod ->
                    annotationSet.addAll( findAnnotationsWithStereoTypeNoCache(interfaceMethod, stereotype))
            );
        }
        return Collections.unmodifiableSet(annotationSet);
    }

    /**
     * Find all the annotations on the given {@link AnnotatedElement} candidates for the given stereotype
     *
     * @param candidates The annotated element
     * @param stereotype The stereotype
     * @return The collection of annotations
     */
    public static Set<Annotation> findAnnotationsWithStereoType(AnnotatedElement[] candidates, Class<?> stereotype) {
        Set<Annotation> annotations = new HashSet<>();
        for (AnnotatedElement candidate : candidates) {
            annotations.addAll(findAnnotationsWithStereoType(candidate, stereotype));
        }
        return annotations;
    }

    /**
     * Find all the annotations on the given {@link AnnotatedElement} for the given stereotype
     *
     * @param annotatedElement The annotated element
     * @param stereotype The stereotype
     * @return The collection of annotations
     */
    public static Collection<? extends Annotation> findAnnotationsWithStereoType(AnnotatedElement annotatedElement, Class<?> stereotype) {
        if(annotatedElement instanceof Method) {
            return findAnnotationsWithStereoType((Method)annotatedElement, stereotype);
        }
        else if(annotatedElement instanceof Class){
            return findAnnotationsWithStereoType((Class)annotatedElement, stereotype);
        }
        return Collections.emptySet();
    }

    private static boolean isNotInternalAnnotation(Annotation ann) {
        return !Retention.class.isInstance(ann) && !Documented.class.isInstance(ann) && !Target.class.isInstance(ann);
    }

    private static class AnnotationKey {
        private final AnnotatedElement element;
        private final Class annotationClass;

        public AnnotationKey(AnnotatedElement element, Class annotationClass) {
            this.element = element;
            this.annotationClass = annotationClass;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            AnnotationKey that = (AnnotationKey) o;

            if (!element.equals(that.element)) return false;
            return annotationClass.equals(that.annotationClass);
        }

        @Override
        public int hashCode() {
            int result = element.hashCode();
            result = 31 * result + annotationClass.hashCode();
            return result;
        }
    }
}
