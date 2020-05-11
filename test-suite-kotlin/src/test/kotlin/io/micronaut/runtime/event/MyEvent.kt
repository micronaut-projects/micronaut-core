package io.micronaut.runtime.event

import io.micronaut.context.event.ApplicationEvent

class MyEvent(source: Any) : ApplicationEvent(source)