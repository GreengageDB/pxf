EXTENSION   = pxf_fdw
DATA        = pxf_fdw--1.0.sql

# pxf_fdw.so is a symlink to pxf.so built in external-table, which contains the FDW code

is_ssl_supported :=$(filter https,$(PXF_PROTOCOL))
REGRESS_COMMON = pxf_fdw_wrapper pxf_fdw_server pxf_fdw_user_mapping pxf_fdw_foreign_table pxf_fdw_metadata
REGRESS_HTTP   = pxf_fdw_http
REGRESS_HTTPS  := $(if $(is_ssl_supported), pxf_fdw_ssl)
REGRESS        = $(REGRESS_COMMON) $(REGRESS_HTTP) $(REGRESS_HTTPS)

PG_CONFIG ?= pg_config
PGXS := $(shell $(PG_CONFIG) --pgxs)
ifndef PGXS
    $(error Make sure the Greengage installation binaries are in your PATH. i.e. export PATH=<path to your Greengage installation>/bin:$$PATH)
endif
include $(PGXS)

PXF_EXT_TABLE_DIR := ../external-table
PXF_SHLIB         := pxf$(DLSUFFIX)
PXF_FDW_SHLIB     := pxf_fdw$(DLSUFFIX)

EXTRA_CLEAN = $(PXF_FDW_SHLIB)

all: $(PXF_FDW_SHLIB)

.PHONY: $(PXF_EXT_TABLE_DIR)/$(PXF_SHLIB)
$(PXF_EXT_TABLE_DIR)/$(PXF_SHLIB):
	$(MAKE) -C $(PXF_EXT_TABLE_DIR) $(PXF_SHLIB)

$(PXF_FDW_SHLIB): $(PXF_EXT_TABLE_DIR)/$(PXF_SHLIB)
	ln -sf $< $@

# install pxf.so from external-table too, so the symlink is never dangling
install: install-shlib-link
.PHONY: install-shlib-link
install-shlib-link:
	$(MAKE) -C $(PXF_EXT_TABLE_DIR) install
	$(MKDIR_P) '$(DESTDIR)$(pkglibdir)'
	ln -sf $(PXF_SHLIB) '$(DESTDIR)$(pkglibdir)/$(PXF_FDW_SHLIB)'

uninstall: uninstall-shlib-link
.PHONY: uninstall-shlib-link
uninstall-shlib-link:
	rm -f '$(DESTDIR)$(pkglibdir)/$(PXF_FDW_SHLIB)'

# pxf.so itself is staged into ../gpextable by external-table
.PHONY: stage
stage: $(PXF_FDW_SHLIB)
	mkdir -p $(DESTDIR)build/stage/fdw
	ln -sf ../gpextable/$(PXF_SHLIB) $(DESTDIR)build/stage/fdw/$(PXF_FDW_SHLIB)
	install -c -m 644 pxf_fdw.control $(DESTDIR)build/stage/fdw/
	install -c -m 644 pxf_fdw--1.0.sql $(DESTDIR)build/stage/fdw/
	@echo "gpdb.version=$(GP_VERSION)" > $(DESTDIR)build/stage/fdw/metadata
	@echo "gpdb.major-version=$(GP_MAJORVERSION)" >> $(DESTDIR)build/stage/fdw/metadata

.PHONY: clean-all
clean-all: clean
	rm -rf build

.PHONY: test
test: install installcheck

.PHONY: installcheck-http
installcheck-http:
	$(MAKE) installcheck REGRESS="$(REGRESS_COMMON) $(REGRESS_HTTP)"

.PHONY: installcheck-https
installcheck-https:
	$(MAKE) installcheck REGRESS="$(REGRESS_COMMON) $(REGRESS_HTTPS)"

installcheck:

ISOLATION2_ROOT=$(pgxsdir)/src/test/isolation2

isolationcheck: submake
	make ISOLATION2_ROOT=$(ISOLATION2_ROOT) -C isolation2 isolationcheck
