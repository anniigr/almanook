package app.almanook.sourcing;

import java.net.URI;
import java.nio.charset.Charset;

/** Bytes retrieved from an untrusted URL after all safety checks. */
public record FetchedResource(URI finalUri, String contentType, Charset charset, byte[] body) {}
