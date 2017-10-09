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

/**
 * Utility methods for annotations.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class AnnotationUtil {

    private static final Cache<AnnotationKey, Collection<Annotation>> ANNOTATIONS_BY_STEREOTYPE_CACHE = Caffeine.newBuilder()
            .maximumSize(100)
            .build();

    private static final Cache<AnnotationKey, Collection<? extends Annotation>> ANNOTATIONS_BY_TYPE_CACHE = Caffeine.newBuilder()
            .maximumSize(100)
            .build();

    /**
     * Constant indicating an zero annotation
     */
    public static final Annotation[] ZERO_ANNOTATIONS = new Annotation[0];

    /**
     * Constant indicating an zero annotation
     */
    public static final AnnotatedElement[] ZERO_ANNOTATED_ELEMENTS = new AnnotatedElement[0];

    /**
     * An empty re-usable element
     */
    public static final AnnotatedElement EMPTY_ANNOTATED_ELEMENT = new AnnotatedElement() {
        @Override
        public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
            return null;
        }

        @Override
        public Annotation[] getAnnotations() {
            return ZERO_ANNOTATIONS;
        }

        @Override
        public Annotation[] getDeclaredAnnotations() {
            return ZERO_ANNOTATIONS;
        }
    };

    /**
     * Finds an annotation on the given class for the given stereotype
     *
     * @param element The element
     * @param stereotype The stereotype
     * @return The annotation
     */
    public static Optional<Annotation> findAnnotationWithStereoType(AnnotatedElement element, Class stereotype) {
        String stereotypeName = stereotype.getName();
        return findAnnotationWithStereoType(element, stereotypeName);
    }

    /**
     * Finds an annotation on the given class for the given stereotype
     *
     * @param element The element
     * @param stereotypeName The stereotype
     * @return The annotation
     */
    public static Optional<Annotation> findAnnotationWithStereoType(AnnotatedElement element, String stereotypeName) {
        if(element instanceof Class) {
            return findAnnotationWithStereoType((Class)element, stereotypeName);
        }
        else {
            Annotation[] annotations = element.getAnnotations();
            return findAnnotationWithStereoType(stereotypeName, annotations);
        }
    }

    /**
     * Finds an annotation on the given class for the given stereotype. The result of this method is cached.
     * This method should not be used in runtime code that is executed repeatedly. Consumers of this method should be aware
     * that code that utilizes the method should be executed once upon startup.
     *
     *
     * @param type The type
     * @param stereotype The stereotype
     * @return The annotation
     */
    public static Optional<Annotation> findAnnotationWithStereoType(Class type, Class stereotype) {
        String stereotypeName = stereotype.getName();
        return findAnnotationWithStereoType(type, stereotypeName);
    }

    /**
     * Finds an annotation on the given class for the given stereotype. The result of this method is cached.
     * This method should not be used in runtime code that is executed repeatedly. Consumers of this method should be aware
     * that code that utilizes the method should be executed once upon startup.
     *
     *
     * @param type The type
     * @param stereotypeName The stereotype
     * @return The annotation
     */
    public static Optional<Annotation> findAnnotationWithStereoType(Class type, String stereotypeName) {
        return findAnnotationsWithStereoType(type, stereotypeName).stream().findFirst();
    }

    /**
     * Finds an annotation on the given method for the given type. The result of this method is cached.
     * This method should not be used in runtime code that is executed repeatedly. Consumers of this method should be aware
     * that code that utilizes the method should be executed once upon startup.
     *
     * @param method The method
     * @param annotationType The type
     * @param <T> The annotation generic type
     * @return The annotation
     */
    public static <T extends Annotation> Optional<T> findAnnotation(Method method, @Nullable Class<T> annotationType) {
        if(annotationType == null || method == null) {
            return Optional.empty();
        }
        return findAnnotations(method, annotationType).stream().findFirst();
    }


    /**
     * Finds all annotations on the given method for the given type. The result of this method is cached.
     * This method should not be used in runtime code that is executed repeatedly. Consumers of this method should be aware
     * that code that utilizes the method should be executed once upon startup.
     *
     * @param method The method
     * @param annotationType The type
     * @param <T> The annotation generic type
     * @return The annotation
     */
    public static <T extends Annotation> Collection<T> findAnnotations(Method method, @Nullable Class<T> annotationType) {
        if(annotationType == null || method == null) {
            return Collections.emptySet();
        }
        AnnotationKey key = new AnnotationKey(method, annotationType.getName());
        return (Collection<T>) ANNOTATIONS_BY_TYPE_CACHE.get(key, annotationKey ->
                findAnnotationsByTypeNoCache(method, annotationType)
        );
    }

    /**
     * Finds an annotation on the given class for the given type. The result of this method is cached.
     * This method should not be used in runtime code that is executed repeatedly. Consumers of this method should be aware
     * that code that utilizes the method should be executed once upon startup.
     *
     * @param type The type to search
     * @param annotationType The annotation type
     * @param <T> The annotation generic type
     * @return The annotation
     */
    public static <T extends Annotation> Optional<T> findAnnotation(@Nullable Class type, @Nullable Class<T> annotationType) {
        if(annotationType == null || type == null) {
            return Optional.empty();
        }
        return findAnnotations(type, annotationType).stream().findFirst();
    }

    /**
     * Finds all annotations on the given class for the given type. The result of this method is cached.
     * This method should not be used in runtime code that is executed repeatedly. Consumers of this method should be aware
     * that code that utilizes the method should be executed once upon startup.
     *
     * @param type The type to search
     * @param annotationType The annotation type
     * @param <T> The annotation generic type
     * @return The annotation
     */
    public static <T extends Annotation> Collection<T> findAnnotations(@Nullable Class type, @Nullable Class<T> annotationType) {
        if(annotationType == null || type == null) {
            return Collections.emptyList();
        }
        AnnotationKey key = new AnnotationKey(type, annotationType.getName());
        return (Collection<T>) ANNOTATIONS_BY_TYPE_CACHE.get(key, annotationKey -> findAnnotationsByTypeNoCache(type, annotationType));
    }

    /**
     * Finds an annotation on the given class for the given element. The result of this method is cached.
     * This method should not be used in runtime code that is executed repeatedly. Consumers of this method should be aware
     * that code that utilizes the method should be executed once upon startup.
     *
     * @param element The element
     * @param type The the annotation type
     * @param <T> The annotation generic type
     * @return The annotation
     */
    public static <T extends Annotation> Optional<T> findAnnotation(AnnotatedElement element, @Nullable Class<T> type) {
        if(type == null || element == null) {
            return Optional.empty();
        }
        if(element instanceof Method) {
            return findAnnotation((Method)element, type);
        }
        else if(element instanceof Class) {
            return findAnnotation((Class)element, type);
        }
        else {
            return Optional.ofNullable(element.getAnnotation(type));
        }
    }

    /**
     * Finds an annotation on the given class for the given element. The result of this method is cached.
     * This method should not be used in runtime code that is executed repeatedly. Consumers of this method should be aware
     * that code that utilizes the method should be executed once upon startup.
     *
     * @param element The element
     * @param type The the annotation type
     * @param <T> The annotation generic type
     * @return The annotation
     */
    public static <T extends Annotation> Collection<T> findAnnotations(AnnotatedElement element, @Nullable Class<T> type) {
        if(type == null || element == null) {
            return Collections.emptyList();
        }
        if(element instanceof Method) {
            return findAnnotations((Method)element, type);
        }
        else if(element instanceof Class) {
            return findAnnotations((Class)element, type);
        }
        else {
            return Arrays.asList( element.getAnnotationsByType(type) );
        }
    }

    /**
     * Finds an annotation on the given class for the given type
     *
     * @param annotations The annotations
     * @param type The annotation type
     * @param <T> The annotation generic type
     * @return The annotation
     */
    public static <T extends Annotation> Optional<T> findAnnotation(Annotation[] annotations, @Nullable Class<T> type) {
        if(type == null) {
            return Optional.empty();
        }
        return (Optional<T>) Arrays.stream(annotations)
                .filter(ann -> ann.annotationType() == type)
                .findFirst();
    }

    /**
     * Finds an annotation on the given class for the given stereotype
     *
     * @param stereotype The stereotype
     * @param annotations The annotations to search
     * @param <T> The annotation generic type
     * @return The annotation
     */
    public static <T extends Annotation> Optional<T> findAnnotationWithStereoType(Class stereotype, Annotation... annotations) {
        String stereotypeName = stereotype.getName();
        return findAnnotationWithStereoType(stereotypeName, annotations);
    }

    /**
     * Finds an annotation on the given class for the given stereotype
     *
     * @param stereotypeName The stereotype
     * @param annotations The annotations to search
     * @param <T> The annotation generic type
     * @return The annotation
     */
    public static <T extends Annotation> Optional<T> findAnnotationWithStereoType(String stereotypeName, Annotation... annotations) {
        for(Annotation ann : annotations) {
            if(stereotypeName.equals(ann.annotationType().getName())) {
                return Optional.of((T) ann);
            }
            else if(isNotInternalAnnotation(ann)) {
                if(findAnnotationWithStereoType(stereotypeName, ann.annotationType().getAnnotations()).isPresent()) {
                    return Optional.of((T) ann);
                }
            }
        }
        return Optional.empty();
    }
    /**
     * Finds an annotation from the given array of annotations that matches the given stereotype
     *
     * @param stereotype The stereotype
     * @param annotations The annotations to search
     * @return The annotation
     */
    public static Collection<Annotation> findAnnotationsWithStereoType(Class<?> stereotype, Annotation... annotations) {
        String stereotypeName = stereotype.getName();
        Collection<Annotation> annotationList = new ArrayList<>();
        for(Annotation ann : annotations) {
            if(stereotypeName.equals(ann.annotationType().getName())) {
                annotationList.add(  ann);
            }
            else if(isNotInternalAnnotation(ann)) {
                if(findAnnotationWithStereoType(ann.annotationType(), stereotype).isPresent()) {
                    annotationList.add( ann);
                }
            }
        }
        return Collections.unmodifiableCollection(annotationList);
    }

    /**
     * Finds an annotation from the given array of annotations that matches the given stereotype
     *
     * @param stereotypeName The stereotype
     * @param annotations The annotations to search
     * @return The annotation
     */
    public static Collection<Annotation> findAnnotationsWithStereoType(String stereotypeName, Annotation... annotations) {
        Collection<Annotation> annotationList = new ArrayList<>();
        for(Annotation ann : annotations) {
            if(stereotypeName.equals(ann.annotationType().getName())) {
                annotationList.add(  ann);
            }
            else if(isNotInternalAnnotation(ann)) {
                if(findAnnotationWithStereoType(stereotypeName, ann.annotationType().getAnnotations() ).isPresent()) {
                    annotationList.add( ann);
                }
            }
        }
        return Collections.unmodifiableCollection(annotationList);
    }

    /**
     * Find annotations for the given stereotype on the given class. The result of this method is cached.
     * This method should not be used in runtime code that is executed repeatedly. Consumers of this method should be aware
     * that code that utilizes the method should be executed once upon startup.
     *
     * @param type The class to search
     * @param stereotype The stereotype
     * @return The annotations
     */
    public static Collection<Annotation> findAnnotationsWithStereoType(final Class type, final Class<?> stereotype) {
        if(type == null || stereotype == null) {
            return Collections.emptySet();
        }
        String stereotypeName = stereotype.getName();
        return findAnnotationsWithStereoType(type, stereotypeName);
    }

    /**
     * Find annotations for the given stereotype on the given class. The result of this method is cached.
     * This method should not be used in runtime code that is executed repeatedly. Consumers of this method should be aware
     * that code that utilizes the method should be executed once upon startup.
     *
     *
     * @param type The class to search
     * @param stereotypeName The stereotype
     * @return The annotations
     */
    public static Collection<Annotation> findAnnotationsWithStereoType(final Class type, final String stereotypeName) {
        AnnotationKey key = new AnnotationKey(type, stereotypeName);
        return ANNOTATIONS_BY_STEREOTYPE_CACHE.get(key, annotationKey -> findAnnotationsWithStereoTypeNoCache(type, stereotypeName));
    }


    /**
     * Find annotations for the given stereotype on the given method. The result of this method is cached.
     * This method should not be used in runtime code that is executed repeatedly. Consumers of this method should be aware
     * that code that utilizes the method should be executed once upon startup.
     *
     *
     * @param method The method
     * @param stereotype The stereotype
     * @return The annotation set
     */
    public static Collection<Annotation> findAnnotationsWithStereoType(final Method method, final Class<?> stereotype) {
        String stereotypeName = stereotype.getName();
        return findAnnotationsWithStereoType(method, stereotypeName);
    }

    /**
     * Find annotations for the given stereotype on the given method. The result of this method is cached.
     * This method should not be used in runtime code that is executed repeatedly. Consumers of this method should be aware
     * that code that utilizes the method should be executed once upon startup.
     *
     *
     * @param method The method
     * @param stereotypeName The stereotype
     * @return The annotation set
     */
    public static Collection<Annotation> findAnnotationsWithStereoType(final Method method, final String stereotypeName) {
        AnnotationKey key = new AnnotationKey(method, stereotypeName);
        return ANNOTATIONS_BY_STEREOTYPE_CACHE.get(key, annotationKey -> findAnnotationsWithStereoTypeNoCache(method, stereotypeName));
    }

    /**
     * Find all the annotations on the given {@link AnnotatedElement} candidates for the given stereotype
     *
     * @param candidates The annotated element
     * @param stereotype The stereotype
     * @return The collection of annotations
     */
    public static Collection<Annotation> findAnnotationsWithStereoType(AnnotatedElement[] candidates, Class<?> stereotype) {
        Collection<Annotation> annotations = new ArrayList<>();
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
        return findAnnotationsWithStereoType(stereotype, annotatedElement.getAnnotations());
    }

    private static boolean isNotInternalAnnotation(Annotation ann) {
        return !Retention.class.isInstance(ann) && !Documented.class.isInstance(ann) && !Target.class.isInstance(ann);
    }

    private static Collection<Annotation> findAnnotationsWithStereoTypeNoCache(Method method,String stereotype) {
        Collection<Annotation> annotations = new ArrayList<>(findAnnotationsWithStereoType(stereotype, method.getAnnotations()));
        Class<?> classToSearch = method.getDeclaringClass().getSuperclass();
        while(classToSearch != Object.class && classToSearch != null) {
            Optional<Method> declaredMethod = ReflectionUtils.getDeclaredMethod(classToSearch, method.getName(), method.getParameterTypes());
            declaredMethod.ifPresent(superMethod ->
                    annotations.addAll( findAnnotationsWithStereoTypeNoCache(superMethod, stereotype))
            );
            classToSearch = classToSearch.getSuperclass();
        }
        Set<Class> allInterfaces = ReflectionUtils.getAllInterfaces(method.getDeclaringClass());
        for (Class itfe : allInterfaces) {
            Optional<Method> declaredMethod = ReflectionUtils.getDeclaredMethod(itfe, method.getName(), method.getParameterTypes());
            declaredMethod.ifPresent(interfaceMethod ->
                    annotations.addAll( findAnnotationsWithStereoTypeNoCache(interfaceMethod, stereotype))
            );
        }
        annotations.addAll(findAnnotationsWithStereoTypeNoCache(method.getDeclaringClass(), stereotype));
        return annotations.isEmpty() ? Collections.emptyList() : annotations;
    }

    private static Collection<Annotation> findAnnotationsWithStereoTypeNoCache(Class type, String stereotypeName) {
        Collection annotationList = new ArrayList();
        Class classToSearch = type;
        while(classToSearch != Object.class && classToSearch != null) {
            Annotation[] annotations = classToSearch.getAnnotations();
            annotationList.addAll(findAnnotationsWithStereoType(stereotypeName, annotations));
            Set<Class> allInterfaces = ReflectionUtils.getAllInterfaces(classToSearch);
            for (Class itfe : allInterfaces) {
                annotationList.addAll(findAnnotationsWithStereoType(stereotypeName, itfe.getAnnotations()));
            }
            classToSearch = classToSearch.getSuperclass();
        }
        return Collections.unmodifiableCollection(annotationList);
    }

    private static <T extends Annotation> Collection<T> findAnnotationsByTypeNoCache(@Nullable Method method, @Nullable Class<T> annotationType) {
        T annotation = method.getAnnotation(annotationType);
        Collection<T> annotations = new ArrayList<>();
        if(annotation != null) {
            annotations.add(annotation);
        }
        Class<?> classToSearch = method.getDeclaringClass().getSuperclass();
        while(classToSearch != Object.class && classToSearch != null) {
            Optional<Method> declaredMethod = ReflectionUtils.getDeclaredMethod(classToSearch, method.getName(), method.getParameterTypes());

            if( declaredMethod.isPresent() ) {
                annotation = declaredMethod.get().getAnnotation(annotationType);
                if(annotation != null) {
                    annotations.add(annotation);
                }
            }
            classToSearch = classToSearch.getSuperclass();
        }
        Set<Class> allInterfaces = ReflectionUtils.getAllInterfaces(method.getDeclaringClass());
        for (Class itfe : allInterfaces) {
            Optional<Method> declaredMethod = ReflectionUtils.getDeclaredMethod(itfe, method.getName(), method.getParameterTypes());
            if( declaredMethod.isPresent() ) {
                annotation = declaredMethod.get().getAnnotation(annotationType);
                if(annotation != null) {
                    annotations.add(annotation);
                }
            }
        }
        annotations.addAll(findAnnotationsByTypeNoCache(method.getDeclaringClass(), annotationType));
        return annotations.isEmpty() ? Collections.emptyList() : annotations;
    }

    private static <T extends Annotation> Collection<T> findAnnotationsByTypeNoCache(@Nullable Class type, @Nullable Class<T> annotationType) {
        Collection<T> annotationList = new ArrayList<>();
        Class classToSearch = type;
        while(classToSearch != Object.class && classToSearch != null) {
            Annotation[] annotations = classToSearch.getAnnotations();
            for (Annotation annotation : annotations) {
                if( annotation.annotationType() == annotationType ) {
                    annotationList.add((T) annotation);
                }
            }
            classToSearch = classToSearch.getSuperclass();
        }
        Set<Class> allInterfaces = ReflectionUtils.getAllInterfaces(type);
        for (Class itfe : allInterfaces) {
            Collection<T> anns = findAnnotationsByTypeNoCache(itfe, annotationType);
            annotationList.addAll(anns);
        }
        return annotationList.isEmpty() ? Collections.emptyList() : annotationList;
    }

    private static class AnnotationKey {


        private final AnnotatedElement element;
        private final String annotationClassName;

        public AnnotationKey(AnnotatedElement element, String annotationClass) {
            this.element = element;
            this.annotationClassName = annotationClass;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            AnnotationKey that = (AnnotationKey) o;

            if (!element.equals(that.element)) return false;
            return annotationClassName.equals(that.annotationClassName);
        }

        @Override
        public int hashCode() {
            int result = element.hashCode();
            result = 31 * result + annotationClassName.hashCode();
            return result;
        }
    }
}
