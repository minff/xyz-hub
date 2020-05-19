# Example for using WebMessageBroker

The WebMessageBroker provides an abstract MessageBroker implementation as an optional alternative to the default SnsMessageBroker implementation.

Currently one can choose between the following WebMessageBroker extends:
- StaticWebMessageBroker (*use e.g. for local development and testing*)
- S3WebMessageBroker (*use e.g. for a well known static set of xyz-hub instances*)
- TargetGroupWebMessageBroker (*use with AWS Elastic Loadbalancing*)
- ServiceDiscoveryWebMessageBroker (preferred, *use with AWS ECS Service Discovery*)

## Example local stack with docker-compose

An example stack and all dependencies can be started locally using Docker compose.
```bash
docker-compose -f docker-compose-nginx.yml build
```
```bash
docker-compose -f docker-compose-nginx.yml up -d --scale xyz-hub=2
```
```bash
Creating volume "xyz-hub_pgdata" with default driver
Creating volume "xyz-hub_pgadmindata" with default driver
Creating volume "xyz-hub_dynamodbdata" with default driver
Creating redis    ... done
Creating dynamodb        ... done
Creating postgres        ... done
Creating redis-commander   ... done
Creating pgadmin           ... done
Creating dynamodb-admin    ... done
Creating xyz-hub_xyz-hub_1 ... done
Creating xyz-hub_xyz-hub_2 ... done
Creating swagger-ui        ... done
Creating nginx             ... done
```

Now you can access the following endpoints.
 * http://localhost:8080/hub/
 * http://localhost:8080/swagger/
 * http://localhost:8080/pgadmin4/ (user@localhost/password)
 * http://localhost:8080/dynamodb/
 * http://localhost:8080/redis/

The example stack for the WebMessageBroker contains the below configuration for a default static target datasource.
```bash
ADMIN_MESSAGE_BROKER=StaticWebMessageBroker
STATIC_WEB_MESSAGE_BROKER_CONFIG='{"xyz-hub_xyz-hub_1": "8181","xyz-hub_xyz-hub_2": "8181"}'
```

### Example startup log output
```bash
docker-compose -f docker-compose-nginx.yml logs --tail=10000 --follow|grep MessageBroker
xyz-hub_1          | 2020-05-19 12:45:09,549 INFO  WebMessageBroker The StaticWebMessageBroker was initialized.
xyz-hub_1          | 2020-05-19 12:45:09,549 DEBUG WebMessageBroker TARGET_ENDPOINTS: {xyz-hub_xyz-hub_1=8181, xyz-hub_xyz-hub_2=8181}, PeriodicUpdate: false, PeriodicUpdateDelay: 0
xyz-hub_2          | 2020-05-19 12:45:09,614 INFO  WebMessageBroker The StaticWebMessageBroker was initialized.
xyz-hub_2          | 2020-05-19 12:45:09,627 DEBUG WebMessageBroker TARGET_ENDPOINTS: {xyz-hub_xyz-hub_1=8181, xyz-hub_xyz-hub_2=8181}, PeriodicUpdate: false, PeriodicUpdateDelay: 0
```

### Example publish log output
```bash
xyz-hub_1          | 2020-05-19 12:48:18,913 DEBUG WebMessageBroker Send AdminMessage.@Class: InvalidateSpaceCacheMessage , Source.Ip: 96239ffaaf61
xyz-hub_1          | 2020-05-19 12:48:18,917 DEBUG WebMessageBroker TARGET_ENDPOINTS: {xyz-hub_xyz-hub_1=8181, xyz-hub_xyz-hub_2=8181}, PeriodicUpdate: false, PeriodicUpdateDelay: 0
xyz-hub_1          | 2020-05-19 12:48:18,917 DEBUG WebMessageBroker Preparing request for target: xyz-hub_xyz-hub_1:8181
xyz-hub_1          | 2020-05-19 12:48:18,939 DEBUG WebMessageBroker Preparing request for target: xyz-hub_xyz-hub_2:8181
xyz-hub_1          | 2020-05-19 12:48:18,966 DEBUG WebMessageBroker Send AdminMessage to all target endpoints running in background.
xyz-hub_1          | 2020-05-19 12:48:18,967 DEBUG WebMessageBroker AdminMessage was: {"@class":"com.here.xyz.hub.config.SpaceConfigClient$InvalidateSpaceCacheMessage","source":{"id":"b0c99480-8c0c-4e27-b9a7-05f56c305f0c","ip":"96239ffaaf61","port":8181},"destination":null,"id":"GyXGyMf4"}
xyz-hub_1          | 2020-05-19 12:48:18,967 DEBUG WebMessageBroker Drop AdminMessage.@Class: InvalidateSpaceCacheMessage , Source.Ip: 96239ffaaf61
xyz-hub_1          | 2020-05-19 12:48:18,989 DEBUG WebMessageBroker Drop AdminMessage.@Class: InvalidateSpaceCacheMessage , Source.Ip: 96239ffaaf61
xyz-hub_2          | 2020-05-19 12:48:19,009 DEBUG WebMessageBroker Handle AdminMessage.@Class: InvalidateSpaceCacheMessage , Source.Ip: 96239ffaaf61
```

## In Production

In production the WebMessageBroker should be best used with AWS ECS Service Discovery configuration or with AWS ELB Target Group configuration.

It is also possible to get a list of target endpoints from a json file object from S3.

### AWS ECS Service Discovery target datasource

```bash
ADMIN_MESSAGE_BROKER=ServiceDiscoveryWebMessageBroker
SERVICE_DISCOVERY_WEB_MESSAGE_BROKER_SERVICE_ID=srv-22dygtsduanhozc3
```

Assuming you are using AWS CloudFormation to deploy the xyz-hub as an ECS Service you may want to add a AWS::ServiceDiscovery::PrivateDnsNamespace and AWS::ServiceDiscovery::Service resources as well as a AWS::IAM::Policy allowing your instances to use the servicediscovery to your stack.

### AWS ELB Target Group target datasource

```bash
ADMIN_MESSAGE_BROKER=TargetGroupWebMessageBroker
TARGET_GROUP_WEB_MESSAGE_BROKER_ELB_TARGETGROUP_ARN=arn:aws:elasticloadbalancing:us-east-1:123456789012:targetgroup/xyz-hub-service/12ab34cd56ef78gh
```

Assuming you are using AWS CloudFormation to deploy the xyz-hub as an ECS Service you may want to add a AWS::IAM::Policy allowing your instances to use the elasticloadbalancing to your stack.

### AWS S3 target datasource

```bash
ADMIN_MESSAGE_BROKER=S3WebMessageBroker
S3_WEB_MESSAGE_BROKER_BUCKET=xyz-hub-admin
S3_WEB_MESSAGE_BROKER_OBJECT=service-instances.json
```

Assuming you are using AWS CloudFormation to deploy the xyz-hub you may want to add a AWS::S3::Bucket to your stack.

## Configuration Reference

Note: Environment variables are preferred over properties!

### Available environment variables
```bash
# Enable the WebMessageBroker
ADMIN_MESSAGE_BROKER=<MessageBroker>

# Configure the StaticWebMessageBroker for static
ADMIN_MESSAGE_BROKER=StaticWebMessageBroker
STATIC_WEB_MESSAGE_BROKER_CONFIG='{"xyz-hub_xyz-hub_1": "8181","xyz-hub_xyz-hub_2": "8181"}'

# Configure the ServiceDiscoveryWebMessageBroker for AWS ECS Service Discovery
ADMIN_MESSAGE_BROKER=ServiceDiscoveryWebMessageBroker
SERVICE_DISCOVERY_WEB_MESSAGE_BROKER_SERVICE_ID=srv-22dygtsduanhozc3
SERVICE_DISCOVERY_WEB_MESSAGE_BROKER_PERIODIC_UPDATE_DELAY=30000

# Configure the TargetGroupWebMessageBroker for AWS ELB Target Group
ADMIN_MESSAGE_BROKER=TargetGroupWebMessageBroker
TARGET_GROUP_WEB_MESSAGE_BROKER_ELB_TARGETGROUP_ARN=arn:aws:elasticloadbalancing:us-east-1:123456789012:targetgroup/xyz-hub-service/12ab34cd56ef78gh
TARGET_GROUP_WEB_MESSAGE_BROKER_PERIODIC_UPDATE_DELAY=30000

# Configure the S3WebMessageBroker for AWS S3
ADMIN_MESSAGE_BROKER=S3WebMessageBroker
S3_WEB_MESSAGE_BROKER_BUCKET=xyz-hub-admin
S3_WEB_MESSAGE_BROKER_OBJECT=service-instances.json
S3_WEB_MESSAGE_BROKER_PERIODIC_UPDATE=false
S3_WEB_MESSAGE_BROKER_PERIODIC_UPDATE_DELAY=30000
```

### Available java properties
```bash
# Enable the WebMessageBroker
-DAdminMessageBroker=<MessageBroker>

# Configure the StaticWebMessageBroker for static
-DAdminMessageBroker=StaticWebMessageBroker
-Dcom.here.xyz.hub.rest.admin.StaticWebMessageBroker.CONFIG='{"xyz-hub_xyz-hub_1": "8181","xyz-hub_xyz-hub_2": "8181"}'

# Configure the ServiceDiscoveryWebMessageBroker for AWS ECS Service Discovery
-DAdminMessageBroker=ServiceDiscoveryWebMessageBroker
-Dcom.here.xyz.hub.rest.admin.ServiceDiscoveryWebMessageBroker.SERVICE_ID=srv-22dygtsduanhozc3
-Dcom.here.xyz.hub.rest.admin.ServiceDiscoveryWebMessageBroker.PERIODIC_UPDATE_DELAY=30000

# Configure the TargetGroupWebMessageBroker for AWS ELB Target Group
-DAdminMessageBroker=TargetGroupWebMessageBroker
-Dcom.here.xyz.hub.rest.admin.TargetGroupWebMessageBroker.ELB_TARGETGROUP_ARN=arn:aws:elasticloadbalancing:us-east-1:123456789012:targetgroup/xyz-hub-service/12ab34cd56ef78gh
-Dcom.here.xyz.hub.rest.admin.TargetGroupWebMessageBroker.PERIODIC_UPDATE_DELAY=30000

# Configure the S3WebMessageBroker for AWS S3
-DAdminMessageBroker=S3WebMessageBroker
-Dcom.here.xyz.hub.rest.admin.S3WebMessageBroker.BUCKET=xyz-hub-admin
-Dcom.here.xyz.hub.rest.admin.S3WebMessageBroker.OBJECT=service-instances.json
-Dcom.here.xyz.hub.rest.admin.S3WebMessageBroker.PERIODIC_UPDATE=false
-Dcom.here.xyz.hub.rest.admin.S3WebMessageBroker.PERIODIC_UPDATE_DELAY=30000
```

### Example json file object on S3:
```json
{
    "xyz-hub_xyz-hub_1": "8181",
    "xyz-hub_xyz-hub_2": "8181"
}
```