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

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class EADSchemaLoader {

	public static final String EAD_200804_XSD = "ead-200804.xsd";
	public static final String HTTP_WWW_LOC_GOV_EAD_EAD_XSDL = "http://www.loc.gov/ead/ead.xsd";

	private static Logger logger = LoggerFactory.getLogger(EADSchemaLoader.class);

	public static Schema load() throws Exception {
		logger.trace("Try to get EAD schema file {} from classpath", EAD_200804_XSD);

		Source src;
		InputStream is = EADSchemaLoader.class.getClassLoader().getResourceAsStream(EAD_200804_XSD);
		if (is != null) {
			logger.trace("EAD schema file found on classpath");
			src = new StreamSource(is);
		} else {
			logger.trace("EAD schema file not found on classpath. Try to download from URL {}", HTTP_WWW_LOC_GOV_EAD_EAD_XSDL);
			try {
				src = new StreamSource(new URL(HTTP_WWW_LOC_GOV_EAD_EAD_XSDL).openStream());
				logger.trace("EAD schema obtained by URL");
			} catch (IOException e) {
				throw new Exception("Cannot obtain schema for validation. Neither is the file " + EAD_200804_XSD +
						" to be found on the classpath nor can the schema be obtained from " + HTTP_WWW_LOC_GOV_EAD_EAD_XSDL + ".");
			}
		}

		SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		return sf.newSchema(src);
	}
}
