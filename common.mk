SHELL := /bin/bash
# Undefine MAKEOVERRIDES to prevent Spring Boot expression issues
override undefine MAKEOVERRIDES

# Fail early if GPHOME not found
ifeq ($(GPHOME),)
$(error GPHOME not defined)
endif

# Default PXF_HOME
PXF_HOME ?= $(GPHOME)/pxf
