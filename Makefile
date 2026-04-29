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

PACKAGE_NAME := $(shell grep '^Source:' debian/control.in | awk '{print $$2}')
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

install-ext: install-fdw

install-server:
	make -C server install-server DESTDIR=$(DESTDIR) GPHOME=$(GPHOME) PXF_HOME=$(PXF_HOME)

install-cli:
	make -C cli install DESTDIR=$(DESTDIR) GPHOME=$(GPHOME) PXF_HOME=$(PXF_HOME)

install-fdw:
	make -C fdw install DESTDIR=$(DESTDIR) GPHOME=$(GPHOME) PXF_HOME=$(PXF_HOME)
	make -C external-table install DESTDIR=$(DESTDIR) GPHOME=$(GPHOME) PXF_HOME=$(PXF_HOME)

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
DATE_RFC := $(shell date -R)
ARTIFACTS_DIR := $(CURDIR)/./Package

./version :
# 	@echo "Update $@"
# 	./getversion > $@
	@cat $@
version-vars: ./version
	$(eval FULL_VERSION    := $(shell perl -pe 's, ,-,g' ./version))
	$(eval PACKAGE_VERSION := $(shell perl -pe 's, .*,,g; s/-SNAPSHOT/~snapshot/' ./version))
	$(eval DISTRO_CODENAME := $(shell lsb_release -sc))
	$(eval IS_RELEASE      := $(if $(findstring ~snapshot,$(PACKAGE_VERSION)),no,yes))
	$(eval STABILITY       := $(if $(filter yes,$(IS_RELEASE)),stable,unstable))
	$(eval BUILD_TYPE      := $(if $(filter yes,$(IS_RELEASE)),Release build,Development build))

version-info : version-vars
	@echo "PACKAGE_VERSION: $(PACKAGE_VERSION)"
	@echo "FULL_VERSION: $(FULL_VERSION)"
	@echo "DISTRO_CODENAME: $(DISTRO_CODENAME)"
	@echo "IS_RELEASE: $(IS_RELEASE)"
	@echo "STABILITY: $(STABILITY)"
	@echo "BUILD_TYPE: $(BUILD_TYPE)"

# Generate control file
debian/control: debian/control.in
	@echo "=== Generating debian/control for GP$(GP_MAJORVERSION) ==="
	sed 's|@GP_MAJORVERSION@|$(GP_MAJORVERSION)|g' $< > $@

# Generate package control files
changelog : debian/changelog
debian/changelog: version-vars debian/control
	$(eval PACKAGE_NAME := $(shell grep '^Source:' debian/control | awk '{print $$2}'))
	$(eval MAINTAINER   := $(shell grep '^Maintainer:' debian/control | sed 's/Maintainer: //'))
	@echo "$(PACKAGE_NAME) ($(PACKAGE_VERSION)) $(DISTRO_CODENAME); urgency=low" > $@
	@echo "" >> $@
	@echo "  * $(BUILD_TYPE)" >> $@
	@echo "" >> $@
	@echo " -- $(MAINTAINER)  $(DATE_RFC)" >> $@

DEB_PREREQS  := debian/changelog debian/control
DEBUILD_ENV  := GPHOME="$(GPHOME)" PXF_HOME="$(PXF_HOME)" GP_MAJORVERSION="$(GP_MAJORVERSION)"
DEBUILD_CMD  := debuild --preserve-env -us -uc -b

# $(1) — human-readable name, $(2) — DH_OPTIONS package name (empty = build all)
define debuild-pkg
	@echo "Building $(1) package"
	@$(DEBUILD_ENV) $(if $(2),DH_OPTIONS="-p$(2)") $(DEBUILD_CMD)
	@$(MAKE) _collect-artifacts
endef

# Default packaging target
pkg: pkg-deb

# Build Debian package
pkg-deb: $(DEB_PREREQS)
	$(call debuild-pkg,pxf (all))

pkg-deb-server: $(DEB_PREREQS)
	$(call debuild-pkg,pxf-server,pxf-server)

pkg-deb-cli: $(DEB_PREREQS)
	$(call debuild-pkg,pxf-cli,pxf-cli)

pkg-deb-fdw: $(DEB_PREREQS)
	$(call debuild-pkg,pxf-fdw$(GP_MAJORVERSION),pxf-fdw$(GP_MAJORVERSION))

pkg-deb-ext: pkg-deb-fdw

_collect-artifacts:
	@mkdir -p $(ARTIFACTS_DIR)
	@find $(CURDIR)/../ -maxdepth 1 -type f \( -name "*.deb" \
	                                        -o -name "*.ddeb" \
	                                        -o -name "*.build" \
	                                        -o -name "*.buildinfo" \
	                                        -o -name "*.changes" \) \
	                                        -exec mv -f {} $(ARTIFACTS_DIR)/ \;

.PHONY: pkg pkg-deb pkg-deb-server pkg-deb-cli pkg-deb-fdw pkg-deb-ext \
        build-server build-ext install-server-pkg install-ext \
        _collect-artifacts changelog version-vars version-info


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
