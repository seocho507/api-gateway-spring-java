package com.seocho507.apigateway.filter;

import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

public class ResponseAdapter extends ServerHttpResponseDecorator {

    private Flux<DataBuffer> body;
    private Mono<String> cachedBody;

    public ResponseAdapter(ServerHttpResponse delegate) {
        super(delegate);
    }

    @Override
    public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
        this.body = Flux.from(body);
        this.cachedBody = this.body
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    return new String(bytes, StandardCharsets.UTF_8);
                })
                .reduce(String::concat);

        return super.writeWith(this.body);
    }

    public Mono<String> getBody() {
        return cachedBody;
    }
}
