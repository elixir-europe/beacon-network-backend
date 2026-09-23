/**
 * *****************************************************************************
 * Copyright (C) 2026 ELIXIR ES, Spanish National Bioinformatics Institute (INB)
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
package es.bsc.inb.ga4gh.beacon.network.log;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.logging.ErrorManager;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.zip.GZIPOutputStream;

/**
 * Simple Log FileHandler with limited number of rows.
 * 
 * The log file grows until the number of rows exceeds maxRows + tailRows.
 * Then maxRows logs are compressed into another file and the current log is 
 * left with the last tailRows.
 * 
 * If the log file already exists and the number of rows exceeds maxRows + tailRows,
 * all rows before last tailRows are compressed.
 * 
 * @author Dmitry Repchevsky
 */

public class BeaconLogSplitFilesHandler extends Handler {
    
    private final Path path;
    private final FileChannel ch;
    
    private final long maxRows;  // maximum rows to keep in the storage log
    private final long tailRows; // additional rows to keep for backlog
    
    private long mark; // store position of the end of maxRows in the file
    private long rows; // current rows (records) kept in the log
    
    
    public BeaconLogSplitFilesHandler(Path path) throws IOException {
        this(path, 10000, 1000);
    }

    public BeaconLogSplitFilesHandler(Path path, long maxRows, long tailRows) 
            throws IOException {
        
        this.path = path;
        this.maxRows = maxRows;
        this.tailRows = tailRows;

        ch = FileChannel.open(path, StandardOpenOption.CREATE, 
                StandardOpenOption.READ, StandardOpenOption.WRITE);
        
        if (ch.size() > 0) {
            // count number of rows and setup the split mark
            try {
                final BufferedReader reader = new BufferedReader(
                        Channels.newReader(ch, StandardCharsets.UTF_8));
                
                long m = 0;
                for (int i; (i = reader.read()) >= 0; m++) {
                    if (i == '\n' && ++rows % maxRows == 0) {
                        mark = m + 1;
                    }
                }
            } catch(IOException ex) {
                reportError("error reading log file", ex, ErrorManager.GENERIC_FAILURE);
            }
        }
    }

    private int write(String s) throws IOException {
        final ByteBuffer buf = ByteBuffer.wrap(s.getBytes(StandardCharsets.UTF_8));
        while (buf.hasRemaining()) {
            ch.write(buf);
        }
        return buf.capacity();
    }

    @Override
    public synchronized void publish(LogRecord record) {
        if (!isLoggable(record)) {
            return;
        }
        
        if (rows == maxRows) {
            try {
                mark = ch.position();
            } catch (IOException ex) {
                reportError("error reading file position", ex, ErrorManager.GENERIC_FAILURE);
            }
        } else if (rows > maxRows && rows % maxRows >= tailRows) {
            compress();
        }
        
        try {
            final String msg = getFormatter().format(record);
            write(msg);    
        } catch (IOException ex) {
            reportError("error log file writing", ex, ErrorManager.WRITE_FAILURE);
        } catch (Exception ex) {
            reportError("error log message formatting", ex, ErrorManager.FORMAT_FAILURE);
        }
        
        rows++;
    }
    
    private void compress() {
        final String fname = path.getFileName().toString();
        
        final String gzname;
        if (fname.lastIndexOf('.') == fname.length() - 4) {
            gzname = fname.substring(0, fname.length() - 4) 
                    + System.currentTimeMillis() + ".log.gz";
        } else {
            gzname = fname + System.currentTimeMillis();
        }
        
        // compress the log [0 .. mark]
        final Path gzpath = Paths.get(path.getParent().toString(), gzname);
        try (GZIPOutputStream gzip = new GZIPOutputStream(Files.newOutputStream(
                gzpath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE));
                WritableByteChannel wbc = Channels.newChannel(gzip)) {
            for (long i, count = mark; (i = ch.transferTo(mark - count, count, wbc)) > 0; count -= i) {}
        } catch (IOException ex) {
            reportError("error compressing log file", ex, ErrorManager.WRITE_FAILURE);
        }
        
        try {
            // copy [mark .. size] (tailRows) to the file start
            ch.position(0);
            for (long i, pos = mark, len = ch.size() - mark; len > 0; i = ch.transferTo(pos, len, ch), pos += i, len -= i) {}
            
            ch.truncate(ch.size() - mark);
        } catch (IOException ex) {
            reportError("error truncating log file", ex, ErrorManager.WRITE_FAILURE);
        }
        
        mark = 0;
        rows = tailRows;
    }

    @Override
    public void flush() {
        try {
            ch.force(true);
        } catch (IOException ex) {
            reportError(null, ex, ErrorManager.FLUSH_FAILURE);
        }
    }

    @Override
    public void close() {
        try {
            ch.close();
        } catch (IOException ex) {
            reportError(null, ex, ErrorManager.CLOSE_FAILURE);
        }
    }
}
