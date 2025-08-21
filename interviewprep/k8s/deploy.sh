#!/bin/bash

# NAV Enterprise Integration Platform - Kubernetes Deployment Script
#
# This script deploys the complete NAV integration platform to a Kubernetes cluster.
# It demonstrates production-grade deployment patterns including:
# - Namespace isolation
# - Resource quotas and limits
# - Network policies for security
# - Health checks and monitoring
# - Horizontal Pod Autoscaling
# - Persistent storage for databases
# - ConfigMaps and Secrets management

set -e  # Exit on any error

echo "========================================="
echo "NAV Enterprise Integration Platform"
echo "Kubernetes Deployment Script"
echo "========================================="

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if kubectl is available
if ! command -v kubectl &> /dev/null; then
    print_error "kubectl is not installed or not in PATH"
    exit 1
fi

# Check if we can connect to Kubernetes cluster
if ! kubectl cluster-info &> /dev/null; then
    print_error "Cannot connect to Kubernetes cluster. Please check your kubeconfig."
    exit 1
fi

print_success "Connected to Kubernetes cluster"

# Function to wait for deployment to be ready
wait_for_deployment() {
    local deployment=$1
    local namespace=$2
    print_status "Waiting for deployment $deployment to be ready..."
    kubectl rollout status deployment/$deployment -n $namespace --timeout=300s
    if [ $? -eq 0 ]; then
        print_success "Deployment $deployment is ready"
    else
        print_error "Deployment $deployment failed to become ready"
        return 1
    fi
}

# Function to wait for pods to be ready
wait_for_pods() {
    local label=$1
    local namespace=$2
    local expected_count=${3:-1}
    print_status "Waiting for pods with label $label to be ready..."
    kubectl wait --for=condition=ready pod -l $label -n $namespace --timeout=300s
}

# Step 1: Create namespace and basic configuration
print_status "Step 1: Creating namespace and configuration..."
kubectl apply -f namespace.yaml
print_success "Namespace and configuration created"

# Step 2: Deploy databases and messaging infrastructure
print_status "Step 2: Deploying infrastructure services..."
kubectl apply -f postgres.yaml
kubectl apply -f kafka.yaml
kubectl apply -f rabbitmq.yaml

print_status "Waiting for infrastructure services to be ready..."
wait_for_deployment "postgres" "nav-integration"
wait_for_deployment "zookeeper" "nav-integration"
wait_for_deployment "kafka" "nav-integration"
wait_for_deployment "rabbitmq" "nav-integration"

# Wait a bit more for services to be fully initialized
print_status "Waiting for services to initialize..."
sleep 30

# Step 3: Deploy monitoring infrastructure
print_status "Step 3: Deploying monitoring services..."
kubectl apply -f monitoring.yaml

wait_for_deployment "redis" "nav-integration"
wait_for_deployment "zipkin" "nav-integration"
wait_for_deployment "prometheus" "nav-integration"

# Step 4: Build and deploy external services
print_status "Step 4: Building Docker images for external services..."

# Note: In a real environment, these would be built and pushed to a registry
print_warning "Docker images need to be built and pushed to a container registry"
print_warning "Skipping external services deployment for now"

echo "To build and deploy external services:"
echo "1. Build Docker images:"
echo "   cd external-services/folkeregister && docker build -t nav/folkeregister-api:latest ."
echo "   cd ../skatteetaten && docker build -t nav/skatteetaten-api:latest ."
echo "   cd ../bank && docker build -t nav/bank-api:latest ."
echo "   cd ../a-ordningen && docker build -t nav/aordningen-api:latest ."
echo ""
echo "2. Push to registry and deploy:"
echo "   kubectl apply -f external-services.yaml"

# Step 5: Deploy API Gateway
print_status "Step 5: Deploying API Gateway..."
print_warning "API Gateway deployment skipped - requires Docker images"

# Step 6: Deploy main application
print_status "Step 6: Deploying main application..."
print_warning "Main application deployment skipped - requires Docker image"

echo "To build and deploy the main application:"
echo "1. Build Docker image:"
echo "   docker build -t nav/integration-platform:latest ."
echo ""
echo "2. Push to registry and deploy:"
echo "   kubectl apply -f main-application.yaml"

# Step 7: Apply network policies
print_status "Step 7: Applying network policies..."
kubectl apply -f network-policies.yaml
print_success "Network policies applied"

# Display status
print_status "Current deployment status:"
kubectl get all -n nav-integration

# Display service endpoints
print_status "Service endpoints:"
echo "RabbitMQ Management: http://localhost:31672 (if using NodePort)"
echo "Zipkin Tracing: http://localhost:30411 (if using NodePort)" 
echo "Prometheus Metrics: http://localhost:30090 (if using NodePort)"

# Display useful commands
echo ""
print_status "Useful commands:"
echo "View pods: kubectl get pods -n nav-integration"
echo "View services: kubectl get svc -n nav-integration"
echo "View logs: kubectl logs -f deployment/<deployment-name> -n nav-integration"
echo "Port forward: kubectl port-forward svc/<service-name> <local-port>:<service-port> -n nav-integration"
echo "Scale deployment: kubectl scale deployment <deployment-name> --replicas=<count> -n nav-integration"

# Cleanup instructions
echo ""
print_warning "To clean up the deployment:"
echo "kubectl delete namespace nav-integration"

print_success "Kubernetes deployment script completed!"
print_status "Infrastructure services are running. Build and push Docker images to complete the deployment."