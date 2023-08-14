# Portals

## Docker

### Build the Docker Container
To build the Docker container for the server and client, use the following command. 

```bash
docker build -f scripts/deployment/docker/Dockerfile . -t portals
```

### Run the Server

To run the portals server from a container, run the following command.

```bash
docker run --rm -d -p 8080:8080 portals
```

This maps the port 8080 from the container to the port 8080 on the host machine. Additionally, the container is run in detached mode, so that the container runs in the background. To instead run the container in interactive mode, replace the `-d` with `-it`. The `--rm` flag removes the container after it is stopped.

```bash
docker run --rm -it -p 8080:8080 portals
```

The container can also be run with entrypoint to the bash shell.

```bash
docker run --rm -it -p 8080:8080 portals bash
```

### Run the Server and Client with Docker

The following commands will run both a server and a client, and submit the applications from the client to the server.
  
```bash
# Start the server in one terminal window
docker run --rm -it -p 8080:8080 portals
# Start the clients in another terminal window
# Get the IP address of the server
# Run a client to submit the class files
docker run --rm -it portals sbt "distributed/runMain portals.distributed.ClientCLI submitDir --directory portals-distributed/target/scala-3.3.0/classes --ip host.docker.internal --port 8080"
# Run a client to launch the application
docker run --rm -it portals sbt "distributed/runMain portals.distributed.ClientCLI  launch --application portals.distributed.examples.HelloWorld$ --ip host.docker.internal --port 8080"
```

### Remove the Docker Images

To remove the Docker images, use the following command.

```bash
docker rmi portals
```

## Kubernetes

If you want to run the server on a kubernetes cluster (e.g. a local minikube setup)

1. Preparations.
  
Make sure your cluster/minikube setup is running.

```bash
minikube start
```

Load the docker image into minikube.

```bash
minikube image load portals
```

2. Deploy the container into the Kubernetes environment.

```bash
kubectl apply -f scripts/deployment/Kubernetes/Deployment.yaml
```

(Setup port forwarding.)

```bash
kubectl port-forward service/portals-service 8080:8080
```

Check to see if the container is running.

```bash
kubectl get pods
```

Start inspecting the logs of the server (in a separate terminal window)

```bash
watch -n1 kubectl logs --tail 50 service/portals-service
```

4. Connect to the server from a client.

There are many options for this, choose one of them.
```bash
# == Docker:
# Run a client to submit the class files
docker run --rm -it portals sbt "distributed/runMain portals.distributed.ClientCLI submitDir --directory portals-distributed/target/scala-3.3.0/classes --ip host.docker.internal --port 8080"
# Run a client to launch the application
docker run --rm -it portals sbt "distributed/runMain portals.distributed.ClientCLI  launch --application portals.distributed.examples.HelloWorld$ --ip host.docker.internal --port 8080"
# == SBT:
# Submit the class files
sbt "distributed/runMain portals.distributed.ClientCLI submitDir --directory portals-distributed/target/scala-3.3.0/classes"
# Launch the application
sbt "distributed/runMain portals.distributed.ClientCLI  launch --application portals.distributed.examples.HelloWorld$ --port 8080"
```

5. Remove the deployment.

```bash
kubectl delete -f scripts/deployment/Kubernetes/Deployment.yaml
```
