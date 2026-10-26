SHELL := /bin/bash
# Undefine MAKEOVERRIDES to prevent Spring Boot expression issues
override undefine MAKEOVERRIDES

# Default PXF_HOME
PXF_HOME ?= $(GPHOME)/pxf
