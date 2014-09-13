#!/bin/bash
java \
-XX:+UseSerialGC \
-Dlog4j.configurationFile=/home/hltcoe/mthomas/git/concrete-stanford/src/main/resources/log4j2.json \
-cp /home/hltcoe/mthomas/git/concrete-stanford/target/concrete-stanford-3.3.2-SNAPSHOT-jar-with-dependencies.jar \
concrete.server.concurrent.RedisEnabledStanfordConverter localhost 45445 /home/hltcoe/mthomas/data/giga/gigatwo.db
