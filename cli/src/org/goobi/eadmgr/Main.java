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

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

class Main {

	private static final String PROMPT_NAME = "eadmgr [Options] [File]";
	private static final String PROMPT_HINT = "Try 'eadmgr -h' for more information.";
	private static final Options options = new Options();
	static {
		options.addOption("h", "help", false, "Print this usage information");
	}

	public static void main(String[] args) {

		CommandLine cmd = parseArguments(args);
		
		if (noOptionsOrHelpRequested(args, cmd)) {
			printUsageInfo(options);
			System.exit(0);
		}

		String[] leftOverArgs = cmd.getArgs();
		if (leftOverArgs.length == 0) {
			exitWithError(1, "No filename given.");
		}

	}

	private static boolean noOptionsOrHelpRequested(String[] args, CommandLine cmd) {
		return (args.length == 0) || (cmd.hasOption("h"));
	}

	private static CommandLine parseArguments(String[] args) {
		CommandLineParser parser = new BasicParser();
		try {
			return parser.parse(options, args);
		}
		catch (ParseException pex) {
			exitWithError(1, pex.getMessage() + "\n" + PROMPT_HINT);
		}
		return null;
	}

	private static void printUsageInfo(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(PROMPT_NAME, options);
	}

	private static void exitWithError(int code, String msg) {
		System.err.println("Error: " + msg);
		System.exit(code);
	}
}

