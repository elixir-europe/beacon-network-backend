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
 *****************************************************************************
 */

package es.bsc.inb.ga4gh.beacon.network.openid;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.ws.rs.core.HttpHeaders;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.ListIterator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Dmitry Repchevsky
 */

public class OIDCRequestWrapper extends HttpServletRequestWrapper {

    private final OidcTokenVerifier verifier;
    private List<String> auths;
    
    public OIDCRequestWrapper(OidcTokenVerifier verifier, 
            HttpServletRequest request) {
        super(request);
        this.verifier = verifier;
    }

    @Override
    public String getAuthType() {
        return super.getAuthType();
    }

    @Override
    public String getHeader(String name) {
        final String header = super.getHeader(name);
        if (HttpHeaders.AUTHORIZATION.equals(name) && header.startsWith("Bearer ")) {
            final String token = header.substring(7);
            if (auths != null) {
                return auths.contains(header) ? header : null;
            }
            if (verifier.verify(token) == null) {
                return null;
            }
        }
        return header;
    }
        
    @Override
    public Enumeration<String> getHeaders(String name) {
        final Enumeration<String> headers = super.getHeaders(name);
        if (HttpHeaders.AUTHORIZATION.equals(name)) {
            if (auths == null) {
                auths = Collections.list(super.getHeaders(name));
                final ListIterator<String> iter = auths.listIterator();
                while (iter.hasNext()) {
                    final String header = iter.next();
                    if (header.startsWith("Bearer ")) {
                        final String token = header.substring(7);
                        if (verifier.verify(token) == null) {
                            iter.remove();
                            Logger.getLogger(OIDCRequestWrapper.class.getName()).log(
                                    Level.INFO, "removing invalid token ...{0}", 
                                    token.substring(Math.max(token.length()-7, 0)));
                        }
                    }
                }
            }
            return Collections.enumeration(auths);
        }
        return headers;
    }
        
    @Override
    public Enumeration getHeaderNames() {
        if (super.getHeader(HttpHeaders.AUTHORIZATION) != null) {
            final Enumeration<String> auths = getHeaders(HttpHeaders.AUTHORIZATION);
            if (!auths.hasMoreElements()) {
                // if we have no valid authorization headers - remove it
                final List<String> names = Collections.list(super.getHeaderNames());
                names.remove(HttpHeaders.AUTHORIZATION);
                return Collections.enumeration(names);
            }
        }
        return super.getHeaderNames();
    }
}