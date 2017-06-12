/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/siteresources/trunk/siteresources-api/api/src/java/org/etudes/siteresources/api/SiteResourcesService.java $
 * $Id: SiteResourcesService.java 7104 2014-01-14 23:08:10Z ggolden $
 ***********************************************************************************
 *
 * Copyright (c) 2013, 2014 Etudes, Inc.
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
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * SiteResourcesService tracks general resources that are used by authored content in various tools in a site.
 */
public interface SiteResourcesService
{
	/**
	 * Add a new site placement for an existing site resource.
	 * 
	 * @param resource
	 *        The site resource.
	 * @param siteId
	 *        The new site id.
	 * @param name
	 *        The new name, including path and file name as appropriate to the use.
	 * @return The new SitePlacement.
	 */
	SitePlacement addSitePlacement(SiteResource resource, String siteId, String name);

	/**
	 * Add a new site resource, and place it in a site.
	 * 
	 * @param type
	 *        The mime type.
	 * @param length
	 *        The length in bytes.
	 * @param contents
	 *        An input stream to read to get the contents.
	 * @param siteId
	 *        The using site.
	 * @param name
	 *        The name (i.e. file name, path, as appropriate to the site use).
	 * @return The SitePlacement created.
	 */
	SitePlacement addSiteResource(String type, int length, InputStream contents, String siteId, String name);

	/**
	 * Add a reference to a site resource by a tool item.
	 * 
	 * @param siteId
	 *        The using site id.
	 * @param tool
	 *        The using tool name.
	 * @param toolId
	 *        The id within the using tool.
	 * @param name
	 *        The use name, including path and file name as appropriate to the use.
	 * @return The ToolReference created.
	 */
	ToolReference addToolReference(String siteId, String tool, Long toolId, String name);

	/**
	 * Remove all tool references for this tool item.
	 * 
	 * @param siteId
	 *        The site id.
	 * @param tool
	 *        The tool name.
	 * @param toolId
	 *        the id of the tool's item within the site.
	 */
	void clearToolReferences(String siteId, String tool, Long toolId);

	/**
	 * Collect the tool references to any site resources referenced by the references (in html files) as relative URLs.
	 * 
	 * @param references
	 *        The set of tool references to seed the check.
	 * @param siteId
	 *        The using site id.
	 * @param tool
	 *        The using tool name.
	 * @param toolId
	 *        The id within the using tool.
	 * @param intoPath
	 *        The path for imported references
	 * @return the set of indirect references (not including the original references).
	 */
	Set<ToolReference> collectIndirectReferences(Set<ToolReference> references, String siteId, String tool, Long toolId, String intoPath);

	/**
	 * If the html is a full document, return just the body contents.
	 * 
	 * @param html
	 *        The html.
	 * @return The body contents of the html, or the full html if it's already a fragment.
	 */
	String convertToFragment(String html);

	/**
	 * Expand any references in the html that are relative to the html base into a fuller url that includes the base.
	 * 
	 * @param html
	 *        The html to convert
	 * @param base
	 *        The base URL (ending with "/")
	 * @return The converted html.
	 */
	String expandRelRefsInChs(String html, String base);

	/**
	 * Find a site placement by name.
	 * 
	 * @param siteId
	 *        The site id.
	 * @param name
	 *        The resource name (i.e. file name).
	 * @return The SitePlacement found, or null if not found.
	 */
	SitePlacement getSitePlacement(String siteId, String name);

	/**
	 * Get a list of all the resources placed in the site.
	 * 
	 * @param siteId
	 *        The site id.
	 * @return The List of placements, may be empty.
	 */
	List<SitePlacement> getSitePlacements(String siteId);

	/**
	 * Get the resource with this id.
	 * 
	 * @param id
	 *        The site resource id.
	 * @return The site resource found, or null if there is not one with this id.
	 */
	SiteResource getSiteResource(Long id);

	/**
	 * Find a tool reference.
	 * 
	 * @param siteId
	 *        The site id.
	 * @param tool
	 *        The tool name.
	 * @param toolId
	 *        the id of the tool's item within the site.
	 * @param resourceName
	 *        The resource name (i.e. file name).
	 * @return The ToolReference found, or null if not found.
	 */
	ToolReference getToolReference(String siteId, String tool, Long toolId, String resourceName);

	/**
	 * Get all the tool references to the resource placed in the site.
	 * 
	 * @param placement
	 *        The site placement.
	 * @return A List of ToolReference for all the references to this placement; may be empty.
	 */
	List<ToolReference> getToolReferences(SitePlacement placement);

	/**
	 * Find all tool reference from a tool item.
	 * 
	 * @param siteId
	 *        The site id.
	 * @param tool
	 *        The tool name.
	 * @param toolId
	 *        the id of the tool's item within the site.
	 * @return The list of ToolReference found; may be empty.
	 */
	List<ToolReference> getToolReferences(String siteId, String tool, Long toolId);

	/**
	 * For each harvest reference (if any), if the site does not have a resource named by the "to" portion, pull the CHS resource named in the "from" portion over.
	 * 
	 * @param harvest
	 *        The collection of things to harvest. May be empty.
	 * @param siteId
	 *        The site id of the receiving site.
	 * @param tool
	 *        The using tool name.
	 * @param toolId
	 *        The id within the using tool.
	 * @param intoPath
	 *        The path for imported references
	 */
	void harvestChsResources(Set<HarvestReference> harvest, String siteId, String tool, Long toolId, String intoPath);

	/**
	 * Check if the site placement of a resource has any tool references.
	 * 
	 * @param placement
	 *        The SitePlacement.
	 * @return TRUE if the placement has any tool references, FALSE if not.
	 */
	Boolean hasToolReferences(SitePlacement placement);

	/**
	 * Parse a tool reference access URL into a tool reference. If the tool reference is defined, it will have a non-null id. Otherwise, it will have the fields filled in from the URL.
	 * 
	 * @param url
	 *        The access URL.
	 * @return The ToolReference.
	 */
	ToolReference parseToolReferenceUrl(String url);

	/**
	 * Register a resource owner.
	 * 
	 * @param owner
	 *        The owner.
	 */
	void registerOwner(SiteResourceOwner owner);

	/**
	 * Remove this placement of a resource in a site, and if the resource has no more placements, remove the resource.
	 * 
	 * @param placement
	 *        The SitePlacement.
	 */
	void removeSitePlacement(SitePlacement placement);

	/**
	 * Change the name portion of this placement to use the new name.
	 * 
	 * @param placement
	 *        The SitePlacement.
	 * @param name
	 *        The new name.
	 */
	void renameSitePlacement(SitePlacement placement, String name);

	/**
	 * Translate the source html so that any embedded references to CHS site resources become tool references based on the tool information given, and record in >references< the tool item's references to these resources (to be registered by the caller).
	 * Harvest the resource if needed.
	 * 
	 * @param source
	 *        The source to process.
	 * @param siteId
	 *        The using site id.
	 * @param tool
	 *        The using tool name.
	 * @param toolId
	 *        The id within the using tool.
	 * @param intoPath
	 *        The path for imported references
	 * @param references
	 *        The set to contain the tool references.
	 * @param harvest
	 *        The set to contain any resources we need to harvest.
	 * @return The converted html.
	 */
	String toolLocalizeEmbeddedChsReferences(String source, String siteId, String tool, Long toolId, String intoPath, Set<ToolReference> references,
			Set<HarvestReference> harvest);

	/**
	 * Translate the source string (html, or a naked site resource url) so that any embedded references to site resources become tool references based on the tool information given, and record in >references< the tool item's references to these resources
	 * (to be registered by the caller).
	 * 
	 * @param source
	 *        The source to process.
	 * @param siteId
	 *        The using site id.
	 * @param tool
	 *        The using tool name.
	 * @param toolId
	 *        The id within the using tool.
	 * @param references
	 *        The list to contain the tool references.
	 * @return The converted html.
	 */
	String toolLocalizeEmbeddedToolReferences(String source, String siteId, String tool, Long toolId, Set<ToolReference> references);

	/**
	 * Compute a resource name based on name that would be unique in the folder of the site.
	 * 
	 * @param folder
	 *        The folder name.
	 * @param name
	 *        The resource name.
	 * @param siteId
	 *        The site id.
	 * @return The unique name.
	 */
	String uniqueResourceName(String folder, String name, String siteId);

	/**
	 * Unregister a resource owner.
	 * 
	 * @param owner
	 *        The owner.
	 */
	void unregisterOwner(SiteResourceOwner owner);

	/**
	 * Add any tool references in toAdd that are not in toRemove and that do not already exist; remove any in toRemove not in toAdd that do exist.
	 * 
	 * @param toRemove
	 *        The list of tool references we want to remove.
	 * @param toAdd
	 *        The list of tool references to add.
	 */
	void updateToolReferences(Collection<ToolReference> toRemove, Collection<ToolReference> toAdd);

}
