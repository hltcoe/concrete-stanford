#!/bin/bash

set -e

CLASSPATH="/home/concrete/concrete-stanford/concrete-stanford.jar"

exec java -classpath "$CLASSPATH" -Xms1g -Xmx2g \
    edu.jhu.hlt.concrete.stanford.server.ConcreteStanfordThriftServerLauncher \
    "$@"
