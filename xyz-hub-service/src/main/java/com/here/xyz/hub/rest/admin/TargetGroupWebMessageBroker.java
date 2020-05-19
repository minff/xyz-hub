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
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingAsync;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingAsyncClientBuilder;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetHealthRequest;

/**
 * The {@link TargetGroupWebMessageBroker} extends the {@link WebMessageBroker}
 * abstract.
 * 
 * To use the {@link TargetGroupWebMessageBroker} you can use the java property
 * "AdminMessageBroker={@link TargetGroupWebMessageBroker}" or set the
 * environment variable
 * "ADMIN_MESSAGE_BROKER={@link TargetGroupWebMessageBroker}".
 * 
 * The {@link TargetGroupWebMessageBroker} must be configured. You can use the
 * java properties
 * "com.here.xyz.hub.rest.admin.TargetGroupWebMessageBroker.ELB_TARGETGROUP_ARN"
 * and
 * "com.here.xyz.hub.rest.admin.TargetGroupWebMessageBroker.PERIODIC_UPDATE_DELAY"
 * or set the environment variables
 * "TARGET_GROUP_WEB_MESSAGE_BROKER_ELB_TARGETGROUP_ARN" and
 * "TARGET_GROUP_WEB_MESSAGE_BROKER_PERIODIC_UPDATE_DELAY".
 * 
 */

public class TargetGroupWebMessageBroker extends WebMessageBroker {

	private static volatile TargetGroupWebMessageBroker instance;
	private static volatile AmazonElasticLoadBalancingAsync ELB_CLIENT;
	private static volatile String ELB_TARGETGROUP_ARN;
	private static volatile Integer PERIODIC_UPDATE_DELAY;
	private static volatile Boolean isInitialized;

	static {
		ELB_TARGETGROUP_ARN = getConfig("TARGET_GROUP_WEB_MESSAGE_BROKER_ELB_TARGETGROUP_ARN",
				TargetGroupWebMessageBroker.class.getName() + ".ELB_TARGETGROUP_ARN", null);
		PERIODIC_UPDATE_DELAY = Integer.parseInt(getConfig("TARGET_GROUP_WEB_MESSAGE_BROKER_PERIODIC_UPDATE_DELAY",
				TargetGroupWebMessageBroker.class.getName() + ".PERIODIC_UPDATE_DELAY", "30000"));
		try {
			ELB_CLIENT = AmazonElasticLoadBalancingAsyncClientBuilder.standard()
					.withCredentials(new DefaultAWSCredentialsProviderChain()).build();
			isInitialized = true;
			logger.info("The TargetGroupWebMessageBroker was initialized.");
		} catch (Exception e) {
			logger.warn("Initializing the TargetGroupWebMessageBroker failed with error: {} ", e.getMessage());
			ELB_CLIENT = null;
			PERIODIC_UPDATE_DELAY = 0;
			isInitialized = false;
		}
		instance = new TargetGroupWebMessageBroker();
	}

	@Override
	protected Boolean isInitialized() {
		return isInitialized;
	}

	@Override
	protected Boolean getPeriodicUpdate() {
		return (ELB_CLIENT != null && isInitialized);
	}

	@Override
	protected Integer getPeriodicUpdateDelay() {
		return PERIODIC_UPDATE_DELAY;
	}

	@Override
	protected ConcurrentHashMap<String, String> getTargetEndpoints() throws Exception {
		// TODO: minor: support multiple target group arn once required
		ConcurrentHashMap<String, String> targetEndpoints = new ConcurrentHashMap<String, String>();
		DescribeTargetHealthRequest request = new DescribeTargetHealthRequest();
		request.setTargetGroupArn(ELB_TARGETGROUP_ARN);
		// TODO: minor: respect possible pagination
		ELB_CLIENT.describeTargetHealth(request).getTargetHealthDescriptions().forEach(targetInstance -> targetEndpoints
				.put(targetInstance.getTarget().getId(), Integer.toString(targetInstance.getTarget().getPort())));
		return targetEndpoints;
	}

	static TargetGroupWebMessageBroker getInstance() {
		return instance;
	}

}