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

import java.io.File;

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

		print("Parsing...");
		EadDocument ead = EadDocument.Factory.parse(eadFile);
		println("[OK]");

		if (cmdl.hasOption("v")) {
			print("Validating...");
			boolean valid = ead.validate();
			println(valid ? "[OK]" : "[FAIL]");
			return valid ? 0 : 1;
		}

		if (cmdl.hasOption("p")) {
			println(ead.xmlText());
		}

		return 0;
	}

	@Override
	public void postProcessing() {
		//To change body of implemented methods use File | Settings | File Templates.
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

