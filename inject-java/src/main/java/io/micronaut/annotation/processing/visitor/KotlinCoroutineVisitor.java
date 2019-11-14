package io.micronaut.annotation.processing.visitor;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

/**
 * A {@link TypeElementVisitor} that will add the {@code io.micronaut.kotlin.coroutines.intercept.KotlinCoroutineAroundInterceptor}
 */
@Internal
public class KotlinCoroutineVisitor implements TypeElementVisitor<Object, Executable> {

    private boolean supportCoroutines = false;
    private boolean supportedAnnotation = false;

    @Override
    public void start(VisitorContext visitorContext) {
        this.supportCoroutines = visitorContext
                .getClassElement("kotlin.coroutines.intrinsics.IntrinsicsKt").isPresent();
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        this.supportedAnnotation = element.hasStereotype(getSupportedAnnotations()) && !element.isFinal();
    }

    /**
     * @return An array of annotation types that support coroutine around advice
     */
    // Adjust this method if other framework types need coroutine advice applied.
    protected String[] getSupportedAnnotations() {
        return new String[] {
               "io.micronaut.http.annotation.Controller"
        };
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (supportCoroutines && supportedAnnotation) {
            final ParameterElement[] parameters = element.getParameters();
            if (ArrayUtils.isNotEmpty(parameters)) {
                final ParameterElement lastParameter = parameters[parameters.length - 1];
                final boolean isSuspendMethod =
                        lastParameter.getType().isAssignable("kotlin.coroutines.Continuation");
                if (isSuspendMethod) {
                    // don't require it to be bound, interceptor will bind it
                    lastParameter.annotate("javax.annotation.Nullable");
                    // add the AOP interceptor
                    element.annotate("io.micronaut.runtime.kotlin.coroutines.intercept.KotlinCoroutineAroundAdvice", builder -> {
                        final ClassElement typeArg = lastParameter.getGenericType().getFirstTypeArgument().orElse(null);
                        if (typeArg != null && typeArg.isAssignable("kotlin.Unit")) {
                            builder.member("unit", true);
                        }
                    });
                }
            }
        }
    }
}
