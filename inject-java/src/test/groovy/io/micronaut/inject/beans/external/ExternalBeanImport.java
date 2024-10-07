
package io.micronaut.inject.beans.external;

import io.micronaut.context.annotation.Import;
import io.micronaut.inject.test.external.ExternalBean;

// This will create a bean definition without the bean class on the class pass
// It should be skipped and not break the bean context
@Import(classes = ExternalBean.class)
public class ExternalBeanImport {
}
