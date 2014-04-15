# Ivy Maven Plugin

A plugin to add apache Ivy dependencies to a Maven project. This is a fork of [Evgeny Goldin's Ivy Maven Plugin](https://github.com/evgeny-goldin/maven-plugins) that fixes support for transitive dependencies.

## Configuration

The *ivy* goal can be configured with

- *settings* a String file path or URL that points to an Ivy settings (xml) file.
- *transitive* a boolean (default false), whether to add the transitive dependencies of the configured dependencies too.
- *scope* a String (default "compile") to define what Maven scope to add the dependencies to.
- *dependencies* a list of dependency elements, in the same format as the [Maven Dependency Plugin](http://maven.apache.org/plugins/maven-dependency-plugin/copy-mojo.html#artifactItems)

The fields of each dependency element correspond to their Ivy equivalent as follows:

- *groupId* is an Ivy module *organisation*
- *artifactId* is an Ivy module *name*
- *version* is an Ivy module *revision*
- *type* is the Ivy artifact *type* and *ext*
- *classifier* is a String or regex that matches an Ivy artifact's name.
- *baseVersion* is the Ivy *configuration* to use, defaulting to "default" if not given

The plugin can only refer to Ivy artifacts from the *default* configuration of each Ivy dependency.

## Examples 

An example of what to put in your pom.xml:

<pre>
&lt;plugin&gt;
  &lt;groupId&gt;com.github.remis-thoughts&lt;/groupId&gt;
  &lt;artifactId&gt;ivy-maven-plugin&lt;/artifactId&gt;
  &lt;version&gt;1.0.2&lt;/version&gt;
  &lt;executions&gt;
    &lt;execution&gt;
      &lt;id&gt;add-dependencies&lt;/id&gt;
      &lt;phase&gt;initialize&lt;/phase&gt;
      &lt;goals&gt;
        &lt;goal&gt;ivy&lt;/goal&gt;
      &lt;/goals&gt;
      &lt;configuration&gt;
        &lt;settings&gt;ivy-settings.xml&lt;/settings&gt;
        &lt;dependencies&gt;
          &lt;dependency&gt;
            &lt;groupId&gt;commons-lang&lt;/groupId&gt;
            &lt;artifactId&gt;commons-lang&lt;/artifactId&gt;
            &lt;version&gt;2.6&lt;/version&gt;
          &lt;/dependency&gt;
        &lt;/dependencies&gt;
        &lt;transitive&gt;false&lt;/transitive&gt;
      &lt;/configuration&gt;
    &lt;/execution&gt;
  &lt;/executions&gt;
&lt;/plugin&gt;
</pre>
