package com.sportsbook.protocol.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import org.apache.avro.Schema;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;

final class AvroRecordTestSupport {

  private AvroRecordTestSupport() {}

  static void assertFields(Schema schema, String... fields) {
    assertThat(schema.getFields().stream().map(Schema.Field::name))
        .containsExactlyElementsOf(Arrays.asList(fields));
  }

  static <T extends SpecificRecord> T roundTrip(T expected, Class<T> type) throws IOException {
    byte[] payload;
    try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      var encoder = EncoderFactory.get().binaryEncoder(output, null);
      new SpecificDatumWriter<T>(expected.getSchema()).write(expected, encoder);
      encoder.flush();
      payload = output.toByteArray();
    }
    return new SpecificDatumReader<>(type)
        .read(null, DecoderFactory.get().binaryDecoder(payload, null));
  }
}
