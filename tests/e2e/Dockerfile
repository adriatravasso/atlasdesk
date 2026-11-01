FROM cypress/browsers:22.15.0

USER root
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        novnc \
        python3-websockify \
        x11vnc \
        xvfb \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /e2e
COPY tests/e2e/e2e-entrypoint.sh /usr/local/bin/e2e-entrypoint
RUN chmod 0755 /usr/local/bin/e2e-entrypoint

ENTRYPOINT ["/usr/local/bin/e2e-entrypoint"]
