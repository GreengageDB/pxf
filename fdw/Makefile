EXTENSION   = pxf_fdw
DATA        = pxf_fdw--1.0.sql
MODULE_big  = pxf_fdw
OBJS        = pxf_fdw.o pxf_bridge.o pxf_deparse.o pxf_filter.o pxf_header.o pxf_option.o libchurl.o

with_llvm = no

is_ssl_supported :=$(filter https,$(PXF_PROTOCOL))
REGRESS_SSL := $(if $(is_ssl_supported), pxf_fdw_ssl)
REGRESS     = pxf_fdw_wrapper pxf_fdw_server pxf_fdw_user_mapping pxf_fdw_foreign_table \
		$(REGRESS_SSL)
ISOLATIONCHECKS = pxf_fdw_metadata
SHLIB_LINK += -lcurl

PXF_API_VERSION := $(shell cat ../api_version)
PG_CPPFLAGS     := -DPXF_API_VERSION=\"$(PXF_API_VERSION)\"

PG_CONFIG ?= pg_config
PGXS := $(shell $(PG_CONFIG) --pgxs)
ifndef PGXS
    $(error Make sure the Greengage installation binaries are in your PATH. i.e. export PATH=<path to your Greengage installation>/bin:$$PATH)
endif
include $(PGXS)


.PHONY: stage
stage: pxf_fdw.so
	mkdir -p $(DESTDIR)build/stage/fdw
	install -c -m 755 pxf_fdw.so $(DESTDIR)build/stage/fdw/pxf_fdw.so
	install -c -m 644 pxf_fdw.control $(DESTDIR)build/stage/fdw/
	install -c -m 644 pxf_fdw--1.0.sql $(DESTDIR)build/stage/fdw/
	@echo "gpdb.version=$(GP_VERSION)" > $(DESTDIR)build/stage/fdw/metadata
	@echo "gpdb.major-version=$(GP_MAJORVERSION)" >> $(DESTDIR)build/stage/fdw/metadata

.PHONY: clean-all
clean-all: clean
	rm -rf build

.PHONY: test
test: install installcheck

installcheck: isolationcheck

isolationcheck: submake
	$(pg_isolation2_regress_installcheck) $(ISOLATIONCHECKS)