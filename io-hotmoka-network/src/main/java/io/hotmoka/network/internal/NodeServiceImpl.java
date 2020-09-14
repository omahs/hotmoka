package io.hotmoka.network.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import io.hotmoka.network.NodeServiceConfig;
import io.hotmoka.network.NodeService;
import io.hotmoka.nodes.Node;

/**
 * A simple web service that exposes some REST APIs to access an instance of a {@link io.hotmoka.nodes.Node}.
 */
public class NodeServiceImpl implements NodeService {
	private final static Logger LOGGER = LoggerFactory.getLogger(NodeServiceImpl.class);
	private final ConfigurableApplicationContext context;

	/**
	 * Yields an implementation of a network service that exposes an API to a given Hotmoka node.
	 * 
	 * @param config the configuration of the network
	 * @param node the Hotmoka node
	 */
    public NodeServiceImpl(NodeServiceConfig config, Node node) {
    	context = SpringApplication.run(Application.class, springArgumentsFor(config));
    	context.getBean(Application.class).setNode(node);
        LOGGER.info("Network server for Hotmoka node started");
    }

    @Override
    public void close() {
    	SpringApplication.exit(context);
    	LOGGER.info("Network server for Hotmoka node closed");
    }

    /**
     * Builds, from the configuration, the array of arguments required by Spring in order to start the application.
     * 
     * @param config the configuration
     * @return the array of arguments required by Spring
     */
    private static String[] springArgumentsFor(NodeServiceConfig config) {
    	return new String[] {
   			"--server.port=" + config.port,
   			"--spring.main.banner-mode=" + (config.showSpringBanner ? Banner.Mode.CONSOLE : Banner.Mode.OFF)
    	};
    }
}