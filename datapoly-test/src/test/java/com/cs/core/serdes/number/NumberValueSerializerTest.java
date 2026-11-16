// Use of this source code is governed by a BSD-style license
package com.cs.core.serdes.number;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/**
 * Number serialization dispatch: every Number flavor the Jackson tree can hand over must
 * reach a matching writeNumber overload, BigDecimal is floored to the configured scale,
 * and exotic Number types fall back to their string form.
 */
public class NumberValueSerializerTest {

    private String serialized(NumberValueSerializer serializer, Number value) throws IOException {
        StringWriter writer = new StringWriter();
        try (JsonGenerator generator = new JsonFactory().createGenerator(writer)) {
            generator.writeStartObject();
            generator.writeFieldName("v");
            serializer.serialize(value, generator, null);
            generator.writeEndObject();
            generator.flush();
        }
        return writer.toString();
    }

    @Test
    public void nullValueWritesNothing() throws IOException {
        // Jackson emits the field-name colon together with the value, so a skipped value
        // leaves just the bare name inside the object
        assertEquals("{\"v\"}", serialized(new NumberValueSerializer(null), null));
    }

    @Test
    public void bigDecimalIsFlooredToTheConfiguredScale() throws IOException {
        NumberValueSerializer scale2 = new NumberValueSerializer("2");
        assertEquals("{\"v\":3.14}", serialized(scale2, new BigDecimal("3.149")));
        // ROUND_FLOOR rounds towards negative infinity: negatives move away from zero
        assertEquals("{\"v\":-3.15}", serialized(scale2, new BigDecimal("-3.149")));
    }

    @Test
    public void invalidScaleStringFallsBackToDefaultSix() throws IOException {
        // "abc" is not a number and neither is null: NumberUtils.toInt default applies
        assertEquals("{\"v\":3.141592}", serialized(new NumberValueSerializer("abc"),
                new BigDecimal("3.141592653589")));
        assertEquals("{\"v\":3.141592}", serialized(new NumberValueSerializer(null),
                new BigDecimal("3.141592653589")));
    }

    @Test
    public void eachNumberFlavorUsesItsOwnWriter() throws IOException {
        NumberValueSerializer serializer = new NumberValueSerializer("2");
        assertEquals("{\"v\":123}", serialized(serializer, new BigInteger("123")));
        assertEquals("{\"v\":5}", serialized(serializer, 5L));
        assertEquals("{\"v\":1.5}", serialized(serializer, 1.5D));
        assertEquals("{\"v\":2.5}", serialized(serializer, 2.5F));
        assertEquals("{\"v\":7}", serialized(serializer, 7));
        assertEquals("{\"v\":3}", serialized(serializer, (byte) 3));
        assertEquals("{\"v\":9}", serialized(serializer, (short) 9));
    }

    @Test
    public void exoticNumberTypesFallBackToStringForm() throws IOException {
        // AtomicInteger/AtomicLong are Number but none of the boxed flavors: toString path
        assertEquals("{\"v\":4}", serialized(new NumberValueSerializer(null), new AtomicInteger(4)));
        assertEquals("{\"v\":-9}", serialized(new NumberValueSerializer(null), new AtomicLong(-9L)));
    }
}
