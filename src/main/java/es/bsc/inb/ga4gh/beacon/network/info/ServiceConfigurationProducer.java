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

package es.bsc.inb.ga4gh.beacon.network.info;

import es.bsc.inb.ga4gh.beacon.framework.model.v200.common.SchemaReference;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.configuration.BeaconConfiguration;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.configuration.ServiceConfiguration;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.EntryTypeDefinition;
import es.bsc.inb.ga4gh.beacon.network.config.BeaconNetworkConfiguration;
import static es.bsc.inb.ga4gh.beacon.network.config.ConfigurationProperties.BEACON_NETWORK_CONFIGURATION_FILE;
import es.bsc.inb.ga4gh.beacon.network.config.NetworkConfigUpdatedEvent;
import es.bsc.inb.ga4gh.beacon.network.config.NetworkConfiguration;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author Dmitry Repchevsky
 */

@Singleton
public class ServiceConfigurationProducer {
    
    @Inject 
    private BeaconNetworkConfiguration cfg;

    @Inject
    private BeaconInfoProducer beacon_info;
    
    @Inject
    private NetworkConfiguration network_configuration;

    private ServiceConfiguration configuration;
    private BeaconConfiguration becon_configuration;

    @PostConstruct
    public void init() {
        configuration = cfg.loadConfiguration(BEACON_NETWORK_CONFIGURATION_FILE, ServiceConfiguration.class);
        if (configuration == null) {
            configuration = new ServiceConfiguration();
        }
        becon_configuration = configuration.getResponse();
        if (becon_configuration == null) {
            configuration.setResponse(becon_configuration = new BeaconConfiguration());
        }
        configuration.setMeta(beacon_info.beaconInfo().getMeta());
    }
    
    /**
     * Called when beacon network configuration has been updated.
     * 
     * @param event update event
     */
    public void onEvent(@ObservesAsync NetworkConfigUpdatedEvent event) {
        configuration.setResponse(aggregate(becon_configuration));
    }

    /**
     * Aggregate entry types descriptions.
     * Template configuration is used to provide standard Beacon v2 schemas.
     * The template data will be filtered out on the basement of real data 
     * provided by the backed beacons.
     * 
     * @param template standard set of entries description
     * 
     * @return BeaconConfiguration to be used in the '/configuration' endpoint.
     */
    private BeaconConfiguration aggregate(BeaconConfiguration template) {
        final BeaconConfiguration aggregated = new BeaconConfiguration();
        aggregated.setEntryTypes(new HashMap());
        
        final Map<String,ServiceConfiguration> configurations = network_configuration.getConfigurations();
        for (ServiceConfiguration service_configuration : configurations.values()) {
            final BeaconConfiguration proxy = service_configuration.getResponse();
            if (proxy != null) {
                aggregateBeaconEntries(aggregated, template, proxy);
            }
        }
        
        return aggregated;
    }
    
    private void aggregateBeaconEntries(
            BeaconConfiguration aggregated,
            BeaconConfiguration template,
            BeaconConfiguration proxy) {
        
        final Map<String, EntryTypeDefinition> template_entries = template.getEntryTypes();
        final Map<String, EntryTypeDefinition> proxy_entries = proxy.getEntryTypes();
        
        if (proxy_entries != null) {
            Map<String, EntryTypeDefinition> aggregated_entries = aggregated.getEntryTypes();
            for (Map.Entry<String, EntryTypeDefinition> proxy_map_entry : proxy_entries.entrySet()) {
                final String proxy_entry_id = proxy_map_entry.getKey();
                final EntryTypeDefinition proxy_entry = proxy_map_entry.getValue();

                EntryTypeDefinition aggregated_entry = aggregated_entries.get(proxy_entry_id);
                if (aggregated_entry == null) {
                    aggregated_entry = proxy_entry;
                    if (template_entries != null) {
                        final EntryTypeDefinition template_entry = template_entries.get(proxy_entry_id);
                        if (template_entry != null) {
                            aggregated_entry = template_entry;
                        }
                    }
                    aggregated_entries.put(proxy_entry_id, aggregated_entry);
                }

                final SchemaReference proxy_schema = proxy_entry.getDefaultSchema();
                if (proxy_schema != null) {
                    addSchemaDefinition(aggregated_entry, proxy_schema);
                }
                final List<SchemaReference> schemas = proxy_entry.getAdditionallySupportedSchemas();
                if (schemas != null) {
                    for (SchemaReference schema : schemas) {
                        addSchemaDefinition(aggregated_entry, schema);
                    }
                }
            }
        }
    }

    private void addSchemaDefinition(EntryTypeDefinition target_entry, SchemaReference proxy_schema) {

        final String proxy_schema_id = proxy_schema.getId();
        final String proxy_schema_url = proxy_schema.getReferenceToSchemaDefinition();

        if (proxy_schema_id != null && 
            proxy_schema_url != null) {

            final SchemaReference target_schema = target_entry.getDefaultSchema();
            if (target_schema == null) {
                target_entry.setDefaultSchema(proxy_schema);
            } else {
                if (!(proxy_schema.getId().equals(target_schema.getId()) &&
                    Objects.equals(proxy_schema.getSchemaVersion(), target_schema.getSchemaVersion()) &&
                    proxy_schema.getReferenceToSchemaDefinition().equals(target_schema.getReferenceToSchemaDefinition()))) {

                    List<SchemaReference> schemas = target_entry.getAdditionallySupportedSchemas();
                    if (schemas == null) {
                        target_entry.setAdditionallySupportedSchemas(schemas = new ArrayList());
                    } else {
                        for (SchemaReference schema : schemas) {
                            if (proxy_schema_id.equals(schema.getId()) &&
                                Objects.equals(proxy_schema.getSchemaVersion(), schema.getSchemaVersion()) &&
                                proxy_schema_url.equals(schema.getReferenceToSchemaDefinition())) {
                                return;
                            }
                        }
                    }
                    schemas.add(proxy_schema);
                }
            }
        }
    }
    
    @Produces
    public ServiceConfiguration serviceConfiguration() {
        return configuration;
    }
}
