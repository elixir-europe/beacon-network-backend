/**
 * *****************************************************************************
 * Copyright (C) 2025 ELIXIR ES, Spanish National Bioinformatics Institute (INB)
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

import es.bsc.inb.ga4gh.beacon.framework.model.v200.common.Pagination;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.requests.BeaconRequestBody;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.requests.BeaconRequestQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Dmitry Repchevsky
 */

@ApplicationScoped
public class BeaconNetworkRequestAnalyzer {
    
    @Inject
    private Jsonb jsonb;

    /**
     * Decode BeaconRequestBody from POST content
     * 
     * @param content
     * @return 
     */
    public BeaconRequestBody getBeaconRequest(byte[] content) {
        try {
            return jsonb.fromJson(new InputStreamReader(
                    new ByteArrayInputStream(content), StandardCharsets.UTF_8), 
                    BeaconRequestBody.class);
        } catch (Exception ex) {
                Logger.getLogger(BeaconNetworkRequestAnalyzer.class.getName())
                        .log(Level.INFO, "error parsing request", ex);
        }

        return null;
    }

    /**
     * Construct BeaconRequestQuery from GET parameters
     * 
     * @param request
     * @return 
     */
    public BeaconRequestQuery getRequestQuery(HttpServletRequest request) {
        final BeaconRequestQuery query = new BeaconRequestQuery();
        
        query.setIncludeResultsetResponses("ALL");
        
        final String skip = request.getParameter("skip");
        final String limit = request.getParameter("limit");
        if (skip != null || limit != null) {
            Integer int_skip;
            try {
                int_skip = Integer.valueOf(skip);
            } catch(NumberFormatException ex) {
                int_skip = null;
            }

            Integer int_limit;
            try {
                int_limit = Integer.valueOf(skip);
            } catch(NumberFormatException ex) {
                int_limit = null;
            }
            query.setPagination(new Pagination(int_skip, int_limit));
        }
        
        return query;
    }

    public byte[] getContent(HttpServletRequest request) {
        try {
            final ServletInputStream in = request.getInputStream();
            if (in != null) {
                return in.readAllBytes();
            }
        } catch(IOException ex) {}
        return new byte[0];
    }
}
