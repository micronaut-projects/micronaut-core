package io.micronaut.tracing.instrument

import groovy.transform.InheritConstructors
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Secondary
import io.micronaut.core.util.StringUtils

import javax.inject.Named
import javax.inject.Singleton
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

@Singleton
@Named("custom")
@Requires(property = PROP, value = StringUtils.TRUE)
@Secondary
class IExecutorServiceImpl extends AbstractExecutorService implements IExecutorService{

    public static final String PROP = 'iexecutor.enabled'

    @Override
    void newMethod() {

    }

    @Override
    void shutdown() {

    }

    @Override
    List<Runnable> shutdownNow() {
        return []
    }

    @Override
    boolean isShutdown() {
        return false
    }

    @Override
    boolean isTerminated() {
        return false
    }

    @Override
    boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return false
    }

    @Override
    void execute(Runnable command) {
        command.run()
    }
}
