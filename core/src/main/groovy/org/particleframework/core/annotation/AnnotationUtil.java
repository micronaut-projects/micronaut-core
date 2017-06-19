package org.particleframework.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Utility methods for annotations
 *
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class AnnotationUtil {

    /**
     * Finds an annotation on the given class for the given stereotype
     *
     * @param cls The class
     * @param stereotype The stereotype
     * @return The annotation
     */
    public static Annotation findAnnotationWithStereoType(Class cls, Class<? extends Annotation> stereotype) {
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
    public static Annotation findAnnotationWithStereoType(Class<? extends Annotation> stereotype, Annotation... annotations) {
        for(Annotation ann : annotations) {
            if(stereotype.isInstance(ann)) {
                return ann;
            }
            else if(!Retention.class.isInstance(ann) && !Documented.class.isInstance(ann) && !Target.class.isInstance(ann)) {
                if(findAnnotationWithStereoType(ann.getClass(), stereotype) != null) {
                    return ann;
                }
            }
        }
        return null;
    }
}
