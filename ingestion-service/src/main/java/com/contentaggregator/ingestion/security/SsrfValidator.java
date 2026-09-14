package com.contentaggregator.ingestion.security;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
public class SsrfValidator {

  static {
    System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host");
  }

  private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
  private static final String LOCALHOST = "localhost";
  private static final String LOOPBACK_IP = "127.0.0.1";
  private static final String METADATA_IP = "169.254.169.254";

  private static final int IPV4_ANY_LOCAL_PREFIX = 0;
  private static final int IPV4_PRIVATE_10_PREFIX = 10;
  private static final int IPV4_LOOPBACK_PREFIX = 127;
  private static final int IPV4_CGNAT_PREFIX = 100;
  private static final int IPV4_CGNAT_MIN_SECOND = 64;
  private static final int IPV4_CGNAT_MAX_SECOND = 127;
  private static final int IPV4_LINK_LOCAL_PREFIX = 169;
  private static final int IPV4_LINK_LOCAL_SECOND = 254;
  private static final int IPV4_PRIVATE_172_PREFIX = 172;
  private static final int IPV4_PRIVATE_172_MIN_SECOND = 16;
  private static final int IPV4_PRIVATE_172_MAX_SECOND = 31;
  private static final int IPV4_PRIVATE_192_PREFIX = 192;
  private static final int IPV4_PRIVATE_192_SECOND = 168;
  private static final int IPV4_MULTICAST_RESERVED_MIN = 224;

  private static final int IPV6_ULA_MASK = 0xFE;
  private static final int IPV6_ULA_PREFIX = 0xFC;
  private static final int IPV6_LINK_LOCAL_FIRST = 0xFE;
  private static final int IPV6_LINK_LOCAL_SECOND_MASK = 0xC0;
  private static final int IPV6_LINK_LOCAL_SECOND_PREFIX = 0x80;
  private static final int IPV6_MAPPED_BYTE_VALUE = 0xFF;
  private static final int IPV6_PREFIX_ZERO_COUNT = 10;
  private static final int IPV6_BYTE_10_INDEX = 10;
  private static final int IPV6_BYTE_11_INDEX = 11;
  private static final int IPV6_IPV4_OFFSET = 12;
  private static final int IPV4_ADDR_LENGTH = 4;

  public boolean isSafeUrl(String urlString) {
    try {
      validateUrl(urlString);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public InetAddress validateUrl(String urlString) {
    if (urlString == null || urlString.isBlank()) {
      throw new SsrfBlockedException("URL cannot be empty");
    }

    URI uri = parseUri(urlString);
    validateScheme(uri);
    String host = validateHostString(uri);

    checkExplicitBlockedHost(host);
    return checkDnsResolution(host);
  }

  private URI parseUri(String urlString) {
    try {
      return URI.create(urlString);
    } catch (Exception e) {
      throw new SsrfBlockedException("Invalid URI format: " + urlString, e);
    }
  }

  private void validateScheme(URI uri) {
    String scheme = uri.getScheme();
    if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
      throw new SsrfBlockedException(
          "Scheme '" + scheme + "' not allowed. Only HTTP/HTTPS supported.");
    }
  }

  private String validateHostString(URI uri) {
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw new SsrfBlockedException("Host cannot be empty");
    }
    return host;
  }

  private void checkExplicitBlockedHost(String host) {
    if (LOCALHOST.equalsIgnoreCase(host)
        || LOOPBACK_IP.equalsIgnoreCase(host)
        || METADATA_IP.equalsIgnoreCase(host)) {
      throw new SsrfBlockedException("Direct access to loopback or cloud metadata is blocked");
    }
  }

  private InetAddress checkDnsResolution(String host) {
    try {
      return resolveAndValidateAddresses(host);
    } catch (SsrfBlockedException e) {
      throw e;
    } catch (UnknownHostException e) {
      handleUnknownHost(host, e);
      return null;
    } catch (Exception e) {
      throw new SsrfBlockedException("Failed to resolve host: " + host, e);
    }
  }

  private InetAddress resolveAndValidateAddresses(String host) throws UnknownHostException {
    InetAddress[] addresses = InetAddress.getAllByName(host);
    for (InetAddress addr : addresses) {
      if (isPrivateOrLocalAddress(addr)) {
        throw new SsrfBlockedException(
            "Host resolves to private or non-routable IP address: " + addr.getHostAddress());
      }
    }
    return addresses[0];
  }

  private void handleUnknownHost(String host, UnknownHostException e) {
    if (isInvalidDomainFormat(host)) {
      throw new SsrfBlockedException("Unresolvable or invalid host format: " + host, e);
    }
  }

  private boolean isInvalidDomainFormat(String host) {
    return !host.contains(".") || host.startsWith(".") || host.endsWith(".");
  }

  public boolean isPrivateOrLocalAddress(InetAddress addr) {
    if (addr.isLoopbackAddress()
        || addr.isAnyLocalAddress()
        || addr.isSiteLocalAddress()
        || addr.isLinkLocalAddress()
        || addr.isMulticastAddress()) {
      return true;
    }

    if (addr instanceof Inet4Address) {
      return isPrivateIpv4(addr.getAddress());
    } else if (addr instanceof Inet6Address) {
      return isPrivateIpv6(addr.getAddress());
    }
    return false;
  }

  private boolean isPrivateIpv4(byte[] bytes) {
    int b0 = bytes[0] & 0xFF;
    int b1 = bytes[1] & 0xFF;
    return isStandardPrivateOrReserved(b0, b1) || isExtendedSpecialIpv4(b0, b1);
  }

  private boolean isStandardPrivateOrReserved(int b0, int b1) {
    if (b0 == IPV4_ANY_LOCAL_PREFIX || b0 == IPV4_PRIVATE_10_PREFIX || b0 == IPV4_LOOPBACK_PREFIX) {
      return true;
    }
    if (b0 == IPV4_PRIVATE_172_PREFIX
        && b1 >= IPV4_PRIVATE_172_MIN_SECOND
        && b1 <= IPV4_PRIVATE_172_MAX_SECOND) {
      return true;
    }
    return b0 == IPV4_PRIVATE_192_PREFIX && b1 == IPV4_PRIVATE_192_SECOND;
  }

  private boolean isExtendedSpecialIpv4(int b0, int b1) {
    if (b0 == IPV4_CGNAT_PREFIX && b1 >= IPV4_CGNAT_MIN_SECOND && b1 <= IPV4_CGNAT_MAX_SECOND) {
      return true;
    }
    if (b0 == IPV4_LINK_LOCAL_PREFIX && b1 == IPV4_LINK_LOCAL_SECOND) {
      return true;
    }
    return b0 >= IPV4_MULTICAST_RESERVED_MIN;
  }

  private boolean isPrivateIpv6(byte[] bytes) {
    if ((bytes[0] & IPV6_ULA_MASK) == IPV6_ULA_PREFIX) {
      return true;
    }
    if ((bytes[0] & 0xFF) == IPV6_LINK_LOCAL_FIRST
        && (bytes[1] & IPV6_LINK_LOCAL_SECOND_MASK) == IPV6_LINK_LOCAL_SECOND_PREFIX) {
      return true;
    }
    return isIpv4MappedAndPrivate(bytes);
  }

  private boolean isIpv4MappedAndPrivate(byte[] bytes) {
    for (int i = 0; i < IPV6_PREFIX_ZERO_COUNT; i++) {
      if (bytes[i] != 0) {
        return false;
      }
    }
    if ((bytes[IPV6_BYTE_10_INDEX] & 0xFF) == IPV6_MAPPED_BYTE_VALUE
        && (bytes[IPV6_BYTE_11_INDEX] & 0xFF) == IPV6_MAPPED_BYTE_VALUE) {
      try {
        byte[] ipv4 = new byte[IPV4_ADDR_LENGTH];
        System.arraycopy(bytes, IPV6_IPV4_OFFSET, ipv4, 0, IPV4_ADDR_LENGTH);
        return isPrivateOrLocalAddress(InetAddress.getByAddress(ipv4));
      } catch (UnknownHostException ignored) {
        return true;
      }
    }
    return false;
  }
}
