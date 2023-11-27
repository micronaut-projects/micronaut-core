package io.micronaut.connection.interceptor;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.core.annotation.Internal;

import javax.sql.DataSource;
import java.sql.Connection;

@EachBean(DataSource.class)
@ContextualConnectionAdvice
@Internal
public interface ContextualConnection extends Connection {
}
