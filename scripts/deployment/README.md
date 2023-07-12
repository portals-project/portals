# Portals

## Build the Docker Container
The Dockerfile
To build the container (exchange test 1 for a more meaningful container tag):

`docker build -f scripts/deployment/docker/Dockerfile . -t test1`

To run the container:

`docker run -dp 127.0.0.1:8080:8080 test1`

## Kubernetes

If you want to run the server on a kubernetes cluster (e.g. a local minikube setup)

0. Make sure your cluster/minikube setup is running

`minikube start`

1. Deploy the container into the Kubernetes environment.

`kubectl apply -f scripts/deployment/Kubernetes/Deployment.yaml`

2. Wait for a bit and check if the pods have started

`kubectl get pods`

Known Issues :
- If the status is `ImagePullBackOff` and you are using minikube, make sure that minikube can actually use the build container. Some minikube setups require you to push the build docker image to minikube with the following command:
`minikube image load <image name>`


- For WSL/minikube Users the connection to the pod can be a little tricky, the easiest workaround is using something like:
`kubectl port-forward <pod-name> 8080:8080` To force k8s to forward the port from the pod to  localhost
