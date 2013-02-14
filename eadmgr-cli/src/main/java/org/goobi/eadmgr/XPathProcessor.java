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

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.xpath.*;
import java.util.HashMap;
import java.util.Iterator;

public class XPathProcessor {

	private XPath xpath;
	private Node node;
	private VariableResolver vresolver;
	private NSContext nscontext;

	public XPathProcessor() {
		vresolver = new VariableResolver();
		nscontext = new NSContext();

		XPathFactory xpf = XPathFactory.newInstance();
		xpf.setXPathVariableResolver(vresolver);

		this.xpath = xpf.newXPath();
		this.xpath.setNamespaceContext(nscontext);
	}

	public void setQueryNode(Node n) {
		this.node = n;
	}

	public void setVariable(String name, Object value) {
		if (value == null) {
			vresolver.remove(new QName(name));
		} else {
			vresolver.put(new QName(name), value);
		}
	}

	public void declareNamespace(String prefix, String uri) {
		nscontext.put(prefix, uri);
	}

	public Node query(String expr) throws XPathExpressionException {
		return query(expr, this.node);
	}

	public Node query(String expr, Node n) throws XPathExpressionException {
		XPathExpression xpe = this.xpath.compile(expr);
		return (Node) xpe.evaluate(n, XPathConstants.NODE);
	}

	public NodeList queryList(String expr, Node n) throws XPathExpressionException {
		XPathExpression xpe = this.xpath.compile(expr);
		return (NodeList) xpe.evaluate(n, XPathConstants.NODESET);
	}

	private class VariableResolver extends HashMap implements XPathVariableResolver {
		@Override
		public Object resolveVariable(QName variableName) {
			return this.get(variableName);
		}
	}

	private class NSContext extends HashMap<String, String> implements NamespaceContext {
		@Override
		public String getNamespaceURI(String prefix) {
			return this.get(prefix);
		}

		@Override
		public String getPrefix(String namespaceURI) {
			return null;
		}

		@Override
		public Iterator getPrefixes(String namespaceURI) {
			return null;
		}
	}

}
