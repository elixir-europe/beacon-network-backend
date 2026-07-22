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
 *****************************************************************************
 */

package es.bsc.inb.ga4gh.beacon.network.openid;

import es.bsc.inb.ga4gh.beacon.network.config.ConfigurationProperties;
import static es.bsc.inb.ga4gh.beacon.network.config.ConfigurationProperties.BN_TOKEN_AUDIENCE_API_URI_CHECK;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.URI;

/**
 * @author Dmitry Repchevsky
 */

@WebFilter(
        urlPatterns = "/*",
        asyncSupported = true,
        dispatcherTypes = {DispatcherType.REQUEST}
)
public class OIDCRequestFilter implements Filter {
    
    private OidcTokenVerifier verifier;
    
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) 
            throws IOException, ServletException {
        
        if (ConfigurationProperties.BN_TOKEN_ISSUER != null && 
            ConfigurationProperties.BN_TOKEN_AUDIENCE != null &&
            req instanceof HttpServletRequest request &&
            request.getHeader(HttpHeaders.AUTHORIZATION) != null) {
            if (verifier == null) {
                final OidcProvider provider = new OidcProvider(ConfigurationProperties.BN_TOKEN_ISSUER);
                
                // this beacon network API uri
                final String uri = BN_TOKEN_AUDIENCE_API_URI_CHECK ? UriBuilder.fromUri(URI.create(request.getRequestURL().toString()))
                        .replacePath(request.getContextPath()).path(request.getServletPath()).build().toString() : null;

                verifier = new OidcTokenVerifier(provider, uri);
            }
            final OIDCRequestWrapper wrapper = new OIDCRequestWrapper(verifier, request);
            chain.doFilter(wrapper, res);
        } else {
            chain.doFilter(req, res);
        }
    }
}
