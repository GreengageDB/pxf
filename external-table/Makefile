EXTENSION   = pxf
DATA        = pxf--2.1.sql pxf--2.0--2.1.sql pxf--2.1--2.0.sql pxf--2.0.sql pxf--1.0--2.0.sql pxf--1.0.sql
MODULE_big  = pxf
OBJS        = src/pxfprotocol.o src/pxfbridge.o src/pxfuriparser.o src/libchurl.o src/pxfutils.o src/pxfheaders.o src/gpdbwritableformatter.o src/pxfdelimited_formatter.o src/pxffilters.o src/pxf_fdw.o src/pxf_fdw_bridge.o src/pxf_fdw_deparse.o src/pxf_fdw_header.o src/pxf_fdw_option.o
REGRESS     = setup pxf pxfinvalid
SHLIB_LINK += -lcurl

with_llvm = no

PXF_API_VERSION := $(shell cat ../api_version)
PG_CPPFLAGS     := -DPXF_API_VERSION=\"$(PXF_API_VERSION)\"

PG_CONFIG ?= pg_config
PGXS := $(shell $(PG_CONFIG) --pgxs)
ifndef PGXS
	$(error Make sure the Greengage installation binaries are in your PATH. i.e. export PATH=<path to your Greengage installation>/bin:$$PATH)
endif
# FDW sources include libpq-fe.h
PG_CPPFLAGS += -I$(shell $(PG_CONFIG) --includedir)
include $(PGXS)

.PHONY: stage
stage: pxf.so
	mkdir -p build/stage/gpextable
	install -c -m 755 pxf.so build/stage/gpextable/pxf.so
	install -c -m 644 pxf.control build/stage/gpextable/
	install -c -m 644 $(DATA) build/stage/gpextable/
	@echo "gpdb.version=$(GP_VERSION)" > build/stage/gpextable/metadata
	@echo "gpdb.major-version=$(GP_MAJORVERSION)" >> build/stage/gpextable/metadata

.PHONY: clean-all
clean-all: clean
	rm -rf build
