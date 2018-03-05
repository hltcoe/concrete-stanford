# Docker images

This project has a number of docker images that make running the project easier.

## Run

``` shell
docker run --rm -it hltcoe/concrete-stanford:eng-latest

Usage: <main class> [options] /path/to/1.tar.gz /path/to/2.tar.gz ...
  Options:
    --enable-std-err
      Enable standard error. By default, Stanford prints a lot of output to
      std err.
      Default: false
    --fail-fast
      Stop with a non-zero status code on the first exception. Useful if each
      document is expected to be successfully processed.
      Default: false
    --help
      Print usage information and exit.
    --only-tokenize
      If true, stop after tokenization and sentence split/segmentation.
      Default: false
    --output-path
      The path to place output files.
      Default: ./comms.tar.gz
    --overwrite
      Overwrite files?
      Default: false
    --run-coref
      Run coreference resolution on the communications. Currently only enabled
      for English.
      Default: false
    --tokenized-input
      If true, assume input has already been tokenized.
      Default: false
```

The following example would mount a volume of data in the image, run only
the tokenization phase, and write the output to the mounted volume:

```
docker run --rm -it \
    --volume $HOME/mounts/docker/:/out/ \
    --name some-stanford \
    hltcoe/concrete-stanford:eng-latest \
    --only-tokenize \
    --output-path /out/eng-tokenized-docker.tar.gz \
    /out/eng.tar.gz
```


## Build

If you want to personally build an image:

``` shell
# from project root
docker build -t yourtag -f eng/Dockerfile . # or esp, or zho
```
