package io.micronaut.docs.lifecycle

import java.util.concurrent.atomic.AtomicBoolean

class Connection {

    var stopped = AtomicBoolean(false)
    fun stop() {// <2>
        stopped.compareAndSet(false, true)
    }
}
