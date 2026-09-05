# One greenfinger node.
#
# The same jar the launchers run, so a container and a process started by run-local.sh are the
# same thing: a node that finds its peers by cluster name and shares the crawl with them.
#
# Build from the deploy directory, which is where the jar and the configuration live:
#
#     docker build -t greenfinger:local .
#
# run-docker.sh does that and then starts as many of them as you ask for.
FROM eclipse-temurin:17-jre

WORKDIR /app

# Configuration is mounted rather than baked in, so changing a setting does not mean rebuilding
# the image. The jar is copied because it is what the image is for.
# bin/, which is where packaging puts it, and by the versioned name it now carries. The copy at
# the top of deploy/ is whatever was there before the jars moved, and building from it is how an
# image ends up a release behind. Renamed on the way in so the entry point needs no wildcard.
COPY lib/greenfinger-api-*.jar /app/greenfinger-api.jar
COPY docker/static /app/static
# A node keeps its own complete copy of everything, so this is a volume in every deployment worth
# the name: a container that is replaced without one starts from an empty database and has to be
# sent the whole crawl again.
VOLUME ["/app/data"]

# The data store, in the two halves the launchers use. system/ is the crawl's working state and
# is always local; user/ is what was crawled -- assets, index, vector -- and is what a search
# reads. A node pointed at MinIO, Elasticsearch and Qdrant writes nothing under user/ at all.
ENV GF_DATA_STORE=/app/data \
    GF_SYSTEM_DATA_DIR=/app/data/system \
    GF_USER_DATA_DIR=/app/data/user \
    GF_DB_URL=jdbc:h2:file:/app/data/system/greenfinger \
    # Inside the volume, because the models are hundreds of megabytes fetched over the network
    # and the home directory is not persisted. Left at its default, every container start -- every
    # rolling update, every restart -- downloads them again, and a host without internet access
    # cannot start a node at all.
    GF_MODEL_DIR=/app/data/models \
    GF_LOG_FILE=/app/logs/greenfinger.log \
    # No fixed -Xmx. A container has a memory limit and the jvm can read it, so the heap follows
    # whatever the container was given -- a fixed 2g on three containers is how the middle one
    # gets killed by the kernel on a laptop, and an OOM kill looks like a node that simply left.
    JAVA_OPTS="-XX:MaxRAMPercentage=70 -Dfile.encoding=UTF-8"

EXPOSE 50080 22000

# Not the shell form: with an exec entry point the jvm is pid 1 and gets the stop signal, which is
# what lets a crawl wind down instead of being killed halfway through a page.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/greenfinger-api.jar \
  --spring.config.additional-location=file:/app/config/,file:/app/config/api/"]
