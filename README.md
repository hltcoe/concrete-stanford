concrete-stanford
=================
Provides a library that can take `Communication` objects, containing `Section`s, and
annotate them using the Stanford CoreNLP framework. This produces `Communication` objects
with `Tokenization` objects, and optionally `EntityMention` and `Entity` objects.

Maven dependency
---
```xml
<dependency>
  <groupId>edu.jhu.hlt</groupId>
  <artifactId>concrete-stanford</artifactId>
  <version>4.2.1</version>
</dependency>
```

Sectioned Communications are required
---
All examples assume the input files contain `Communication` objects with, at minimum,
`Section` objects underneath them. This library will not produce useful output
if there are no `Section` objects underneath the `Communication` objects that are run.

Quick start / API Usage
---
Create annotator object:
```java
StanfordAgigaPipe pipe = new StanfordAgigaPipe();
```

Run over a `Communication` object:
```java
// Sections are required for useful output
Communication withSections = ...
Communication annotatedWithStanford = pipe.process(withSections);
```

`annotatedWithStanford` is a `Communication` with the output of the system
- sentences, tokenizations, entity mentions, and entities.

Running as a command-line program
---
You can run this tool as a command line program.
* Input: a path to a file on disk that is either a serialized Concrete `Communication` (ending with
`.concrete`), a `.tar` file of serialized Concrete `Communication` objects, or a `.tar.gz` file
with serialized Concrete `Communication` objects. Recall that each `Communication` must have
`Section` objects.
* Output: a path that represents the desired output directory.

### Prepare
Replace the environment variables in the code below with directories that represent your
input and output.

### TLDR
The following should be compliant in any `sh`-like shell.

```sh
export CONC_STAN_INPUT_FILE=/path/to/.concrete/or/.tar/or/.tar.gz
export CONC_STAN_OUTPUT_DIR=/path/to/output/dir
mvn clean compile assembly:single
java -cp target/concrete-stanford-4.2.1-jar-with-dependencies.jar edu.jhu.hlt.concrete.stanford.ConcreteStanfordAnnotator \
$CONC_STAN_INPUT_FILE \
$CONC_STAN_OUTPUT_DIR
```
