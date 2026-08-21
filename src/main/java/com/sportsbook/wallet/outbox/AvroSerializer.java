package com.sportsbook.wallet.outbox;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;

public final class AvroSerializer {

  private AvroSerializer() {}

  public static byte[] serialize(SpecificRecord record) {
    Objects.requireNonNull(record, "record");
    try {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      var encoder = EncoderFactory.get().binaryEncoder(output, null);
      new SpecificDatumWriter<SpecificRecord>(record.getSchema()).write(record, encoder);
      encoder.flush();
      return output.toByteArray();
    } catch (IOException failure) {
      throw new IllegalStateException(
          "Could not encode Avro schema " + record.getSchema().getFullName(), failure);
    }
  }

  public static <T extends SpecificRecord> T deserialize(byte[] payload, Class<T> type) {
    Objects.requireNonNull(payload, "payload");
    Objects.requireNonNull(type, "type");
    try {
      return new SpecificDatumReader<>(type)
          .read(null, DecoderFactory.get().binaryDecoder(payload, null));
    } catch (IOException failure) {
      throw new IllegalStateException("Could not decode Avro type " + type.getName(), failure);
    }
  }
}
