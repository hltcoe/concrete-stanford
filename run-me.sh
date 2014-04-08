#!/bin/bash

##################################
# Arg 1 : path to jar file
# Arg 2 : output file
##################################
java -cp $1 edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe \
    --input /export/common/microscale/2014/data/sectioned/sectioned-comm-v0.1.1-SNAPSHOT.concrete \
    --output $2
