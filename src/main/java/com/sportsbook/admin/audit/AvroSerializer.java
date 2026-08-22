package com.sportsbook.admin.audit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;

public final class AvroSerializer {

  private AvroSerializer() {}

  public static byte[] toBytes(SpecificRecord record) {
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      var writer = new SpecificDatumWriter<SpecificRecord>(record.getSchema());
      var encoder = EncoderFactory.get().binaryEncoder(bytes, null);
      writer.write(record, encoder);
      encoder.flush();
      return bytes.toByteArray();
    } catch (IOException failure) {
      throw new UncheckedIOException("Avro serialization failed", failure);
    }
  }
}
