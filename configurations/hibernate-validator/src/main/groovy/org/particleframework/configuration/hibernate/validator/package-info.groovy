@Configuration
@Requires(classes = [Validator, HibernateValidator, ELContext ])
package org.particleframework.configuration.hibernate.validator

import org.hibernate.validator.HibernateValidator
import org.particleframework.context.annotation.Configuration
import org.particleframework.context.annotation.Requires

import javax.el.ELContext
import javax.validation.Validator
