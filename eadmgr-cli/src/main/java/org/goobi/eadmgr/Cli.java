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
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

class Cli extends CliBase {

	public static final String EAD_200804_XSDL = "ead-200804.xsd";
	public static final String HTTP_WWW_LOC_GOV_EAD_EAD_XSDL = "http://www.loc.gov/ead/ead.xsd";
	private static boolean isQuietOption = false;
	private String[] args;
	private Options options;
	private CommandLine cmdl;
	private File eadFile;

	public static void main(String[] args) {
		Cli cli = new Cli();
		System.exit(cli.run(args));
	}

	private void println(String msg) {
		if (!isQuietOption) {
			System.out.println(msg);
		}
	}

	private void print(String msg) {
		if (!isQuietOption) {
			System.out.print(msg);
		}
	}

	@Override
	public void initOptions() {
		options = new Options();
		options.addOption("h", "help", false, "Print this usage information");
		options.addOption("p", "print", false, "Parse given file and print XML structure");
		options.addOption("v", "validate", false, "Parse given file and validate XML structure. Exits with error code 1 if validation fails.");
		options.addOption("q", "quiet", false, "No output to stdout.");
	}

	public void parseArguments(String[] args) throws Exception {
		CommandLineParser parser = new BasicParser();
		this.args = args;
		this.cmdl = parser.parse(options, args);
	}

	@Override
	public boolean validateArguments() {
		return ((args.length > 0) && (!cmdl.hasOption("h")));
	}

	@Override
	public void preProcessing() throws Exception {
		isQuietOption = cmdl.hasOption('q');

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

		boolean validate = cmdl.hasOption("v");
		Document ead = readEadFile(validate);

		if (ead != null) {
			if (cmdl.hasOption("p")) {
				println(ead.toString());
			}
		} else {
			returnCode = 1;
		}

		return returnCode;
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

