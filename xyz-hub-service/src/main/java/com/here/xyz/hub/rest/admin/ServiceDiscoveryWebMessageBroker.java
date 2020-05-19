/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package com.here.xyz.hub.rest.admin;

import java.util.concurrent.ConcurrentHashMap;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.servicediscovery.AWSServiceDiscoveryAsync;
import com.amazonaws.services.servicediscovery.AWSServiceDiscoveryAsyncClientBuilder;
import com.amazonaws.services.servicediscovery.model.ListInstancesRequest;

/**
 * The {@link ServiceDiscoveryWebMessageBroker} extends the
 * {@link WebMessageBroker} abstract.
 * 
 * To use the {@link ServiceDiscoveryWebMessageBroker} you can use the java
 * property "AdminMessageBroker={@link ServiceDiscoveryWebMessageBroker}" or set
 * the environment variable
 * "ADMIN_MESSAGE_BROKER={@link ServiceDiscoveryWebMessageBroker}".
 * 
 * The {@link ServiceDiscoveryWebMessageBroker} must be configured. You can use
 * the java properties
 * "com.here.xyz.hub.rest.admin.ServiceDiscoveryWebMessageBroker.SERVICE_ID"
 * and
 * "com.here.xyz.hub.rest.admin.ServiceDiscoveryWebMessageBroker.PERIODIC_UPDATE_DELAY"
 * or set the environment variables
 * "SERVICE_DISCOVERY_WEB_MESSAGE_BROKER_SERVICE_ID" and
 * "SERVICE_DISCOVERY_WEB_MESSAGE_BROKER_PERIODIC_UPDATE_DELAY".
 * 
 */

public class ServiceDiscoveryWebMessageBroker extends WebMessageBroker {

	private static volatile ServiceDiscoveryWebMessageBroker instance;
	private static volatile AWSServiceDiscoveryAsync SD_CLIENT;
	private static volatile String SD_SERVICE_ID;
	private static volatile Integer PERIODIC_UPDATE_DELAY;
	private static volatile Boolean isInitialized;

	static {
		SD_SERVICE_ID = getConfig("SERVICE_DISCOVERY_WEB_MESSAGE_BROKER_SERVICE_ID",
				ServiceDiscoveryWebMessageBroker.class.getName() + ".SERVICE_ID", "xyz-hub");
		PERIODIC_UPDATE_DELAY = Integer.parseInt(getConfig("SERVICE_DISCOVERY_WEB_MESSAGE_BROKER_PERIODIC_UPDATE_DELAY",
				ServiceDiscoveryWebMessageBroker.class.getName() + ".PERIODIC_UPDATE_DELAY", "30000"));
		try {
			SD_CLIENT = AWSServiceDiscoveryAsyncClientBuilder.standard()
					.withCredentials(new DefaultAWSCredentialsProviderChain()).build();
			isInitialized = true;
			logger.info("The ServiceDiscoveryWebMessageBroker was initialized.");
		} catch (Exception e) {
			logger.warn("Initializing the ServiceDiscoveryWebMessageBroker failed with error: {} ", e.getMessage());
			SD_CLIENT = null;
			PERIODIC_UPDATE_DELAY = 0;
			isInitialized = false;
		}
		instance = new ServiceDiscoveryWebMessageBroker();
	}

	@Override
	protected Boolean isInitialized() {
		return isInitialized;
	}

	@Override
	protected Boolean getPeriodicUpdate() {
		return (SD_CLIENT != null && isInitialized);
	}

	@Override
	protected Integer getPeriodicUpdateDelay() {
		return PERIODIC_UPDATE_DELAY;
	}

	@Override
	protected ConcurrentHashMap<String, String> getTargetEndpoints() throws Exception {
		ConcurrentHashMap<String, String> targetEndpoints = new ConcurrentHashMap<String, String>();
		ListInstancesRequest request = new ListInstancesRequest();
		request.setServiceId(SD_SERVICE_ID);
		// TODO: minor: respect possible pagination
		SD_CLIENT.listInstances(request).getInstances()
				.forEach(targetInstance -> targetEndpoints.put(targetInstance.getAttributes().get("AWS_INSTANCE_IPV4"),
						targetInstance.getAttributes().get("AWS_INSTANCE_PORT")));
		return targetEndpoints;
	}

	static ServiceDiscoveryWebMessageBroker getInstance() {
		return instance;
	}

}