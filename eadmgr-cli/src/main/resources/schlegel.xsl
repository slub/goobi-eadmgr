<?xml version="1.0" encoding="UTF-8"?>
<!--
  ~ This file is part of the Goobi Application - a Workflow tool for the support of
  ~ mass digitization.
  ~
  ~ Visit the websites for more information.
  ~     - http://gdz.sub.uni-goettingen.de
  ~     - http://www.goobi.org
  ~     - http://launchpad.net/goobi-production
  ~
  ~ This program is free software; you can redistribute it and/or modify it under
  ~ the terms of the GNU General Public License as published by the Free Software
  ~ Foundation; either version 2 of the License, or (at your option) any later
  ~ version.
  ~
  ~ This program is distributed in the hope that it will be useful, but WITHOUT ANY
  ~ WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
  ~ PARTICULAR PURPOSE. See the GNU General Public License for more details. You
  ~ should have received a copy of the GNU General Public License along with this
  ~ program; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
  ~ Suite 330, Boston, MA 02111-1307 USA
  -->
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:ead="urn:isbn:1-931666-22-9">

    <xsl:output encoding="UTF8" method="xml" indent="yes"/>


    <xsl:template match="//ead:archdesc">
        <xsl:element name="convolute">
            <xsl:element name="id">
                <xsl:value-of select="@id"/>
            </xsl:element>
            <xsl:element name="title">
                <xsl:value-of select="ead:did/ead:unittitle"/>
            </xsl:element>

            <xsl:element name="owner">
                <xsl:element name="id">
                    <xsl:value-of select="ead:did/ead:repository/ead:corpname/@authfilenumber"/>
                </xsl:element>
                <xsl:element name="name">
                    <xsl:value-of select="ead:did/ead:repository/ead:corpname/@normal"/>
                </xsl:element>
            </xsl:element>

            <xsl:element name="folders">
                <xsl:apply-templates select="ead:dsc/ead:c[attribute::level='class']"/>
            </xsl:element>
        </xsl:element>
    </xsl:template>

    <xsl:template match="ead:c[attribute::level='class']">
        <xsl:element name="folder">
            <xsl:element name="id">
                <xsl:value-of select="@id"/>
            </xsl:element>
            <xsl:element name="title">
                <xsl:value-of select="ead:did/ead:unittitle"/>
            </xsl:element>
            <xsl:element name="signature">
                <xsl:value-of select="ead:did/ead:unitid"/>
            </xsl:element>
            <xsl:element name="folder">
                <xsl:value-of
                        select="ead:c[attribute::level='item'][1]/ead:did/ead:container[attribute::type='folder']"/>
            </xsl:element>
            <xsl:element name="elements">
                <xsl:apply-templates select="ead:c[attribute::level='item']"/>
            </xsl:element>
        </xsl:element>
    </xsl:template>

    <xsl:template match="ead:c[attribute::level='item']">
        <xsl:element name="letter">
            <xsl:element name="id">
                <xsl:value-of select="@id"/>
            </xsl:element>
            <xsl:element name="title">
                <xsl:value-of select="normalize-space(ead:did/ead:unittitle/text()[1])"/>
            </xsl:element>
            <xsl:element name="signature">
                <xsl:value-of select="normalize-space(ead:did/ead:unitid[attribute::type='shelfMark']/text()[1])"/>
            </xsl:element>
            <xsl:element name="further-signature">
                <xsl:value-of
                        select="normalize-space(ead:did/ead:unitid[attribute::type='furtherShelfMark']/text()[1])"/>
            </xsl:element>
            <xsl:element name="folio">
                <xsl:value-of select="ead:did/ead:container[attribute::type='folio']"/>
            </xsl:element>
            <xsl:element name="date">
                <xsl:value-of select="ead:did/ead:unittitle/ead:unitdate/@normal"/>
            </xsl:element>
            <xsl:element name="origin">
                <xsl:value-of select="ead:did/ead:unittitle/ead:geogname[attribute::role]"/>
            </xsl:element>
            <xsl:element name="extent">
                <xsl:value-of select="ead:did/ead:physdesc/ead:extent"/>
            </xsl:element>
            <xsl:element name="dimensions">
                <xsl:value-of select="ead:did/ead:physdesc/ead:dimensions"/>
            </xsl:element>
            <xsl:element name="language">
                <xsl:value-of select="ead:did/ead:langmaterial/ead:language/@langcode"/>
            </xsl:element>
            <xsl:apply-templates select="ead:did/ead:unittitle/ead:persname"/>
        </xsl:element>

    </xsl:template>

    <xsl:template match="ead:persname[attribute::role='creator']">
        <xsl:element name="creator">
            <xsl:element name="gnd-id">
                <xsl:value-of select="@authfilenumber"/>
            </xsl:element>
            <xsl:element name="name">
                <xsl:value-of select="@normal"/>
            </xsl:element>
        </xsl:element>
    </xsl:template>

    <xsl:template match="ead:persname[attribute::role='addressee']">
        <xsl:element name="addressee">
            <xsl:element name="gnd-id">
                <xsl:value-of select="@authfilenumber"/>
            </xsl:element>
            <xsl:element name="name">
                <xsl:value-of select="@normal"/>
            </xsl:element>
        </xsl:element>
    </xsl:template>

    <xsl:template match="text()"/>

</xsl:stylesheet>