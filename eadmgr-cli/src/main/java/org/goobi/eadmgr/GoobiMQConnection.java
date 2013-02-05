/*
 * This file is part of the Goobi Application - a Workflow tool for the support of
 * mass digitization.
 *
 * Visit the websites for more information.
 *     - http://gdz.sub.uni-goettingen.de
 *     - http://www.goobi.org
 *     - http://launchpad.net/goobi-production
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details. You
 * should have received a copy of the GNU General Public License along with this
 * program; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.goobi.eadmgr;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;
import java.util.Map;

public class GoobiMQConnection {

	// URL of the JMS server. DEFAULT_BROKER_URL will just mean
	// that JMS server is on localhost
	private static String defaultBrokerUrl = ActiveMQConnection.DEFAULT_BROKER_URL;
	// Name of the queue we will be sending messages to
	private static String subject = "GoobiProduction.createNewProcessWithLogicalStructureData.Queue";
	private Connection connection;
	private Session session;
	private MessageProducer producer;

	public GoobiMQConnection() throws JMSException {
		initActiveMqConnection(defaultBrokerUrl);
	}

	private void initActiveMqConnection(String brokerUrl) throws JMSException {
		ConnectionFactory connectionFactory = new ActiveMQConnectionFactory(brokerUrl);
		connection = connectionFactory.createConnection();
		connection.start();

		session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
		Destination destination = session.createQueue(subject);
		producer = session.createProducer(destination);
	}

	public void close() throws JMSException {
		if (connection != null) {
			connection.close();
		}
	}

	public void send(Map<String, Object> message) throws JMSException {

		MapMessage mapMessage = session.createMapMessage();

		for (String key : message.keySet()) {
			mapMessage.setObject(key, message.get(key));
		}

		producer.send(mapMessage);
	}

}
