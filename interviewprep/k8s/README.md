# NAV Enterprise Integration Platform - Kubernetes Deployment

This directory contains Kubernetes manifests for deploying the complete NAV Enterprise Integration Platform to a Kubernetes cluster. The deployment showcases production-grade patterns used in Norwegian government systems.

## Architecture Overview

The Kubernetes deployment implements a microservices architecture with the following components:

### Core Services
- **Main Application** (`nav-integration-app`): Spring Boot integration platform with Apache Camel ESB
- **API Gateway** (`api-gateway`): Spring Cloud Gateway for request routing and load balancing
- **External Services**: Simulated government registries (Folkeregister, Skatteetaten, Bank, A-Ordningen)

### Infrastructure Services
- **PostgreSQL**: Primary database with persistent storage
- **Kafka + Zookeeper**: Event streaming platform for pub/sub messaging
- **RabbitMQ**: Message queue system for work distribution patterns
- **Redis**: Caching and session storage

### Monitoring Stack
- **Zipkin**: Distributed tracing for request correlation
- **Prometheus**: Metrics collection and monitoring
- **Health Checks**: Comprehensive liveness/readiness probes

## Enterprise Patterns Demonstrated

### 1. **High Availability (HA)**
- Multiple replicas for stateless services
- Pod Disruption Budgets for graceful updates
- Horizontal Pod Autoscaling based on CPU/memory metrics
- Health checks with proper startup/liveness/readiness probes

### 2. **Security**
- Network policies for traffic segmentation and security
- Secrets management for sensitive configuration
- RBAC-ready service accounts (can be extended)
- Container security contexts

### 3. **Observability**
- Distributed tracing with Zipkin integration
- Prometheus metrics scraping with custom annotations
- Structured logging with correlation IDs
- Health check endpoints for all services

### 4. **Resilience**
- Circuit breakers in application code
- Retry patterns with exponential backoff
- Dead letter queues for failed messages
- Graceful shutdown handling

### 5. **Scalability**
- Horizontal Pod Autoscaling (HPA) for demand-based scaling
- Resource limits and requests for optimal scheduling
- Persistent volumes for stateful services
- Load balancing across service replicas

## File Structure

```
k8s/
├── namespace.yaml           # Namespace, ConfigMap, and Secrets
├── postgres.yaml           # PostgreSQL database with persistent storage
├── kafka.yaml              # Kafka and Zookeeper for event streaming
├── rabbitmq.yaml           # RabbitMQ for message queues
├── external-services.yaml  # External government service simulators
├── api-gateway.yaml        # Spring Cloud Gateway with Ingress
├── main-application.yaml   # Main Spring Boot application
├── monitoring.yaml         # Zipkin, Prometheus, Redis
├── network-policies.yaml   # Security policies for traffic control
├── deploy.sh              # Automated deployment script
└── README.md              # This documentation
```

## Prerequisites

### Kubernetes Cluster Requirements
- Kubernetes 1.24+ with RBAC enabled
- Persistent Volume support (e.g., EBS on AWS, Persistent Disks on GCP)
- Ingress controller (nginx-ingress recommended)
- At least 8GB RAM and 4 CPU cores available across nodes

### Tools Required
- `kubectl` configured to access your cluster
- Docker for building images
- Container registry access (Docker Hub, ECR, GCR, etc.)

### Optional But Recommended
- `helm` for package management
- `kustomize` for configuration management
- Monitoring stack (if not using included Prometheus setup)

## Deployment Instructions

### Quick Start (Infrastructure Only)
```bash
# Make deployment script executable
chmod +x deploy.sh

# Deploy infrastructure services
./deploy.sh
```

This deploys the infrastructure components (databases, messaging, monitoring) but requires Docker images to be built for the application services.

### Complete Deployment

1. **Build Docker Images**
```bash
# Build main application
docker build -t your-registry/nav-integration-platform:latest .

# Build external services
cd external-services/folkeregister
docker build -t your-registry/folkeregister-api:latest .

cd ../skatteetaten
docker build -t your-registry/skatteetaten-api:latest .

cd ../bank
docker build -t your-registry/bank-api:latest .

cd ../a-ordningen
docker build -t your-registry/aordningen-api:latest .

# Build API Gateway
cd ../../api-gateway
docker build -t your-registry/api-gateway:latest .
```

2. **Push Images to Registry**
```bash
docker push your-registry/nav-integration-platform:latest
docker push your-registry/folkeregister-api:latest
docker push your-registry/skatteetaten-api:latest
docker push your-registry/bank-api:latest
docker push your-registry/aordningen-api:latest
docker push your-registry/api-gateway:latest
```

3. **Update Image References**
Edit the YAML files to reference your registry:
```yaml
# In main-application.yaml, external-services.yaml, api-gateway.yaml
image: your-registry/nav-integration-platform:latest
```

4. **Deploy Complete Stack**
```bash
# Deploy infrastructure
./deploy.sh

# Deploy application services
kubectl apply -f external-services.yaml
kubectl apply -f api-gateway.yaml
kubectl apply -f main-application.yaml
```

## Configuration

### Environment-Specific Configuration
Modify `namespace.yaml` ConfigMap and Secret for different environments:

```yaml
# ConfigMap for environment-specific settings
SPRING_PROFILES_ACTIVE: "production"  # or "staging", "development"
KAFKA_ENABLED: "true"                  # Enable real Kafka vs mock
```

### Resource Scaling
Adjust resource requests/limits based on your cluster capacity:

```yaml
resources:
  requests:
    memory: "1Gi"
    cpu: "500m"
  limits:
    memory: "2Gi"
    cpu: "1000m"
```

### Storage Classes
Update `storageClassName` in PVCs for your cluster:
```yaml
spec:
  storageClassName: "gp2"  # AWS EBS
  # storageClassName: "standard"  # GKE
  # storageClassName: "managed-csi"  # AKS
```

## Accessing Services

### Internal Service Discovery
Services communicate using Kubernetes DNS:
- `postgres:5432` - Database
- `kafka:9092` - Kafka brokers
- `rabbitmq:5672` - RabbitMQ AMQP
- `nav-integration-service:8080` - Main application

### External Access

#### Through Ingress (Production)
Configure your domain in `api-gateway.yaml`:
```yaml
spec:
  rules:
  - host: nav-integration.yourdomain.com
```

#### Through NodePort (Development)
Services exposed via NodePort:
- RabbitMQ Management: `http://node-ip:31672`
- Zipkin UI: `http://node-ip:30411`
- Prometheus UI: `http://node-ip:30090`

#### Port Forwarding (Local Development)
```bash
# Main application
kubectl port-forward svc/nav-integration-service 8080:8080 -n nav-integration

# RabbitMQ Management
kubectl port-forward svc/rabbitmq 15672:15672 -n nav-integration

# Zipkin tracing
kubectl port-forward svc/zipkin 9411:9411 -n nav-integration

# Prometheus metrics
kubectl port-forward svc/prometheus 9090:9090 -n nav-integration
```

## Monitoring and Observability

### Application Metrics
- **Endpoint**: `http://nav-integration-service:8080/interviewprep/actuator/prometheus`
- **Metrics**: JVM metrics, custom business metrics, HTTP request metrics
- **Collection**: Automatic scraping by Prometheus

### Distributed Tracing
- **Zipkin UI**: Available on port 9411
- **Trace Correlation**: All requests have correlation IDs
- **Service Map**: Visual representation of service calls

### Health Checks
- **Liveness**: `/interviewprep/actuator/health/liveness`
- **Readiness**: `/interviewprep/actuator/health/readiness`
- **Startup**: `/interviewprep/actuator/health` (extended timeout)

### Log Aggregation
Configure log forwarding to your preferred solution:
- **ELK Stack** (Elasticsearch, Logstash, Kibana)
- **EFK Stack** (Elasticsearch, Fluent Bit, Kibana)
- **Cloud Solutions** (CloudWatch, Stackdriver, Azure Monitor)

## Scaling and Performance

### Horizontal Pod Autoscaling (HPA)
Configured for main application and API Gateway:
```yaml
minReplicas: 3
maxReplicas: 20
targetCPUUtilizationPercentage: 70
```

### Manual Scaling
```bash
# Scale main application
kubectl scale deployment nav-integration-app --replicas=5 -n nav-integration

# Scale external service
kubectl scale deployment folkeregister --replicas=3 -n nav-integration
```

### Performance Tuning
- **JVM Settings**: Configured via `JAVA_OPTS` environment variable
- **Database Connections**: Tune HikariCP pool settings
- **Message Consumers**: Adjust concurrency levels

## Security

### Network Policies
Implemented defense-in-depth approach:
- **Default Deny**: All traffic blocked by default
- **Selective Allow**: Only required communication permitted
- **Service Isolation**: Database accessible only by main app

### RBAC (Optional Extension)
Create service accounts with minimal permissions:
```bash
# Create service account
kubectl create serviceaccount nav-integration-sa -n nav-integration

# Bind to appropriate role
kubectl create rolebinding nav-integration-binding \
  --clusterrole=view \
  --serviceaccount=nav-integration:nav-integration-sa \
  -n nav-integration
```

### Secrets Management
- **Kubernetes Secrets**: For database passwords, JWT keys
- **External Secret Operators**: For integration with Vault, AWS Secrets Manager
- **Image Pull Secrets**: For private container registries

## Troubleshooting

### Common Issues

#### Pod Stuck in Pending State
```bash
# Check node resources and scheduling
kubectl describe pod <pod-name> -n nav-integration
kubectl get nodes
kubectl top nodes
```

#### Application Won't Start
```bash
# Check application logs
kubectl logs deployment/nav-integration-app -n nav-integration

# Check dependencies
kubectl get svc -n nav-integration
kubectl get endpoints -n nav-integration
```

#### Database Connection Issues
```bash
# Test database connectivity
kubectl exec -it deployment/nav-integration-app -n nav-integration -- \
  bash -c "apt update && apt install -y postgresql-client && \
  pg_isready -h postgres -p 5432"
```

#### Message Queue Issues
```bash
# Check RabbitMQ status
kubectl port-forward svc/rabbitmq 15672:15672 -n nav-integration
# Visit http://localhost:15672 (nav/navpassword)

# Check Kafka topics
kubectl exec -it deployment/kafka -n nav-integration -- \
  kafka-topics.sh --list --bootstrap-server localhost:9092
```

### Debugging Commands
```bash
# Get all resources
kubectl get all -n nav-integration

# Describe problem pods
kubectl describe pod <pod-name> -n nav-integration

# Check events
kubectl get events -n nav-integration --sort-by=.metadata.creationTimestamp

# Resource usage
kubectl top pods -n nav-integration
kubectl top nodes
```

## Maintenance

### Updates and Rollouts
```bash
# Update application image
kubectl set image deployment/nav-integration-app \
  nav-integration-app=your-registry/nav-integration-platform:v2.0.0 \
  -n nav-integration

# Check rollout status
kubectl rollout status deployment/nav-integration-app -n nav-integration

# Rollback if needed
kubectl rollout undo deployment/nav-integration-app -n nav-integration
```

### Backup and Disaster Recovery
```bash
# Backup PostgreSQL
kubectl exec deployment/postgres -n nav-integration -- \
  pg_dump -U nav navdb > backup.sql

# Backup persistent volumes (cluster-specific)
# AWS EBS snapshots, GCP disk snapshots, etc.
```

### Cleanup
```bash
# Remove entire deployment
kubectl delete namespace nav-integration

# Remove specific components
kubectl delete -f main-application.yaml
kubectl delete -f external-services.yaml
```

## Production Considerations

### Resource Planning
- **CPU**: Plan for 2-4 cores per main application replica
- **Memory**: 2-4GB per main application replica
- **Storage**: 20GB+ for PostgreSQL, 10GB+ for Kafka
- **Network**: Consider bandwidth for message throughput

### High Availability
- **Multi-AZ Deployment**: Spread across availability zones
- **Database HA**: Consider PostgreSQL clustering or managed services
- **Message Queue HA**: RabbitMQ clustering for production

### Compliance and Governance
- **Data Residency**: Ensure data stays within required regions
- **Audit Logging**: Enable comprehensive audit trails
- **GDPR Compliance**: Implement data protection measures
- **Security Scanning**: Regular vulnerability assessments

This Kubernetes deployment provides a solid foundation for a production-grade NAV integration platform, demonstrating modern cloud-native patterns and Norwegian government system requirements.