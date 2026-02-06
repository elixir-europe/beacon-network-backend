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

package es.bsc.inb.ga4gh.beacon.network.config;

import static es.bsc.inb.ga4gh.beacon.network.config.ConfigurationProperties.BEACON_NETWORK_CONFIG_DIR;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.json.bind.JsonbBuilder;
import jakarta.servlet.ServletContext;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Dmitry Repchevsky
 */

@Singleton
public class BeaconNetworkConfiguration {

    @Inject 
    private ServletContext ctx;

    public <T> T loadConfiguration(String file, Class<T> clazz) {
        T bean = null;
        if (ConfigurationProperties.BN_CONFIG_DIR_PROPERTY != null) {
            final Path path = Paths.get(ConfigurationProperties.BN_CONFIG_DIR_PROPERTY, file);
            if (Files.exists(path)) {
                synchronized(BeaconNetworkConfiguration.class) {
                    try(InputStream in = Files.newInputStream(path, StandardOpenOption.READ)) {
                        bean = JsonbBuilder.create().fromJson(in, clazz);
                    } catch (NoSuchFileException ex) {
                    } catch (Exception ex) {
                        Logger.getLogger(BeaconNetworkConfiguration.class.getName()).log(Level.WARNING, null, ex);
                    }
                }
            }
        }

        if (bean == null) {
            synchronized(BeaconNetworkConfiguration.class) {
                if (bean == null) {
                    try(InputStream in = ctx.getResourceAsStream(BEACON_NETWORK_CONFIG_DIR + file)) {
                        if (in == null) {
                            Logger.getLogger(BeaconNetworkConfiguration.class.getName()).log(
                                    Level.INFO, "no file found: {0}", BEACON_NETWORK_CONFIG_DIR + file);
                        } else {
                            bean = JsonbBuilder.create().fromJson(in, clazz);
                        }
                    } catch (Exception ex) {
                        Logger.getLogger(BeaconNetworkConfiguration.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        }
        
        return bean;
    }
}
