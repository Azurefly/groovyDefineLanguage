package com.pl.gdl.server.http;

public interface HttpTransport {
    HttpResponse execute(HttpRequest request) throws Exception;
}
