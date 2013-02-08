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

/**
 * Defines a basic workflow for processing command line arguments.
 * <p/>
 * 1. Initialize options.
 * 2. Parsing command line arguments.
 * 3. Do pre-processing, processing and post-processing.
 */
public abstract class CliBase {

	/**
	 * Implement initialization of command line options for parsing and printing usage information here.
	 */
	public abstract void initOptions();

	/**
	 * Implement parsing of command line arguments here.
	 * <p/>
	 * Consider storing parsing result in some member variable, so that the state can be accessed by following
	 * processing steps.
	 *
	 * @param args Command line arguments.
	 */
	public abstract void parseArguments(String[] args) throws Exception;

	/**
	 * Implement pre-processing steps here, if necessary.
	 */
	public void preProcessing() throws Exception {
	}

	/**
	 * Implement actual argument processing here.
	 *
	 * @return Determined program exit code.
	 */
	public abstract int processing() throws Exception;

	/**
	 * Implement post-processing steps here, if necessary.
	 */
	public void postProcessing() throws Exception {
	}

	/**
	 * Handle any Exception that might break the workflow.
	 *
	 * @param ex The Exception that actually occurred.
	 */
	public abstract void handleException(Exception ex);

	/**
	 * Run CLI processing workflow with given command line options.
	 *
	 * @param args Command line options that have been passed to the program.
	 * @return Program return code. The exit code is determined by the <code>processing()</code> method. However,
	 *         if an Exception occurs, an exit code of 1 is returned indicating some failure.
	 */
	public int run(String[] args) {
		int exitCode = 0;
		initOptions();
		try {
			parseArguments(args);
			preProcessing();
			exitCode = processing();
			postProcessing();
		} catch (Exception ex) {
			handleException(ex);
			exitCode = 1;
		}
		return exitCode;
	}

}
