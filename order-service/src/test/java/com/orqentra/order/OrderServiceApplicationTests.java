package com.orqentra.order;

import org.junit.jupiter.api.Test;

import com.orqentra.order.support.AbstractIntegrationTest;

/**
 * The whole context wires up against real infrastructure: every bean definition
 * resolves, every @ConfigurationProperties binds, Flyway's migrations apply cleanly.
 * More specific tests exercise individual behaviours; this just proves nothing is
 * structurally broken.
 */
class OrderServiceApplicationTests extends AbstractIntegrationTest {

	@Test
	void contextLoads() {
	}

}
