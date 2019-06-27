#!/bin/bash

$JAVA_HOME/bin/js \
    --jvm \
    --vm.Dtruffle.class.path.append=target/simpletool-19.0.2-SNAPSHOT.jar \
    --simple-code-coverage \
    src/test/java/com/oracle/simpletool/test/test.js
