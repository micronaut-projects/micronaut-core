package io.micronaut.runtime.context.scope.refresh.refresher

import java.util.concurrent.atomic.AtomicInteger

class Pool {
    static final AtomicInteger CREATED = new AtomicInteger()
    final int number = CREATED.incrementAndGet()
    final String url
    int applied

    Pool(String url) {
        this.url = url
    }
}
