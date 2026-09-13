package io.micronaut.inject.visitor;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records what a visitor sees of the classes in the {@code transformorder} package.
 */
public class ConstructorArityVisitor implements TypeElementVisitor<Object, Object> {

    public static final Map<String, String> SEEN = new LinkedHashMap<>();
    public static final List<VisitorContext> STARTS = new ArrayList<>();

    @Override
    public void start(VisitorContext visitorContext) {
        STARTS.add(visitorContext);
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (element.getPackageName().equals("transformorder")) {
            SEEN.put(element.getName(), "record=" + element.isRecord()
                + " primary=" + element.getPrimaryConstructor().map(MethodElement::getParameters).map(p -> p.length).orElse(-1)
                + " methods=" + element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared()).stream().map(MethodElement::getName).sorted().toList());
        }
    }
}
