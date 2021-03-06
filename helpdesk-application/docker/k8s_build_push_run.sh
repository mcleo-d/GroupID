#!/usr/bin/env bash
printf "Building java project [helpdesk-renderer]\n\n"

printf "Get current project version and replace it inside the build manifest file\n"
cd ..
cp target/helpdesk-renderer.jar docker/helpdesk-renderer.jar
VERSION=$(grep -Po -m 1 '<version>\K[^<]*' pom.xml)
cd -
printf "Project version: $(printf $VERSION)\n\n"

cp k8s_deployment.yaml.template k8s_deployment.yaml
sed -i -- "s/<VERSION>/$(printf $VERSION)/g" k8s_deployment.yaml

printf "Pulling Docker Java8 base image\n\n"
docker pull openjdk:8-jdk-alpine

printf "Building docker image [helpdesk-renderer] for GCE\n"
printf "docker build -t gcr.io/${PROJECT_ID}/helpdesk-renderer:$VERSION .\n\n"
export PROJECT_ID="$(gcloud config get-value project -q)"
docker build -f Dockerfile -t gcr.io/${PROJECT_ID}/helpdesk-renderer:$VERSION .

printf "Uploading docker image [helpdesk-renderer] on GCE\n"
printf "gcloud docker -- push gcr.io/${PROJECT_ID}/helpdesk-renderer:$VERSION\n"
gcloud docker -- push gcr.io/${PROJECT_ID}/helpdesk-renderer:$VERSION

printf "\nDeleting previous service on kubernetes\n"
printf "kubectl delete service helpdesk-renderer-service\n"
kubectl delete service helpdesk-renderer-service

printf "\nDeleting previous deployment on kubernetes\n"
printf "kubectl delete deployment helpdesk-renderer\n"
kubectl delete deployment helpdesk-renderer

printf "\nCreating a new deployment on kubernetes\n"
printf "kubectl create -f k8s_deployment.yaml\n"
kubectl create -f k8s_deployment.yaml

printf "\nCreating a new service on kubernetes\n"
printf "kubectl create -f k8s_service.yaml\n"
kubectl create -f k8s_service.yaml

printf "\nClearing generated files\n"
rm -rf helpdesk-renderer.jar
rm -rf k8s_deployment.yaml
