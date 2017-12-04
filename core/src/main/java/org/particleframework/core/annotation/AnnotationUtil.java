package org.particleframework.core.annotation;

import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.reflect.ReflectionUtils;

import javax.annotation.Nullable;
import java.lang.annotation.*;
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

    public static final List<String> INTERNAL_ANNOTATION_NAMES = Arrays.asList(
            Retention.class.getName(),
            Deprecated.class.getName(),
            Inherited.class.getName(),
            SuppressWarnings.class.getName(),
            Override.class.getName(),
            Repeatable.class.getName(),
            Documented.class.getName(),
            Target.class.getName()
    );

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
     * Find the value of the annotation dynamically
     *
     * @param annotation The annotation
     * @param requiredType The required requiredType
     * @param attribute The attribute
     * @param <T> The value requiredType
     * @return An {@link Optional} of the value if it is present
     */
    public static <T> Optional<T> findValueOfType(Annotation annotation, Class<T> requiredType, String attribute) {
        Class<? extends Annotation> annotationType = annotation.annotationType();
        Optional<Method> method = ReflectionUtils.getDeclaredMethod(annotationType, attribute);
        return method.flatMap( m -> ConversionService.SHARED.convert( ReflectionUtils.invokeMethod(annotation, m), requiredType) );
    }



    /**
     * Find the value of the annotation dynamically
     *
     * @param annotation The annotation
     * @param requiredType The required requiredType
     * @param <T> The value requiredType
     * @return An {@link Optional} of the value if it is present
     */
    public static <T> Optional<T> findValueOfType(Annotation annotation, Class<T> requiredType) {
        return findValueOfType(annotation, requiredType, "value");
    }
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
     * Find all the annotations on the given {@link AnnotatedElement} candidates for the given stereotype
     *
     * @param candidates The annotated element
     * @param stereotype The stereotype
     * @return The collection of annotations
     */
    public static Collection<Annotation> findAnnotationsWithStereoType(AnnotatedElement[] candidates, Class<?> stereotype) {
        Collection<Annotation> annotations = new ArrayList<>();
        for (AnnotatedElement candidate : candidates) {
            Collection<? extends Annotation> found = findAnnotationsWithStereoType(candidate, stereotype);
            for (Annotation annotation : found) {
                if(!annotations.contains(annotation)) {
                    annotations.add(annotation);
                }
            }
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
        return findAnnotationsWithStereoType(stereotype, annotatedElement.getAnnotations());
    }

    private static boolean isNotInternalAnnotation(Annotation ann) {
        return !INTERNAL_ANNOTATION_NAMES.contains(ann.annotationType().getName());
    }

}
