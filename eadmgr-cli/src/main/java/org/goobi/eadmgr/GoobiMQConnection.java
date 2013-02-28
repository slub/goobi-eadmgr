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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import static java.lang.Thread.sleep;

public class GoobiMQConnection {

	private Connection connection;
	private Session session;
	private MessageProducer producer;
	private MessageConsumer consumer;
	private SynchronousMessageListener listener;
	private Logger logger = LoggerFactory.getLogger(GoobiMQConnection.class);

	public GoobiMQConnection(String brokerUrl, String subjectQueue, String resultTopic) throws JMSException {
		initActiveMqConnection(brokerUrl, subjectQueue, resultTopic);
	}

	private void initActiveMqConnection(String brokerUrl, String subjectQueue, String resultTopic) throws JMSException {
		logger.trace("Initialize ActiveMQ connection to {}.", brokerUrl);
		logger.trace("Using queue {}.", subjectQueue);

		ConnectionFactory connectionFactory = new ActiveMQConnectionFactory(brokerUrl);
		connection = connectionFactory.createConnection();
		connection.start();

		logger.trace("Connection established. Now creating session.");

		session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
		producer = session.createProducer(session.createQueue(subjectQueue));

		consumer = session.createConsumer(session.createTopic(resultTopic));
		listener = new SynchronousMessageListener();
		consumer.setMessageListener(listener);
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

	public Map<String, Object> sendAndWaitForResult(Map<String, Object> message) throws JMSException {
		send(message);

		logger.trace("Receiving result message");
		Message msg = listener.waitForMessage();

		HashMap result = new HashMap<String, Object>();
		if (msg instanceof MapMessage) {
			MapMessage mmsg = (MapMessage) msg;
			Enumeration nameEnum = mmsg.getMapNames();
			while (nameEnum.hasMoreElements()) {
				String propertyName = (String) nameEnum.nextElement();
				result.put(propertyName, mmsg.getObject(propertyName));
			}
			logger.debug("Result message received");
		} else {
			logger.debug("No appropriate result message received");
		}

		return result;
	}

	private class SynchronousMessageListener implements MessageListener {
		private Message last;

		@Override
		public void onMessage(Message message) {
			last = message;
		}

		public Message waitForMessage() {
			while (last == null) {
				try {
					sleep(100);
				} catch (InterruptedException e) {
					break;
				}
			}
			Message msg = last;
			last = null;
			return msg;
		}
	}
}
