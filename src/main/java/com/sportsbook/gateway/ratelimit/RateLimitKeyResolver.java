package com.sportsbook.gateway.ratelimit;

import io.netty.util.NetUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@EnableConfigurationProperties(RateLimitProperties.class)
public final class RateLimitKeyResolver {

  private static final String USER_PREFIX = "gateway:ratelimit:user:";
  private static final String IP_PREFIX = "gateway:ratelimit:ip:";

  private final RateLimitProperties properties;
  private final List<IpAddressMatcher> trustedProxies;

  public RateLimitKeyResolver(RateLimitProperties properties) {
    this.properties = properties;
    this.trustedProxies =
        properties.trustedProxyCidrs().stream().map(IpAddressMatcher::new).toList();
  }

  public ResolvedKey resolve(HttpServletRequest request) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.isAuthenticated()
        && !(authentication instanceof AnonymousAuthenticationToken)
        && StringUtils.hasText(authentication.getName())) {
      return new ResolvedKey(USER_PREFIX + authentication.getName(), properties.user());
    }
    return new ResolvedKey(IP_PREFIX + clientAddress(request), properties.ip());
  }

  private String clientAddress(HttpServletRequest request) {
    String peer = request.getRemoteAddr();
    if (trustedProxies.stream().noneMatch(proxy -> proxy.matches(peer))) {
      return peer;
    }
    String forwarded = request.getHeader("X-Forwarded-For");
    if (!StringUtils.hasText(forwarded)) {
      return peer;
    }
    List<String> hops = new ArrayList<>();
    for (String value : forwarded.split(",", -1)) {
      String hop = normalize(value.trim());
      if (hop == null) {
        return peer;
      }
      hops.add(hop);
    }
    for (int index = hops.size() - 1; index >= 0; index--) {
      String hop = hops.get(index);
      if (trustedProxies.stream().noneMatch(proxy -> proxy.matches(hop))) {
        return hop;
      }
    }
    return peer;
  }

  private static String normalize(String address) {
    byte[] bytes = NetUtil.createByteArrayFromIpAddressString(address);
    if (bytes == null) {
      return null;
    }
    try {
      return InetAddress.getByAddress(bytes).getHostAddress();
    } catch (UnknownHostException impossible) {
      throw new IllegalStateException("Validated IP address could not be parsed", impossible);
    }
  }

  public record ResolvedKey(String value, RateLimitProperties.Limit limit) {}
}
