package com.sportsbook.gateway.events;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;

public final class AvroTestSupport {

  public static byte[] encode(SpecificRecord record) {
    try {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(output, null);
      DatumWriter<SpecificRecord> writer = new SpecificDatumWriter<>(record.getSchema());
      writer.write(record, encoder);
      encoder.flush();
      return output.toByteArray();
    } catch (IOException failure) {
      throw new AssertionError("Unable to encode Avro test record", failure);
    }
  }

  private AvroTestSupport() {}
}
