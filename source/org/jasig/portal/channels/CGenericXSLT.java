/**
 * Copyright � 2001 The JA-SIG Collaborative.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. Redistributions of any form whatsoever must retain the following
 *    acknowledgment:
 *    "This product includes software developed by the JA-SIG Collaborative
 *    (http://www.jasig.org/)."
 *
 * THIS SOFTWARE IS PROVIDED BY THE JA-SIG COLLABORATIVE "AS IS" AND ANY
 * EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE JA-SIG COLLABORATIVE OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package org.jasig.portal.channels;

import org.jasig.portal.*;
import org.jasig.portal.utils.XSLT;

import org.jasig.portal.helpers.SAXHelper;
import org.xml.sax.DocumentHandler;
import org.xml.sax.SAXException;

import org.w3c.dom.Document;
import java.util.Hashtable;
import java.util.Enumeration;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.net.URL;
import java.net.MalformedURLException;

/**
 * <p>A channel which transforms XML for rendering in the portal.</p>
 *
 * <p>Static channel parameters to be supplied:
 *
 *  1) "xmlUri" - a URI representing the source XML document
 *  2) "sslUri" - a URI representing the corresponding .ssl (stylesheet list) file
 *  3) "xslTitle" - a title representing the stylesheet (optional)
 *                  <i>If no title parameter is specified, a default
 *                  stylesheet will be chosen according to the media</i>
 *  4) "xslUri" - a URI representing the stylesheet to use
 *                  <i>If <code>xslUri</code> is supplied, <code>sslUri</code>
 *                  and <code>xslTitle</code> will be ignored.
 * </p>
 * <p>The static parameters above can be overridden by including
 * parameters of the same name (<code>xmlUri</code>, <code>sslUri</code>,
 * <code>xslTitle</code> and/or <code>xslUri</code> in the HttpRequest string.</p>
 * <p>This channel can be used for all XML formats including RSS.
 * Any other parameters passed to this channel via HttpRequest will get
 * passed in turn to the XSLT stylesheet as stylesheet parameters. They can be
 * read in the stylesheet as follows:
 * <code>&lt;xsl:param name="yourParamName"&gt;aDefaultValue&lt;/xsl:param&gt;</code></p>
 * <p>CGenericXSLT is also useful for channels that have no dynamic data.  In these types
 * of channels, all the markup comes from the XSLT stylesheets.  An empty XML document
 * can be used and is included with CGenericXSLT.  Just set the xml parameter</p>
 * @author Steve Toth, stoth@interactivebusiness.com
 * @author Ken Weiner, kweiner@interactivebusiness.com
 * @version $Revision$
 */
public class CGenericXSLT implements IChannel, ICacheable
{
  protected String xmlUri;
  protected String sslUri;
  protected String xslTitle;
  protected String xslUri;
  protected ChannelRuntimeData runtimeData;
  protected String media;
  protected MediaManager mm;

  // Get channel parameters.
  public void setStaticData (ChannelStaticData sd)
  {
    this.xmlUri = sd.getParameter("xmlUri");
    this.sslUri = sd.getParameter("sslUri");
    if (sslUri != null)
      this.sslUri = UtilitiesBean.fixURI(sslUri);

    this.xslTitle = sd.getParameter("xslTitle");
    this.xslUri = sd.getParameter("xslUri");
    mm=new MediaManager();
  }

  public void setRuntimeData (ChannelRuntimeData rd)
  {
    runtimeData = rd;

    String xmlUri = runtimeData.getParameter("xmlUri");

    if (xmlUri != null)
      this.xmlUri = xmlUri;

    String sslUri = runtimeData.getParameter("sslUri");

    if (sslUri != null)
      this.sslUri = UtilitiesBean.fixURI(sslUri);

    String xslTitle = runtimeData.getParameter("xslTitle");

    if (xslTitle != null)
      this.xslTitle = xslTitle;

    String xslUri = runtimeData.getParameter("xslUri");

    if (xslUri != null)
      this.xslUri = xslUri;

    media = mm.getMedia(runtimeData.getBrowserInfo());
  }

  public void receiveEvent (PortalEvent ev)
  {
    // No events to process here
  }

  // Access channel runtime properties.
  public ChannelRuntimeProperties getRuntimeProperties ()
  {
    return new ChannelRuntimeProperties ();
  }

  public void renderXML(DocumentHandler out) throws PortalException
  {
    String xml;
    Document xmlDoc;

    try	{
	org.apache.xerces.parsers.DOMParser domParser = new org.apache.xerces.parsers.DOMParser();
        org.jasig.portal.utils.DTDResolver dtdResolver = new org.jasig.portal.utils.DTDResolver();
        domParser.setEntityResolver(dtdResolver);
        domParser.parse(UtilitiesBean.fixURI(xmlUri));
        xmlDoc = domParser.getDocument();
    }
    catch (IOException e) {
	throw new ResourceMissingException (xmlUri, "", e.getMessage());
    }
    catch (SAXException se) {
        throw new GeneralRenderingException("Problem parsing " + xmlUri + ": " + se);
    }

    runtimeData.put("baseActionURL", runtimeData.getBaseActionURL());
    
    try {
        if (xslUri != null)
	    XSLT.transform(xmlDoc, new URL(xslUri), out, runtimeData);
        else {
	    if (xslTitle != null)
		XSLT.transform(xmlDoc, new URL(sslUri), out, runtimeData, xslTitle, media);
	    else
		XSLT.transform(xmlDoc, new URL(sslUri), out, runtimeData, media);
        }
    }
    catch (SAXException se) {
	throw new GeneralRenderingException("Problem performing the transformation:" + se.toString());
    }
    catch (IOException ioe) {
        StringWriter sw = new StringWriter();
        ioe.printStackTrace(new PrintWriter(sw));
        sw.flush();
        throw new GeneralRenderingException(sw.toString());
    }
  }

    // ICacheable-related methods
    public ChannelCacheKey generateKey() {
	ChannelCacheKey k=new ChannelCacheKey();
	k.setKey(this.getKey());
	k.setKeyScope(ChannelCacheKey.SYSTEM_KEY_SCOPE);
	k.setKeyValidity(new Long(System.currentTimeMillis()));
	return k;
    }

    public boolean isCacheValid(Object validity) {
	if(validity instanceof Long) {
	    // set timeout to 15 minutes
	    return (System.currentTimeMillis()-((Long)validity).longValue()<15*60*1000);
	} else {
	    return false;
	}
    }

  private String getKey()
  {
    // Maybe not the best way to generate a key, but it seems to work.
    // If you know a better way, please change it!
    StringBuffer sbKey = new StringBuffer(1024);
    sbKey.append("xmluri:").append(xmlUri).append(", ");
    sbKey.append("sslUri:").append(sslUri).append(", ");
    sbKey.append("xslUri:").append(xslUri).append(", ");
    sbKey.append("params:").append(runtimeData.toString()).append(", ");
    sbKey.append("media:").append(media);
    return sbKey.toString();
  }
}
