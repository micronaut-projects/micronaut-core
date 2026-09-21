package pythontest.introduction.reflective;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds a {@link Proxy} of the declaring interface and reads the annotations of the invoked
 * method reflectively, like a framework that is unaware of Micronaut's annotation metadata.
 */
public class ReflectiveServiceInterceptor implements MethodInterceptor<Object, Object> {

    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    @Override
    public @Nullable Object intercept(MethodInvocationContext<Object, Object> context) {
        Class<Object> declaringType = context.getDeclaringType();
        Object service = services.computeIfAbsent(declaringType, ReflectiveServiceInterceptor::createService);
        return context.getExecutableMethod().invoke(service, context.getParameterValues());
    }

    private static Object createService(Class<?> type) {
        if (!type.isInterface()) {
            throw new IllegalStateException("The type implemented by the service must be an interface, found '" + type.getName() + "'");
        }
        ReflectiveService service = type.getAnnotation(ReflectiveService.class);
        if (service == null) {
            throw new IllegalStateException("The interface is not annotated with @ReflectiveService: " + type.getName());
        }
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return method.getName().equals("toString") ? "ReflectiveService(" + type.getName() + ")" : method.invoke(proxy, args);
            }
            Prompt prompt = method.getAnnotation(Prompt.class);
            if (prompt == null) {
                throw new IllegalStateException("No @Prompt on " + method);
            }
            StringBuilder response = new StringBuilder(prompt.value());
            if (!service.value().isEmpty()) {
                response.append(" [").append(service.value()).append(']');
            }
            for (Class<?> tool : prompt.tools()) {
                response.append(" tool=").append(tool.getSimpleName());
            }
            if (prompt.priority() != Prompt.Priority.NORMAL) {
                response.append(" priority=").append(prompt.priority());
            }
            Annotation[][] parameterAnnotations = method.getParameterAnnotations();
            for (int i = 0; i < parameterAnnotations.length; i++) {
                String name = "p" + i;
                for (Annotation annotation : parameterAnnotations[i]) {
                    if (annotation instanceof Var variable) {
                        name = variable.value();
                    }
                }
                response.append(' ').append(name).append('=').append(args[i]);
            }
            return response.toString();
        });
    }
}
