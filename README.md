# Microservices

## Description
This project's intention was to imitate a mock internship surrounded around the development of a scalable product and order service using a microservice archetecture. No source code was provided from the instructor and much of the development decisions were left to us. This project has taken many forms which the ProfilingWriteup.pdf explains the profiling results around the stages. Ultimatly the cleaned up turned in version was a dockerized cluster of the product, user and order services front faced by Nginx.

## Table of Contents
- [Usage](#usage)
- [TechStack](#techstack)
- [Scalability](#Scalability)
- [Testing](#testing)

## Usage
To use any version (Although I recommend the FinalProduct), once the folder is available locally, use  dockerfile's to create the required docker containers of the services and then use docker compose to spin up the cluster.

## TechStack
This project was developed using Java async for the individual services.

The front facing interservice communicator was first made in python flask, however after profiling, this was a heat zone for perfomance limiting the overall efficiency. Thus Nginx was used as the front facing load balencer offering a great performance increase.

Data persistence was initialized with PostgreSql.

Docker was used for ease of deployment and scalability.

## Scalability
Due to security concerns with deployment enviroment we were required to test this project on, I was required to run one cluster on one machine. However the capability of horizontal scaling is still possible. Although I striped the capability of the horizontal scaling in the final version to streamline the deployment on the testing machine, the postIscsAsync-3 version of this project contains a reporting service in the iscs.py and the individual services. This reporting functionality allowed any spun up services to report their ip's to the iscs to allow for no micro management other then ensuring the iscs's ip and port are correct and present in the required config.json file. 

## Testing
Once a cluster is up and running, ensure the base_url in ~/tests/stressworkload.py is configured correctly. On line 141, the rate_limit of the requests can be configured. Running this file will send requests concurrently and report the average requests handled per second.
