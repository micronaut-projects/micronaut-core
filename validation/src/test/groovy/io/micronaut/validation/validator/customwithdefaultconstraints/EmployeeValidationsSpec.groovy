package io.micronaut.validation.validator.customwithdefaultconstraints

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.Nullable
import io.micronaut.validation.validator.Validator
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

import javax.validation.ConstraintViolation
import javax.validation.ConstraintViolationException
import javax.validation.Valid
import javax.validation.constraints.NotBlank
import java.util.stream.Collectors

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertIterableEquals
import static org.junit.jupiter.api.Assertions.assertThrows

@Issue("https://github.com/micronaut-projects/micronaut-core/issues/6519")
class EmployeeValidationsSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run()


    void "test validations where both custom message constraint default validations fail"() {
        given:
        Validator validator = applicationContext.getBean(Validator.class)
        Employee emp = new Employee();
        emp.setName("");
        emp.setExperience(10);

        Set<String> messages = new HashSet<>();
        messages.add("must not be blank");
        messages.add("Experience Ineligible");

        when:
        final Set<ConstraintViolation<Employee>> constraintViolations = validator.validate(emp)

        then:
        Set<String> violationMessages = constraintViolations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
        assertEquals(2, constraintViolations.size());
        assertIterableEquals(messages, violationMessages);
    }

    void "test whether exceptions thrown when both custom message constraint default validations fail"() {
        given:
        EmployeeService employeeService = applicationContext.getBean(EmployeeService.class)
        Employee emp = new Employee()
        emp.setName("")
        emp.setExperience(10)

        when:
        final ConstraintViolationException exception =
                assertThrows(ConstraintViolationException.class, () ->
                        employeeService.startHoliday(emp)
                )
        then:
        String notBlankValidated = exception.getConstraintViolations().stream().filter(constraintViolation -> Objects.equals(constraintViolation.getPropertyPath().toString(), "startHoliday.emp.name")).map(ConstraintViolation::getMessage).findFirst().get();
        String experienceValidated = exception.getConstraintViolations().stream().filter(constraintViolation -> Objects.equals(constraintViolation.getPropertyPath().toString(), "startHoliday.emp")).map(ConstraintViolation::getMessage).findFirst().get();
        assertEquals("must not be blank", notBlankValidated);
        assertEquals("Experience Ineligible", experienceValidated);

    }

    void "test whether exceptions thrown when both custom message cascade constraint default validations fail"() {
        given:
        EmployeeService employeeService = applicationContext.getBean(EmployeeService.class)
        Employee junior = new Employee()
        junior.setName("")
        junior.setExperience(10)

        Employee emp = new Employee()
        emp.setName("")
        emp.setExperience(10)
        emp.setJunior(junior);

        when:
        final ConstraintViolationException exception =
                assertThrows(ConstraintViolationException.class, () ->
                        employeeService.startHoliday(emp)
                )
        then:
        String notBlankValidated = exception.getConstraintViolations().stream().filter(constraintViolation -> Objects.equals(constraintViolation.getPropertyPath().toString(), "startHoliday.emp.name")).map(ConstraintViolation::getMessage).findFirst().get();
        String experienceValidated = exception.getConstraintViolations().stream().filter(constraintViolation -> Objects.equals(constraintViolation.getPropertyPath().toString(), "startHoliday.emp")).map(ConstraintViolation::getMessage).findFirst().get();
        assertEquals("must not be blank", notBlankValidated);
        assertEquals("Experience Ineligible", experienceValidated);

    }

}


@Singleton
class EmployeeService {
    @Executable
    String startHoliday(@Valid Employee emp) {
        return "Person " + emp.getName() + " is eligible for sabbatical holiday as the person is of " + emp.getExperience() + " years experienced";
    }
}

@Introspected
@EmployeeExperienceConstraint
class Employee {

    private String name;

    private int experience;


    private Employee junior;

    @EmployeeExperienceConstraint
    @Nullable
    Employee getJunior() {
        return junior
    }

    void setJunior(Employee junior) {
        this.junior = junior
    }

    int getExperience() {
        return experience;
    }

    void setExperience(int experience) {
        this.experience = experience;
    }

    @NotBlank
    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }
}
