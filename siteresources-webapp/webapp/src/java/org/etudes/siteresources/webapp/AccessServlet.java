/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/siteresources/trunk/siteresources-webapp/webapp/src/java/org/etudes/siteresources/webapp/AccessServlet.java $
 * $Id: AccessServlet.java 10280 2015-03-17 22:28:48Z ggolden $
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

package org.etudes.siteresources.webapp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.siteresources.api.SitePlacement;
import org.etudes.siteresources.api.SiteResource;
import org.etudes.siteresources.api.SiteResourcesService;
import org.etudes.siteresources.api.ToolReference;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.content.api.ContentTypeImageService;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.thread_local.api.ThreadLocalManager;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.util.StringUtil;
import org.sakaiproject.util.Validator;
import org.sakaiproject.util.Web;

/**
 * The AccessServlet servlet fields get requests for site resources, either directly or embedded in content.
 */
@SuppressWarnings("serial")
public class AccessServlet extends HttpServlet
{
	/** The name of the cookie we use to keep sakai session. */
	public static final String SESSION_COOKIE = "JSESSIONID";

	/** The name of the system property that will be used when setting the value of the session cookie. */
	protected static final String SAKAI_SERVERID = "sakai.serverId";

	/** The chunk size used when streaming (100k). */
	protected static final int STREAM_BUFFER_SIZE = 102400;

	/** Our log. */
	private static Log M_log = LogFactory.getLog(AccessServlet.class);

	/**
	 * Shutdown the servlet.
	 */
	public void destroy()
	{
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
		return "Access";
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
	}

	/**
	 * Check the security for this user doing this function within this context.
	 * 
	 * @param userId
	 *        the user id.
	 * @param function
	 *        the function.
	 * @param context
	 *        The context.
	 * @param ref
	 *        The entity reference.
	 * @return true if the user has permission, false if not.
	 */
	protected boolean checkSecurity(String userId, String function, String context)
	{
		// check for super user
		if (securityService().isSuperUser(userId)) return true;

		// check for the user / function / context-as-site-authz
		// use the site ref for the security service (used to cache the security calls in the security service)
		String siteRef = siteService().siteReference(context);

		// form the azGroups for a context-as-implemented-by-site
		Collection<String> azGroups = new ArrayList<String>(2);
		azGroups.add(siteRef);
		azGroups.add("!site.helper");

		boolean rv = securityService().unlock(userId, function, siteRef, azGroups);
		return rv;
	}

	/**
	 * Respond to requests.
	 * 
	 * @param req
	 *        The servlet request.
	 * @param res
	 *        The servlet response.
	 * @throws ServletException.
	 * @throws IOException.
	 */
	protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException
	{
		establishSessionFromCookie(req, res);
		try
		{
			// let site resources service parse the request
			String path = req.getPathInfo();
			ToolReference ref = siteResourcesService().parseToolReferenceUrl(path);
			if (ref == null)
			{
				M_log.warn("dispatchDoc - URL not recognized: " + path);

				res.sendError(HttpServletResponse.SC_BAD_REQUEST);
				return;
			}

			// handle site resource requests
			if (ref.getTool().equals("site"))
			{
				// security: the user must be instructor in site
				if (!siteService().allowUpdateSite(ref.getSiteId()))
				{
					res.sendError(HttpServletResponse.SC_NOT_FOUND);
					return;
				}

				// find the SiteResource
				SitePlacement p = siteResourcesService().getSitePlacement(ref.getSiteId(), ref.getName());
				if (p == null)
				{
					// try for a zip entry
					String[] parts = StringUtil.split(ref.getName(), "/");
					for (int i = 1; i <= parts.length - 2; i++)
					{
						String zipResource = StringUtil.unsplit(parts, 0, parts.length - i, "/") + ".zip";
						p = siteResourcesService().getSitePlacement(ref.getSiteId(), zipResource);
						if (p != null)
						{
							String entryName = StringUtil.unsplit(parts, parts.length - i, i, "/");
							if (doGetZip(req, res, p.getSiteResource(), entryName)) return;
						}
					}

					res.sendError(HttpServletResponse.SC_NOT_FOUND);
					return;
				}

				// special .zip handling
				if (Validator.getFileExtension(ref.getName()).equalsIgnoreCase("zip"))
				{
					if (doGetZip(req, res, p.getSiteResource(), null)) return;
				}

				// stream it out
				sendContent(req, res, p.getSiteResource());
			}

			// TODO: this needs to be plug-in from homepage
			else if (ref.getTool().equals("home"))
			{
				// assure that the tool item has a registered reference for this
				if (ref.getId() == null)
				{
					// try for a zip entry
					String[] parts = StringUtil.split(ref.getName(), "/");
					for (int i = 1; i <= parts.length - 2; i++)
					{
						String zipResource = StringUtil.unsplit(parts, 0, parts.length - i, "/") + ".zip";
						ToolReference zipRef = siteResourcesService().getToolReference(ref.getSiteId(), ref.getTool(), ref.getToolId(), zipResource);
						if (zipRef != null)
						{
							SitePlacement p = siteResourcesService().getSitePlacement(zipRef.getSiteId(), zipRef.getName());
							if (p != null)
							{
								String entryName = StringUtil.unsplit(parts, parts.length - i, i, "/");
								if (doGetZip(req, res, p.getSiteResource(), entryName)) return;
							}
						}
					}

					res.sendError(HttpServletResponse.SC_NOT_FOUND);
					return;
				}

				// find the site resource
				SitePlacement p = siteResourcesService().getSitePlacement(ref.getSiteId(), ref.getName());
				if (p == null)
				{
					res.sendError(HttpServletResponse.SC_NOT_FOUND);
					return;
				}

				// assure that the user has at least visit permission to the site
				// TODO: home is public
				// if (!siteService().allowAccessSite(siteId))
				// {
				// res.sendError(HttpServletResponse.SC_NOT_FOUND);
				// return;
				// }

				// TODO: may need more tool-specific security

				// special .zip handling
				if (Validator.getFileExtension(ref.getName()).equalsIgnoreCase("zip"))
				{
					if (doGetZip(req, res, p.getSiteResource(), null)) return;
				}

				// stream it out
				sendContent(req, res, p.getSiteResource());
			}

			// other handlers
			else
			{
				M_log.warn("dispatchDoc - handler not recognized: " + path);

				res.sendError(HttpServletResponse.SC_BAD_REQUEST);
				return;
			}
		}
		catch (Exception e)
		{
			M_log.warn("doGet: ", e);
			res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
		finally
		{
			// clear any bound current values
			threadLocalManager().clear();
		}
	}

	protected boolean doGetZip(HttpServletRequest req, HttpServletResponse res, SiteResource r, String entryName)
	{
		ZipFile zip = null;
		try
		{
			zip = r.getZip();

			// find an entry point and redirect
			if (entryName == null)
			{
				// possible entry points
				ZipEntry entry = zip.getEntry("index.html");
				if (entry == null) entry = zip.getEntry("story.html");

				if (entry != null)
				{
					String addr = Web.returnUrl(req, req.getPathInfo());
					addr = addr.substring(0, addr.length() - 4);
					addr = addr + "/" + entry.getName();
					try
					{
						res.sendRedirect(addr);
						return true;
					}
					catch (IOException e)
					{
					}
				}
			}

			// serve this entry
			else
			{
				ZipEntry entry = zip.getEntry(entryName);
				if (entry != null)
				{
					int len = (int) entry.getSize();
					String fileName = Validator.escapeResourceName(Validator.getFileName(entry.getName()));
					InputStream content = zip.getInputStream(entry);
					String contentType = contentTypeImageService().getContentType(Validator.getFileExtension(fileName));

					sendContentBinary(req, res, contentType, null, len, content);

					return true;
				}
			}
		}
		catch (IOException e)
		{
			M_log.warn("doGetZip" + e);
		}

		finally
		{
			if (zip != null)
			{
				try
				{
					zip.close();
				}
				catch (IOException e)
				{
				}
			}
		}

		return false;
	}

	/**
	 * Respond to requests.
	 * 
	 * @param req
	 *        The servlet request.
	 * @param res
	 *        The servlet response.
	 * @throws ServletException.
	 * @throws IOException.
	 */
	protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException
	{
	}

	/**
	 * Check for a session cookie - and if found, set that session as the current session
	 * 
	 * @param req
	 *        The request object.
	 * @param res
	 *        The response object.
	 * @return The Session object if found, else null.
	 */
	protected Session establishSessionFromCookie(HttpServletRequest req, HttpServletResponse res)
	{
		// compute the session cookie suffix, based on this configured server id
		String suffix = System.getProperty(SAKAI_SERVERID);
		if ((suffix == null) || (suffix.length() == 0))
		{
			suffix = "sakai";
		}

		// find our session id from our cookie
		Cookie c = findCookie(req, SESSION_COOKIE, suffix);
		if (c == null) return null;

		// get our session id
		String sessionId = c.getValue();

		// remove the server id suffix
		int dotPosition = sessionId.indexOf(".");
		if (dotPosition > -1)
		{
			sessionId = sessionId.substring(0, dotPosition);
		}

		// find the session
		Session s = sessionManager().getSession(sessionId);

		// mark as active
		if (s != null)
		{
			s.setActive();
		}

		// set this as the current session
		sessionManager().setCurrentSession(s);

		return s;
	}

	/**
	 * Find a cookie by this name from the request; one with a value that has the specified suffix.
	 * 
	 * @param req
	 *        The servlet request.
	 * @param name
	 *        The cookie name
	 * @param suffix
	 *        The suffix string to find at the end of the found cookie value.
	 * @return The cookie of this name in the request, or null if not found.
	 */
	protected Cookie findCookie(HttpServletRequest req, String name, String suffix)
	{
		Cookie[] cookies = req.getCookies();
		if (cookies != null)
		{
			for (int i = 0; i < cookies.length; i++)
			{
				if (cookies[i].getName().equals(name))
				{
					if ((suffix == null) || cookies[i].getValue().endsWith(suffix))
					{
						return cookies[i];
					}
				}
			}
		}

		return null;
	}

	/**
	 * Send the requested CHS resource
	 * 
	 * @param req
	 *        request
	 * @param res
	 *        response
	 * @param resourceId
	 *        the CHS resource id to dispatch
	 * @param secure
	 *        if false, bypass normal CHS security
	 * @throws ServletException
	 * @throws IOException
	 */
	protected void sendContent(HttpServletRequest req, HttpServletResponse res, SiteResource r) throws IOException
	{
		int len = r.getLength();
		String contentType = r.getMimeType();

		// for text, we need to do some special handling
		if (contentType.startsWith("text/"))
		{
			// get the content as text
			String contentText = r.getString();
			sendContentText(req, res, contentType, contentText);
		}

		// for non-text, just send it (stream it in chunks to avoid the elephant-in-snake problem)
		else
		{
			InputStream content = r.getStream();
			if (content == null)
			{
				res.sendError(HttpServletResponse.SC_BAD_REQUEST);
			}

			sendContentBinary(req, res, contentType, null, len, content);
		}
	}

	protected void sendContentBinary(HttpServletRequest req, HttpServletResponse res, String contentType, String encoding, int len,
			InputStream content) throws IOException
	{
		OutputStream out = null;

		try
		{
			if ((encoding != null) && (encoding.length() > 0))
			{
				contentType = contentType + "; charset=" + encoding;
			}
			res.setContentType(contentType);
			// res.addHeader("Content-Disposition", disposition);
			res.setContentLength(len);

			// set the buffer of the response to match what we are reading from the request
			if (len < STREAM_BUFFER_SIZE)
			{
				res.setBufferSize(len);
			}
			else
			{
				res.setBufferSize(STREAM_BUFFER_SIZE);
			}

			out = res.getOutputStream();

			// chunk
			byte[] chunk = new byte[STREAM_BUFFER_SIZE];
			int lenRead;
			while ((lenRead = content.read(chunk)) != -1)
			{
				out.write(chunk, 0, lenRead);
			}
		}
		catch (Throwable e)
		{
			// M_log.warn("sendContentBinary (while streaming, ignoring): " + e);
		}
		finally
		{
			// be a good little program and close the stream - freeing up valuable system resources
			if (content != null)
			{
				content.close();
			}

			if (out != null)
			{
				try
				{
					out.close();
				}
				catch (Throwable ignore)
				{
				}
			}
		}
	}

	protected void sendContentText(HttpServletRequest req, HttpServletResponse res, String contentType, String text) throws IOException
	{
		// text/url - send a redirect to the URL
		if (contentType.equals("text/url"))
		{
			res.sendRedirect(text);
		}

		// text/anything but html
		else if (!contentType.endsWith("/html"))
		{
			PrintWriter out = res.getWriter();
			res.setContentType("text/html");

			// send it as html in a PRE section
			out.println("<html><head>");
			out.println("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
			out.println("</head><body>");
			out.print("<pre>");
			out.print(text);
			out.println("</pre>");
			out.println("</body></html>");
		}

		// text/html
		else
		{
			PrintWriter out = res.getWriter();
			res.setContentType("text/html");

			// if just a fragment, wrap it into a full document
			boolean fragment = !text.startsWith("<html");
			if (fragment)
			{
				out.println("<html><head>");
				out.println("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
				out.println("<script type=\"text/javascript\" src=\"/ckeditor/ckeditor/plugins/ckeditor_wiris/core/WIRISplugins.js?viewer=image\" defer=\"defer\"></script>");
				out.println("</head><body>");
			}

			// out.print(accessToCdpDoc(text, false));
			out.print(text);

			if (fragment)
			{
				out.println("</body></html>");
			}
		}
	}

	/**
	 * @return The ContentTypeImageService, via the component manager.
	 */
	private ContentTypeImageService contentTypeImageService()
	{
		return (ContentTypeImageService) ComponentManager.get(ContentTypeImageService.class);
	}

	/**
	 * @return The SecurityService, via the component manager.
	 */
	private SecurityService securityService()
	{
		return (SecurityService) ComponentManager.get(SecurityService.class);
	}

	/**
	 * @return The SessionManager, via the component manager.
	 */
	private SessionManager sessionManager()
	{
		return (SessionManager) ComponentManager.get(SessionManager.class);
	}

	/**
	 * @return The SiteResourcesService, via the component manager.
	 */
	private SiteResourcesService siteResourcesService()
	{
		return (SiteResourcesService) ComponentManager.get(SiteResourcesService.class);
	}

	/**
	 * @return The SiteService, via the component manager.
	 */
	private SiteService siteService()
	{
		return (SiteService) ComponentManager.get(SiteService.class);
	}

	/**
	 * @return The ThreadLocalManager, via the component manager.
	 */
	private ThreadLocalManager threadLocalManager()
	{
		return (ThreadLocalManager) ComponentManager.get(ThreadLocalManager.class);
	}
}
