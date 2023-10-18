/**
 * *****************************************************************************
 * Copyright (C) 2023 ELIXIR ES, Spanish National Bioinformatics Institute (INB)
 * and Barcelona Supercomputing Center (BSC)
 *
 * Modifications to the initial code base are copyright of their respective
 * authors, or their employers as appropriate.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 * *****************************************************************************
 */

package es.bsc.inb.ga4gh.beacon.network.engine;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Caching InputStream that keeps read data.
 * 
 * @author Dmitry Repchevsky
 */

public class DynamicBufferedInputStream extends FilterInputStream {
    
    public int pos;
    public byte[] buf;
    
    public DynamicBufferedInputStream(InputStream in) {
        super(in);
        
        buf = new byte[1024];
    }
    
    @Override
    public int read() throws IOException {
        if (pos >= buf.length) {
            buf = Arrays.copyOf(buf, buf.length * 2);
        }
        int b = in.read();
        buf[pos++] = (byte)b;
        return b;
    }

    @Override
    public int read(byte b[]) throws IOException {
        if (pos + b.length >= buf.length) {
            buf = Arrays.copyOf(buf, Math.max(buf.length * 2, pos + b.length));
        }
        final int n = read(b, 0, b.length);
        if (n > 0) {
            System.arraycopy(b, 0, buf, pos, n);
            pos += n;
        }
        return n;
    }
    
    @Override
    public int read(byte b[], int off, int len) throws IOException {
        if (pos + len >= buf.length) {
            buf = Arrays.copyOf(buf, Math.max(buf.length * 2, pos + len));
        }
        final int n = in.read(b, off, len);
        if (n > 0) {
            System.arraycopy(b, off, buf, pos, n);
            pos += n;
        }
        return n;
    }
    
    @Override
    public long skip(long n) throws IOException {
        if (pos + n >= buf.length) {
            buf = Arrays.copyOf(buf, Math.max(buf.length * 2, pos + (int)n));
        }
        final int s = read(buf, pos, (int) n);
        if (s > 0) {
            pos += s;
        }
        return s;
    }
    
    @Override
    public void close() throws IOException {
        super.readAllBytes();
        in.close();
    }
}
