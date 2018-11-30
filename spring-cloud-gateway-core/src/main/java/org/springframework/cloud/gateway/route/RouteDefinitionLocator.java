/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.route;

import reactor.core.publisher.Flux;
/**
 * 路由定义信息的定位器，
 * 負責讀取路由配置( org.springframework.cloud.gateway.route.RouteDefinition
 * 子類實現類
 *  1.CachingRouteDefinitionLocator -RouteDefinitionLocator包裝類， 緩存目標RouteDefinitionLocator 為routeDefinitions提供緩存功能
 *  2.CompositeRouteDefinitionLocator -RouteDefinitionLocator包裝類，組合多種 RouteDefinitionLocator 的實現，為 routeDefinitions提供統一入口
 *  3.PropertiesRouteDefinitionLocator-从配置文件(GatewayProperties 例如，YML / Properties 等 ) 讀取RouteDefinition
 *  4.DiscoveryClientRouteDefinitionLocator-从註冊中心( 例如，Eureka / Consul / Zookeeper / Etcd 等 )讀取RouteDefinition
 *  5.RouteDefinitionRepository-从存儲器( 例如，內存 / Redis / MySQL 等 )讀取RouteDefinition
 */

/**
 * @author Spencer Gibb
 */
public interface RouteDefinitionLocator {

	Flux<RouteDefinition> getRouteDefinitions();
}
