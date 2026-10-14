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


export GP_MAJORVERSION


default: all

.PHONY: all extensions external-table fdw cli server install install-server clean test it help

all: extensions cli server
	@echo "===> PXF compilation is complete <==="

extensions: external-table fdw

external-table cli server:
	@echo "===> Compiling [$@] module <==="
	make -C $@

fdw:
	@echo "===> Compiling [$@] module <==="
	make -C fdw

clean:
	rm -rf build
	set -e ;\
	for module in $${PXF_MODULES[@]}; do \
		echo "===> Cleaning [$${module}] module <===" ;\
		make -C $${module} clean-all ;\
	done ;\
	echo "===> PXF cleaning is complete <==="

test:
	make -C cli test
	make -C server test

it:
	make -C automation TEST=$(TEST)

install:
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

# Default target
pkg: pkg-deb
pkg-deb: pkg-deb-all

# List of packages: "all" generates the main target (pkg-deb-all)
PACKAGES := all pxf-server pxf-cli greengage$(GP_MAJORVERSION)-pxf-fdw

# Template to generate a target for each package
define PKG_template
pkg-deb-$1: $(DEB_PREREQS)
	@echo "Building $1 package"
	@$(DEBUILD_ENV) $(if $(filter all,$(1)),,DH_OPTIONS="-p$(1)") $(DEBUILD_CMD)
	@$(MAKE) _collect-artifacts
endef

# Generate targets for all entries in PACKAGES
$(foreach pkg,$(PACKAGES),$(eval $(call PKG_template,$(pkg))))

_collect-artifacts:
	@mkdir -p $(ARTIFACTS_DIR)
	@find $(CURDIR)/../ -maxdepth 1 -type f \( -name "*.deb" \
	                                        -o -name "*.ddeb" \
	                                        -o -name "*.build" \
	                                        -o -name "*.buildinfo" \
	                                        -o -name "*.changes" \) \
	                                        -exec mv -f {} $(ARTIFACTS_DIR)/ \;

.PHONY: pkg pkg-deb pkg-deb-all pkg-deb-pxf-server pkg-deb-pxf-cli pkg-deb-greengage$(GP_MAJORVERSION)-pxf-fdw \
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
	@echo	'  - pkg-deb - create PXF DEB packages'
