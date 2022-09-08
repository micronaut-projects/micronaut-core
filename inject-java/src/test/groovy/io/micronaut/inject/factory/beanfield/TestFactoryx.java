package io.micronaut.inject.factory.beanfield;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.ReflectiveAccess;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;

@Factory
public class TestFactoryx {

    @Bean
    @Named
    public static String VAL1 = "val1";

    @Bean
    @Named
    static Integer VAL2 = 123;

    @Bean
    @Named
    protected static Double VAL3 = 789d;

    @Bean
    @Named
    @ReflectiveAccess
    private static Float VAL4 = 744f;

    @Bean
    @Named
    public Boolean VAL5 = Boolean.TRUE;

    @Bean
    @Named
    BigDecimal VAL6 = BigDecimal.TEN;

    @Bean
    @Named
    protected BigInteger VAL7 = BigInteger.ONE;

    @Bean
    @Named
    @ReflectiveAccess
    private LocalDate VAL8 = LocalDate.MAX;

    @Bean
    @Named
    @ReflectiveAccess
    private int VAL9 = 999;

    @Bean
    @Named
    @ReflectiveAccess
    private int[] VAL10 = {1, 2, 3};

    @Bean
    @Named
    @ReflectiveAccess
    private static int VAL11 = 333;

    @Bean
    @Named
    @ReflectiveAccess
    private static int[] VAL12 = {4, 5, 6};

}

@Singleton
class MyBean {

    public MyBean(@Named int val11) {
    }
}
