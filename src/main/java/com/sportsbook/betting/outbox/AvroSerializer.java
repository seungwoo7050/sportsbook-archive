package com.sportsbook.betting.outbox;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;

public final class AvroSerializer {

  public static byte[] serialize(SpecificRecord record) {
    Objects.requireNonNull(record, "record");
    try {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(output, null);
      new SpecificDatumWriter<SpecificRecord>(record.getSchema()).write(record, encoder);
      encoder.flush();
      return output.toByteArray();
    } catch (IOException exception) {
      throw new IllegalArgumentException("Could not encode Avro record", exception);
    }
  }

  public static <T extends SpecificRecord> T deserialize(byte[] payload, Class<T> type) {
    Objects.requireNonNull(payload, "payload");
    try {
      BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(payload, null);
      T value = new SpecificDatumReader<T>(type).read(null, decoder);
      if (!decoder.isEnd()) {
        throw new IllegalArgumentException("Trailing bytes after Avro record");
      }
      return value;
    } catch (IOException exception) {
      throw new IllegalArgumentException("Could not decode Avro record", exception);
    }
  }

  private AvroSerializer() {}
}
