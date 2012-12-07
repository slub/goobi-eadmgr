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

import isbn1931666229.EadDocument;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class Cli extends CliBase {

	private String[] args;
	private Options options;
	private CommandLine cmdl;
	private File eadFile;
	private static boolean isQuietOption = false;

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

		EadDocument ead = parseEadFile();

		if (cmdl.hasOption("v")) {
			returnCode = validateEadDocument(ead);
		}

		if (cmdl.hasOption("p")) {
			println(ead.xmlText());
		}

		return returnCode;
	}

	private int validateEadDocument(EadDocument ead) {
		print("Validating...");

		List<XmlError> validationErrors = new ArrayList<XmlError>();
		XmlOptions opt = new XmlOptions();
		opt.setErrorListener(validationErrors);
		opt.setSavePrettyPrint();
		opt.setSavePrettyPrintIndent(4);
		opt.setSavePrettyPrintOffset(4);

		boolean valid = ead.validate(opt);

		if (valid) {
			println("[OK]");
		} else {
			println("[FAIL]");
			for(XmlError e : validationErrors) {
				println("[" + e.getLine() + "] " + e.getMessage());
				println(e.getCursorLocation().xmlText(opt) + "\n");
			}
			return 1;
		}

		return 0;
	}

	private EadDocument parseEadFile() throws XmlException, IOException {
		print("Parsing...");

		XmlOptions opt = new XmlOptions();
		opt.setLoadLineNumbers();

		EadDocument ead = EadDocument.Factory.parse(eadFile,opt);
		println("[OK]");

		return ead;
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

