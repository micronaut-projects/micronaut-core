package io.micronaut.docs.requires;

import io.micronaut.context.annotation.Requires;

import javax.sql.DataSource;
import java.lang.annotation.*;

// tag::annotation[]
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.PACKAGE, ElementType.TYPE])
@Requires(beans = DataSource.class)
@Requires(property = "datasource.url")
@interface RequiresJdbc {
}
// end::annotation[]
