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
import java.io.File;

public class EADDocument {

	private Logger logger = LoggerFactory.getLogger(EADDocument.class);
	private Document ead;

	public static final String SCHLEGEL_XSL = "schlegel.xsl";

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

	public Document extractFolderData(String folderId) throws Exception {
		logger.trace("Get extraction profile file {} from classpath", SCHLEGEL_XSL);
		Source extractionProfile = new StreamSource(this.getClass().getClassLoader().getResourceAsStream(SCHLEGEL_XSL));
		logger.info("Extract data for {} using extraction profile {}", folderId, extractionProfile);
		return extract(ead, extractionProfile, folderId);
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
		Node folderNode = xp.query("/convolute/folders/folder[id=$folder_id]");
		if (folderNode == null) {
			throw new Exception("No folder with ID " + folderId);
		}
		Node idNode = xp.query("/convolute/id");
		Node titleNode = xp.query("/convolute/title");
		Node ownerNode = xp.query("/convolute/owner");

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		Document doc = db.newDocument();

		Element convolute = doc.createElement("convolute");
		Element folders = doc.createElement("folders");

		doc.appendChild(convolute);
		convolute.appendChild(doc.adoptNode(idNode.cloneNode(true)));
		convolute.appendChild(doc.adoptNode(titleNode.cloneNode(true)));
		convolute.appendChild(doc.adoptNode(ownerNode.cloneNode(true)));
		convolute.appendChild(folders);
		folders.appendChild(doc.adoptNode(folderNode.cloneNode(true)));

		return doc;
	}

}
