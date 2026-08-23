package com.sportsbook.orchestration.fixture;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;

public final class FixtureEncoder {
  private FixtureEncoder() {}

  public static EncodedFixture encode(FixtureType type, Path jsonPath) throws IOException {
    GenericRecord record = decodeSingleRecord(type, jsonPath);
    return new EncodedFixture(type.key(record), encodeBinary(type, record));
  }

  private static GenericRecord decodeSingleRecord(FixtureType type, Path jsonPath)
      throws IOException {
    try (InputStream input = Files.newInputStream(jsonPath)) {
      Decoder decoder = DecoderFactory.get().jsonDecoder(type.schema(), input);
      GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(type.schema());
      GenericRecord record = reader.read(null, decoder);
      try {
        reader.read(null, decoder);
        throw new IllegalArgumentException("fixture must contain exactly one Avro record");
      } catch (EOFException expected) {
        return record;
      }
    }
  }

  private static byte[] encodeBinary(FixtureType type, GenericRecord record) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BinaryEncoder encoder = EncoderFactory.get().directBinaryEncoder(output, null);
    new GenericDatumWriter<GenericRecord>(type.schema()).write(record, encoder);
    encoder.flush();
    return output.toByteArray();
  }

  public record EncodedFixture(String key, byte[] payload) {
    public EncodedFixture {
      payload = payload.clone();
    }

    @Override
    public byte[] payload() {
      return payload.clone();
    }
  }
}
