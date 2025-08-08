package io.github.stardomains3.oxproxion

import io.noties.prism4j.annotations.PrismBundle

@PrismBundle(include = ["c", "clike", "clojure", "cpp", "csharp", "css", "dart", "git", "go", "groovy", "java", "javascript", "json", "kotlin", "latex", "makefile", "markdown", "markup", "python", "scala", "sql", "swift", "yaml"],
    grammarLocatorClassName = ".ExampleGrammarLocator")
class GrammarLocator { }