package org.particleframework.context;

import org.particleframework.inject.*;

import java.util.*;

/**
 * Default implementation of the {@link ComponentResolutionContext} interface
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultComponentResolutionContext implements ComponentResolutionContext{

    private final Context context;
    private final ComponentDefinition rootDefinition;
    private final Deque<Object> objectsInCreation;
    private final Path path;

    public DefaultComponentResolutionContext(Context context, ComponentDefinition rootDefinition) {
        this.context = context;
        this.rootDefinition = rootDefinition;
        this.objectsInCreation = new LinkedList<>();
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

    @Override
    public Deque<Object> getObjectsInCreation() {
        return objectsInCreation;
    }

    class DefaultPath extends LinkedList<Segment> implements Path {

        public DefaultPath() {
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
        public Path pushContructorResolve(ComponentDefinition declaringType, Argument argument) {
            push(new ConstructorSegment(declaringType, argument));
            return this;
        }

        @Override
        public Path pushMethodArgumentResolve(ComponentDefinition declaringType, MethodInjectionPoint methodInjectionPoint, Argument argument) {
            push(new MethodSegment(declaringType, methodInjectionPoint, argument));
            return this;
        }

        @Override
        public Path pushFieldResolve(ComponentDefinition declaringType, FieldInjectionPoint fieldInjectionPoint) {
            push(new FieldSegment(declaringType, fieldInjectionPoint ));
            return this;
        }
    }

    /**
     * A segment that represents a constructor
     */
    class ConstructorSegment extends AbstractSegment {
        public ConstructorSegment(ComponentDefinition declaringClass, Argument argument) {
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

        public MethodSegment(ComponentDefinition declaringType, MethodInjectionPoint methodInjectionPoint, Argument argument) {
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
        public FieldSegment(ComponentDefinition declaringClass, FieldInjectionPoint fieldInjectionPoint) {
            super(declaringClass, fieldInjectionPoint.getName(), DefaultArgument.from(Collections.singletonMap(fieldInjectionPoint.getName(), fieldInjectionPoint.getType()))[0]);
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

        public AbstractSegment(ComponentDefinition declaringClass, String name, Argument argument) {
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

            if (!declaringComponent.equals(that.declaringComponent)) return false;
            if (!name.equals(that.name)) return false;
            return argument.equals(that.argument);
        }

        @Override
        public int hashCode() {
            int result = declaringComponent.hashCode();
            result = 31 * result + name.hashCode();
            result = 31 * result + argument.hashCode();
            return result;
        }

        protected void outputArguments(ComponentDefinition declaringType, StringBuilder baseString) {
            Argument[] arguments = declaringType.getConstructor().getArguments();
            outputArguments(baseString, arguments);
        }

        protected void outputArguments(StringBuilder baseString, Argument[] arguments) {
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
