package io.micronaut.docs.ioc.validation;

// tag::class[]
import io.micronaut.core.annotation.Introspected;
import javax.validation.constraints.*;

@Introspected
public class Person {
    private String name;
    @Min(18)
    private int age;

    @NotBlank
    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setAge(int age) {
        this.age = age;
    }
}
// end::class[]
