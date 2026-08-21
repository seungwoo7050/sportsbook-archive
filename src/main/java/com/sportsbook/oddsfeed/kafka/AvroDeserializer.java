package com.sportsbook.oddsfeed.kafka;

import java.io.IOException;
import org.apache.avro.Schema;
import org.apache.avro.data.TimeConversions;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

public class AvroDeserializer<T extends SpecificRecord> implements Deserializer<T> {

  static {
    SpecificData.get().addLogicalTypeConversion(new TimeConversions.TimestampMillisConversion());
  }

  private final Schema schema;

  public AvroDeserializer(Schema schema) {
    this.schema = schema;
  }

  @Override
  public T deserialize(String topic, byte[] data) {
    if (data == null) {
      return null;
    }
    try {
      SpecificDatumReader<T> reader = new SpecificDatumReader<>(schema);
      BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(data, null);
      return reader.read(null, decoder);
    } catch (IOException error) {
      throw new SerializationException("Failed to deserialize Avro record on " + topic, error);
    }
  }
}
