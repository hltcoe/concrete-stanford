#!/bin/bash

set -e


# find the most recent (presumably only) version of the JAR
CLASSPATH="$(find concrete-stanford/target -name "concrete-stanford-*-jar-with-dependencies.jar" | sort --version-sort | head -n 1)"

exec java -classpath "$CLASSPATH" -Xms1g -Xmx2g \
    edu.jhu.hlt.concrete.stanford.server.ConcreteStanfordThriftServerLauncher \
    "$@"
