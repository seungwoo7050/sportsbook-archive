package com.sportsbook.admin.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import org.springframework.util.StringUtils;

final class CidrBlock {

  private static final int IPV4_OCTETS = 4;
  private static final int MAX_IPV4_OCTET = 255;
  private static final int IPV6_BYTES = 16;

  private final byte[] network;
  private final int prefixLength;

  private CidrBlock(byte[] network, int prefixLength) {
    this.network = network.clone();
    this.prefixLength = prefixLength;
  }

  static CidrBlock parse(String value) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalArgumentException("CIDR must not be blank");
    }
    String[] parts = value.trim().split("/", -1);
    if (parts.length != 2) {
      throw new IllegalArgumentException("CIDR must contain one prefix length");
    }
    InetAddress address =
        parseAddress(parts[0]).orElseThrow(() -> new IllegalArgumentException("Invalid CIDR IP"));
    int prefix;
    try {
      prefix = Integer.parseInt(parts[1]);
    } catch (NumberFormatException invalidPrefix) {
      throw new IllegalArgumentException("Invalid CIDR prefix", invalidPrefix);
    }
    int maximumPrefix = address.getAddress().length * Byte.SIZE;
    if (prefix < 0 || prefix > maximumPrefix) {
      throw new IllegalArgumentException("CIDR prefix is out of range");
    }
    return new CidrBlock(address.getAddress(), prefix);
  }

  static Optional<InetAddress> parseAddress(String value) {
    if (!StringUtils.hasText(value)) {
      return Optional.empty();
    }
    String candidate = value.trim();
    return candidate.indexOf(':') >= 0 ? parseIpv6(candidate) : parseIpv4(candidate);
  }

  private static Optional<InetAddress> parseIpv4(String candidate) {
    String[] octets = candidate.split("\\.", -1);
    if (octets.length != IPV4_OCTETS) {
      return Optional.empty();
    }
    byte[] address = new byte[IPV4_OCTETS];
    for (int index = 0; index < octets.length; index++) {
      String octet = octets[index];
      if (!octet.matches("0|[1-9][0-9]{0,2}")) {
        return Optional.empty();
      }
      int parsed = Integer.parseInt(octet);
      if (parsed > MAX_IPV4_OCTET) {
        return Optional.empty();
      }
      address[index] = (byte) parsed;
    }
    try {
      return Optional.of(InetAddress.getByAddress(address));
    } catch (UnknownHostException impossibleLength) {
      throw new IllegalStateException(
          "IPv4 addresses always contain four octets", impossibleLength);
    }
  }

  private static Optional<InetAddress> parseIpv6(String candidate) {
    if (!candidate.matches("[0-9A-Fa-f:]+")) {
      return Optional.empty();
    }
    try {
      InetAddress parsed = InetAddress.getByName(candidate);
      return parsed.getAddress().length == IPV6_BYTES ? Optional.of(parsed) : Optional.empty();
    } catch (UnknownHostException invalidLiteral) {
      return Optional.empty();
    }
  }

  boolean contains(InetAddress address) {
    byte[] candidate = address.getAddress();
    if (candidate.length != network.length) {
      return false;
    }
    int completeBytes = prefixLength / Byte.SIZE;
    int remainingBits = prefixLength % Byte.SIZE;
    for (int index = 0; index < completeBytes; index++) {
      if (candidate[index] != network[index]) {
        return false;
      }
    }
    if (remainingBits == 0) {
      return true;
    }
    int mask = -1 << (Byte.SIZE - remainingBits);
    return (candidate[completeBytes] & mask) == (network[completeBytes] & mask);
  }
}
