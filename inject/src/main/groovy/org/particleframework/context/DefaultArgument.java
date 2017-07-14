package org.particleframework.context;

import org.particleframework.core.annotation.AnnotationUtil;
import org.particleframework.core.annotation.Internal;
import org.particleframework.inject.Argument;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents an argument to a constructor or method
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class DefaultArgument<T> implements Argument<T> {
    private final Class type;
    private final String name;
    private final Annotation qualifier;
    private final Class[] genericTypes;
    private final Annotation[] annotations;

    DefaultArgument(Class type, String name, Annotation qualifier, Annotation[] annotations, Class...genericTypes) {
        this.type = type;
        this.name = name;
        this.annotations = annotations;
        this.qualifier = qualifier;
        this.genericTypes = genericTypes;
    }

    DefaultArgument(Class type, String name, Annotation qualifier,  Class...genericTypes) {
        this.type = type;
        this.name = name;
        this.annotations = new Annotation[0];
        this.qualifier = qualifier;
        this.genericTypes = genericTypes;
    }

    @Override
    public Class[] getGenericTypes() {
        return this.genericTypes;
    }

    /**
     * Builds the arguments from the given maps. A LinkedHashMap is used to maintain the order of the arguments
     *
     * @param arguments The arguments
     * @param qualifiers The qualifiers
     * @param genericTypes The generic types
     *
     * @return The argument array
     */
    static Argument[] from(Map<String, Class> arguments,
                           Map<String, Annotation> qualifiers,
                           Map<String, List<Class>> genericTypes,
                           AnnotationResolver annotationResolver) {
        if(arguments == null) {
            return new Argument[0];
        }

        List<Argument> args = new ArrayList<>(arguments.size());
        int i = 0;
        for (Map.Entry<String, Class> entry : arguments.entrySet()) {
            String name = entry.getKey();
            Annotation[] annotations = annotationResolver.resolveAnnotations(i++);
            Annotation qualifier = qualifiers != null ? qualifiers.get(name) : null;
            List<Class> genericTypeList = genericTypes != null ? genericTypes.get(name) : null;
            Class[] genericsArray = genericTypeList != null ? genericTypeList.toArray(new Class[genericTypeList.size()]) : new Class[0];
            args.add( new DefaultArgument(entry.getValue(), name, qualifier, annotations, genericsArray));
        }
        return args.toArray(new Argument[arguments.size()]);
    }

    @Override
    public Class getType() {
        return type;
    }

    @Override
    public Annotation getQualifier() {
        return this.qualifier;
    }

    @Override
    public <A extends Annotation> A findAnnotation(Class<? extends Annotation> stereotype) {
        return AnnotationUtil.findAnnotationWithStereoType(stereotype, annotations);
    }

    @Override
    public <A extends Annotation> A getAnnotation(Class<A> annotationClass) {
        for (Annotation annotation : annotations) {
            if(annotation.annotationType().equals(annotationClass)) {
                return (A) annotation;
            }
        }
        return null;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return type.getSimpleName() + " " + name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DefaultArgument that = (DefaultArgument) o;

        if (!type.equals(that.type)) return false;
        if (!name.equals(that.name)) return false;
        return qualifier != null ? qualifier.equals(that.qualifier) : that.qualifier == null;
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + (qualifier != null ? qualifier.hashCode() : 0);
        return result;
    }

    @Override
    public Annotation[] getAnnotations() {
        return annotations;
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return annotations;
    }

    interface AnnotationResolver {
        Annotation[] resolveAnnotations(int index);
    }
}