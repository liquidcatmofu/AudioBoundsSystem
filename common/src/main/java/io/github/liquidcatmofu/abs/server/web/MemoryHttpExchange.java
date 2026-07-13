package io.github.liquidcatmofu.abs.server.web;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/** In-memory exchange used to pass Minecraft RPC requests through existing API handlers. */
public final class MemoryHttpExchange extends HttpExchange {
    private final Headers requestHeaders = new Headers();
    private final Headers responseHeaders = new Headers();
    private final Map<String, Object> attributes = new HashMap<>();
    private final URI uri;
    private final String method;
    private InputStream requestBody;
    private OutputStream responseBody = new ByteArrayOutputStream();
    private int responseCode = -1;

    public MemoryHttpExchange(String method, URI uri, byte[] requestBody) {
        this.method = method;
        this.uri = uri;
        this.requestBody = new ByteArrayInputStream(requestBody == null ? new byte[0] : requestBody);
    }

    @Override public Headers getRequestHeaders() { return requestHeaders; }
    @Override public Headers getResponseHeaders() { return responseHeaders; }
    @Override public URI getRequestURI() { return uri; }
    @Override public String getRequestMethod() { return method; }
    @Override public HttpContext getHttpContext() { return null; }
    @Override public void close() {}
    @Override public InputStream getRequestBody() { return requestBody; }
    @Override public OutputStream getResponseBody() { return responseBody; }
    @Override public void sendResponseHeaders(int responseCode, long responseLength) { this.responseCode = responseCode; }
    @Override public InetSocketAddress getRemoteAddress() { return new InetSocketAddress("127.0.0.1", 0); }
    @Override public int getResponseCode() { return responseCode; }
    @Override public InetSocketAddress getLocalAddress() { return new InetSocketAddress("127.0.0.1", 0); }
    @Override public String getProtocol() { return "HTTP/1.1"; }
    @Override public Object getAttribute(String name) { return attributes.get(name); }
    @Override public void setAttribute(String name, Object value) { attributes.put(name, value); }
    @Override public void setStreams(InputStream input, OutputStream output) {
        if (input != null) requestBody = input;
        if (output != null) responseBody = output;
    }
    @Override public HttpPrincipal getPrincipal() { return null; }

    public byte[] responseBytes() {
        if (!(responseBody instanceof ByteArrayOutputStream bytes)) {
            throw new IllegalStateException("Response stream was replaced");
        }
        return bytes.toByteArray();
    }
}
