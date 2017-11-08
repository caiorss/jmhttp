#!/usr/bin/env sh
#
# Shell script wrapper to run app during development.
# Always run $ make, before running this script.
#
jarget exec javax.jmdns/jmdns/3.4.1 -- scala bin/jmhttp.jar "$@"
