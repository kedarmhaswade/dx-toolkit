DXJava: DNAnexus Java API
=========================

`DXJava` is compatible with Java 7 and higher.

[Javadocs](http://autodoc.dnanexus.com/bindings/java/current/)

[Examples of DNAnexus Platform apps written in Java](../doc/examples/dx-java-apps)

Development
-----------

### Build dependencies

* Maven (`apt-get install maven2`)

### Building

From dx-toolkit, run:

    make && make java

To create a project for Eclipse development:

    cd src/java; mvn eclipse:eclipse

### Tests

Ensure you have logged in to the platform with a valid token (for example,
using `dx login`). Then, from from dx-toolkit/src/java:

    mvn test
