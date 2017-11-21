package com.redhat.rhforum;

import org.junit.runner.RunWith;
import org.jboss.arquillian.junit.Arquillian;
import org.wildfly.swarm.arquillian.DefaultDeployment;
import org.junit.Test;

@RunWith(Arquillian.class)
@DefaultDeployment
public class ClienteTest {

	@Test
	public void should_start_service() {
	}
}