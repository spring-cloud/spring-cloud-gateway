package org.springframework.cloud.gateway.filter;

import java.net.URI;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ALREADY_ROUTED_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

/**
 * @author Arjun Curat
 */

@RunWith(MockitoJUnitRunner.class)
public class ForwardRoutingFilterTests {

	private ServerWebExchange exchange;

	@Mock
	private GatewayFilterChain chain;

	@Mock
	private ObjectProvider<DispatcherHandler> objectProvider;

	@Mock
	private DispatcherHandler dispatcherHandler;

	@InjectMocks
	private ForwardRoutingFilter forwardRoutingFilter;

	@Before
	public void setup() {
		exchange = MockServerWebExchange.from(MockServerHttpRequest.get("localendpoint").build());
		when(objectProvider.getIfAvailable()).thenReturn(this.dispatcherHandler);
	}

	@Test
	public void shouldNotFilterWhenGatewayRequestUrlSchemeIsNotForward() {
		URI uri = UriComponentsBuilder.fromUriString("http://endpoint").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, uri);
		forwardRoutingFilter.filter(exchange, chain);

		verifyZeroInteractions(dispatcherHandler);
		verify(chain).filter(exchange);
		verifyNoMoreInteractions(chain);
	}

	@Test
	public void shouldFilterWhenGatewayRequestUrlSchemeIsForward() {
		URI uri = UriComponentsBuilder.fromUriString("forward://endpoint").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, uri);

		assertThat(exchange.getAttributes().get(GATEWAY_ALREADY_ROUTED_ATTR)).isNull();

		forwardRoutingFilter.filter(exchange, chain);

		verifyNoMoreInteractions(chain);
		verify(dispatcherHandler).handle(exchange);

		assertThat(exchange.getAttributes().get(GATEWAY_ALREADY_ROUTED_ATTR)).isEqualTo(true);
	}

	@Test
	public void shouldFilterAndKeepHostPathAsSpecified() {

		URI uri = UriComponentsBuilder.fromUriString("forward://host/outage").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, uri);

		ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);

		forwardRoutingFilter.filter(exchange, chain);

		verify(dispatcherHandler).handle(captor.capture());

		ServerWebExchange webExchange = captor.getValue();

		URI forwardedUrl = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);

		assertThat(forwardedUrl).hasScheme("forward").hasHost("host").hasPath("/outage");

	}


	@Test
	public void shouldNotFilterWhenGatewayRequestUrlSchemeIsForwardButAlreadyRouted() {
		URI uri = UriComponentsBuilder.fromUriString("forward://host").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, uri);
		exchange.getAttributes().put(GATEWAY_ALREADY_ROUTED_ATTR, true);

		forwardRoutingFilter.filter(exchange, chain);

		verifyZeroInteractions(dispatcherHandler);
		verify(chain).filter(exchange);
		verifyNoMoreInteractions(chain);
	}

	@Test
	public void orderIsLowestPrecedence() {
		assertThat(forwardRoutingFilter.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
	}

}
