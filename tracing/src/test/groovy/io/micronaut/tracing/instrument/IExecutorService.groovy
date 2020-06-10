package io.micronaut.tracing.instrument

import java.util.concurrent.ExecutorService

interface IExecutorService extends ExecutorService {

    void newMethod();
}