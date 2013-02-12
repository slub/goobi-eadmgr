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

import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import java.util.Map;

public class GoobiMQConnection {

	private Connection connection;
	private Session session;
	private MessageProducer producer;

	private Logger logger = LoggerFactory.getLogger(GoobiMQConnection.class);

	public GoobiMQConnection(String brokerUrl, String subjectQueue) throws JMSException {
		initActiveMqConnection(brokerUrl, subjectQueue);
	}

	private void initActiveMqConnection(String brokerUrl, String subjectQueue) throws JMSException {
		logger.trace("Initialize ActiveMQ connection to {}.", brokerUrl);
		logger.trace("Using queue {}.", subjectQueue);

		ConnectionFactory connectionFactory = new ActiveMQConnectionFactory(brokerUrl);
		connection = connectionFactory.createConnection();
		connection.start();

		logger.trace("Connection established. Now creating session.");

		session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
		Destination destination = session.createQueue(subjectQueue);
		producer = session.createProducer(destination);
	}

	public void close() throws JMSException {
		logger.trace("Closing ActiveMQ connection.");
		if (connection != null) {
			connection.close();
		}
	}

	public void send(Map<String, Object> message) throws JMSException {
		logger.trace("Sending ActiveMQ MapMessage {}.", message);
		MapMessage mapMessage = session.createMapMessage();
		for (String key : message.keySet()) {
			mapMessage.setObject(key, message.get(key));
		}
		producer.send(mapMessage);
	}

}
