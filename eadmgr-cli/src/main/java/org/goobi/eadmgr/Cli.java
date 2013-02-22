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

import java.io.File;
import java.text.MessageFormat;
import java.util.*;

import static org.apache.activemq.ActiveMQConnection.DEFAULT_BROKER_URL;

class Cli extends CliBase {

	public static final String DEFAULT_PROCESS_TEMPLATE = "Schlegel";
	public static final String DEFAULT_DOCTYPE = "multivolume";
	public static final String DEFAULT_SUBJECT_QUEUE = "GoobiProduction.createNewProcessWithLogicalStructureData.Queue";
	public static final String ACTIVEMQ_CONFIGURING_URL = "http://activemq.apache.org/cms/configuring.html";
	public static final String PROMPT_HINT = "Try 'eadmgr -h' for more information.";
	private String[] args;
	private Options options;
	private File eadFile;
	private String brokerUrl;
	private String doctype;
	private String template;
	private String folderId;
	private boolean isDryRun;
	private boolean isValidateOption;
	private Collection<String> collections;
	private Logger logger;
	private String subjectQueue;
	private Commands command;
	private boolean isUseFolderId;
	private Map<String, String> userMessageFields;

	public static void main(String[] args) {
		Cli cli = new Cli();
		System.exit(cli.run(args));
	}

	private void println(String msg) {
		System.out.println(msg);
	}

	private void printList(List<String> list) {
		for (String s : list) {
			println(s);
		}
	}

	@Override
	@SuppressWarnings("AccessStaticViaInstance") // workaround for screwed OptionBuilder API
	public void initOptions() {
		options = new Options();

		// mutually exclusive main commands
		OptionGroup mainCommands = new OptionGroup();
		mainCommands.addOption(new Option("h", "help", false, "Print this usage information"));
		mainCommands.addOption(new Option("l", "list-folder-ids", false, "List all folder IDs available for process creation."));
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
				.withLongOpt("use-folder-id")
				.withDescription("Use folder ID when generating ActiveMQ Message IDs. If not given a random 128 bit universally unique identifier (UUID) is generated.")
				.create());
		options.addOption(OptionBuilder
				.withLongOpt("collection")
				.withDescription("Name of a collection to which the newly created process should be assigned.")
				.hasArg()
				.create("C"));
		options.addOption(OptionBuilder
				.withDescription("User defined option in the form of <key>=<value> to append to the ActiveMQ message.")
				.hasArgs()
				.create("O"));
	}

	public void parseArguments(String[] args) throws Exception {
		CommandLineParser parser = new BasicParser();
		this.args = args;
		CommandLine cmdl = parser.parse(options, args);

		determineCommand(cmdl);

		// stop parsing the rest of the arguments if user requests help
		if (command == Commands.Help) {
			return;
		}

		brokerUrl = cmdl.getOptionValue("u", DEFAULT_BROKER_URL);
		subjectQueue = cmdl.getOptionValue("q", DEFAULT_SUBJECT_QUEUE);
		doctype = cmdl.getOptionValue("d", DEFAULT_DOCTYPE);
		isDryRun = cmdl.hasOption("dry-run");
		isValidateOption = cmdl.hasOption("validate");
		template = cmdl.getOptionValue("t", DEFAULT_PROCESS_TEMPLATE);
		isUseFolderId = cmdl.hasOption("use-folder-id");
		userMessageFields = splitAndMap(cmdl.getOptionValues("O"));

		if (command == Commands.Create) {
			folderId = cmdl.getOptionValue('c');
			collections = new ArrayList<String>();
			String[] optVal = cmdl.getOptionValues("collection");
			if (optVal != null) {
				collections.addAll(Arrays.asList(optVal));
			}
			if (collections.isEmpty()) {
				throw new Exception("Option 'create-process' requires option 'collection' to be properly specified.");
			}
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

		boolean verbose = cmdl.hasOption('v');
		System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", verbose ? "TRACE" : "INFO");
		logger = LoggerFactory.getLogger(Cli.class);

	}

	private Map<String, String> splitAndMap(String[] optionValues) throws Exception {
		if (optionValues != null) {
			HashMap<String, String> result = new HashMap<String, String>();
			for (String s : optionValues) {
				String[] split = s.split("=");
				if ((split.length == 2) && (!split[0].isEmpty()) && (!split[1].isEmpty())) {
					result.put(split[0], split[1]);
				} else {
					throw new Exception("Invalid argument for option: -O " + s);
				}
			}
			return result;
		}
		return null;
	}

	private void determineCommand(CommandLine cmdl) {
		if (cmdl.hasOption('h')) {
			command = Commands.Help;
		} else if (cmdl.hasOption("l")) {
			command = Commands.List;
		} else if (cmdl.hasOption("c")) {
			command = Commands.Create;
		}
	}

	@Override
	public int processing() throws Exception {

		if ((command == Commands.Help) || (args.length == 0)) {
			printUsageInformation();
			return 0;
		}

		logger.info("Processing " + eadFile.getAbsolutePath());

		int returnCode = 0;

		EADDocument ead = new EADDocument();
		ead.readEadFile(eadFile, isValidateOption);

		switch (command) {
			case List:
				List<String> folderIds = ead.getFolderIds();
				printList(folderIds);
				break;
			case Create:
				Document vd = ead.extractFolderData(folderId);
				send(vd, template, doctype, brokerUrl, collections, userMessageFields);
				break;
		}

		return returnCode;
	}

	private void send(Document vd, String template, String doctype, String brokerUrl, Collection<String> collections, Map<String, String> userMessageFields) throws Exception {
		logger.info("Sending XML message to ActiveMQ server at {}", brokerUrl);
		logger.trace("Collections: {}", collections);
		logger.trace("Process template: {}", template);
		logger.trace("Message doctype: {}", doctype);

		String uuid = (isUseFolderId) ? folderId : String.valueOf(java.util.UUID.randomUUID());

		Map<String, Object> m = new HashMap<String, Object>();
		m.put("id", uuid);
		m.put("template", template);
		m.put("docType", doctype);
		m.put("collections", collections);
		m.put("userMessageFields", userMessageFields);
		m.put("xml", String.valueOf(XMLSerializer.serialize(vd)));

		if (isDryRun) {
			println(m.toString());
		} else {
			GoobiMQConnection conn = new GoobiMQConnection(brokerUrl, subjectQueue);
			conn.send(m);
			conn.close();
		}
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

	private enum Commands {
		Help,
		List,
		Create
	}
}

