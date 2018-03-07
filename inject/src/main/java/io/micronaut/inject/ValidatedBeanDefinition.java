package io.micronaut.inject;

import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.exceptions.BeanInstantiationException;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.Optional;
import java.util.Set;

/**
 * A bean definition that is validated with javax.validation
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ValidatedBeanDefinition<T> extends BeanDefinition<T> {

    /**
     * Validates the bean with the validator factory if present
     *
     * @param resolutionContext The resolution context
     * @param instance The instance
     * @return The instance
     */
    default T validate(BeanResolutionContext resolutionContext, T instance) {
        Optional<ValidatorFactory> validatorFactoryBean = resolutionContext.getContext().findBean(ValidatorFactory.class);
        if(validatorFactoryBean.isPresent()) {
            ValidatorFactory validatorFactory = validatorFactoryBean.get();
            Validator validator = validatorFactory.getValidator();
            Set<ConstraintViolation<T>> errors = validator.validate(instance);
            if(!errors.isEmpty()) {
                StringBuilder builder = new StringBuilder();
                builder.append( "Validation failed for bean definition [" );
                builder.append( instance.getClass().getName() );
                builder.append( "]\nList of constraint violations:[\n" );
                for (ConstraintViolation<?> violation : errors) {
                    builder.append( "\t" ).append( violation.getPropertyPath() ).append(" - ").append(violation.getMessage()).append("\n");
                }
                builder.append( "]" );
                throw new BeanInstantiationException(resolutionContext, builder.toString());
            }
        }
        return instance;
    }
}
