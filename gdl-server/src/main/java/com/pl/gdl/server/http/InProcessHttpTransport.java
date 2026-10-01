package com.pl.gdl.server.http;

public class InProcessHttpTransport implements HttpTransport {
    private final GdlHttpServer server;

    public InProcessHttpTransport(GdlHttpServer server) {
        this.server = server;
    }

    @Override
    public HttpResponse execute(HttpRequest request) throws Exception {
        return server.handleDirect(request);
    }
}
