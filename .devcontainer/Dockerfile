FROM maven:3.9.6-eclipse-temurin-11

RUN apt-get update && \
    apt-get install -y vim git curl ssh && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /workspace
COPY . /workspace

EXPOSE 8080
ENV MAVEN_OPTS="-Xmx1024m"
CMD ["/bin/bash"]
