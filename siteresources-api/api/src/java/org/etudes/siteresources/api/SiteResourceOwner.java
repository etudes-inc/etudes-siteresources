/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/siteresources/trunk/siteresources-api/api/src/java/org/etudes/siteresources/api/SiteResourceOwner.java $
 * $Id: SiteResourceOwner.java 7104 2014-01-14 23:08:10Z ggolden $
 ***********************************************************************************
 *
 * Copyright (c) 2014 Etudes, Inc.
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
 * A tool that stores things in site resources.
 */
public interface SiteResourceOwner
{
	/**
	 * @return the tool (name) used in references.
	 */
	String getTool();

	/**
	 * Inform the owner that an item references a newly deleted resource
	 * 
	 * @param reference
	 *        The existing tool reference.
	 */
	void informDeletedResource(ToolReference reference);

	/**
	 * Inform the owner that an item references a newly added resource
	 * 
	 * @param reference
	 *        The existing tool reference.
	 * @param placement
	 *        The new resource placement.
	 */
	void informNewResource(ToolReference reference, SitePlacement placement);

	/**
	 * Inform the owner that an item references a newly renamed resource
	 * 
	 * @param reference
	 *        The existing tool reference.
	 * @param placement
	 *        The newly renamed resource placement.
	 */
	void informRenamedResource(ToolReference reference, SitePlacement placement);
}
