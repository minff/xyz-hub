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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingClientBuilder;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetHealthRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetHealthResult;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.servicediscovery.AWSServiceDiscovery;
import com.amazonaws.services.servicediscovery.AWSServiceDiscoveryClient;
import com.amazonaws.services.servicediscovery.model.ListInstancesRequest;
import com.amazonaws.services.servicediscovery.model.ListInstancesResult;
import com.amazonaws.util.IOUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.here.xyz.hub.Service;
import com.here.xyz.hub.rest.AdminApi;
import com.here.xyz.hub.rest.ApiParam.Query;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.ext.web.client.WebClient;

/**
 * 
 * The {@link HttpMessageBroker} provides the infrastructural implementation of
 * how to send & receive {@link AdminMessage}s.
 * 
 * The {@link HttpMessageBroker} is sending messages directly to xyz
 * admin/messages endpoints.
 * 
 * The {@link HttpMessageBroker} does not require the admin/messages endpoint to
 * be exposed on a public ip.
 * 
 * To use the {@link HttpMessageBroker} you can use the java property
 * "AdminMessageBroker={@link HttpMessageBroker}" or set the environment
 * variable "ADMIN_MESSAGE_BROKER={@link HttpMessageBroker}".
 * 
 * The {@link HttpMessageBroker} must be configured to query the message targets
 * dynamically from either aws s3, aws ecs service discovery, aws elb target
 * group or set to a static configuration.
 * 
 */

public class HttpMessageBroker implements MessageBroker {

	private static final Logger logger = LogManager.getLogger();

	private static final HttpMessageBroker instance;
	private static final ThreadLocal<ObjectMapper> mapper = ThreadLocal.withInitial(ObjectMapper::new);
	private static final long MAX_MESSAGE_SIZE = 256 * 1024;
	private static final String OWN_NODE_MESSAGING_URL;

	static {
		String ownNodeUrl;
		if (Service.configuration.ADMIN_MESSAGE_JWT == null) {
			ownNodeUrl = null;
		} else {
			try {
				ownNodeUrl = Node.OWN_INSTANCE.getUrl() != null
						? new URL(Node.OWN_INSTANCE.getUrl(),
								AdminApi.ADMIN_MESSAGES_ENDPOINT + "?" + Query.ACCESS_TOKEN + "="
										+ Service.configuration.ADMIN_MESSAGE_JWT).toString()
						: null;
			} catch (MalformedURLException e) {
				logger.error("Error creating the Node URL: {}", e.getMessage());
				ownNodeUrl = null;
			}
		}
		OWN_NODE_MESSAGING_URL = ownNodeUrl;
		logger.debug("OWN_NODE_MESSAGING_URL: {}", OWN_NODE_MESSAGING_URL);
		instance = new HttpMessageBroker();
	}

	private final WebClient HTTP_CLIENT;
	private String TARGETS_DATASOURCE;
	private volatile AmazonS3 S3_CLIENT;
	private String S3_BUCKET_NAME;
	private String S3_OBJECT;
	private volatile AWSServiceDiscovery SD_CLIENT;
	private String SD_SERVICE_NAME;
	private volatile AmazonElasticLoadBalancing ELB_CLIENT;
	private String ELB_TARGETGROUP_ARN;
	private String STATIC_CONFIG;

	private HttpMessageBroker() {
		logger.info("Initializing the HttpMessageBroker");
		HTTP_CLIENT = WebClient.create(Service.vertx);
		try {
			TARGETS_DATASOURCE = getConfig("HTTP_MESSAGE_BROKER_TARGETS_DATASOURCE",
					HttpMessageBroker.class.getName() + ".TARGETS_DATASOURCE", "static");
			switch (TARGETS_DATASOURCE) {
				case "s3":
					createS3Client();
					logger.info("S3_CLIENT initialized for s3://{}/{}", S3_BUCKET_NAME, S3_OBJECT);
					break;
				case "sd":
					createServiceDiscoveryClient();
					logger.info("SD_CLIENT initialized for {}", SD_SERVICE_NAME);
					break;
				case "tg":
					createElasticLoadBalancingClient();
					logger.info("ELB_CLIENT initialized for {}", ELB_TARGETGROUP_ARN);
					break;
				case "static":
					createStaticClient();
					logger.info("STATIC_CONFIG initialized for {}", STATIC_CONFIG);
					break;
				default:
					throw new Exception("Configuration is not supported.");
			}
		} catch (Exception e) {
			logger.error("Initializing the HttpMessageBroker failed for TARGETS_DATASOURCE {} with error: {} ",
					TARGETS_DATASOURCE, e);
		}
	}

	static HttpMessageBroker getInstance() {
		return instance;
	}

	@Override
	public void sendMessage(AdminMessage message) {
		logger.info("Publish AdminMessage.@Class: {} , Source.Ip: {}", message.getClass().getSimpleName(),
				message.source.ip);
		if (!Node.OWN_INSTANCE.equals(message.destination)) {
			String jsonMessage = null;
			try {
				jsonMessage = mapper.get().writeValueAsString(message);
				sendMessage(jsonMessage);
			} catch (JsonProcessingException e) {
				logger.error("Error while serializing AdminMessage of type {} prior to send it.",
						message.getClass().getSimpleName());
			} catch (Exception e) {
				logger.error("Error while sending AdminMessage: {}", e.getMessage());
				logger.debug("AdminMessage was: {}", jsonMessage);
			}
		}
		// Receive it (also) locally (if applicable)
		/*
		 * NOTE: Local messages will always be received directly and only once. This is
		 * also true for a broadcast message with the #broadcastIncludeLocalNode flag
		 * being active.
		 */
		receiveMessage(message);
	}

	private void sendMessage(String message) {
		if (HTTP_CLIENT == null) {
			logger.error("The AdminMessage cannot be processed. The HTTP_CLIENT is not ready. AdminMessage was: {}",
					message);
			return;
		}
		if (message.length() > MAX_MESSAGE_SIZE) {
			throw new RuntimeException(
					"The AdminMessage cannot be processed. The AdminMessage is larger than the MAX_MESSAGE_SIZE.");
		}
		Map<String, String> messageTargetEndpoints = getTargetEndpoints();
		if (messageTargetEndpoints != null && messageTargetEndpoints.size() > 0) {
			@SuppressWarnings("rawtypes")
			List<Future> targetEndpointRequests = new ArrayList<>();
			for (String key : messageTargetEndpoints.keySet()) {
				logger.debug("Preparing notification for target: {}:{}", key, messageTargetEndpoints.get(key));
				targetEndpointRequests.add(notifyEndpoint(key, messageTargetEndpoints.get(key), message));
			}
			CompositeFuture.join(targetEndpointRequests).setHandler(handler -> {
				if (handler.succeeded()) {
					logger.info("Publish AdminMessage to all target endpoints succeeded.");
				} else {
					logger.error(
							"Publish AdminMessage to all target endpoints failed. Some requests did not complete.");
				}
				logger.debug("AdminMessage was: {}", message);
			});
		} else {
			logger.warn("Publish AdminMessage cannot run. The HttpMessageBroker has no target endpoints.");
			logger.debug("AdminMessage was: {}", message);
		}
	}

	@Override
	public void receiveRawMessage(byte[] rawJsonMessage) {
		if (rawJsonMessage == null) {
			logger.error("No bytes given for receiving the AdminMessage.", new NullPointerException());
			return;
		}
		try {
			receiveMessage(mapper.get().readTree(new String(rawJsonMessage)).asText());
		} catch (Exception e) {
			logger.error("Error while de-serializing the received raw AdminMessage {} : {}", new String(rawJsonMessage),
					e);
		}
	}

	private void receiveMessage(String jsonMessage) {
		AdminMessage message;
		try {
			message = mapper.get().readValue(jsonMessage, AdminMessage.class);
			receiveMessage(message);
		} catch (IOException e) {
			logger.error("Error while de-serializing the received AdminMessage {} : {}", jsonMessage, e);
		} catch (Exception e) {
			logger.error("Error while receiving the received AdminMessage {} : {}", jsonMessage, e);
		}
	}

	private void receiveMessage(AdminMessage message) {
		if (message.source == null) {
			throw new NullPointerException("The source node of the AdminMessage must be defined.");
		}
		if (message.destination == null
				&& (!Node.OWN_INSTANCE.equals(message.source) || message.broadcastIncludeLocalNode)
				|| Node.OWN_INSTANCE.equals(message.destination)) {
			try {
				logger.info("Receive AdminMessage.@Class: {} , Source.Ip: {}", message.getClass().getSimpleName(),
						message.source.ip);
				message.handle();
			} catch (RuntimeException e) {
				logger.error("Error while trying to handle the received AdminMessage {} : {}", message, e);
			}
		} else {
			logger.debug("Skipped AdminMessage.@Class: {} , Source.Ip: {}", message.getClass().getSimpleName(),
					message.source.ip);
		}
	}

	private Future<String> notifyEndpoint(String endpointName, String endpointPort, String message) {
		Future<String> future = Future.future();
		HTTP_CLIENT.post(Integer.parseInt(endpointPort), endpointName, AdminApi.ADMIN_MESSAGES_ENDPOINT)
				.putHeader("Authorization", "bearer " + Service.configuration.ADMIN_MESSAGE_JWT)
				.sendJson(message, handler -> {
					if (handler.succeeded()) {
						future.complete(Integer.toString(handler.result().statusCode()));
					} else {
						logger.error(
								"The HttpMessageBroker HTTP_CLIENT failed to post data to endpoint {}:{}. The error is: {}",
								endpointName, endpointPort, handler.cause().getMessage());
						future.fail(handler.cause().getMessage());
					}
				});
		return future;
	}

	private Map<String, String> getTargetEndpoints() {
		switch (TARGETS_DATASOURCE) {
			case "s3":
				return queryTargetEndpointsS3();
			case "sd":
				return queryTargetEndpointsSD();
			case "tg":
				return queryTargetEndpointsELB();
			case "static":
				return queryTargetEndpointsSTATIC();
			default:
				logger.error("The HttpMessageBroker has no targets. The TARGETS_DATASOURCE {} is not supported.",
						TARGETS_DATASOURCE);
				return null;
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, String> getTargetEndpoints(String config) throws Exception {
		// string should contain json
		// e.g. { "instance": "port", "instance": "port", ... }
		return mapper.get().readValue(config, Map.class);
	}

	private Map<String, String> queryTargetEndpointsSTATIC() {
		try {
			return getTargetEndpoints(STATIC_CONFIG);
		} catch (Exception e) {
			logger.error("The HttpMessageBroker STATIC_CONFIG failed to set target endpoints with error {} ",
					e.getMessage());
		}
		return new HashMap<>();
	}

	private Map<String, String> queryTargetEndpointsELB() {
		// TODO: minor: support multiple target group arn
		Map<String, String> targetEndpoints = new HashMap<>();
		if (ELB_CLIENT == null) {
			logger.error("The HttpMessageBroker ELB_CLIENT is not ready. The target endpoints cannot be queried.");
		} else {
			try {
				DescribeTargetHealthRequest request = new DescribeTargetHealthRequest();
				request.setTargetGroupArn(ELB_TARGETGROUP_ARN);
				DescribeTargetHealthResult result = ELB_CLIENT.describeTargetHealth(request);
				// TODO: minor: respect possible pagination
				result.getTargetHealthDescriptions()
						.forEach(targetInstance -> targetEndpoints.put(targetInstance.getTarget().getId(),
								Integer.toString(targetInstance.getTarget().getPort())));
			} catch (Exception e) {
				logger.error("The HttpMessageBroker ELB_CLIENT failed to query target endpoints with error {} ",
						e.getMessage());
			}
		}
		return targetEndpoints;
	}

	private Map<String, String> queryTargetEndpointsSD() {
		Map<String, String> targetEndpoints = new HashMap<>();
		if (SD_CLIENT == null) {
			logger.error("The HttpMessageBroker SD_CLIENT is not ready. The target endpoints cannot be queried.");
		} else {
			try {
				ListInstancesRequest request = new ListInstancesRequest();
				request.setServiceId(SD_SERVICE_NAME);
				ListInstancesResult result = SD_CLIENT.listInstances(request);
				// TODO: minor: respect possible pagination
				result.getInstances()
						.forEach(targetInstance -> targetEndpoints.put(
								targetInstance.getAttributes().get("AWS_INSTANCE_IPV4"),
								targetInstance.getAttributes().get("AWS_INSTANCE_PORT")));
			} catch (Exception e) {
				logger.error("The HttpMessageBroker ELB_CLIENT failed to query target endpoints with error {} ",
						e.getMessage());
			}
		}
		return targetEndpoints;
	}

	private Map<String, String> queryTargetEndpointsS3() {
		if (S3_CLIENT == null) {
			logger.error("The HttpMessageBroker S3_CLIENT is not ready. The target endpoints cannot be queried.");
		} else {
			try {
				return getTargetEndpoints(new String(
						IOUtils.toByteArray(S3_CLIENT.getObject(S3_BUCKET_NAME, S3_OBJECT).getObjectContent())));
			} catch (Exception e) {
				logger.error("The HttpMessageBroker S3_CLIENT failed to query target endpoints with error {} ",
						e.getMessage());
			}
		}
		return new HashMap<>();
	}

	private void createStaticClient() {
		STATIC_CONFIG = getConfig("HTTP_MESSAGE_BROKER_STATIC_CONFIG",
				HttpMessageBroker.class.getName() + ".STATIC_CONFIG", "{}");
	}

	private void createElasticLoadBalancingClient() {
		ELB_CLIENT = AmazonElasticLoadBalancingClientBuilder.defaultClient();
		ELB_TARGETGROUP_ARN = getConfig("HTTP_MESSAGE_BROKER_ELB_TARGETGROUP_ARN",
				HttpMessageBroker.class.getName() + ".ELB_TARGETGROUP_ARN", null);
	}

	private void createServiceDiscoveryClient() {
		SD_CLIENT = AWSServiceDiscoveryClient.builder().build();
		SD_SERVICE_NAME = getConfig("HTTP_MESSAGE_BROKER_SD_SERVICE_NAME",
				HttpMessageBroker.class.getName() + ".SD_SERVICE_NAME", "xyz-hub");
	}

	private void createS3Client() {
		String endpoint = getConfig("HTTP_MESSAGE_BROKER_S3_API_ENDPOINT",
				HttpMessageBroker.class.getName() + ".S3_API_ENDPOINT", null);
		String region = getConfig("HTTP_MESSAGE_BROKER_S3_API_ENDPOINT_REGION",
				HttpMessageBroker.class.getName() + ".S3_API_ENDPOINT_REGION", null);
		if (endpoint != null && region != null) {
			S3_CLIENT = AmazonS3ClientBuilder.standard().withPathStyleAccessEnabled(true)
					.withCredentials(new DefaultAWSCredentialsProviderChain())
					.withEndpointConfiguration(new EndpointConfiguration(endpoint, region)).build();
		} else {
			S3_CLIENT = AmazonS3ClientBuilder.standard().withCredentials(new DefaultAWSCredentialsProviderChain())
					.build();
		}
		S3_BUCKET_NAME = getConfig("HTTP_MESSAGE_BROKER_S3_BUCKET", HttpMessageBroker.class.getName() + ".S3_BUCKET",
				"xyz-hub-admin-messaging");
		S3_OBJECT = getConfig("HTTP_MESSAGE_BROKER_S3_OBJECT", HttpMessageBroker.class.getName() + ".S3_OBJECT",
				"instances.json");
	}

	private String getConfig(String environmentKey, String propertyKey, String defaultValue) {
		return System.getenv(environmentKey) != null ? System.getenv(environmentKey)
				: System.getProperty(propertyKey, defaultValue);
	}

}
