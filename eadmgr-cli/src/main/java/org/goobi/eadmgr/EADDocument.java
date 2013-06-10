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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.xpath.XPathExpressionException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

public class EADDocument {

	private Logger logger = LoggerFactory.getLogger(EADDocument.class);
	private Document ead;

	public void readEadFile(File eadFile, boolean validateAgainstSchema) {
		logger.trace(validateAgainstSchema ? "Read and validate" : "Reading");

		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			dbf.setNamespaceAware(true);

			if (validateAgainstSchema) {
				Schema eadSchema = EADSchema.getInstance();
				dbf.setSchema(eadSchema);
			}

			DocumentBuilder db = dbf.newDocumentBuilder();

			db.setErrorHandler(new ErrorHandler() {
				@Override
				public void warning(SAXParseException exception) throws SAXException {
					throw exception;
				}

				@Override
				public void error(SAXParseException exception) throws SAXException {
					throw exception;
				}

				@Override
				public void fatalError(SAXParseException exception) throws SAXException {
					throw exception;
				}
			});
			this.ead = db.parse(eadFile);

			// http://stackoverflow.com/questions/13786607/normalization-in-dom-parsing-with-java-how-does-it-work
			this.ead.getDocumentElement().normalize();

		} catch (SAXParseException spe) {
			logger.error(spe.getMessage() + " (at line " + spe.getLineNumber() + ")");
		} catch (Exception ex) {
			logger.error(ex.getMessage());
		}

	}

	public Document extractFolderData(String folderId, String extractionProfileFilename) throws Exception {
		Source extractionProfile = getFromClasspathOrFilesystem(extractionProfileFilename);
		logger.info("Extract data for {} using extraction profile {}", folderId, extractionProfileFilename);
		return extract(ead, extractionProfile, folderId);
	}

	private StreamSource getFromClasspathOrFilesystem(String extractionProfileFilename) throws Exception {
		logger.trace("Try to get extraction profile file {} from classpath", extractionProfileFilename);

		InputStream in;
		in = this.getClass().getClassLoader().getResourceAsStream(extractionProfileFilename);

		if (in != null) {
			logger.trace("Extraction profile found on classpath");
		} else {
			logger.trace("Extraction profile not found on classpath. Try to load from file {}", extractionProfileFilename);
			try {
				in = new FileInputStream(extractionProfileFilename);
				logger.trace("Extraction profile obtained from filesystem");
			} catch (FileNotFoundException e) {
				throw new Exception("Cannot obtain extraction profile. Neither is " + extractionProfileFilename +
						" to be found on the classpath nor can the file be obtained from filesystem.");
			}
		}

		return new StreamSource(in);
	}

	private Document extract(Document ead, Source extractionProfile, String volumeId)
			throws Exception {
		DOMResult r = transform(ead, extractionProfile);
		Document doc = filter(volumeId, r);
		return doc;
	}

	private DOMResult transform(Document ead, Source extractionProfile) throws TransformerException {
		DOMResult result = new DOMResult();
		XsltProcessor xsltproc = new XsltProcessor();
		xsltproc.transform(new DOMSource(ead), result, extractionProfile);
		return result;
	}

	private Document filter(String folderId, DOMResult r) throws Exception {
		XPathProcessor xp = new XPathProcessor();
		xp.setQueryNode(r.getNode());

		xp.setVariable("folder_id", folderId);
		Node folderNode = xp.query("/bundle/folders/folder[id=$folder_id]");
		if (folderNode == null) {
			throw new Exception("No folder with ID " + folderId);
		}
		Node idNode = xp.query("/bundle/id");
		Node titleNode = xp.query("/bundle/title");
		Node ownerNode = xp.query("/bundle/owner");

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		Document doc = db.newDocument();

		Element bundle = doc.createElement("bundle");
		Element folders = doc.createElement("folders");

		doc.appendChild(bundle);
		bundle.appendChild(doc.adoptNode(idNode.cloneNode(true)));
		bundle.appendChild(doc.adoptNode(titleNode.cloneNode(true)));
		bundle.appendChild(doc.adoptNode(ownerNode.cloneNode(true)));
		bundle.appendChild(folders);
		folders.appendChild(doc.adoptNode(folderNode.cloneNode(true)));

		return doc;
	}

	public List<String> getFolderIds() throws XPathExpressionException {
		List<String> result = new LinkedList<String>();

		XPathProcessor xp = new XPathProcessor();
		xp.declareNamespace("ead", "urn:isbn:1-931666-22-9");
		NodeList nl = xp.queryList("//ead:dsc/ead:c[@level='class']/@id", ead);

		for (int i = 0; i < nl.getLength(); i++) {
			result.add(nl.item(i).getNodeValue());
		}

		return result;
	}
}
