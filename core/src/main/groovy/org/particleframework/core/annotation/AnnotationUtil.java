package org.particleframework.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility methods for annotations
 *
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class AnnotationUtil {

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
    public static Set<? extends Annotation> findAnnotationsWithStereoType(Class<?> stereotype, Annotation... annotations) {
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

    private static boolean isNotInternalAnnotation(Annotation ann) {
        return !Retention.class.isInstance(ann) && !Documented.class.isInstance(ann) && !Target.class.isInstance(ann);
    }
}
