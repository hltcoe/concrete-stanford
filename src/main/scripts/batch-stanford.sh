#!/usr/bin/env sh
#######################################################
### 4 arguments for input:
###
### Arg 1: absolute path of folder containing .tar.gz
###   files of Communications to be run thru Stanford
### Arg 2: Output directory where files will be written
### Arg 3: Path to concrete-stanford project dir
### Arg 4: Absolute path to built stanford jar
###
### Files will have the same name, so 1.tar.gz
### will have the same name, but in the output dir.
###
### Because qsub is stupid, if your file names contain
### things like ':', you might get errors.
#######################################################
INPUT_DIR=$1
OUT_DIR=$2
STANFORD_SRC_DIR=$3
JAR_PATH=$4

for F in $(find $INPUT_DIR -type f -name "*.tar.gz"); do
    FILENAME=$(basename $F)
    echo "Submitting for job file: $FILENAME"
    qsub -N "stanford-$FILENAME" $STANFORD_SRC_DIR/src/main/scripts/qsub-stanford-tgz.sh $F $OUT_DIR $JAR_PATH
done
