FROM maven:3-ibm-semeru-17-focal AS builder

MAINTAINER MohrJonas

RUN DEBIAN_FRONTEND=noninteractive apt update
RUN DEBIAN_FRONTEND=noninteractive apt install --no-install-recommends -y git

WORKDIR /

RUN git clone https://github.com/MohrJonas/autoPlaylist

FROM dxjoke/tectonic-docker:latest-bullseye-biber





RUN DEBIAN_FRONTEND=noninteractive apt update
RUN DEBIAN_FRONTEND=noninteractive apt install --no-install-recommends -y git