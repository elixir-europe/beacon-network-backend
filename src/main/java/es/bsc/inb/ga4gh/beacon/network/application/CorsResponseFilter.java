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

package es.bsc.inb.ga4gh.beacon.network.application;

import java.io.IOException;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

/**
 * @author Dmitry Repchevsky
 */

@WebFilter(
        urlPatterns = "/*",
        asyncSupported = true,
        dispatcherTypes = {DispatcherType.REQUEST}
)
public class CorsResponseFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        
        if (res instanceof HttpServletResponse) {
            String origin = null;
            if (req instanceof HttpServletRequest) {
                final HttpServletRequest http_req = (HttpServletRequest)req;
                origin = http_req.getHeader("Origin");
                if (origin == null) {
                    origin = http_req.getHeader("Referer");
                }
            }
            final HttpServletResponseWrapper wrapper = new HttpServletResponseWrapper((HttpServletResponse)res);
            if (origin == null) {
                origin = "*";
            }

            wrapper.setHeader("Access-Control-Allow-Origin", origin);
            wrapper.setHeader("Access-Control-Allow-Credentials", "true");
            wrapper.setHeader("Access-Control-Request-Private-Network", "true");
            wrapper.setHeader("Access-Control-Allow-Private-Network", "true");
            wrapper.setHeader("Access-Control-Allow-Methods", "GET, HEAD, POST, DELETE, PUT, PATCH, OPTIONS");
            wrapper.setHeader("Access-Control-Allow-Headers", "Access-Control-Allow-Headers, Authorization, Referer, Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");
            chain.doFilter(req, wrapper);
        } else {
            chain.doFilter(req, res);
        }
    }
}
