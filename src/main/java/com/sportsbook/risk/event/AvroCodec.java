package com.sportsbook.risk.event;

import java.io.ByteArrayOutputStream;
import java.util.Objects;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecordBase;

/** Plain Avro binary codec for the one-schema-per-topic wire contract. */
public final class AvroCodec {
  private AvroCodec() {}

  public static <T extends SpecificRecordBase> byte[] encode(T record) {
    Objects.requireNonNull(record, "record");
    try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(output, null);
      new SpecificDatumWriter<T>(record.getSchema()).write(record, encoder);
      encoder.flush();
      return output.toByteArray();
    } catch (Exception exception) {
      throw new IllegalStateException(
          "failed to encode " + record.getClass().getSimpleName(), exception);
    }
  }

  public static <T extends SpecificRecordBase> T decode(byte[] payload, Class<T> type) {
    Objects.requireNonNull(payload, "payload");
    Objects.requireNonNull(type, "type");
    try {
      BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(payload, null);
      T value = new SpecificDatumReader<>(type).read(null, decoder);
      if (!decoder.isEnd()) {
        throw new IllegalArgumentException("Avro payload contains trailing bytes");
      }
      return value;
    } catch (Exception exception) {
      throw new IllegalStateException("failed to decode " + type.getSimpleName(), exception);
    }
  }
}
