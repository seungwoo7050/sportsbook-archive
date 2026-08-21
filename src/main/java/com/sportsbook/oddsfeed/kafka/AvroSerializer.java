package com.sportsbook.oddsfeed.kafka;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.avro.data.TimeConversions;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;

public class AvroSerializer<T extends SpecificRecord> implements Serializer<T> {

  static {
    SpecificData.get().addLogicalTypeConversion(new TimeConversions.TimestampMillisConversion());
  }

  @Override
  public byte[] serialize(String topic, T data) {
    if (data == null) {
      return null;
    }
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try {
      SpecificDatumWriter<T> writer = new SpecificDatumWriter<>(data.getSchema());
      BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(output, null);
      writer.write(data, encoder);
      encoder.flush();
      return output.toByteArray();
    } catch (IOException error) {
      throw new SerializationException("Failed to serialize Avro record on " + topic, error);
    }
  }
}
