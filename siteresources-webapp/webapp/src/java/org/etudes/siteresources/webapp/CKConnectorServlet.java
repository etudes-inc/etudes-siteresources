/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/siteresources/trunk/siteresources-webapp/webapp/src/java/org/etudes/siteresources/webapp/CKConnectorServlet.java $
 * $Id: CKConnectorServlet.java 12369 2015-12-23 21:20:37Z ggolden $
 ***********************************************************************************
 *
 * Copyright (c) 2013, 2014, 2015 Etudes, Inc.
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
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import net.coobird.thumbnailator.Thumbnails;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.siteresources.api.SitePlacement;
import org.etudes.siteresources.api.SiteResourcesService;
import org.etudes.util.DateHelper;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.thread_local.api.ThreadLocalManager;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Connect the ckfinder browser component to site resources.
 * 
 * Implements the CKFinder server side connector: http://docs.cksource.com/CKFinder_2.x/Server_Side_Integration/
 */
public class CKConnectorServlet extends HttpServlet
{
	/** The name of the cookie we use to keep sakai session. */
	public static final String SESSION_COOKIE = "JSESSIONID";

	protected static int ACL_FileDelete = 128;
	protected static int ACL_FileRename = 64;

	protected static int ACL_FileUpload = 32;
	protected static int ACL_FileView = 16;
	protected static int ACL_FolderCreate = 2;
	protected static int ACL_FolderDelete = 8;
	protected static int ACL_FolderRename = 4;
	protected static int ACL_FolderView = 1;
	protected static int ACL_z_ALL = ACL_FolderView + ACL_FileView + ACL_FileUpload + ACL_FileRename + ACL_FileDelete;
	protected static final int MAX_BUFFER_SIZE = 1024;
	/** The name of the system property that will be used when setting the value of the session cookie. */
	protected static final String SAKAI_SERVERID = "sakai.serverId";

	/** The chunk size used when streaming (100k). */
	protected static final int STREAM_BUFFER_SIZE = 102400;

	/** Our log. */
	private static Log M_log = LogFactory.getLog(CKConnectorServlet.class);

	private static final long serialVersionUID = -4227260157927283065L;

	/** CKFinder license information. */
	protected String licenseKey = null;
	protected String licenseName = null;

	public void init() throws ServletException
	{
		// read configuration - once the config service is ready
		ComponentManager.whenAvailable(ServerConfigurationService.class, new Runnable()
		{
			public void run()
			{
				licenseKey = serverConfigurationService().getString("ckfinder.licenseKey");
				licenseName = serverConfigurationService().getString("ckfinder.licenseName");
			}
		});
	}

	protected String accessUrl(String siteId, String resourceId)
	{
		return "/resources/access/" + siteId + "/site/0" + resourceId;
	}

	/**
	 * Seed the response document with common elements.
	 * 
	 * @param doc
	 * @param commandStr
	 * @param type
	 * @param currentFolder
	 * @return
	 */
	protected Node createCommonXml(Document doc, String type, String currentFolder, String siteId)
	{
		// URL to the folder for display (and for inclusion into the ckeditor source)
		String curFolderUrl = accessUrl(siteId, currentFolder);

		Element root = doc.createElement("Connector");
		doc.appendChild(root);
		root.setAttribute("resourceType", type);

		Element errEl = doc.createElement("Error");
		errEl.setAttribute("number", "0");
		root.appendChild(errEl);

		Element element = doc.createElement("CurrentFolder");
		element.setAttribute("path", currentFolder);
		element.setAttribute("url", curFolderUrl);
		element.setAttribute("acl", "255");
		root.appendChild(element);

		return root;
	}

	/**
	 * create a Document object for the return
	 */
	protected Document createDocument()
	{
		Document document = null;
		try
		{
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			document = builder.newDocument();
		}
		catch (ParserConfigurationException e)
		{
			M_log.warn("createDocument: " + e.toString());
		}
		return document;
	}

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		Session s = establishSessionFromCookie(request);
		try
		{
			// request details from the ckfinder
			String commandStr = request.getParameter("command");
			// type is "Images" when we are in the image dialog, "Files" when we are in the link dialog
			String type = request.getParameter("type");
			String subFolder = request.getParameter("currentFolder");
			String siteId = request.getParameter("siteId");
			String userId = s.getUserId(); // request.getParameter("userId");
			String rtype = request.getParameter("rtype");
			if (type == null) type = rtype;
			Site site = null;
			try
			{
				site = siteService().getSite(siteId);
			}
			catch (IdUnusedException e)
			{
			}

			String currentFolderForResource = "";
			if (subFolder != null && !subFolder.equals("/"))
			{
				currentFolderForResource = currentFolderForResource + subFolder.substring(1, subFolder.length());
			}
			String currentFolder = "/" + currentFolderForResource;

			// called when the finder is started
			if ("Init".equals(commandStr))
			{
				Document document = createDocument();
				getInit(site, document, type, currentFolder);
				respondInXML(document, response);
			}

			// called to build up the folder hierarchy: once for the "currentFolder", and then once for each folder reported within
			else if ("GetFolders".equals(commandStr))
			{
				Document document = createDocument();
				Node root = createCommonXml(document, type, currentFolder, siteId);
				getFolders(siteId, userId, currentFolder, root, document);
				respondInXML(document, response);
			}

			// called to get the list of files for "currentFolder" (which has "subFolder" appended already)
			else if ("GetFiles".equals(commandStr))
			{
				Document document = createDocument();
				Node root = createCommonXml(document, type, currentFolder, siteId);
				getFiles(siteId, userId, currentFolder, root, document, type);
				respondInXML(document, response);
			}

			// called when the user selects (via the right click menu) to download a file
			else if ("DownloadFile".equals(commandStr))
			{
				String fileName = request.getParameter("FileName");
				String resourceName = currentFolderForResource + fileName;

				// find the resource
				SitePlacement p = siteResourcesService().getSitePlacement(siteId, resourceName);
				if (p != null)
				{
					downloadContent(request, response, p);
				}
				else
				{
					response.sendError(HttpServletResponse.SC_BAD_REQUEST);
				}
			}

			// called for a thumbnail of an image resource
			else if ("Thumbnail".equals(commandStr))
			{
				String fileName = request.getParameter("FileName");
				String resourceName = currentFolderForResource + fileName;

				// find the resource
				SitePlacement p = siteResourcesService().getSitePlacement(siteId, resourceName);
				if (p != null)
				{
					// stream a newly created thumbnail
					response.setContentType("image/png");
					OutputStream out = response.getOutputStream();
					try
					{
						// http://code.google.com/p/thumbnailator/
						Thumbnails.of(p.getSiteResource().getStream()).size(80, 80).outputFormat("png").toOutputStream(out);
					}
					finally
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
		}
		finally
		{
			// clear any bound current values
			threadLocalManager().clear();
		}
	}

	/**
	 * Manage the Post requests (FileUpload).<br>
	 * 
	 * The servlet accepts commands sent in the following format:<br>
	 * connector?Command=FileUpload&Type=ResourceType<br>
	 * <br>
	 * It stores the file (renaming it in case a file with the same name exists) and then return an HTML file with a javascript command in it.
	 * 
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		Map<String, Object> params = null;
		try
		{
			Session s = establishSessionFromCookie(request);

			params = processBody(request);

			response.setContentType("text/plain; charset=UTF-8");
			// response.setHeader("Cache-Control", "no-cache");

			String command = request.getParameter("command");
			// String type = request.getParameter("type");
			String subFolder = request.getParameter("currentFolder");
			String type = request.getParameter("type");
			String rtype = request.getParameter("rtype");
			if (type == null) type = rtype;
			String siteId = request.getParameter("siteId");

			// String userId = s.getUserId(); // request.getParameter("userId");
			// Site site = null;
			// try
			// {
			// site = siteService().getSite(siteId);
			// }
			// catch (IdUnusedException e)
			// {
			// }

			String currentFolder = "";
			if (subFolder != null && !subFolder.equals("/"))
			{
				currentFolder = currentFolder + subFolder.substring(1, subFolder.length());
			}

			// security - site update permission

			if (command.equals("FileUpload"))
			{
				String responseType = request.getParameter("response_type");
				String cKFinderFuncNum = request.getParameter("CKFinderFuncNum");
				FileItem upload = (FileItem) params.get("upload");
				if (upload != null)
				{
					String resourceName = upload.getName();
					int length = (int) upload.getSize();

					String mimeType = upload.getContentType();
					mimeType = improveMimeType(resourceName, mimeType);

					InputStream contents = upload.getInputStream();

					// reject non-type files
					if (matchesType(mimeType, type))
					{
						// make the name unique
						resourceName = siteResourcesService().uniqueResourceName(subFolder, resourceName, siteId);

						// add a new SiteResource
						siteResourcesService().addSiteResource(mimeType, length, contents, siteId, currentFolder + resourceName);

						// response for type "txt" - text/plain
						response.setContentType("text/plain; charset=UTF-8");
						response.setHeader("Cache-Control", "no-cache");

						// String rv = "<script type=\"text/javascript\">" + resourceName + "|";
						String rv = resourceName + "|"; // add any error message after the |
						OutputStream out = response.getOutputStream();
						out.write(rv.getBytes("UTF-8"));
						out.close();

						// for no responseType - text/html
						// <script type="text/javascript">
						// window.parent.OnUploadCompleted( 0, '' ) ;
						// </script>

						// for cKFinderFuncNum set - text/html fileUrl is the access URL to the uploaded file
						// <script type='text/javascript'>window.parent.CKFINDER.tools.callFunction(funcNum, fileUrl, message)</script>
					}
				}
			}
			else if (command.equals("QuickUploadAttachment"))
			{
				InputStream contents = null;
				String resourceName = "";

				contents = request.getInputStream();
				InputStream origContents = contents;
				String mimeType = request.getHeader("Content-Type");

				// If there's no filename, make a guid name with the mime extension?

				if (contents != null)
				{
					if ("".equals(resourceName))
					{
						resourceName = UUID.randomUUID().toString() + ".wav";
					}
					mimeType = improveMimeType(resourceName, mimeType);

					if (siteId == null) siteId = (String) this.sessionManager().getCurrentSession().getAttribute("ck.siteId");

					siteResourcesService().addSiteResource(mimeType, 0, contents, siteId, "Home/" + resourceName);

					// response for type "txt" - text/plain
					response.setContentType("text/plain; charset=UTF-8");
					response.setHeader("Cache-Control", "no-cache");

					// String rv = "<script type=\"text/javascript\">" + resourceName + "|";
					OutputStream out = response.getOutputStream();
					String url = accessUrl(siteId, "/Home/") + resourceName;
					out.write(url.getBytes("UTF-8"));
					out.close();
				}
			}
			else if (command.equals("CopyFiles") || command.equals("MoveFiles") || command.equals("DeleteFiles"))
			{
				Document document = createDocument();
				Node root = createCommonXml(document, type, currentFolder, siteId);

				// which resources
				List<SitePlacement> placements = new ArrayList<SitePlacement>();
				int i = 0;
				String paramName = "files[" + i + "][name]";
				while (request.getParameter(paramName) != null)
				{
					String name = request.getParameter(paramName);
					String folder = request.getParameter("files[" + i + "][folder]");
					// String options = request.getParameter("files[" + i + "][options]");
					// String typex = request.getParameter("files[" + i + "][type]");

					SitePlacement p = siteResourcesService().getSitePlacement(siteId, folder.substring(1, folder.length()) + name);
					if (p != null)
					{
						placements.add(p);
					}

					i++;
					paramName = "files[" + (i) + "][name]";
				}

				if (command.equals("DeleteFiles"))
				{
					// TODO: fail if in use?
					String fail = null;
					for (SitePlacement p : placements)
					{
						// check for any tool references
						if (siteResourcesService().hasToolReferences(p))
						{
							if (fail == null)
							{
								fail = "Not Deleted: ";
							}

							fail += p.getFileName() + " ";
						}
					}

					if (fail == null)
					{
						for (SitePlacement p : placements)
						{
							siteResourcesService().removeSitePlacement(p);

							Element deletedEl = document.createElement("DeletedFile");
							root.appendChild(deletedEl);
							deletedEl.setAttribute("name", p.getFileName());
						}
					}

					else
					{
						fail += "in use.";
						NodeList nodes = ((Element) root).getElementsByTagName("Error");
						Element errNode = (Element) nodes.item(0);
						errNode.setAttribute("number", "1");
						errNode.setAttribute("text", fail);
					}
				}

				else if (command.equals("MoveFiles"))
				{
					// TODO: fail if file in use?
					String fail = null;
					for (SitePlacement p : placements)
					{
						if (siteResourcesService().hasToolReferences(p))
						{
							if (fail == null)
							{
								fail = "Not moved: ";
							}
							fail += p.getFileName() + " ";
						}
					}

					if (fail == null)
					{
						// TODO: (if not fail if in use) update any uses of these resources to reflect the resulting path in the name
						int count = 0;
						for (SitePlacement p : placements)
						{
							// make the name unique
							String resourceName = p.getFileName();
							resourceName = siteResourcesService().uniqueResourceName(subFolder, resourceName, siteId);

							count++;
							siteResourcesService().renameSitePlacement(p, currentFolder + resourceName);
						}

						Element el = document.createElement("MoveFiles");
						root.appendChild(el);
						el.setAttribute("moved", Integer.toString(count));
					}
					else
					{
						fail += " in use.";
						NodeList nodes = ((Element) root).getElementsByTagName("Error");
						Element errNode = (Element) nodes.item(0);
						errNode.setAttribute("number", "1");
						errNode.setAttribute("text", fail);
					}
				}

				else if (command.equals("CopyFiles"))
				{
					int count = 0;
					for (SitePlacement p : placements)
					{
						// make the name unique
						String resourceName = p.getFileName();
						resourceName = siteResourcesService().uniqueResourceName(subFolder, resourceName, siteId);

						count++;
						siteResourcesService().addSitePlacement(p.getSiteResource(), siteId, currentFolder + resourceName);
					}

					Element el = document.createElement("CopyFiles");
					root.appendChild(el);
					el.setAttribute("copied", Integer.toString(count));
				}

				respondInXML(document, response);
			}

			else if (command.equals("RenameFile"))
			{
				Document document = createDocument();
				Node root = createCommonXml(document, type, currentFolder, siteId);

				String fileName = request.getParameter("fileName");
				String newFileName = request.getParameter("newFileName");

				SitePlacement p = siteResourcesService().getSitePlacement(siteId, currentFolder + fileName);
				if (p != null)
				{
					// TODO: fail it new name exists?
					String fail = null;
					SitePlacement currentPlacement = siteResourcesService().getSitePlacement(siteId, currentFolder + newFileName);
					if (currentPlacement != null)
					{
						fail = "Not renamed: " + p.getFileName() + " exists.";
					}

					// TODO: fail if in use
					if (fail == null)
					{
						if (siteResourcesService().hasToolReferences(p))
						{
							fail = "Not renamed: " + p.getFileName() + " in use.";
						}
					}

					if (fail == null)
					{
						// TODO: (if not fail if in use) update any uses of these resources to reflect the resulting name
						siteResourcesService().renameSitePlacement(p, currentFolder + newFileName);

						Element renamedEl = document.createElement("RenamedFile");
						root.appendChild(renamedEl);
						renamedEl.setAttribute("name", fileName);
						renamedEl.setAttribute("newName", newFileName);
					}
					else
					{
						NodeList nodes = ((Element) root).getElementsByTagName("Error");
						Element errNode = (Element) nodes.item(0);
						errNode.setAttribute("number", "1");
						errNode.setAttribute("text", fail);
					}
				}

				respondInXML(document, response);
			}
		}
		finally
		{
			processBodyDone(params);

			// clear any bound current values
			threadLocalManager().clear();
		}
	}

	/**
	 * Send the requested site resource as a save-able download
	 * 
	 * @param req
	 *        request
	 * @param res
	 *        response
	 * @param placement
	 *        The SitePlacement.
	 * @throws IOException
	 */
	protected void downloadContent(HttpServletRequest req, HttpServletResponse res, SitePlacement placement) throws IOException
	{
		OutputStream out = null;
		int len = placement.getSiteResource().getLength();
		InputStream content = placement.getSiteResource().getStream();
		if (content == null)
		{
			res.sendError(HttpServletResponse.SC_BAD_REQUEST);
		}

		try
		{
			res.setContentType(placement.getSiteResource().getMimeType());
			res.setContentLength(len);
			res.setHeader("Cache-Control", "cache, must-revalidate");
			res.setHeader("Pragma", "public");
			res.setHeader("Expires", "0");
			res.setHeader("Content-Disposition", "attachment; filename=\"" + placement.getFileName() + "\"");

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
			M_log.warn("sendContentBinary (while streaming, ignoring): " + e);
		}
		finally
		{
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

	/**
	 * Check for a session cookie - and if found, set that session as the current session
	 * 
	 * @param req
	 *        The request object.
	 * @return The Session object if found, else null.
	 */
	protected Session establishSessionFromCookie(HttpServletRequest req)
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
	 * Respond to the GetFiles command.
	 * 
	 * @param siteId
	 * @param userId
	 * @param currentFolder
	 * @param root
	 * @param doc
	 * @param type
	 */
	protected void getFiles(String siteId, String userId, String currentFolder, Node root, Document doc, String type)
	{
		Element files = doc.createElement("Files");
		root.appendChild(files);

		// collect the site resources defined for the site
		List<SitePlacement> placements = siteResourcesService().getSitePlacements(siteId);
		for (SitePlacement p : placements)
		{
			// filter based on request type (Files or Images or Flash)
			if (!matchesType(p.getSiteResource().getMimeType(), type)) continue;

			// filter based on path
			String[] path = p.getFilePath();
			if (("/".equals(currentFolder) && (path == null))
					|| ((path != null) && (path.length == 2) && (currentFolder.equals("/" + path[0] + "/"))))
			{
				Element element = doc.createElement("File");
				element.setAttribute("name", p.getFileName());
				element.setAttribute("date", timeDisplayInUserZone(p.getSiteResource().getDate().getTime()));
				element.setAttribute("size", lengthInK(p.getSiteResource().getLength()));

				files.appendChild(element);
			}
		}
	}

	/**
	 * Respond to the GetFolders command.
	 * 
	 * @param siteId
	 * @param userId
	 * @param currentFolder
	 * @param root
	 * @param doc
	 */
	protected void getFolders(String siteId, String userId, String currentFolder, Node root, Document doc)
	{
		Element folders = doc.createElement("Folders");
		root.appendChild(folders);

		// ACL=49: value to disable context menu options

		// fixed set of folders
		// TODO: by tool inclusion in site?
		if (currentFolder.equals("/"))
		{
			Element element = doc.createElement("Folder");
			// element.setAttribute("url", this.accessUrl(siteId, currentFolder + "/General/"));
			element.setAttribute("name", "General");
			element.setAttribute("acl", Integer.toString(ACL_z_ALL));
			element.setAttribute("hasChildren", "false");
			folders.appendChild(element);

			element = doc.createElement("Folder");
			element.setAttribute("name", "Home");
			element.setAttribute("acl", Integer.toString(ACL_z_ALL));
			element.setAttribute("hasChildren", "false");
			folders.appendChild(element);

			// element = doc.createElement("Folder");
			// element.setAttribute("name", "Schedule");
			// element.setAttribute("acl", "49");
			// element.setAttribute("hasChildren", "false");
			// folders.appendChild(element);
			//
			// element = doc.createElement("Folder");
			// element.setAttribute("name", "Announcements");
			// element.setAttribute("acl", "49");
			// element.setAttribute("hasChildren", "false");
			// folders.appendChild(element);
			//
			// element = doc.createElement("Folder");
			// element.setAttribute("name", "Syllabus");
			// element.setAttribute("acl", "49");
			// element.setAttribute("hasChildren", "false");
			// folders.appendChild(element);
			//
			// element = doc.createElement("Folder");
			// element.setAttribute("name", "Modules");
			// element.setAttribute("acl", "49");
			// element.setAttribute("hasChildren", "false");
			// folders.appendChild(element);
			//
			// element = doc.createElement("Folder");
			// element.setAttribute("name", "ATS");
			// element.setAttribute("acl", "49");
			// element.setAttribute("hasChildren", "false");
			// folders.appendChild(element);
			//
			// element = doc.createElement("Folder");
			// element.setAttribute("name", "Resources");
			// element.setAttribute("acl", "49");
			// element.setAttribute("hasChildren", "false");
			// folders.appendChild(element);
		}
	}

	/**
	 * Create the DOM for the init request.
	 * 
	 * @param doc
	 * @param type
	 * @param currentFolder
	 * @return
	 */
	protected Node getInit(Site site, Document doc, String type, String currentFolder)
	{
		Element root = doc.createElement("Connector");
		doc.appendChild(root);

		Element errEl = doc.createElement("Error");
		errEl.setAttribute("number", "0");
		root.appendChild(errEl);

		Element ciEl = doc.createElement("ConnectorInfo");
		ciEl.setAttribute("enabled", "true");
		ciEl.setAttribute("imgWidth", "");
		ciEl.setAttribute("imgHeight", "");
		ciEl.setAttribute("s", this.licenseName);
		ciEl.setAttribute("c", this.licenseKey);
		ciEl.setAttribute("thumbsEnabled", "true");
		// ciEl.setAttribute("thumbsUrl", "");
		// ciEl.setAttribute("thumbsDirectAccess", "true");
		root.appendChild(ciEl);

		Element rtsEl = doc.createElement("ResourceTypes");
		root.appendChild(rtsEl);

		// // configured in megs, report in bytes
		long maxSize = Long.parseLong(serverConfigurationService().getString("content.upload.max", "1")) * 1024 * 1024;

		// URL to access the folder
		String curFolderUrl = accessUrl(site.getId(), currentFolder);

		// if ((this.types != null) && (this.types.size() > 0))
		// {
		// if (this.types.get(type) != null)
		// {
		// // Properties of a resource type(Files, Images and Flash) can be configured in the settings xml
		// ResourceType rt = (ResourceType) this.types.get(type);
		Element rtEl = doc.createElement("ResourceType");
		rtEl.setAttribute("name", ((site == null) ? "Resources" : site.getTitle() + " " + type));
		rtEl.setAttribute("url", curFolderUrl);
		rtEl.setAttribute("allowedExtensions", ""); // bmp,gif,jpeg,jpg,png
		rtEl.setAttribute("deniedExtensions", "");
		rtEl.setAttribute("maxSize", String.valueOf(maxSize));
		rtEl.setAttribute("defaultView", "thumbnails"); // or "list"
		rtEl.setAttribute("hash", "4d8ddfd385d0952b");
		rtEl.setAttribute("hasChildren", "true"); // TODO: when we have sub-folders
		rtEl.setAttribute("acl", Integer.toString(ACL_z_ALL));
		rtsEl.appendChild(rtEl);
		// }
		// }

		return root;
	}

	/**
	 * IE 11 sends files in with the mime type "application/octet-stream" when it really should be "image/*". See if we can get a better mime type from the file extension.
	 * 
	 * @param name
	 *        The file name.
	 * @param type
	 *        The given mime type.
	 * @return A possibly improved mime type.
	 */
	protected String improveMimeType(String name, String type)
	{
		if (((type == null) || "application/octet-stream".equalsIgnoreCase(type)) && (name != null))
		{
			// isolate the file extension
			int pos = name.lastIndexOf(".");
			if (pos != -1)
			{
				String ext = name.substring(pos + 1);

				// recognize some image extensions: jpg jpeg gif png x-png bmp bm tif tiff
				if ("jpg".equalsIgnoreCase(ext))
					return "image/jpeg";
				else if ("jpeg".equalsIgnoreCase(ext))
					return "image/jpeg";
				else if ("gif".equalsIgnoreCase(ext))
					return "image/gif";
				else if ("png".equalsIgnoreCase(ext))
					return "image/png";
				else if ("x-png".equalsIgnoreCase(ext))
					return "image/png";
				else if ("bmp".equalsIgnoreCase(ext))
					return "image/bmp";
				else if ("bm".equalsIgnoreCase(ext))
					return "image/bmp";
				else if ("tif".equalsIgnoreCase(ext))
					return "image/tiff";
				else if ("tiff".equalsIgnoreCase(ext))
					return "image/tiff";
				else if ("html".equalsIgnoreCase(ext))
					return "text/html";
				else if ("htm".equalsIgnoreCase(ext))
					return "text/html";
				else if ("pdf".equalsIgnoreCase(ext))
					return "application/pdf";
				else if ("doc".equalsIgnoreCase(ext))
					return "application/msword";
				else if ("docm".equalsIgnoreCase(ext))
					return "application/vnd.ms-word.document.macroEnabled.12";
				else if ("docx".equalsIgnoreCase(ext))
					return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
				else if ("ppt".equalsIgnoreCase(ext))
					return "application/vnd.ms-powerpoint";
				else if ("ppsx".equalsIgnoreCase(ext))
					return "application/vnd.openxmlformats-officedocument.presentationml.slideshow";
				else if ("url".equalsIgnoreCase(ext))
					return "text/url";
				else if ("webloc".equalsIgnoreCase(ext))
					return "text/url";
				else if ("xls".equalsIgnoreCase(ext))
					return "application/vnd.ms-excel";
				else if ("xlsx".equalsIgnoreCase(ext))
					return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
				else if ("pptx".equalsIgnoreCase(ext)) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
			}
		}

		return type;
	}

	/**
	 * Compute a rounded kbytes string from an int byte count.
	 * 
	 * @param len
	 *        The bytes.
	 * @return The "K" value.
	 */
	protected String lengthInK(int len)
	{
		if (len < 1024) return "1";
		return Integer.toString(Math.round(((float) len) / 1024f));
	}

	/**
	 * Check if the site resource's mime type is appropriate for the requested type.
	 * 
	 * @param resource
	 * @param type
	 * @return
	 */
	protected boolean matchesType(String mime, String type)
	{
		// site title then space then the real type
		String[] parts = type.split(" ");
		type = parts[parts.length - 1];

		if (type.equals("Files")) return true;

		if (type.equals("Flash")) return mime.equals("application/x-shockwave-flash");

		if (type.equals("Images")) return mime.startsWith("image");

		return true;
	}

	/**
	 * Process the request URL parameters and post body data to form a map of all parameters
	 * 
	 * @param req
	 *        The request.
	 * @return The map of parameters: keyed by parameter name, values either String, String[], or (later) file.
	 */
	protected Map<String, Object> processBody(HttpServletRequest req)
	{
		// keyed by name, value can be String, String[], or (later) a file
		Map<String, Object> rv = new HashMap<String, Object>();

		DiskFileItemFactory factory = new DiskFileItemFactory();
		factory.setSizeThreshold(30 * 1024);
		ServletFileUpload upload = new ServletFileUpload(factory);

		String encoding = req.getCharacterEncoding();
		if ((encoding != null) && (encoding.length() > 0)) upload.setHeaderEncoding(encoding);

//		System.out.println("SiteResources: tracker: " + factory.getFileCleaningTracker() + " threshold: " + factory.getSizeThreshold() + " repo: "
//				+ factory.getRepository() + " fileSizeMax: " + upload.getFileSizeMax() + " sizeMax: " + upload.getSizeMax());

		// Parse the request
		try
		{
			List<FileItem> items = upload.parseRequest(req);
			for (FileItem item : items)
			{
				if (item.isFormField())
				{
//					System.out.println("SiteResources: isFormField: " + item.getFieldName() + " : inMemory: " + item.isInMemory() + " size: "
//							+ item.getSize());

					// the key
					String key = item.getFieldName();

					// the value
					String value = item.getString();

					// merge into our map of key / values
					Object current = rv.get(item.getFieldName());

					// if not there, start with the value
					if (current == null)
					{
						rv.put(key, value);
					}

					// if we find a value, change it to an array containing both
					else if (current instanceof String)
					{
						String[] values = new String[2];
						values[0] = (String) current;
						values[1] = value;
						rv.put(key, values);
					}

					// if an array is found, extend our current values to include this additional one
					else if (current instanceof String[])
					{
						String[] currentArray = (String[]) current;
						String[] values = new String[currentArray.length + 1];
						System.arraycopy(currentArray, 0, values, 0, currentArray.length);
						values[currentArray.length] = value;
						rv.put(key, values);
					}

					// clean up if a temp file was used
					if (!item.isInMemory())
					{
						item.delete();
					}
				}
				else
				{
//					System.out.println("SiteResources: file: " + ((DiskFileItem) item).getStoreLocation().getPath() + " : " + item.getFieldName()
//							+ " : inMemory: " + item.isInMemory() + " size: " + item.getSize());

					rv.put(item.getFieldName(), item);
				}
			}
		}
		catch (FileUploadException e)
		{
			// M_log.warn("processBody: exception:" + e);
		}

		// TODO: add URL parameters

		return rv;
	}

	/**
	 * For any file based parameters, delete the temp. files NOW, not waiting for (eventual, and maybe never) finalization.
	 * 
	 * @param parameters
	 *        The parameters (as parsed by processBody
	 */
	protected void processBodyDone(Map<String, Object> parameters)
	{
		if (parameters == null) return;
		for (Object value : parameters.values())
		{
			if (value instanceof DiskFileItem)
			{
				DiskFileItem dfi = (DiskFileItem) value;
				if (!dfi.isInMemory())
				{
					dfi.delete();
				}
			}
		}
	}

	/**
	 * Sends the response in XML format.
	 */
	protected void respondInXML(Document document, HttpServletResponse response)
	{
		if (document == null) return;
		response.setContentType("text/xml; charset=UTF-8");
		response.setHeader("Cache-Control", "no-cache");

		try
		{
			OutputStream out = response.getOutputStream();

			document.getDocumentElement().normalize();

			StringWriter stw = new StringWriter();
			TransformerFactory tFactory = TransformerFactory.newInstance();
			Transformer transformer = tFactory.newTransformer();

			DOMSource source = new DOMSource(document);

			StreamResult result = new StreamResult(stw);
			transformer.transform(source, result);
			out.write(stw.toString().getBytes("UTF-8"));
			out.flush();
			out.close();
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
	}

	/**
	 * @return time formatted for the user's time zone with month & date, hour, minute, am/pm
	 */
	protected String timeDisplayInUserZone(long timeStartMs)
	{
		// use the end-user's locale and time zone prefs
		Locale userLocale = DateHelper.getPreferredLocale(null);
		TimeZone userZone = DateHelper.getPreferredTimeZone(null);

		DateFormat dateFormat = new SimpleDateFormat("yyyyMMddkkmm", userLocale);
		dateFormat.setTimeZone(userZone);

		String rv = dateFormat.format(new Date(timeStartMs));
		return rv;
	}

	/**
	 * @return The ServerConfigurationService, via the component manager.
	 */
	private ServerConfigurationService serverConfigurationService()
	{
		return (ServerConfigurationService) ComponentManager.get(ServerConfigurationService.class);
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
