import java
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

JavaClass = java.type("java.lang.Class")
AuditedRecordClass = java.type("micronaut.docs.reflection.AuditedRecord")
AuditedAnnotation = JavaClass.forName("docs.reflection.Audited")


@MicronautTest
class AuditedRecordSpec:

    @Test
    def the_generated_class_carries_the_annotations_a_library_reads_reflectively(self):
        audited = AuditedRecordClass.class_.getAnnotation(AuditedAnnotation)
        assert audited is not None
        assert audited.value() == "records"
        field = AuditedRecordClass.class_.getDeclaredField("id")
        assert field.getAnnotation(AuditedAnnotation).value() == "record_id"
