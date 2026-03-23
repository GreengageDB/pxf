include common.mk

PXF_MODULES = external-table fdw cli server
export PXF_MODULES

PXF_VERSION ?= $(shell cat version)
export PXF_VERSION

PG_CONFIG ?= pg_config
PGXS := $(shell $(PG_CONFIG) --pgxs)
ifndef PGXS
	$(error Make sure the Greengage installation binaries are in your PATH. i.e. export PATH=<path to your Greengage installation>/bin:$$PATH)
endif
include $(PGXS)

# variables that control whether the FDW extension will be built and packaged,
# if left empty there is no skipping, otherwise a value should contain a reason for skipping
ifeq ($(shell test $(GP_MAJORVERSION) -lt 6; echo $$?),0)
	SKIP_FDW_BUILD_REASON := "GPDB version $(GP_MAJORVERSION) is less than 6."
endif
ifeq ($(shell test $(GP_MAJORVERSION) -lt 7; echo $$?),0)
	SKIP_FDW_PACKAGE_REASON := "GPDB version $(GP_MAJORVERSION) is less than 7."
endif

ifeq ($(BLD_ARCH),)
	GP_BUILD_ARCH := $(PORTNAME)-$(subst _,-,$(host_cpu))
else
	GP_BUILD_ARCH := $(subst _,-,$(BLD_ARCH))
endif

export SKIP_FDW_BUILD_REASON
export SKIP_FDW_PACKAGE_REASON
export GP_MAJORVERSION
export GP_BUILD_ARCH

PACKAGE_NAME := $(shell grep '^Source:' debian/control | awk '{print $$2}')
PXF_PACKAGE_NAME := $(PACKAGE_NAME)-$(PXF_VERSION)-$(GP_BUILD_ARCH)
export PXF_PACKAGE_NAME

LICENSE ?= ASL 2.0
VENDOR ?= Open Source

default: all

.PHONY: all extensions external-table fdw cli server install install-server stage tar deb deb-tar clean test it help

all: extensions cli server
	@echo "===> PXF compilation is complete <==="

extensions: external-table fdw

external-table cli server:
	@echo "===> Compiling [$@] module <==="
	make -C $@

fdw:
ifeq ($(SKIP_FDW_BUILD_REASON),)
	@echo "===> Compiling [$@] module <==="
	make -C fdw
else
	@echo "Skipping building FDW extension because $(SKIP_FDW_BUILD_REASON)"
endif

clean:
	rm -rf build
	set -e ;\
	for module in $${PXF_MODULES[@]}; do \
		echo "===> Cleaning [$${module}] module <===" ;\
		make -C $${module} clean-all ;\
	done ;\
	echo "===> PXF cleaning is complete <==="

test:
ifeq ($(SKIP_FDW_BUILD_REASON),)
	make -C fdw installcheck
else
	@echo "Skipping testing FDW extension because $(SKIP_FDW_BUILD_REASON)"
endif
	make -C cli test
	make -C server test

it:
	make -C automation TEST=$(TEST)

install:
ifneq ($(SKIP_FDW_BUILD_REASON),)
	@echo "Skipping installing FDW extension because $(SKIP_FDW_BUILD_REASON)"
	$(eval PXF_MODULES := $(filter-out fdw,$(PXF_MODULES)))
endif
	set -e ;\
	for module in $${PXF_MODULES[@]}; do \
		echo "===> Installing [$${module}] module <===" ;\
		make -C $${module} install DESTDIR=$(DESTDIR) GPHOME=$(GPHOME) PXF_HOME=$(PXF_HOME) ;\
	done ;\
	echo "===> PXF installation is complete <==="

install-server:
	make -C server install-server DESTDIR=$(DESTDIR) GPHOME=$(GPHOME) PXF_HOME=$(PXF_HOME)

stage:
	rm -rf build/stage
ifneq ($(SKIP_FDW_PACKAGE_REASON),)
	@echo "Skipping staging FDW extension because $(SKIP_FDW_PACKAGE_REASON)"
	$(eval PXF_MODULES := $(filter-out fdw,$(PXF_MODULES)))
endif
	set -e ;\
	mkdir -p build/stage/$${PXF_PACKAGE_NAME}/pxf ;\
	for module in $${PXF_MODULES[@]}; do \
		echo "===> Staging [$${module}] module <===" ;\
		make -C $${module} stage  DESTDIR=$(DESTDIR) GPHOME=$(GPHOME) PXF_HOME=$(PXF_HOME) ;\
		cp -a "$${module}"/build/stage/* "build/stage/$${PXF_PACKAGE_NAME}/pxf" ;\
	done ;\
	echo $$(git rev-parse --verify HEAD) > build/stage/$${PXF_PACKAGE_NAME}/pxf/commit.sha ;\
	cp package/install_binary build/stage/$${PXF_PACKAGE_NAME}/install_component ;\
	echo "===> PXF staging is complete <==="

#---------------------------------------------------------------------
# Packaging targets with changelog options
#---------------------------------------------------------------------

# Metadata vars
PACKAGE_NAME := $(shell grep '^Source:' debian/control | head -1 | awk '{print $$2}')
MAINTAINER := $(shell grep '^Maintainer:' debian/control | sed 's/Maintainer: //')
DATE_RFC := $(shell date -R)
ARTIFACTS_DIR := $(CURDIR)/./Package

./version :
# 	@echo "Update $@"
# 	./getversion > $@
	@cat $@

version-vars : ./version
	$(eval FULL_VERSION := $(shell [ -f ./version ] && perl -pe 's, ,-,g' ./version))
	$(eval PACKAGE_VERSION := $(shell [ -f ./version ] && perl -pe 's, .*,,g' ./version))
	$(eval IS_RELEASE := $(if $(findstring +dev,$(PACKAGE_VERSION)),no,yes))
	$(eval STABILITY := $(if $(filter yes,$(IS_RELEASE)),stable,unstable))
	$(eval BUILD_TYPE := $(if $(filter yes,$(IS_RELEASE)),Release build,Development build))

version-info : version-vars
	@echo "PACKAGE_VERSION: $(PACKAGE_VERSION)"
	@echo "FULL_VERSION: $(FULL_VERSION)"
	@echo "IS_RELEASE: $(IS_RELEASE)"
	@echo "STABILITY: $(STABILITY)"
	@echo "BUILD_TYPE: $(BUILD_TYPE)"

# Generate package control files
changelog : debian/changelog
debian/changelog : version-vars
	@echo "$(PACKAGE_NAME) ($(PACKAGE_VERSION)) $(STABILITY); urgency=low" > $@
	@echo "" >> $@
	@echo "  * $(BUILD_TYPE)" >> $@
	@echo "" >> $@
	@echo " -- $(MAINTAINER)  $(DATE_RFC)" >> $@

# Default packaging target
pkg : pkg-deb

# Build Debian package
pkg-deb : debian/changelog
	@echo "Building with GPHOME=$(GPHOME) PXF_HOME=$(PXF_HOME), PACKAGE_NAME=$(PACKAGE_NAME)"
	@GPHOME="$(GPHOME)" PXF_HOME="$(PXF_HOME)" debuild --preserve-env -us -uc -b
	@mkdir -p $(ARTIFACTS_DIR)
	@find $(CURDIR)/../ -maxdepth 1 -type f \( -name "*.deb" \
											-o -name "*.ddeb" \
											-o -name "*.build" \
											-o -name "*.buildinfo" \
											-o -name "*.changes" \) \
											-exec mv -f {} $(ARTIFACTS_DIR)/ \;

.PHONY: pkg pkg-deb changelog version-vars version-info


help:
	@echo
	@echo 'Possible targets'
	@echo	'  - all - build extensions, cli, and server modules'
	@echo	'  - extensions - build Greengage external table and foreign data wrapper extensions'
	@echo	'  - external-table - build Greengage external table extension'
	@echo	'  - fdw - build Greengage foreign data wrapper extension'
	@echo	'  - cli - install Go CLI dependencies and build Go CLI'
	@echo	'  - server - install server dependencies and build server module'
	@echo	'  - clean - clean up external-table, fdw, CLI and server binaries'
	@echo	'  - test - runs tests for Go CLI and server'
	@echo	'  - install - install external table and foreign data wrapper extensions, CLI and server binaries'
	@echo	'  - install-server - install server binaries only without running tests'
	@echo	'  - stage - install external table and foreign data wrapper extensions, CLI, and server binaries into build/stage/pxf directory'
	@echo	'  - deb - create PXF DEB package'
