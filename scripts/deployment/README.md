# Portals

## Build the Docker Container
To build the Docker container for the server, use the following command (replace `test-server` with a more meaningful container tag):

`docker build -f scripts/deployment/docker/Dockerfile-Server . -t test-server`

To run the container:

`docker run -dp 127.0.0.1:8080:8080 test-server`

## Kubernetes

If you want to run the server on a kubernetes cluster (e.g. a local minikube setup)

0. Make sure your cluster/minikube setup is running

`minikube start`

1. Deploy the container into the Kubernetes environment (Replace <image-name> with the actual image name you have chosen):

`kubectl apply -f scripts/deployment/Kubernetes/Deployment.yaml`

2. Wait for a bit and check if the pods have started

`kubectl get pods`

## Known Issues :
- If the status of the pod is `ImagePullBackOff`, and you are using Minikube, ensure that Minikube can use the built container. Some Minikube setups require you to push the built Docker image to Minikube with the following command:
`minikube image load <image name>`


- For WSL/Minikube users, connecting to the pod can be a little tricky. The easiest workaround is to use port forwarding:
`kubectl port-forward <pod-name> 8080:8080` To force k8s to forward the port from the pod to  localhost

# Full Example

Before deploying the Server and the Client into the Kubernetes environment, make sure you have started Minikube and built the Docker images for the Client and Server:

```
minikube start
docker build -f scripts/deployment/docker/Dockerfile-ShoppingCartClient . -t test-client
docker build -f scripts/deployment/docker/Dockerfile-Server . -t test-server
```

1. Deploy the Server into the Kubernetes environment (Adjust the image name in the `deployment.yaml` file):

`kubectl apply -f scripts/deployment/Kubernetes/Deployment.yaml`

2. Once the Server has started, you can run the ShoppingCartClient:

`docker run --network host  test-client`