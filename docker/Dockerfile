FROM ubuntu:22.04
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
        openjdk-11-jdk-headless git subversion perl cpanminus build-essential \
        curl ca-certificates unzip python3 python3-pip python3-venv \
    && rm -rf /var/lib/apt/lists/*
# The Debian JDK package directory differs by CPU architecture.
RUN ln -s "$(dirname "$(dirname "$(readlink -f "$(which java)")")")" /usr/lib/jvm/java-11
ENV JAVA_HOME=/usr/lib/jvm/java-11
ENV _JAVA_OPTIONS="-Duser.language=en -Duser.country=US -Dfile.encoding=UTF-8"
ENV PYTHONUNBUFFERED=1 TZ=UTC

# Latest published version tag verified on 2026-10-02; pin for repeatable builds.
ARG DEFECTS4J_TAG=v3.0.1
RUN git clone https://github.com/rjust/defects4j /defects4j \
    && cd /defects4j && git checkout "$DEFECTS4J_TAG" \
    && cpanm --notest --installdeps . \
    && ./init.sh
ENV PATH=/defects4j/framework/bin:$PATH

RUN pip3 install --no-cache-dir tiktoken javalang requests pyyaml
WORKDIR /work
CMD ["bash"]
