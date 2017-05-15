package org.particleframework.context;

import org.particleframework.inject.Argument;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents an argument to a constructor or method
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class DefaultArgument implements Argument {
    private final Class type;
    private final String name;
    private final Annotation qualifier;

    DefaultArgument(Class type, String name, Annotation qualifier) {
        this.type = type;
        this.name = name;
        this.qualifier = qualifier;
    }

    static Argument[] from(Map<String, Class> arguments, LinkedHashMap<String, Annotation> qualifiers) {
        List<Argument> args = new ArrayList<>(arguments.size());
        for (Map.Entry<String, Class> entry : arguments.entrySet()) {
            String name = entry.getKey();
            Annotation qualifier = qualifiers != null ? qualifiers.get(name) : null;
            args.add( new DefaultArgument(entry.getValue(), name, qualifier));
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

        if (type != null ? !type.equals(that.type) : that.type != null) return false;
        return name != null ? name.equals(that.name) : that.name == null;
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        return result;
    }
}