package io.micronaut.sourcegen.example

import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.beans.BeanIntrospection
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertArrayEquals
import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertSame

class HeronTest {
    @Test
    void test() throws Exception {
        BeanIntrospection<Heron> intro = BeanIntrospection.getIntrospection(Heron.class);

        AnnotationValue<Simple> simple = intro.getAnnotation(Simple.class);
        assertNotNull(simple);
        assertEquals("A", simple.stringValue("name").get());
        assertEquals(12, simple.intValue("age").getAsInt());
        assertEquals(Heron.Color.WHITE, simple.enumValue("color", Heron.Color.class).get());
        assertSame(String.class, simple.classValue("type").get());

        AnnotationValue<Nested> nested = intro.getAnnotation(Nested.class);
        assertNotNull(nested);
        assertNotNull(nested.getAnnotation("simple"));

        AnnotationValue<MultiValue> multi = intro.getAnnotation(MultiValue.class);
        assertNotNull(multi);
        List<AnnotationValue<Simple>> simples = multi.getAnnotations("simples", Simple.class);
        assertEquals(2, simples.size());
        assertArrayEquals(new int[]{1, 2, 3}, multi.intValues("values"));
        assertArrayEquals(new String[]{"a", "b"}, multi.stringValues("strings"));

        List<AnnotationValue<Boo>> boos = intro.getAnnotationValuesByType(Boo.class);
        assertEquals(2, boos.size());
        assertEquals("boom", boos.get(0).stringValue("name").get());
        assertEquals("bam", boos.get(1).stringValue("name").get());
    }
}
