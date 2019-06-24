#!/bin/bash

$JAVA_HOME/bin/js \
    --jvm \
    --vm.Dtruffle.class.path.append=target/simpletool-1.0-SNAPSHOT.jar \
    --experimental-options \
    --simple-code-coverage.Enable=true \
    src/test/java/com/oracle/simpletool/test/test.js
