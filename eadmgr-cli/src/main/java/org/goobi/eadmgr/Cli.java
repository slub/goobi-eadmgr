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

import org.apache.commons.cli.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

class Cli extends CliBase {

	public static final String EAD_200804_XSDL = "ead-200804.xsd";
	public static final String HTTP_WWW_LOC_GOV_EAD_EAD_XSDL = "http://www.loc.gov/ead/ead.xsd";
	public static final String SCHLEGEL_XSL = "schlegel.xsl";
	private String[] args;
	private Options options;
	private CommandLine cmdl;
	private File eadFile;
	private XsltProcessor xsltproc;
	private String volumeId;
	private boolean isDryRun;
	private boolean isHelpRequested;
	private boolean isValidateOption;
	private boolean isVerbose;

	public static void main(String[] args) {
		Cli cli = new Cli();
		System.exit(cli.run(args));
	}

	private void println(String msg) {
		if (isVerbose) {
			System.out.println(msg);
		}
	}

	private void print(String msg) {
		if (isVerbose) {
			System.out.print(msg);
		}
	}

	private void print(Document d) throws TransformerException {
		xsltproc.transform(new DOMSource(d), new StreamResult(System.out));
	}

	@Override
	public void initOptions() {
		options = new Options();

		// mutually exclusive main commands
		OptionGroup mainCommands = new OptionGroup();
		mainCommands.addOption(new Option("h", "help", false, "Print this usage information"));
		mainCommands.addOption(new Option("c", "create-process", true,
				"Extracted data for given volume ID as process creation message to configured ActiveMQ server."));
		options.addOptionGroup(mainCommands);

		// additional switches
		options.addOption(OptionBuilder
				.withLongOpt("validate")
				.withDescription("Validate XML structure while parsing the EAD document. Exits with error code 1 if validation fails.").create());
		options.addOption(OptionBuilder
				.withLongOpt("dry-run")
				.withDescription("Print volume information instead of sending it.").create());
		options.addOption("v", "verbose", false, "Be verbose about what is going on.");
	}

	public void parseArguments(String[] args) throws Exception {
		CommandLineParser parser = new BasicParser();
		this.args = args;
		this.cmdl = parser.parse(options, args);

		isDryRun = cmdl.hasOption("dry-run");
		isHelpRequested = cmdl.hasOption('h');
		isValidateOption = cmdl.hasOption("validate");
		isVerbose = cmdl.hasOption('v');
		volumeId = cmdl.getOptionValue('c');
	}

	@Override
	public boolean validateArguments() {
		return ((args.length > 0) && (!isHelpRequested));
	}

	@Override
	public void preProcessing() throws Exception {
		xsltproc = new XsltProcessor();

		String[] leftOverArgs = cmdl.getArgs();
		if (leftOverArgs.length == 0) {
			throw new Exception("No filename given.");
		}
		if (leftOverArgs.length > 1) {
			throw new Exception("Only one filename allowed.");
		}
		this.eadFile = new File(leftOverArgs[0]);
		if (!eadFile.exists() || !eadFile.canRead() || !eadFile.isFile()) {
			throw new Exception("Cannot read " + eadFile.getAbsolutePath());
		}
	}

	@Override
	public int processing() throws Exception {
		println("Processing " + eadFile.getAbsolutePath());

		int returnCode = 0;

		Document ead = readEadFile(isValidateOption);

		if (ead != null) {
			if (volumeId != null) {
				Document vd = extractVolumeData(ead, volumeId);
				if (isDryRun) {
					print(vd);
				} else {
					send(vd);
				}
			}
		} else {
			returnCode = 1;
		}

		return returnCode;
	}

	private void send(Document vd) throws Exception {
		print("Sending...");

		try {
			Map<String, Object> m = new HashMap();
			m.put("id", String.valueOf(java.util.UUID.randomUUID()));
			m.put("template", "Schlegel"); // configurable value ?
			m.put("docType", "multivolume"); // configurable value ?


			ArrayList<String> collections = new ArrayList<String>();
			collections.add("Projekt: Briefedition August Wilhelm Schlegel"); // configurable value(s) ?
			m.put("collections", collections);

			m.put("xml", String.valueOf(serialize(vd)));

			GoobiMQConnection conn = new GoobiMQConnection();
			conn.send(m);
			conn.close();

			println("[OK]");
		} catch (Exception ex) {
			println("[Fail]");
			throw ex;
		}
	}

	private Object serialize(Document vd) throws TransformerException {
		StringWriter sw = new StringWriter();
		StreamResult out = new StreamResult(sw);
		xsltproc.transform(new DOMSource(vd), out);
		return sw.toString();
	}

	private Document extractVolumeData(Document ead, String volumeId) throws Exception {
		println("Extract data using extraction profile " + SCHLEGEL_XSL);
		Source extractionProfile = getExtractionProfile(SCHLEGEL_XSL);
		return extract(ead, extractionProfile, volumeId);
	}

	private StreamSource getExtractionProfile(String profileFilename) {
		return new StreamSource(this.getClass().getClassLoader().getResourceAsStream(profileFilename));
	}

	private Document extract(Document ead, Source extractionProfile, String volumeId)
			throws Exception {
		DOMResult r = transform(ead, extractionProfile);
		Document doc = filter(volumeId, r);
		return doc;
	}

	private DOMResult transform(Document ead, Source extractionProfile) throws TransformerException {
		DOMResult result = new DOMResult();
		xsltproc.transform(new DOMSource(ead), result, extractionProfile);
		return result;
	}

	private Document filter(String volumeId, DOMResult r) throws Exception {
		XPathProcessor xp = new XPathProcessor();
		xp.setQueryNode(r.getNode());

		xp.setVariable("volume_id", volumeId);
		Node volumeNode = xp.query("/multivolume/volumes/volume[id=$volume_id]");
		if (volumeNode == null) {
			throw new Exception("No volume with ID " + volumeId);
		}
		Node idNode = xp.query("/multivolume/id");
		Node ownerNode = xp.query("/multivolume/owner");

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		Document doc = db.newDocument();

		Element multivolume = doc.createElement("multivolume");
		Element volumes = doc.createElement("volumes");

		doc.appendChild(multivolume);
		multivolume.appendChild(doc.adoptNode(idNode.cloneNode(true)));
		multivolume.appendChild(doc.adoptNode(ownerNode.cloneNode(true)));
		multivolume.appendChild(volumes);
		volumes.appendChild(doc.adoptNode(volumeNode.cloneNode(true)));

		return doc;
	}

	private Document readEadFile(boolean validateAgainstSchema) {
		print("Reading...");

		Document doc;
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			dbf.setNamespaceAware(true);

			if (validateAgainstSchema) {
				SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
				Schema schema = sf.newSchema(obtainEADSchema());
				dbf.setSchema(schema);
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
			doc = db.parse(eadFile);

			// http://stackoverflow.com/questions/13786607/normalization-in-dom-parsing-with-java-how-does-it-work
			doc.getDocumentElement().normalize();

			println("[OK]");
		} catch (SAXParseException spe) {
			println("[Fail]");
			println(spe.getMessage() + " (at line " + spe.getLineNumber() + ")");
			return null;
		} catch (Exception ex) {
			println("[Fail]");
			println(ex.getMessage());
			return null;
		}

		return doc;
	}

	private Source obtainEADSchema() throws Exception {

		Source result;

		// try to get schema file from classpath
		InputStream is = this.getClass().getClassLoader().getResourceAsStream(EAD_200804_XSDL);
		if (is != null) {
			result = new StreamSource(is);
		} else {
			try {
				result = new StreamSource(new URL(HTTP_WWW_LOC_GOV_EAD_EAD_XSDL).openStream());
			} catch (IOException e) {
				throw new Exception("Cannot obtain schema for validation. Neither is the file " + EAD_200804_XSDL +
						" to be found on the classpath nor can the schema be obtained from " + HTTP_WWW_LOC_GOV_EAD_EAD_XSDL + ".");
			}
		}

		return result;
	}

	@Override
	public void printUsageInformation() {
		String PROMPT_NAME = "eadmgr [Options] [File]";
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(PROMPT_NAME, options);
	}

	@Override
	public void handleException(Exception ex) {
		String PROMPT_HINT = "Try 'eadmgr -h' for more information.";
		println("Error: " + ex.getMessage() + "\n" + PROMPT_HINT);
	}
}

