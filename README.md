# concrete-stanford
Provides a library that can take `Communication` objects, containing `Section`s, and
annotate them using the Stanford CoreNLP framework. This produces `Communication` objects
with `Tokenization` objects, and optionally `EntityMention` and `Entity` objects.

## Maven dependency (Currently COE only)

```xml
<dependency>
  <groupId>edu.jhu.hlt</groupId>
  <artifactId>concrete-stanford</artifactId>
  <version>4.4.1</version>
</dependency>
```

## Sectioned Communications are required

All examples assume the input files contain `Communication` objects with, at minimum,
`Section` objects underneath them. This library will not produce useful output
if there are no `Section` objects underneath the `Communication` objects that are run. 
There are two primary drivers --- one that processes Tokenized `Concrete` files, and one that does not. 
Each has its own requirements, described below. 

## Quick start / API Usage


Load in a `Communication` with `Section`s with `TextSpan`s:
```java
// Sections are required for useful output
Communication withSections = ...;
// You need to know what language the Communication is written in
String language = "en";
```

Then create an annotator object and the language of the `Communication`:
```java
GenericStanfordAnnotator pipe = StanfordAnnotatorFactory.getAppropriateAnnotator(withSections, language);
```

Run over the `Communication`:
```java
Communication annotatedWithStanford = pipe.process(withSections);
```

`annotatedWithStanford` is a `Communication` with the output of the system. 
This includes sentences and tokenizations, and DEPENDING on the annotator, entity mentions and entities as well.


## Running as a command-line program

You can run this tool as a command line program.
* Input: a path to a file on disk that is either a serialized Concrete `Communication` (ending with
`.concrete`), a `.tar` file of serialized Concrete `Communication` objects, or a `.tar.gz` file
with serialized Concrete `Communication` objects. Recall that each `Communication` must have
`Section` objects.
* Output: a path that represents the desired output directory.

## Known Annotators


`concrete-stanford` can annotate text that is both pre-tokenized and text that is not. 
The annotators are generalized by `GenericStanfordAnnotator`, which has a `process(Communication)` 
method and a pre-validation method `ensurePreconditionsMet`. The former processes an input 
communication and returns an annotated **copy** of the input; the latter ensures that the input 
communication conforms to all assumptions made in `process`.

By default, all annotators add named entity recognition, part-of-speech, lemmatization, 
a constituency parse and three dependency parses (converted deterministically from the 
constituency parse).

### Non-Tokenized Input

The main annotator for non-tokenized input is `AnnotateNonTokenizedConcrete`. 
It requires sectioned data, and each section **must** have valid `textSpans` set. 
Input communications may have sentences, but no sentence may have a tokenization.
While there are other requirements (see `AnnotateNonTokenizedConcrete.ensurePreconditionsMet`), 
these three are the most important.

In addition to the above added annotations, `AnnotateNonTokenizedConcrete` will add entity 
mention identification and coreference.


### Tokenized Input

The main annotator for non-tokenized input is `AnnotateTokenizedConcrete`. 
It requires fully Tokenized data; each {`Section`,`Sentence`,`Token`} **must** have valid `textSpans` set. 
While there are other requirements (see `AnnotateTokenizedConcrete.ensurePreconditionsMet`), these  
are the most important.

Unlike for non-tokenized input, `AnnotateTokenizedConcrete` will **NOT** add entity 
mention identification and coreference.

### Prepare
Replace the environment variables in the code below with directories that represent your
input and output.

### TLDR
The following should be compliant in any `sh`-like shell. 
Replace `<CURRENT-VERSION>` with the correct version of `concrete-stanford`.

```sh
export CONC_STAN_INPUT_FILE=/path/to/.concrete/or/.tar/or/.tar.gz
export CONC_STAN_OUTPUT_DIR=/path/to/output/dir
mvn clean compile assembly:single
java -cp target/concrete-stanford-<CURRENT-VERSION>-jar-with-dependencies.jar edu.jhu.hlt.concrete.stanford.ConcreteStanfordAnnotator \
$CONC_STAN_INPUT_FILE \
$CONC_STAN_OUTPUT_DIR
```
