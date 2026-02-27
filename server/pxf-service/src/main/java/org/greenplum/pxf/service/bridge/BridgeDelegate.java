package org.greenplum.pxf.service.bridge;

import lombok.RequiredArgsConstructor;
import org.greenplum.pxf.api.io.Writable;

import java.io.DataInputStream;

@RequiredArgsConstructor
public class BridgeDelegate implements Bridge {

    private final Bridge delegate;

    @Override
    public boolean beginIteration() throws Exception {
        return delegate.beginIteration();
    }

    @Override
    public Writable getNext() throws Exception {
        return delegate.getNext();
    }

    @Override
    public boolean setNext(DataInputStream inputStream) throws Exception {
        return delegate.setNext(inputStream);
    }

    @Override
    public byte[] endIteration() throws Exception {
        return delegate.endIteration();
    }

    @Override
    public void cancelIteration() throws Exception {
        delegate.cancelIteration();
    }
}
