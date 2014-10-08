#!/bin/sh
#$ -cwd                        # run from current working directory
#$ -j y                        # join stderr to stdout
#$ -V                          # job has the same environment variables as the submission shell
#$ -l h_rt=50:00:00            # runtime limit
#$ -l mem_free=15G             # expected amount of mem
#$ -l num_proc=2
#$ -M maxjthomas@gmail.com
#$ -o /home/hltcoe/mthomas/job-logs/agiga2-rc4    # log here
#$ -m ase                       # a=aborted b=begining e=end s=suspended
#$ -S /bin/bash
#$ -N agiga2                    # job name
#$ -b y                         # run command line vs batch (?)
java \
    -XX:+UseSerialGC \
    -Xmx10G \
    -Dlog4j.configurationFile=/home/hltcoe/mthomas/git/concrete-stanford/src/main/resources/log4j2.json \
    -cp /home/hltcoe/mthomas/git/concrete-stanford/target/concrete-stanford-3.6.7-jar-with-dependencies.jar \
    concrete.server.concurrent.QSubbableStanfordConverter
