package com.sportsbook.settlement.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.event.Money;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.junit.jupiter.api.Test;

class StrictAvroDecoderTest {

  private final StrictAvroDecoder decoder = new StrictAvroDecoder();

  @Test
  void decodesExactlyOneRawSpecificRecord() {
    Money source = Money.newBuilder().setAmount(125L).setCurrency("KRW").build();

    Money decoded = decoder.decode(encode(source), Money.class);

    assertThat(decoded.getAmount()).isEqualTo(125L);
    assertThat(decoded.getCurrency().toString()).isEqualTo("KRW");
  }

  @Test
  void rejectsMalformedAndTrailingBytes() {
    Money source = Money.newBuilder().setAmount(125L).setCurrency("KRW").build();
    byte[] encoded = encode(source);
    byte[] trailing = Arrays.copyOf(encoded, encoded.length + 1);

    assertThatThrownBy(() -> decoder.decode(new byte[0], Money.class))
        .isInstanceOf(StrictAvroDecoder.DecodeException.class);
    assertThatThrownBy(() -> decoder.decode(null, Money.class))
        .isInstanceOf(StrictAvroDecoder.DecodeException.class)
        .hasMessageContaining("Null Money");
    assertThatThrownBy(() -> decoder.decode(trailing, Money.class))
        .isInstanceOf(StrictAvroDecoder.DecodeException.class)
        .hasMessageContaining("Trailing bytes");
  }

  private static byte[] encode(Money record) {
    try {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(output, null);
      new SpecificDatumWriter<Money>(record.getSchema()).write(record, encoder);
      encoder.flush();
      return output.toByteArray();
    } catch (Exception exception) {
      throw new AssertionError(exception);
    }
  }
}
