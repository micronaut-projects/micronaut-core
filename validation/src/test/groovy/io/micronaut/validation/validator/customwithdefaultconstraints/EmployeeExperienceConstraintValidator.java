package io.micronaut.validation.validator.customwithdefaultconstraints;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
public class EmployeeExperienceConstraintValidator implements ConstraintValidator<EmployeeExperienceConstraint, Employee> {

    @Override
    public boolean isValid(Employee value, AnnotationValue<EmployeeExperienceConstraint> annotationMetadata, ConstraintValidatorContext context) {
        if (value.getExperience() <= 20) {
            context.messageTemplate("Experience Ineligible");
            return false;
        }
        return true;
    }
}
