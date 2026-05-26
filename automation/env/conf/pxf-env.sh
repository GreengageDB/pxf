#!/bin/bash

##############################################################################
# This file contains PXF properties that can be specified by users           #
# to customize their deployments. This file is sourced by PXF Server control #
# scripts upon initialization, start and stop of the PXF Server.             #
#                                                                            #
# To update a property, uncomment the line and provide a new value.          #
##############################################################################

# Memory
export PXF_JVM_OPTS="-Xmx1g -Xms1g -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:8000"
