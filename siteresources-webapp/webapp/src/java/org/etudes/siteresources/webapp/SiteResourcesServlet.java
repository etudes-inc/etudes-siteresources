/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/siteresources/trunk/siteresources-webapp/webapp/src/java/org/etudes/siteresources/webapp/SiteResourcesServlet.java $
 * $Id: SiteResourcesServlet.java 6365 2013-11-20 23:37:54Z ggolden $
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

package org.etudes.siteresources.webapp;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.siteresources.api.SiteResourcesService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.site.api.SiteService;

/**
 * The SiteImportServlet servlet gives life to the FileImportService and SiteImportService implementations.
 */
@SuppressWarnings("serial")
public class SiteResourcesServlet extends HttpServlet
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(SiteResourcesServlet.class);

	/**
	 * Shutdown the servlet.
	 */
	public void destroy()
	{
		SiteResourcesServiceImpl impl = (SiteResourcesServiceImpl) ComponentManager.get(SiteService.class);
		if (impl != null)
		{
			impl.destroy();
		}
		ComponentManager.loadComponent(SiteResourcesService.class, null);

		M_log.info("destroy()");
		super.destroy();
	}

	/**
	 * Access the Servlet's information display.
	 * 
	 * @return servlet information.
	 */
	public String getServletInfo()
	{
		return "SiteImportServlet";
	}

	/**
	 * Initialize the servlet.
	 * 
	 * @param config
	 *        The servlet config.
	 * @throws ServletException
	 */
	public void init(ServletConfig config) throws ServletException
	{
		super.init(config);

		SiteResourcesService siteResourcesService = new SiteResourcesServiceImpl();
		ComponentManager.loadComponent(SiteResourcesService.class, siteResourcesService);

		M_log.info("init()");
	}
}
