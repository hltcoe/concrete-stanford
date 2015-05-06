#!/bin/bash

##################################
# Arg 1 : path to jar file
# Arg 2 : input file
# Arg 3 : output file
##################################
java -cp $1 edu.jhu.hlt.concrete.stanford.ConcreteStanfordAnnotator \
    $2 \
    $3
