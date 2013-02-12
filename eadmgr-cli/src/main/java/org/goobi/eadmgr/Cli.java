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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.text.MessageFormat;
import java.util.*;

import static org.apache.activemq.ActiveMQConnection.DEFAULT_BROKER_URL;

class Cli extends CliBase {

	public static final String EAD_200804_XSDL = "ead-200804.xsd";
	public static final String HTTP_WWW_LOC_GOV_EAD_EAD_XSDL = "http://www.loc.gov/ead/ead.xsd";
	public static final String SCHLEGEL_XSL = "schlegel.xsl";
	public static final String DEFAULT_PROCESS_TEMPLATE = "Schlegel";
	public static final String DEFAULT_DOCTYPE = "multivolume";
	public static final String DEFAULT_SUBJECT_QUEUE = "GoobiProduction.createNewProcessWithLogicalStructureData.Queue";
	public static final String ACTIVEMQ_CONFIGURING_URL = "http://activemq.apache.org/cms/configuring.html";
	public static final String PROMPT_HINT = "Try 'eadmgr -h' for more information.";
	private String[] args;
	private Options options;
	private CommandLine cmdl;
	private File eadFile;
	private String brokerUrl;
	private String doctype;
	private String template;
	private String folderId;
	private boolean isDryRun;
	private boolean isHelpRequested;
	private boolean isValidateOption;
	private boolean isVerbose;
	private Collection<String> collections;
	private Logger logger;
	private String subjectQueue;

	public static void main(String[] args) {
		Cli cli = new Cli();
		System.exit(cli.run(args));
	}

	private void println(String msg) {
		System.out.println(msg);
	}

	private void print(Document d) throws TransformerException {
		XsltProcessor xsltproc = new XsltProcessor();
		xsltproc.transform(new DOMSource(d), new StreamResult(System.out));
	}

	@Override
	public void initOptions() {
		options = new Options();

		// mutually exclusive main commands
		OptionGroup mainCommands = new OptionGroup();
		mainCommands.addOption(new Option("h", "help", false, "Print this usage information"));
		mainCommands.addOption(new Option("c", "create-process", true,
				"Extracted data for given folder ID as process creation message to configured ActiveMQ server."));
		options.addOptionGroup(mainCommands);

		// additional switches
		options.addOption(OptionBuilder
				.withLongOpt("validate")
				.withDescription("Validate XML structure while parsing the EAD document. Exits with error code 1 if validation fails.").create());
		options.addOption(OptionBuilder
				.withLongOpt("dry-run")
				.withDescription("Print folder information instead of sending it.").create());
		options.addOption("v", "verbose", false, "Be verbose about what is going on.");
		options.addOption("u", "url", true, MessageFormat.format("ActiveMQ Broker URL. If not given the broker is contacted at \"{0}\".\n" +
				"Note that using the failover protocol will block the program forever if the ActiveMQ host is not reachable unless you specify the \"timeout\" parameter in the URL. See {1} for more information.", DEFAULT_BROKER_URL, ACTIVEMQ_CONFIGURING_URL));
		options.addOption("q", "queue", true, MessageFormat.format("ActiveMQ Subject Queue. If not given messages get enqueue at \"{0}\".", DEFAULT_SUBJECT_QUEUE));
		options.addOption("t", "template", true, MessageFormat.format("Goobi Process Template name. If not given \"{0}\" is used.", DEFAULT_PROCESS_TEMPLATE));
		options.addOption("d", "doctype", true, MessageFormat.format("Goobi Doctype name. If not given \"{0}\" is used.", DEFAULT_DOCTYPE));
		options.addOption(OptionBuilder
				.withLongOpt("collections")
				.hasArg()
				.withDescription("Comma separated list of names of collections to which the newly created process should be assigned.")
				.create());
	}

	public void parseArguments(String[] args) throws Exception {
		CommandLineParser parser = new BasicParser();
		this.args = args;
		this.cmdl = parser.parse(options, args);

		isHelpRequested = cmdl.hasOption('h');
		if (isHelpRequested) {
			// stop parsing the rest of the arguments if user requests help
			return;
		}

		isVerbose = cmdl.hasOption('v');
		System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", isVerbose ? "TRACE" : "INFO");
		logger = LoggerFactory.getLogger(Cli.class);

		brokerUrl = cmdl.getOptionValue("u", DEFAULT_BROKER_URL);
		subjectQueue = cmdl.getOptionValue("q", DEFAULT_SUBJECT_QUEUE);
		doctype = cmdl.getOptionValue("d", DEFAULT_DOCTYPE);
		isDryRun = cmdl.hasOption("dry-run");
		isValidateOption = cmdl.hasOption("validate");

		template = cmdl.getOptionValue("t", DEFAULT_PROCESS_TEMPLATE);
		folderId = cmdl.getOptionValue('c');

		collections = new ArrayList<String>();
		String optVal = cmdl.getOptionValue("collections");
		if (optVal != null) {
			collections.addAll(Arrays.asList(optVal.split(",")));
		}

		if (cmdl.hasOption('c') && (collections.isEmpty())) {
			throw new Exception("Option 'create-process' requires option 'collections' to be properly specified.");
		}
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

		if ((isHelpRequested) || (args.length == 0)) {
			printUsageInformation();
			return 0;
		}

		logger.info("Processing " + eadFile.getAbsolutePath());

		int returnCode = 0;

		Document ead = readEadFile(isValidateOption);

		if (ead != null) {
			if (folderId != null) {
				Document vd = extractFolderData(ead, folderId);
				if (isDryRun) {
					print(vd);
				} else {
					send(vd, template, doctype, brokerUrl, collections);
				}
			}
		} else {
			returnCode = 1;
		}

		return returnCode;
	}

	private void send(Document vd, String template, String doctype, String brokerUrl, Collection<String> collections) throws Exception {
		logger.info("Sending XML message to ActiveMQ server at {}", brokerUrl);
		logger.trace("Collections: {}", collections);
		logger.trace("Process template: {}", template);
		logger.trace("Message doctype: {}", doctype);

		Map<String, Object> m = new HashMap();
		m.put("id", String.valueOf(java.util.UUID.randomUUID()));
		m.put("template", template);
		m.put("docType", doctype);
		m.put("collections", collections);
		m.put("xml", String.valueOf(serialize(vd)));

		GoobiMQConnection conn = new GoobiMQConnection(brokerUrl, subjectQueue);
		conn.send(m);
		conn.close();
	}

	private Object serialize(Document vd) throws TransformerException {
		StringWriter sw = new StringWriter();
		StreamResult out = new StreamResult(sw);
		XsltProcessor xsltproc = new XsltProcessor();
		xsltproc.transform(new DOMSource(vd), out);
		return sw.toString();
	}

	private Document extractFolderData(Document ead, String folderId) throws Exception {
		logger.info("Extract data for {} using extraction profile {}", folderId, SCHLEGEL_XSL);
		Source extractionProfile = getExtractionProfile(SCHLEGEL_XSL);
		return extract(ead, extractionProfile, folderId);
	}

	private StreamSource getExtractionProfile(String profileFilename) {
		logger.trace("Get extraction profile file {} from classpath", profileFilename);
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

	private Document readEadFile(boolean validateAgainstSchema) {
		logger.trace(validateAgainstSchema ? "Read and validate" : "Reading");

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

		} catch (SAXParseException spe) {
			logger.error(spe.getMessage() + " (at line " + spe.getLineNumber() + ")");
			return null;
		} catch (Exception ex) {
			logger.error(ex.getMessage());
			return null;
		}

		return doc;
	}

	private Source obtainEADSchema() throws Exception {

		Source result;

		logger.trace("Try to get EAD schema file {} from classpath", EAD_200804_XSDL);
		InputStream is = this.getClass().getClassLoader().getResourceAsStream(EAD_200804_XSDL);
		if (is != null) {
			logger.trace("EAD schema file found on classpath");
			result = new StreamSource(is);
		} else {
			logger.trace("EAD schema file not found on classpath. Try to download from URL {}", HTTP_WWW_LOC_GOV_EAD_EAD_XSDL);
			try {
				result = new StreamSource(new URL(HTTP_WWW_LOC_GOV_EAD_EAD_XSDL).openStream());
				logger.trace("EAD schema obtained by URL");
			} catch (IOException e) {
				throw new Exception("Cannot obtain schema for validation. Neither is the file " + EAD_200804_XSDL +
						" to be found on the classpath nor can the schema be obtained from " + HTTP_WWW_LOC_GOV_EAD_EAD_XSDL + ".");
			}
		}

		return result;
	}

	private void printUsageInformation() {
		String PROMPT_NAME = "eadmgr [Options] [File]";
		HelpFormatter formatter = new HelpFormatter();
		formatter.setWidth(120);
		formatter.printHelp(PROMPT_NAME, options);
	}

	@Override
	public void handleException(Exception ex) {
		if (logger != null) {
			logger.error(ex.getMessage());
		} else {
			println(ex.getMessage());
		}
		println(PROMPT_HINT);
	}
}

