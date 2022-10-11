package io.micronaut.inject.qualifiers.multiple;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MultipleQualifierSpec {

    @Test
    void testQualifiers() {
        try (ApplicationContext context = ApplicationContext.run()) {
            final MyBean bean = context.getBean(MyBean.class);
            assertTrue(bean.asyncCreditCartProcessor instanceof AsyncCreditCardProcessor);
            assertSame(bean.asyncCreditCartProcessor, bean.fromCtorAsyncCreditCartProcessor);
            assertTrue(bean.syncCreditCartProcessor instanceof CreditCardProcessor);
            assertSame(bean.syncCreditCartProcessor, bean.fromCtorSyncCreditCartProcessor);
            assertTrue(bean.asyncBankTransferProcessor instanceof AsyncBankTransferProcessor);
            assertSame(bean.asyncBankTransferProcessor, bean.fromCtorAsyncBankTransferProcessor);
            assertTrue(bean.syncBankTransferProcessor instanceof BankTransferProcessor);
            assertSame(bean.syncBankTransferProcessor, bean.fromCtorSyncBankTransferProcessor);
        }
    }
}

@Singleton
class MyBean {
    @PayBy(PaymentMethod.CREDIT_CARD)
    @Asynchronous
    Processor asyncCreditCartProcessor;

    @Inject
    @PayBy(PaymentMethod.CREDIT_CARD)
    @Synchronous
    Processor syncCreditCartProcessor;

    @Inject
    @PayBy(PaymentMethod.TRANSFER)
    @Asynchronous
    Processor asyncBankTransferProcessor;

    @Inject
    @PayBy(PaymentMethod.TRANSFER)
    @Synchronous
    Processor syncBankTransferProcessor;


    final Processor fromCtorAsyncCreditCartProcessor;
    final Processor fromCtorSyncCreditCartProcessor;
    final Processor fromCtorAsyncBankTransferProcessor;
    final Processor fromCtorSyncBankTransferProcessor;

    MyBean(
            @PayBy(PaymentMethod.CREDIT_CARD)
            @Asynchronous
            Processor fromCtorAsyncCreditCartProcessor,
            @PayBy(PaymentMethod.CREDIT_CARD)
            @Synchronous
            Processor fromCtorSyncCreditCartProcessor,
            @PayBy(PaymentMethod.TRANSFER)
            @Asynchronous
            Processor fromCtorAsyncBankTransferProcessor,
            @PayBy(PaymentMethod.TRANSFER)
            @Synchronous
            Processor fromCtorSyncBankTransferProcessor) {
        this.fromCtorAsyncCreditCartProcessor = fromCtorAsyncCreditCartProcessor;
        this.fromCtorSyncCreditCartProcessor = fromCtorSyncCreditCartProcessor;
        this.fromCtorAsyncBankTransferProcessor = fromCtorAsyncBankTransferProcessor;
        this.fromCtorSyncBankTransferProcessor = fromCtorSyncBankTransferProcessor;
    }
}

interface Processor {
}

@PayBy(PaymentMethod.CREDIT_CARD)
@Synchronous
@Singleton
class CreditCardProcessor implements Processor {
}

@PayBy(PaymentMethod.TRANSFER)
@Synchronous
@Singleton
class BankTransferProcessor implements Processor {
}

@PayBy(PaymentMethod.CREDIT_CARD)
@Asynchronous
@Singleton
class AsyncCreditCardProcessor implements Processor {
}

@PayBy(PaymentMethod.TRANSFER)
@Asynchronous
@Singleton
class AsyncBankTransferProcessor implements Processor {
}

@Qualifier
@Retention(RUNTIME)
@interface Synchronous {}

@Qualifier
@Retention(RUNTIME)
@interface Asynchronous {}

@Qualifier
@Retention(RUNTIME)
@interface PayBy {
    PaymentMethod value();
}

enum PaymentMethod {
    TRANSFER,
    CREDIT_CARD
}
