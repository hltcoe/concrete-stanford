#!/usr/bin/env zsh
#$ -j y                        # join stderr to stdout
#$ -V                          # job has the same environment variables as the submission shell
#$ -l h_rt=499:00:00            # runtime limit
#$ -l mem_free=32G             # expected amount of mem
#$ -l h_vmem=32G             # expected amount of mem
#$ -l num_proc=16
#$ -b y                         # run command line vs batch (?)
set -e
INPUT_FILE="$1"
OUT_DIR="$2"
BUILT_JAR="$3"

echo "Running on file: $INPUT_FILE"
FILENAME=$(basename $INPUT_FILE)
OUT_FILE="$OUT_DIR/$FILENAME"

echo "Input file: $INPUT_FILE"
echo "Output file: $OUT_FILE"
set +e
java \
    -XX:+UseConcMarkSweepGC \
    -XX:ParallelCMSThreads=3 \
    -Xmx18G \
    -cp $BUILT_JAR \
    edu.jhu.hlt.concrete.stanford.AnnotateNonTokenizedConcrete \
    $INPUT_FILE $OUT_FILE "en"

if [[ ($?) ]]; then
    echo "Succeeded stanford job."
else
    echo "Failed stanford job."
fi
