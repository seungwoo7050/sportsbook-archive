package com.sportsbook.settlement.outbox;

import java.io.ByteArrayOutputStream;
import java.util.Objects;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecordBase;

/** Encodes deterministic raw Avro bytes without a schema-registry prefix. */
public final class StrictAvroEncoder {

  public <T extends SpecificRecordBase> byte[] encode(T record) {
    Objects.requireNonNull(record, "record");
    try {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(output, null);
      new SpecificDatumWriter<T>(record.getSchema()).write(record, encoder);
      encoder.flush();
      return output.toByteArray();
    } catch (Exception exception) {
      throw new IllegalStateException(
          "Failed to encode " + record.getClass().getSimpleName(), exception);
    }
  }
}
