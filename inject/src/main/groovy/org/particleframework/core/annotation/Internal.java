package org.particleframework.core.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Annotates a class or method regarded as internal and not for public consumption
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Retention(RetentionPolicy.SOURCE)
public @interface Internal {

}