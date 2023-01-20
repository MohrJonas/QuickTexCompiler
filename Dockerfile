FROM maven:3-ibm-semeru-17-focal AS builder

MAINTAINER MohrJonas

COPY . /QuickTexCompiler

WORKDIR /QuickTexCompiler

RUN mvn clean package

FROM dxjoke/tectonic-docker:latest-bullseye-biber

RUN DEBIAN_FRONTEND=noninteractive apt update
RUN DEBIAN_FRONTEND=noninteractive apt install --no-install-recommends -y openjdk-17-jdk-headless

COPY --from=builder /QuickTexCompiler/target/QuickTexCompiler-1.2.0.jar /

ENTRYPOINT ["java", "-jar", "/QuickTexCompiler-1.2.0.jar", "-w", "-p", "-s", "/src", "-o", "/out"]