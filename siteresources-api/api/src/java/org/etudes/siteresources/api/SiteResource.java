/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/siteresources/trunk/siteresources-api/api/src/java/org/etudes/siteresources/api/SiteResource.java $
 * $Id: SiteResource.java 10242 2015-03-14 21:41:01Z ggolden $
 ***********************************************************************************
 *
 * Copyright (c) 2013, 2015 Etudes, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.etudes.siteresources.api;

import java.io.InputStream;
import java.util.Date;
import java.util.zip.ZipFile;

/**
 * SiteResource models a single resource used in a site.
 */
public interface SiteResource
{
	/**
	 * @return the resource as a byte array.
	 */
	byte[] getBytes();

	/**
	 * @return the upload date.
	 */
	Date getDate();

	/**
	 * @return the resource id.
	 */
	Long getId();

	/**
	 * @return the byte length of the resource.
	 */
	int getLength();

	/**
	 * @return the mime type of the resource (normalized to lower case).
	 */
	String getMimeType();

	/**
	 * @return the file system path to the resource's storage file.
	 */
	String getStoragePath();

	/**
	 * @return an input stream on the site resource's file contents.
	 */
	InputStream getStream();

	/**
	 * @return the resource as a String.
	 */
	String getString();
	
	/**
	 * @return the resource file open as a zip file.
	 */
	ZipFile  getZip();
}
