package org.springframework.cloud.gateway.server.mvc.filter;

import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

public interface GlobalFilter extends HandlerFilterFunction<ServerResponse, ServerResponse> {

    @Override
    ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next) throws Exception;
}
