package io.arenadata.pxf.plugins.iceberg;

import org.apache.iceberg.Schema;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.Record;
import org.greenplum.pxf.api.OneField;
import org.greenplum.pxf.api.OneRow;
import org.greenplum.pxf.api.io.DataType;
import org.greenplum.pxf.api.model.BasePlugin;
import org.greenplum.pxf.api.model.Resolver;
import org.greenplum.pxf.api.utilities.ColumnDescriptor;

import java.util.List;
import java.util.Map;

import static io.arenadata.pxf.plugins.iceberg.converters.IcebergConverters.getIcebergConverter;
import static java.util.stream.Collectors.toMap;

public class IcebergResolver extends BasePlugin implements Resolver {

    private Map<String, DataType> greengageTypes;

    @Override
    public void afterPropertiesSet() {
        this.greengageTypes = context.getTupleDescription().stream()
                .collect(toMap(ColumnDescriptor::columnName, ColumnDescriptor::getDataType));
        super.afterPropertiesSet();
    }

    @Override
    public List<OneField> getFields(OneRow oneRow) {
        Schema schema = (Schema) context.getMetadata();
        Record icebergRecord = (Record) oneRow.getData();
        return schema.columns().stream()
                .map(column -> new OneField(
                        greengageTypes.get(column.name()).getOID(),
                        getIcebergConverter(
                                greengageTypes.get(column.name()),
                                column.type()
                        ).convertFromIcebergToGreengage(icebergRecord.getField(column.name()))
                )).toList();
    }

    @Override
    public OneRow setFields(List<OneField> list) {
        Schema schema = (Schema) context.getMetadata();
        Record icebergRecord = GenericRecord.create(schema);

        for (int i = 0; i < list.size(); i++) {
            OneField field = list.get(i);
            ColumnDescriptor descriptor = context.getColumn(i);
            icebergRecord.setField(
                descriptor.columnName(),
                getIcebergConverter(
                        descriptor.getDataType(),
                        schema.findType(descriptor.columnName())
                ).convertFromGreengageToIceberg(field.val)
            );
        }
        return new OneRow(icebergRecord);
    }

}
