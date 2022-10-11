package io.micronaut.inject.qualifiers.multiple;

import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MultipleCompositeQualifierSpec {

    @Test
    void testQualifiers() {
        try (ApplicationContext context = ApplicationContext.run()) {
            final XMyBean bean = context.getBean(XMyBean.class);
            assertTrue(bean.creditCartProcessor1 instanceof XCreditCardProcessor);
            assertTrue(bean.creditCartProcessor2 instanceof XCreditCardProcessor);
            assertTrue(bean.creditCartProcessor3 instanceof XCreditCardProcessor);
            assertTrue(bean.bankTransferProcessor1 instanceof XBankTransferProcessor);
            assertTrue(bean.fromCtorCreditCartProcessor1 instanceof XCreditCardProcessor);
            assertTrue(bean.fromCtorCreditCartProcessor2 instanceof XCreditCardProcessor);
            assertTrue(bean.fromCtorCreditCartProcessor3 instanceof XCreditCardProcessor);
            assertTrue(bean.fromCtorBankTransferProcessor1 instanceof XBankTransferProcessor);
        }
    }
}

@Singleton
class XMyBean {

    @Inject
    @XPayBy(XPaymentMethod.CREDIT_CARD)
    MoneyProcessor creditCartProcessor1;

    @Inject
    @RequiresSignature
    MoneyProcessor creditCartProcessor2;

    @Inject
    @RequiresSignature
    @Credit
    MoneyProcessor creditCartProcessor3;

    @Inject
    @XPayBy(XPaymentMethod.TRANSFER)
    MoneyProcessor bankTransferProcessor1;

    final MoneyProcessor fromCtorCreditCartProcessor1;
    final MoneyProcessor fromCtorCreditCartProcessor2;
    final MoneyProcessor fromCtorCreditCartProcessor3;
    final MoneyProcessor fromCtorBankTransferProcessor1;

    XMyBean(
            @XPayBy(XPaymentMethod.CREDIT_CARD)
                    MoneyProcessor fromCtorCreditCartProcessor1,
            @RequiresSignature
                    MoneyProcessor fromCtorCreditCartProcessor2,
            @RequiresSignature
            @Credit
                    MoneyProcessor fromCtorCreditCartProcessor3,
            @XPayBy(XPaymentMethod.TRANSFER)
                    MoneyProcessor fromCtorBankTransferProcessor1) {
        this.fromCtorCreditCartProcessor1 = fromCtorCreditCartProcessor1;
        this.fromCtorCreditCartProcessor2 = fromCtorCreditCartProcessor2;
        this.fromCtorCreditCartProcessor3 = fromCtorCreditCartProcessor3;
        this.fromCtorBankTransferProcessor1 = fromCtorBankTransferProcessor1;
    }
}

interface MoneyProcessor {
}

@XPayBy(XPaymentMethod.CREDIT_CARD)
@RequiresSignature
@Fast
@Credit
@Singleton
class XCreditCardProcessor implements MoneyProcessor {
}

@XPayBy(XPaymentMethod.TRANSFER)
@Debit
@Singleton
class XBankTransferProcessor implements MoneyProcessor {
}

@Qualifier
@Retention(RUNTIME)
@interface RequiresSignature {
}

@Qualifier
@Retention(RUNTIME)
@interface Credit {
}

@Qualifier
@Retention(RUNTIME)
@interface Fast {
}

@Qualifier
@Retention(RUNTIME)
@interface Debit {
}

@Qualifier
@Retention(RUNTIME)
@interface XPayBy {
    XPaymentMethod value();
}

enum XPaymentMethod {
    TRANSFER,
    CREDIT_CARD
}
