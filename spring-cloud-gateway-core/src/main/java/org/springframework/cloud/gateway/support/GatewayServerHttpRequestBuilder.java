package org.springframework.cloud.gateway.support;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.AbstractServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.SslInfo;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import reactor.core.publisher.Flux;

/**
 * Package-private default implementation of {@link ServerHttpRequest.Builder}.
 *
 * @author Rossen Stoyanchev
 * @author Sebastien Deleuze
 * @since 5.0
 */
public class GatewayServerHttpRequestBuilder implements ServerHttpRequest.Builder {

	private boolean encoded;
	private URI uri;

    private HttpHeaders httpHeaders;

    private String httpMethodValue;

    private final MultiValueMap<String, HttpCookie> cookies;

    @Nullable
    private String uriPath;

    @Nullable
    private String contextPath;

    private Flux<DataBuffer> body;

    private final ServerHttpRequest originalRequest;


	public GatewayServerHttpRequestBuilder(ServerHttpRequest original) {
		this(original, true);
	}

	public GatewayServerHttpRequestBuilder(ServerHttpRequest original, boolean encoded) {
        Assert.notNull(original, "ServerHttpRequest is required");

        this.uri = original.getURI();
        this.httpMethodValue = original.getMethodValue();
        this.body = original.getBody();

        this.httpHeaders = new HttpHeaders();
        copyMultiValueMap(original.getHeaders(), this.httpHeaders);

        this.cookies = new LinkedMultiValueMap<>(original.getCookies().size());
        copyMultiValueMap(original.getCookies(), this.cookies);

        this.originalRequest = original;
        this.encoded = encoded;
    }

    private static <K, V> void copyMultiValueMap(MultiValueMap<K,V> source,
                                                 MultiValueMap<K,V> destination) {

        for (Map.Entry<K, List<V>> entry : source.entrySet()) {
            K key = entry.getKey();
            List<V> values = new LinkedList<>(entry.getValue());
            destination.put(key, values);
        }
    }


    @Override
    public ServerHttpRequest.Builder method(HttpMethod httpMethod) {
        this.httpMethodValue = httpMethod.name();
        return this;
    }

    @Override
    public ServerHttpRequest.Builder uri(URI uri) {
        this.uri = uri;
        return this;
    }

    @Override
    public ServerHttpRequest.Builder path(String path) {
        this.uriPath = path;
        return this;
    }

    @Override
    public ServerHttpRequest.Builder contextPath(String contextPath) {
        this.contextPath = contextPath;
        return this;
    }

    @Override
    public ServerHttpRequest.Builder header(String key, String value) {
        this.httpHeaders.add(key, value);
        return this;
    }

    @Override
    public ServerHttpRequest.Builder headers(Consumer<HttpHeaders> headersConsumer) {
        Assert.notNull(headersConsumer, "'headersConsumer' must not be null");
        headersConsumer.accept(this.httpHeaders);
        return this;
    }

    @Override
    public ServerHttpRequest build() {
        URI uriToUse = getUriToUse();
        return new GatewayServerHttpRequest(uriToUse, this.contextPath, this.httpHeaders,
                this.httpMethodValue, this.cookies, this.body, this.originalRequest);

    }

    private URI getUriToUse() {
        if (this.uriPath == null) {
            return this.uri;
        }
        try {
            return UriComponentsBuilder.fromUri(this.uri)
                    .replacePath(uriPath)
                    .build(encoded).toUri();
        }
        catch (RuntimeException ex) {
            throw new IllegalStateException("Invalid URI path: \"" + this.uriPath + "\"");
        }
    }

    private static class GatewayServerHttpRequest extends AbstractServerHttpRequest {

        private final String methodValue;

        private final MultiValueMap<String, HttpCookie> cookies;

        @Nullable
        private final InetSocketAddress remoteAddress;

        @Nullable
        private final SslInfo sslInfo;

        private final Flux<DataBuffer> body;

        private final ServerHttpRequest originalRequest;


        public GatewayServerHttpRequest(URI uri, @Nullable String contextPath,
										HttpHeaders headers, String methodValue, MultiValueMap<String, HttpCookie> cookies,
										Flux<DataBuffer> body, ServerHttpRequest originalRequest) {

            super(uri, contextPath, headers);
            this.methodValue = methodValue;
            this.cookies = cookies;
            this.remoteAddress = originalRequest.getRemoteAddress();
            this.sslInfo = originalRequest.getSslInfo();
            this.body = body;
            this.originalRequest = originalRequest;
        }


        @Override
        public String getMethodValue() {
            return this.methodValue;
        }

        @Override
        protected MultiValueMap<String, HttpCookie> initCookies() {
            return this.cookies;
        }

        @Nullable
        @Override
        public InetSocketAddress getRemoteAddress() {
            return this.remoteAddress;
        }

        @Nullable
        @Override
        protected SslInfo initSslInfo() {
            return this.sslInfo;
        }

        @Override
        public Flux<DataBuffer> getBody() {
            return this.body;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T getNativeRequest() {
            return (T) this.originalRequest;
        }
    }

}
