/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/siteresources/trunk/siteresources-api/api/src/java/org/etudes/siteresources/api/HarvestReference.java $
 * $Id: HarvestReference.java 6924 2013-12-25 01:33:39Z ggolden $
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
 * HarvestReference models an item that needs to be harvested from somewhere (the fromRef string) to a location in site resources (the toName string).
 */
public interface HarvestReference
{
	/**
	 * @return the "from" reference.
	 */
	String getFrom();

	/**
	 * @return the "to" - site resources name.
	 */
	String getTo();
}
