package org.particleframework.context;

import org.particleframework.inject.Argument;

import java.util.ArrayList;
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

    public DefaultArgument(Class type, String name) {
        this.type = type;
        this.name = name;
    }

    static Argument[] from(Map<String, Class> arguments) {
        List<Argument> args = new ArrayList<>(arguments.size());
        for (Map.Entry<String, Class> entry : arguments.entrySet()) {
            args.add( new DefaultArgument(entry.getValue(), entry.getKey()));
        }
        return args.toArray(new Argument[arguments.size()]);
    }

    @Override
    public Class getType() {
        return type;
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