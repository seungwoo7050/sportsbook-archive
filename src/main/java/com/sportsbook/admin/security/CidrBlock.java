package com.sportsbook.admin.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import org.springframework.util.StringUtils;

final class CidrBlock {

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
    if (!candidate.matches("[0-9.]+") && !candidate.matches("[0-9A-Fa-f:]+")) {
      return Optional.empty();
    }
    try {
      return Optional.of(InetAddress.getByName(candidate));
    } catch (UnknownHostException invalidAddress) {
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
