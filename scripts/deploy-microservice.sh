#!/bin/bash

# Function to display usage
usage() {
  echo "Usage: $0 <SERVICE_NAME> <IMAGE> [NAMESPACE] <FILE_PATH> <DOMAIN_NAME> <EMAIL> <PORT> [EUREKA_ROLE] [CONFIG_REPO] [GATEWAY_ROUTES] [EUREKA_CLIENT]"
  exit 1
}

# Validate required inputs
validate_inputs() {
  if [ -z "$SERVICE_NAME" ] || [ -z "$IMAGE" ] || [ -z "$DOMAIN_NAME" ] || [ -z "$EMAIL" ] || [ -z "$PORT" ]; then
    usage
  fi
}

# Create namespace if it doesn't exist
create_namespace() {
  if ! kubectl get ns $NAMESPACE &> /dev/null; then
    kubectl create ns $NAMESPACE
  fi
}

# Install cert-manager if not already installed
install_cert_manager() {
  if ! kubectl get namespace cert-manager &> /dev/null; then
    kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.8.0/cert-manager.yaml
    kubectl wait --for=condition=ready pod -l app.kubernetes.io/instance=cert-manager -n cert-manager --timeout=120s
  fi
}

# Create ClusterIssuer for Let's Encrypt
create_cluster_issuer() {
  if ! kubectl get clusterissuer letsencrypt-prod &> /dev/null; then
    cat <<EOF | kubectl apply -f -
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: ${EMAIL}
    privateKeySecretRef:
      name: letsencrypt-prod
    solvers:
    - http01:
        ingress:
          class: nginx
EOF
  fi
}

# Generate deployment and service manifest
create_deployment_manifest() {
  cat <<EOF > ${SERVICE_NAME}-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${SERVICE_NAME}
  namespace: ${NAMESPACE}
spec:
  replicas: 2
  selector:
    matchLabels:
      app: ${SERVICE_NAME}
  template:
    metadata:
      labels:
        app: ${SERVICE_NAME}
    spec:
      containers:
      - name: ${SERVICE_NAME}
        image: ${IMAGE}
        ports:
        - containerPort: ${PORT}
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "kubernetes"
EOF

  case ${SERVICE_NAME} in
    eureka-service)
      cat <<EOF >> ${SERVICE_NAME}-deployment.yaml
        - name: EUREKA_INSTANCE_PREFERIPADDRESS
          value: "true"
        - name: EUREKA_CLIENT_REGISTERWITHEUREKA
          value: "false"
        - name: EUREKA_CLIENT_FETCHREGISTRY
          value: "false"
EOF
      ;;
    config-server)
      cat <<EOF >> ${SERVICE_NAME}-deployment.yaml
        - name: SPRING_CLOUD_CONFIG_SERVER_GIT_URI
          value: "${CONFIG_REPO}"
EOF
      ;;
    gateway-service)
      cat <<EOF >> ${SERVICE_NAME}-deployment.yaml
        - name: EUREKA_CLIENT_SERVICEURL_DEFAULTZONE
          value: "http://eureka-service.eureka-service.svc.cluster.local:8761/eureka/"
        - name: SPRING_CLOUD_GATEWAY_DISCOVERY_LOCATOR_ENABLED
          value: "true"
EOF
      ;;
    *)
      if [ "${EUREKA_CLIENT}" = "true" ]; then
        cat <<EOF >> ${SERVICE_NAME}-deployment.yaml
        - name: EUREKA_CLIENT_SERVICEURL_DEFAULTZONE
          value: "http://eureka-service.eureka-service.svc.cluster.local:8761/eureka/"
EOF
      fi
      ;;
  esac

  cat <<EOF >> ${SERVICE_NAME}-deployment.yaml
---
apiVersion: v1
kind: Service
metadata:
  name: ${SERVICE_NAME}
  namespace: ${NAMESPACE}
spec:
  selector:
    app: ${SERVICE_NAME}
  ports:
    - protocol: TCP
      port: 80
      targetPort: ${PORT}
EOF
  kubectl apply -f ${SERVICE_NAME}-deployment.yaml
}

# Generate ingress manifest
create_ingress_manifest() {
  cat <<EOF > ${SERVICE_NAME}-ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: ${SERVICE_NAME}-ingress
  namespace: ${NAMESPACE}
  annotations:
    kubernetes.io/ingress.class: nginx
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
spec:
  tls:
  - hosts:
    - ${DOMAIN_NAME}
    secretName: ${SERVICE_NAME}-tls
  rules:
  - host: ${DOMAIN_NAME}
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: ${SERVICE_NAME}
            port:
              number: 80
EOF
  kubectl apply -f ${SERVICE_NAME}-ingress.yaml
}

# Main execution
SERVICE_NAME=$1
IMAGE=$2
NAMESPACE=${3:-default}
FILE_PATH=$4
DOMAIN_NAME=$5
EMAIL=$6
PORT=$7
EUREKA_ROLE=$8
CONFIG_REPO=$9
GATEWAY_ROUTES=${10}
EUREKA_CLIENT=${11}

validate_inputs
create_namespace
install_cert_manager
create_cluster_issuer

mkdir -p /root/cloudinator/${FILE_PATH}
cd /root/cloudinator/${FILE_PATH}

create_deployment_manifest
create_ingress_manifest

# Wait for deployment to be ready
kubectl rollout status deployment/${SERVICE_NAME} -n ${NAMESPACE} --timeout=300s

# Final message
case ${SERVICE_NAME} in
  eureka-service)
    echo "Eureka server deployed. Ensure other services are configured to use it."
    ;;
  config-server)
    echo "Config server deployed. Verify Git repository connectivity."
    ;;
  gateway-service)
    echo "API Gateway deployed. Verify route configurations."
    ;;
  *)
    echo "Service ${SERVICE_NAME} deployed successfully."
    ;;
esac

echo "Deployment of ${SERVICE_NAME} completed."
