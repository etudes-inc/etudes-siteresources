/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/siteresources/trunk/siteresources-api/api/src/java/org/etudes/siteresources/api/ToolReference.java $
 * $Id: ToolReference.java 6433 2013-12-02 23:35:10Z ggolden $
 ***********************************************************************************
 *
 * Copyright (c) 2013 Etudes, Inc.
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

/**
 * ToolReference models a reference within the body of a tool item to a site resource placed in a site.
 */
public interface ToolReference
{
	/**
	 * @return the access URL for this tool reference.
	 */
	String getAccessUrl();

	/**
	 * @return the name with any path components removed.
	 */
	String getFileName();

	/**
	 * @return any path components of the name, each in a separate string in the array, or null if the name has no path components. (The getFileName() will be the last component in the array if returned).
	 */
	String[] getFilePath();

	/**
	 * @return the use id.
	 */
	Long getId();

	/**
	 * @return the use name of the site resource (i.e. the file name with path)
	 */
	String getName();

	/**
	 * @return the site id of the use of this resource.
	 */
	String getSiteId();

	/**
	 * @return The tool id of the use.
	 */
	String getTool();

	/**
	 * @return The tool's item id of the use.
	 */
	Long getToolId();
}
