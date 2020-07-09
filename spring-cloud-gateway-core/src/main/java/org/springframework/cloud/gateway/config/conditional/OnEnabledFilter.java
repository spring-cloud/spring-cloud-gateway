package org.springframework.cloud.gateway.config.conditional;

import com.google.common.base.CaseFormat;

import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SpringCloudCircuitBreakerFilterFactory;
import org.springframework.cloud.gateway.support.NameUtils;

public class OnEnabledFilter extends OnEnabledComponent<GatewayFilterFactory<?>> {

	@Override
	protected String normalizeComponentName(Class<? extends GatewayFilterFactory<?>> filterClass) {
		String filterName = "";

		if (SpringCloudCircuitBreakerFilterFactory.class.isAssignableFrom(filterClass)) {
			filterName = SpringCloudCircuitBreakerFilterFactory.NAME;
		} else {
			filterName = NameUtils.normalizeFilterFactoryName(filterClass);
		}
		return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, filterName);
	}

}
