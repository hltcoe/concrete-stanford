# concrete-stanford
Provides a library that can take `Communication` objects, containing `Section`s, and
annotate them using the Stanford CoreNLP framework. This produces `Communication` objects
with `Tokenization` objects, and optionally `EntityMention` and `Entity` objects.

## Maven dependency

```xml
<dependency>
  <groupId>edu.jhu.hlt</groupId>
  <artifactId>concrete-stanford</artifactId>
  <version>4.8.3</version>
</dependency>
```

## Sectioned Communications are required

All examples assume the input files contain `Communication` objects
with, at minimum, `Section` objects underneath them and the `text`
field set. This library will not produce useful output if there are no
`Section` objects underneath the `Communication` objects that are run.
There are two primary drivers --- one that processes Tokenized
`Concrete` files, and one that does not.  Each has its own
requirements, described below.

## Quick start / API Usage

Load in a `Communication` with `Section`s with `TextSpan`s:
```java
// Sections are required for useful output
Communication withSections = ...;
// You need to know what language the Communication is written in
String language = "en";
```

Then create an annotator object and the language of the `Communication`. The following
example shows the `AnnotateNonTokenizedConcrete` tool.
```java
PipelineLanguage lang = PipelineLanguage.getEnumeration(language);
AnnotateNonTokenizedConcrete analytic = new AnnotateNonTokenizedConcrete(lang);
```

Run over the `Communication`:
```java
// Option 1: Wrap the Communication in an appropriate wrapper to ensure pre-reqs are handled
// Below throws a MiscommunicationException if there are no Sections or there are Sentences
// within the Sections.
NonSentencedSectionedCommunication wc = new NonSentencedSectionedCommunication(withSections);
StanfordPostNERCommunication annotated = annotatedWithStanford = analytic.annotate(wc);
// Call 'getRoot()' to get the root, unwrapped Communication.
Communication unwrapped = annotated.getRoot();

// Option 2: Do not wrap the Communication, and handle the possible exception.
// Below will throw if the passed in Communication 'withSections' is invalid
// for the analytic.
StanfordPostNERCommunication annotated = annotatedWithStanford = analytic.annotate(withSections);
Communication unwrapped = annotated.getRoot();
```

`annotated` is a `Communication` with the output of the system.
This includes sentences and tokenizations, and DEPENDING on the annotator, entity mentions and entities as well.

`StanfordPostNERCommunication` is a utility wrapper that allows easier access to members; see
[here](src/main/java/edu/jhu/hlt/concrete/stanford/StanfordPostNERCommunication.java) for the implementations.

## Running as a command-line program

You can also run this tool as a command line program: both `AnnotateTokenizedConcrete` and
`AnnotateNonTokenizedConcrete` can be run via the command line.

* Argument 1: a path to a file on disk that is either a serialized Concrete `Communication` (ending with
`.concrete`), a `.tar` file of serialized Concrete `Communication` objects, or a `.tar.gz` file
with serialized Concrete `Communication` objects. Recall that each `Communication` must have
`Section` objects and must have `text` fields set.
* Argument 2: a path that represents the desired output. The below are supported:

| Input                                    | Result                                                           |
| -----------------------------------------|------------------------------------------------------------------|
| `.concrete` or `.comm` file              | Produces a single new `.concrete` or `.comm` file                |
| `.tar` file with `Communciation` objects | Produces a single `.tar. file with annotated `Communication`s    |
| `.tar.gz` ...                            | Produces a single `.tar.gz` file with annotated `Communication`s |


Alternatively, you can pass in a directory as output. If only a directory is
used as output, the file name from the input will be used and extension mirrored
(e.g., if `.tar` is input, `.tar.gz` will be output).

* Argument 3 (optional): The language to use. Currently supported are `en` and `cn` (for English
and Chinese). The default is `en`.

## Known Annotators
`concrete-stanford` can annotate text that is both pre-tokenized and text that is not.

By default, all annotators add named entity recognition, part-of-speech, lemmatization,
a constituency parse and three dependency parses (converted deterministically from the
constituency parse).

### Non-Tokenized Input
The main annotator for non-tokenized input is `AnnotateNonTokenizedConcrete`.
It requires sectioned data, and each section **must** have valid `textSpans` set.

In addition to the above added annotations, `AnnotateNonTokenizedConcrete` will add entity
mention identification and coreference.

### Tokenized Input

The main annotator for non-tokenized input is `AnnotateTokenizedConcrete`.
It requires fully Tokenized data; each {`Section`,`Sentence`,`Token`} **must** have valid `textSpans` set.

## Running the tool
### Prepare
Replace the environment variables in the code below with directories that represent your
input and output.

### TLDR
The following should be compliant in any `sh`-like shell.

Be sure to change `[en | cn]` to either `en` or `cn`,
depending on what language your documents are in.

```sh
export CONC_STAN_INPUT_FILE=/path/to/.concrete/or/.tar/or/.tar.gz
export CONC_STAN_OUTPUT_DIR=/path/to/output/dir
mvn clean compile assembly:single
java -cp target/*.jar edu.jhu.hlt.concrete.stanford.AnnotateNonTokenizedConcrete \
$CONC_STAN_INPUT_FILE \
$CONC_STAN_OUTPUT_DIR \
[en | cn]
```
