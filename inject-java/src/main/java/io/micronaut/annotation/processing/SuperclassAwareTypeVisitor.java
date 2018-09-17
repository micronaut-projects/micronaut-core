package io.micronaut.annotation.processing;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.*;
import javax.lang.model.util.AbstractTypeVisitor8;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Processes the type and its super classes.
 *
 * @param <R>
 * @param <P>
 */
public abstract class SuperclassAwareTypeVisitor<R, P> extends AbstractTypeVisitor8<R, P> {
    private final Set<String> processed = new HashSet<>();

    @Override
    public R visitDeclared(DeclaredType type, P p) {
        Element element = type.asElement();

        while ((element.getKind() == ElementKind.CLASS || element.getKind() == ElementKind.INTERFACE) && !element.toString().equals(Object.class.getName())) {
            TypeElement typeElement = (TypeElement) element;
            List<? extends Element> enclosedElements = typeElement.getEnclosedElements();
            for (Element enclosedElement : enclosedElements) {
                boolean isAcceptable = isAcceptable(enclosedElement);
                if (isAcceptable) {
                    String qualifiedName = enclosedElement.toString();
                    // if the method has already been processed then it is overridden so ignore
                    if (!processed.contains(qualifiedName)) {
                        processed.add(qualifiedName);
                        accept(type, enclosedElement, p);
                    }
                }
            }
            List<? extends TypeMirror> interfaces = typeElement.getInterfaces();
            for (TypeMirror anInterface : interfaces) {
                if (anInterface instanceof DeclaredType) {

                    DeclaredType interfaceType = (DeclaredType) anInterface;
                    visitDeclared(interfaceType, p);
                }
            }
            TypeMirror superMirror = typeElement.getSuperclass();
            if (superMirror instanceof DeclaredType) {
                element = ((DeclaredType) superMirror).asElement();
            } else {
                break;
            }
        }

        return null;
    }

    /**
     * @param element The {@link Element}
     * @return Whether the element is public and final
     */
    protected abstract boolean isAcceptable(Element element);

    /**
     * @param type    The {@link DeclaredType}
     * @param element The {@link Element}
     * @param p       The additional type
     */
    protected abstract void accept(DeclaredType type, Element element, P p);

    @Override
    public R visitIntersection(IntersectionType t, P p) {
        return null;
    }

    @Override
    public R visitPrimitive(PrimitiveType t, P p) {
        return null;
    }

    @Override
    public R visitNull(NullType t, P p) {
        return null;
    }

    @Override
    public R visitArray(ArrayType t, P p) {
        return null;
    }

    @Override
    public R visitError(ErrorType t, P p) {
        return null;
    }

    @Override
    public R visitTypeVariable(TypeVariable t, P p) {
        return null;
    }

    @Override
    public R visitWildcard(WildcardType t, P p) {
        return null;
    }

    @Override
    public R visitExecutable(ExecutableType t, P p) {
        return null;
    }

    @Override
    public R visitNoType(NoType t, P p) {
        return null;
    }

    @Override
    public R visitUnion(UnionType t, P p) {
        return null;
    }
}
