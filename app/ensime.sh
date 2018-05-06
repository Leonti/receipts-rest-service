#!/bin/bash

#http://ensime.github.io/build_tools/sbt/

sbt clean ensimeConfig test:compile it:compile ensimeServerIndex