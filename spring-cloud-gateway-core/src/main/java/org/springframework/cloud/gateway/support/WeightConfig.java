/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.support;

import org.springframework.core.style.ToStringCreator;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;

@Validated
public class WeightConfig {
	public static final String CONFIG_PREFIX = "weight";

	@NotEmpty
    private String group;
	private String routeId;
    @Min(0)
    private int weight;

    private WeightConfig() { }

    public WeightConfig(String group, String routeId, int weight) {
        this.routeId = routeId;
        this.group = group;
        this.weight = weight;
    }

    public WeightConfig(String routeId) {
        this.routeId = routeId;
    }

    public String getGroup() {
        return group;
    }

    public WeightConfig setGroup(String group) {
        this.group = group;
        return this;
    }

	public String getRouteId() {
		return routeId;
	}

	public WeightConfig setRouteId(String routeId) {
		this.routeId = routeId;
		return this;
	}

	public int getWeight() {
		return weight;
	}

	public WeightConfig setWeight(int weight) {
		this.weight = weight;
        return this;
    }

    @Override
    public String toString() {
        return new ToStringCreator(this)
                .append("group", group)
                .append("routeId", routeId)
                .append("weight", weight)
                .toString();
    }
}
