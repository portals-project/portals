# Portals

## Build the Docker Container
The Dockerfile
To build the container for the server (exchange test 1 for a more meaningful container tag):

`docker build -f scripts/deployment/docker/Dockerfile-Server . -t test1`

To run the container:

`docker run -dp 127.0.0.1:8080:8080 test1`

## Kubernetes

If you want to run the server on a kubernetes cluster (e.g. a local minikube setup)

0. Make sure your cluster/minikube setup is running

`minikube start`

1. Deploy the container into the Kubernetes environment. (Remember to change the image name to whatever you have chosen before)

`kubectl apply -f scripts/deployment/Kubernetes/Deployment.yaml`

2. Wait for a bit and check if the pods have started

`kubectl get pods`


## Known Issues :
- If the status is `ImagePullBackOff` and you are using minikube, make sure that minikube can actually use the build container. Some minikube setups require you to push the build docker image to minikube with the following command:
`minikube image load <image name>`


- For WSL/minikube Users the connection to the pod can be a little tricky, the easiest workaround is using something like:
`kubectl port-forward <pod-name> 8080:8080` To force k8s to forward the port from the pod to  localhost


# Full Example

0. Preparation: Start Minikube && Build the images for Client and Server

```minikube start
docker build -f scripts/deployment/docker/Dockerfile-ShoppingCartClient . -t test-client
docker build -f scripts/deployment/docker/Dockerfile-Server . -t test-server```

1. Deploy the Server into the Kubernetes environment. (Adjust the image name in the deployment.yaml)

`kubectl apply -f scripts/deployment/Kubernetes/Deployment.yaml`

2. As soon as the Server has started, You can run the ShoppingCartClient.

`docker run --network host  test-client`