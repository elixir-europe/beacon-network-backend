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

package es.bsc.inb.ga4gh.beacon.network.info;

import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconInfoResults;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconInformationalResponseMeta;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconOrganization;
import es.bsc.inb.ga4gh.beacon.network.config.NetworkConfigUpdatedEvent;
import es.bsc.inb.ga4gh.service_info.model.v100.Organization;
import es.bsc.inb.ga4gh.service_info.model.v100.Service;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.net.URI;

/**
 * @author Dmitry Repchevsky
 */

@Singleton
public class ServiceInfoProducer {

    @Inject
    private BeaconInfoProducer beacon_info;
    
    private Service service_info;
    
    @PostConstruct
    public void init() {
        service_info = new Service();
        
        final BeaconInformationalResponseMeta meta = beacon_info.beaconInfo().getMeta();
        if (meta != null) {
            service_info.setId(meta.getBeaconId());
            service_info.setVersion(meta.getApiVersion());
        }
    }
    
    /**
     * Called when beacon network configuration has been updated.
     * 
     * @param event update event
     */
    public void onEvent(@ObservesAsync NetworkConfigUpdatedEvent event) {
        final BeaconInfoResults results = beacon_info.beaconInfo().getResponse();
        if (results != null) {
            service_info.setName(results.getName());
            final BeaconOrganization beaconOrganization = results.getOrganization();
            if (beaconOrganization != null) {
                final Organization organization = new Organization();
                organization.setId(beaconOrganization.getId());
                final String welcome_url = beaconOrganization.getWelcomeUrl();
                if (welcome_url != null) {
                    try {
                        organization.setUrl(URI.create(welcome_url));
                    } catch(IllegalArgumentException ex) {}
                }
                service_info.setOrganization(organization);
            }
        }
    }

    @Produces
    public Service serviceInfo() {
        return service_info;
    }

}
