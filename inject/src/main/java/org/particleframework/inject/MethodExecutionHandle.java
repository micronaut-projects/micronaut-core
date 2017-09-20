package org.particleframework.inject;

/**
 * Represents an execution handle that invokes a method
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface MethodExecutionHandle<R> extends ExecutionHandle<R> {

    /**
     * @return The name of the method
     */
    String getMethodName();
    /**
     * @return Return the return type
     */
    ReturnType<R> getReturnType();
}
