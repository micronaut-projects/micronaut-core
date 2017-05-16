package org.particleframework.context;

import org.particleframework.context.exceptions.CircularDependencyException;
import org.particleframework.core.annotation.Internal;
import org.particleframework.inject.*;

import java.util.*;

/**
 * Default implementation of the {@link ComponentResolutionContext} interface
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class DefaultComponentResolutionContext implements ComponentResolutionContext {

    private final Context context;
    private final ComponentDefinition rootDefinition;
    private final Path path;

    @Internal
    public DefaultComponentResolutionContext(Context context, ComponentDefinition rootDefinition) {
        this.context = context;
        this.rootDefinition = rootDefinition;
        this.path = new DefaultPath();
    }

    @Override
    public Context getContext() {
        return context;
    }

    @Override
    public ComponentDefinition getRootDefinition() {
        return rootDefinition;
    }

    @Override
    public Path getPath() {
        return path;
    }


    class DefaultPath extends LinkedList<Segment> implements Path {

        public static final String RIGHT_ARROW = " --> ";

        DefaultPath() {
        }

        @Override
        public String toString() {
            Iterator<Segment> i = descendingIterator();
            StringBuilder path = new StringBuilder();
            while(i.hasNext()) {
                path.append(i.next().toString());
                if(i.hasNext()) {
                    path.append(" --> ");
                }
            }
            return path.toString();
        }

        @Override
        public String toCircularString() {
            Iterator<Segment> i = descendingIterator();
            StringBuilder path = new StringBuilder();
            String ls = System.getProperty("line.separator");
            while(i.hasNext()) {
                String segmentString = i.next().toString();
                path.append(segmentString);
                if(i.hasNext()) {
                    path.append(RIGHT_ARROW);
                }
                else {
                    int totalLength = path.length() - 3;
                    String spaces = String.join("", Collections.nCopies(totalLength, " "));
                    path.append(ls)
                        .append("^")
                        .append(spaces)
                        .append("|")
                        .append(ls)
                        .append("|")
                        .append(spaces)
                        .append("|").append(ls)
                        .append("|")
                        .append(spaces)
                        .append("|").append(ls).append('+');
                    path.append(String.join("", Collections.nCopies(totalLength, "-"))).append('+');
                }
            }
            return path.toString();
        }

        @Override
        public Path pushConstructorResolve(ComponentDefinition declaringType, Argument argument) {
            ConstructorSegment constructorSegment = new ConstructorSegment(declaringType, argument);
            if(contains(constructorSegment)) {
                throw new CircularDependencyException(DefaultComponentResolutionContext.this, argument, "Circular dependency detected");
            }
            else {
                push(constructorSegment);
            }
            return this;
        }

        @Override
        public Path pushMethodArgumentResolve(ComponentDefinition declaringType, MethodInjectionPoint methodInjectionPoint, Argument argument) {
            MethodSegment methodSegment = new MethodSegment(declaringType, methodInjectionPoint, argument);
            if(contains(methodSegment)) {
                throw new CircularDependencyException(DefaultComponentResolutionContext.this, methodInjectionPoint, argument, "Circular dependency detected");
            }
            else {
                push(methodSegment);
            }

            return this;
        }

        @Override
        public Path pushFieldResolve(ComponentDefinition declaringType, FieldInjectionPoint fieldInjectionPoint) {
            FieldSegment fieldSegment = new FieldSegment(declaringType, fieldInjectionPoint);
            if(contains(fieldSegment)) {
                throw new CircularDependencyException(DefaultComponentResolutionContext.this, fieldInjectionPoint, "Circular dependency detected");
            }
            else {
                push(fieldSegment);
            }
            return this;
        }
    }

    /**
     * A segment that represents a constructor
     */
    class ConstructorSegment extends AbstractSegment {
        ConstructorSegment(ComponentDefinition declaringClass, Argument argument) {
            super(declaringClass, declaringClass.getType().getName(), argument);
        }

        @Override
        public String toString() {
            StringBuilder baseString = new StringBuilder("new ");
            ComponentDefinition declaringType = getDeclaringType();
            baseString.append(declaringType.getType().getSimpleName());
            outputArguments(declaringType, baseString);
            return baseString.toString();
        }
    }

    /**
     * A segment that represents a method
     */
    class MethodSegment extends AbstractSegment {

        private final MethodInjectionPoint methodInjectionPoint;

        MethodSegment(ComponentDefinition declaringType, MethodInjectionPoint methodInjectionPoint, Argument argument) {
            super(declaringType, methodInjectionPoint.getName(), argument);
            this.methodInjectionPoint = methodInjectionPoint;
        }


        @Override
        public String toString() {
            StringBuilder baseString = new StringBuilder(getDeclaringType().getType().getSimpleName()).append('.');
            baseString.append(getName());
            outputArguments(baseString, methodInjectionPoint.getArguments());
            return baseString.toString();
        }
    }
    /**
     * A segment that represents a field
     */
    class FieldSegment extends AbstractSegment {
        FieldSegment(ComponentDefinition declaringClass, FieldInjectionPoint fieldInjectionPoint) {
            super(declaringClass,
                    fieldInjectionPoint.getName(),
                    new DefaultArgument(fieldInjectionPoint.getType(), fieldInjectionPoint.getName(), fieldInjectionPoint.getQualifier()));
        }
        @Override
        public String toString() {
            return getDeclaringType().getType().getSimpleName() + "." + getName();
        }
    }

    abstract class AbstractSegment implements Segment {
        private final ComponentDefinition declaringComponent;
        private final String name;
        private final Argument argument;

        AbstractSegment(ComponentDefinition declaringClass, String name, Argument argument) {
            this.declaringComponent = declaringClass;
            this.name = name;
            this.argument = argument;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public ComponentDefinition getDeclaringType() {
            return  declaringComponent;
        }

        @Override
        public Argument getArgument() {
            return argument;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            AbstractSegment that = (AbstractSegment) o;

            return declaringComponent.equals(that.declaringComponent) && name.equals(that.name) && argument.equals(that.argument);
        }

        @Override
        public int hashCode() {
            int result = declaringComponent.hashCode();
            result = 31 * result + name.hashCode();
            result = 31 * result + argument.hashCode();
            return result;
        }

        void outputArguments(ComponentDefinition declaringType, StringBuilder baseString) {
            Argument[] arguments = declaringType.getConstructor().getArguments();
            outputArguments(baseString, arguments);
        }

        void outputArguments(StringBuilder baseString, Argument[] arguments) {
            baseString.append('(');
            for (int i = 0; i < arguments.length; i++) {
                Argument argument = arguments[i];
                boolean isInjectedArgument = argument.equals(getArgument());
                if(isInjectedArgument) {
                   baseString.append('[');
                }
                baseString.append(argument.toString());
                if(isInjectedArgument) {
                    baseString.append(']');
                }
                if(i != arguments.length-1) {
                    baseString.append(',');
                }
            }
            baseString.append(')');
        }
    }

}
