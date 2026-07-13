package io.github.liquidcatmofu.abs.server.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/** /ui/* — classpath の webui/ リソースを配信する */
public class WebUIHandler implements HttpHandler {
    private static final Map<String, String> MIME = Map.of(
            ".html", "text/html; charset=utf-8",
            ".js",   "application/javascript; charset=utf-8",
            ".css",  "text/css; charset=utf-8",
            ".ico",  "image/x-icon"
    );

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        // 認証はクライアントのループバックハンドラーがこの処理の前に行う。
        String path = exchange.getRequestURI().getPath();

        // /ui → /ui/index.html
        if (path.equals("/ui") || path.equals("/ui/")) {
            path = "/ui/index.html";
        }

        // classpath 上のリソースパスに変換: /ui/foo.js → webui/foo.js
        String resource = "webui" + path.substring("/ui".length());

        InputStream is = WebUIHandler.class.getClassLoader().getResourceAsStream(resource);
        if (is == null) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }

        String mime = MIME.entrySet().stream()
                .filter(e -> resource.endsWith(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("application/octet-stream");

        byte[] bytes = is.readAllBytes();
        is.close();

        exchange.getResponseHeaders().set("Content-Type", mime);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
