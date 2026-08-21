package com.sportsbook.gateway.events;

import com.sportsbook.gateway.kafka.GatewayEventContractException;
import java.io.IOException;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificRecord;

/** Decodes one complete binary Avro record using its shared protocol generated type. */
public final class StrictAvroDecoder {

  public static <T extends SpecificRecord> T decode(byte[] payload, Class<T> type) {
    if (payload == null) {
      throw new GatewayEventContractException("Kafka event payload must not be null");
    }
    try {
      DatumReader<T> reader = new SpecificDatumReader<>(type);
      BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(payload, null);
      T decoded = reader.read(null, decoder);
      if (!decoder.isEnd()) {
        throw new GatewayEventContractException(
            "Avro record has trailing bytes: " + type.getName());
      }
      return decoded;
    } catch (GatewayEventContractException failure) {
      throw failure;
    } catch (IOException | RuntimeException failure) {
      throw new GatewayEventContractException(
          "Failed to decode Avro record " + type.getName(), failure);
    }
  }

  private StrictAvroDecoder() {}
}
