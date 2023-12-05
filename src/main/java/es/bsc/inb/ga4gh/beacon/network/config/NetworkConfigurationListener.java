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

package es.bsc.inb.ga4gh.beacon.network.config;

import static es.bsc.inb.ga4gh.beacon.network.config.NetworkConfiguration.BEACON_NETWORK_CONFIG_DIR;
import static es.bsc.inb.ga4gh.beacon.network.config.NetworkConfiguration.BEACON_NETWORK_CONFIG_DIR_PROPERTY_NAME;
import static es.bsc.inb.ga4gh.beacon.network.config.NetworkConfiguration.BEACON_NETWORK_CONFIG_FILE;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.json.bind.JsonbBuilder;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Dmitry Repchevsky
 */

@WebListener
public class NetworkConfigurationListener implements ServletContextListener {

    private String[] beacon_network_urls;
    
    private BeaconConfigFileWatcher watcher;
    private ScheduledExecutorService timer;
    
    @Inject
    private Event<NetworkConfigChangedEvent> config_changed_event;

    @PostConstruct
    public void init() {
        final String config_dir = System.getenv(BEACON_NETWORK_CONFIG_DIR_PROPERTY_NAME);
        if (config_dir != null) {
            try {
                watcher = new BeaconConfigFileWatcher(Paths.get(config_dir));
            } catch(IOException ex) {
                Logger.getLogger(NetworkConfigurationListener.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @Override
    public void contextInitialized(ServletContextEvent event) {
        try (InputStream in = event.getServletContext()
                        .getResourceAsStream(BEACON_NETWORK_CONFIG_DIR + BEACON_NETWORK_CONFIG_FILE)) {
            if (in == null) {
                Logger.getLogger(NetworkConfigurationListener.class.getName()).log(
                        Level.SEVERE, "no default beacon list file found: {0}", 
                        BEACON_NETWORK_CONFIG_DIR + BEACON_NETWORK_CONFIG_FILE);
            } else {
                beacon_network_urls = JsonbBuilder.create().fromJson(in, String[].class);
            }
        } catch (IOException ex) {
            Logger.getLogger(NetworkConfigurationListener.class.getName()).log(Level.SEVERE, null, ex);
        }

        timer = Executors.newScheduledThreadPool(2);
        final Runnable watchdog = () -> config_changed_event.fireAsync(
                new NetworkConfigChangedEvent(beacon_network_urls));

        timer.scheduleAtFixedRate(watchdog, 60, 60, TimeUnit.MINUTES);
        
        if (watcher != null) {
            timer.submit(watcher);
        } else if (beacon_network_urls != null && beacon_network_urls.length > 0) {
            config_changed_event.fireAsync(new NetworkConfigChangedEvent(beacon_network_urls));
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        timer.shutdown();
    }
    
    private void update(Path file) {
        try (InputStream in = Files.newInputStream(file, StandardOpenOption.READ)) {
            if (in != null) {
                final String[] beacons = JsonbBuilder.create().fromJson(in, String[].class);
                beacon_network_urls = beacons;
            }
        } catch (IOException ex) {
            Logger.getLogger(NetworkConfigurationListener.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        if (beacon_network_urls != null) {
            config_changed_event.fireAsync(new NetworkConfigChangedEvent(beacon_network_urls));
        }
    }        

    public class BeaconConfigFileWatcher implements Runnable {
        
        private final Path path;
        
        public BeaconConfigFileWatcher(Path path) throws IOException {
            this.path = path;
        }

        @Override
        public void run() {
            final Path file = path.resolve(BEACON_NETWORK_CONFIG_FILE);
            update(file);
            
            try (WatchService watch = FileSystems.getDefault().newWatchService()) {
                path.register(watch, ENTRY_CREATE, ENTRY_MODIFY);
                WatchKey key;
                do {
                    Kind kind = null; // the last event
                    key = watch.take();
                    for (WatchEvent event : key.pollEvents()) {
                        if (file.getFileName().equals(event.context())) {
                            kind = event.kind();
                        }
                    }

                    if (kind != null) {
                        Logger.getLogger(BeaconConfigFileWatcher.class.getName())
                                .log(Level.INFO, "{0} {1}", new Object[]{kind, file});
                    }

                    if (ENTRY_CREATE.equals(kind) || ENTRY_MODIFY.equals(kind)) {
                        update(file);
                    } else if (ENTRY_DELETE.equals(kind)) {
                        config_changed_event.fireAsync(new NetworkConfigChangedEvent(beacon_network_urls));
                    }
                } while(key.reset() && !Thread.currentThread().isInterrupted());
            } catch (IOException ex) {
                config_changed_event.fireAsync(new NetworkConfigChangedEvent(beacon_network_urls));
                Logger.getLogger(BeaconConfigFileWatcher.class.getName()).log(Level.SEVERE, null, ex);
            } catch (InterruptedException ex) {}
        }
    }
}
