package io.micronaut.inject.foreach

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import jakarta.inject.Named
import jakarta.inject.Singleton
import spock.lang.PendingFeature
import spock.lang.Specification

class EachBeanRequiresQualifierSpec extends Specification {

    @PendingFeature(reason = "EachBean requires a qualifier. Since Micronaut Framework 4 it throws a multiple possible bean candidates found if any parent lacks the qualifier")
    void "each bean ignores parent beans which lack a name qualifier"() {
        when:
        ApplicationContext ctx = ApplicationContext.run(Collections.singletonMap("spec.name", "EachBeanRequiresQualifierSpec"))

        then:
        noExceptionThrown()
        2 == ctx.getBeansOfType(AttachmentImporter).size()

        and: 'only the bean with excel name qualifiers gets created'
        1 == ctx.getBeansOfType(AttachmentImporterAdapter).size()

        cleanup:
        ctx.close()
    }

    @Requires(property = "spec.name", value = "EachBeanRequiresQualifierSpec")
    @Factory
    static class AttachmentImporterAdapterFactory {
        @EachBean(AttachmentImporter)
        AttachmentImporterAdapter create(AttachmentImporter importer) {
            new AttachmentImporterAdapterImpl(importer)
        }
    }

    @Requires(property = "spec.name", value = "EachBeanRequiresQualifierSpec")
    @Singleton
    static class CsvImporter implements AttachmentImporter {
        @Override
        void load(byte[] arr) {
        }
    }

    @Requires(property = "spec.name", value = "EachBeanRequiresQualifierSpec")
    @Singleton
    @Named("excel")
    static class ExcelImporter implements AttachmentImporter {
        @Override
        void load(byte[] arr) {
        }
    }

    static interface AttachmentImporter {
        void load(byte[] arr)
    }

    static interface AttachmentImporterAdapter {
        void importAttachment(byte[] arr)
    }

    static class AttachmentImporterAdapterImpl implements AttachmentImporterAdapter {
        private final AttachmentImporter delegate
        AttachmentImporterAdapterImpl(AttachmentImporter importer) {
            this.delegate = importer
        }
        @Override
        void importAttachment(byte[] arr) {
            delegate.load(arr)
        }
    }

}
