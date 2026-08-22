package com.sportsbook.settlement.event;

import java.util.Objects;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificRecordBase;

/** Decodes one exact raw Avro record and rejects every trailing byte. */
public final class StrictAvroDecoder {

  public <T extends SpecificRecordBase> T decode(byte[] payload, Class<T> type) {
    Objects.requireNonNull(type, "type");
    if (payload == null) {
      throw new DecodeException("Null " + type.getSimpleName() + " Avro payload");
    }
    try {
      T template = type.getDeclaredConstructor().newInstance();
      SpecificDatumReader<T> reader = new SpecificDatumReader<>(template.getSchema());
      BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(payload, null);
      T decoded = reader.read(null, decoder);
      if (!decoder.isEnd()) {
        throw new DecodeException("Trailing bytes after " + type.getSimpleName());
      }
      return decoded;
    } catch (DecodeException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new DecodeException("Invalid " + type.getSimpleName() + " Avro payload", exception);
    }
  }

  public static final class DecodeException extends RuntimeException {

    public DecodeException(String message) {
      super(message);
    }

    public DecodeException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
