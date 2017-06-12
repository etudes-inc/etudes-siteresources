/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/siteresources/trunk/siteresources-webapp/webapp/src/java/org/etudes/siteresources/webapp/SiteResourcesServiceImpl.java $
 * $Id: SiteResourcesServiceImpl.java 12512 2016-01-11 22:11:14Z ggolden $
 ***********************************************************************************
 *
 * Copyright (c) 2013, 2014, 2015, 2016 Etudes, Inc.
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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.siteimport.api.SiteImportService;
import org.etudes.siteimport.api.SiteImporter;
import org.etudes.siteresources.api.HarvestReference;
import org.etudes.siteresources.api.SitePlacement;
import org.etudes.siteresources.api.SiteResource;
import org.etudes.siteresources.api.SiteResourceOwner;
import org.etudes.siteresources.api.SiteResourcesService;
import org.etudes.siteresources.api.ToolReference;
import org.etudes.util.Different;
import org.etudes.util.XrefHelper;
import org.sakaiproject.authz.api.SecurityAdvisor;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.db.api.SqlReader;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.exception.ServerOverloadException;
import org.sakaiproject.exception.TypeException;
import org.sakaiproject.id.cover.IdManager;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.util.StringUtil;

/**
 * SiteImportServiceImpl handles import of content from one site to another.
 */
public class SiteResourcesServiceImpl implements SiteResourcesService, SiteImporter
{
	protected class HarvestReferenceImpl implements HarvestReference
	{
		protected String from;
		protected String to;

		public HarvestReferenceImpl(String from, String to)
		{
			this.from = from;
			this.to = to;
		}

		public boolean equals(Object obj)
		{
			if (obj == null) return false;
			if (!(obj instanceof HarvestReferenceImpl)) return false;
			if (Different.different(this.from, ((HarvestReferenceImpl) obj).getFrom())) return false;
			if (Different.different(this.to, ((HarvestReferenceImpl) obj).getTo())) return false;

			return true;
		}

		public String getFrom()
		{
			return this.from;
		}

		public String getTo()
		{
			return this.to;
		}

		public int hashCode()
		{
			String hash = this.from + this.to;
			return hash.hashCode();
		}
	}

	protected class SitePlacementImpl implements SitePlacement
	{
		protected Long id;
		protected String name;
		protected String siteId;
		protected SiteResourceImpl siteResource;

		public SitePlacementImpl(Long id, String siteId, String name, SiteResourceImpl r)
		{
			this.id = id;
			this.name = name;
			this.siteId = siteId;
			this.siteResource = r;
		}

		public SitePlacementImpl(SitePlacement p)
		{
			this.id = p.getId();
			this.name = p.getName();
			this.siteId = p.getSiteId();
			this.siteResource = (SiteResourceImpl) p.getSiteResource();
		}

		public boolean equals(Object obj)
		{
			if (obj == null) return false;
			if (!(obj instanceof SitePlacementImpl)) return false;
			if (Different.different(this.id, ((SitePlacementImpl) obj).getId())) return false;
			if (Different.different(this.name, ((SitePlacementImpl) obj).getName())) return false;
			if (Different.different(this.siteId, ((SitePlacementImpl) obj).getSiteId())) return false;
			if (Different.different(this.siteResource.getId(), ((SitePlacementImpl) obj).siteResource.getId())) return false;

			return true;
		}

		public String getFileName()
		{
			if (this.name.indexOf("/") == -1) return getName();
			String[] parts = this.name.split("/");
			return parts[parts.length - 1];
		}

		public String[] getFilePath()
		{
			if (this.name.indexOf("/") == -1) return null;
			String[] parts = this.name.split("/");
			return parts;
		}

		public Long getId()
		{
			return this.id;
		}

		public String getName()
		{
			return this.name;
		}

		public String getSiteId()
		{
			return this.siteId;
		}

		public SiteResource getSiteResource()
		{
			return this.siteResource;
		}

		public int hashCode()
		{
			if (this.id != null) return this.id.hashCode();

			String hash = this.name + this.siteId + this.siteResource.getId().toString();
			return hash.hashCode();
		}

		protected void setId(Long id)
		{
			this.id = id;
		}

		protected void setName(String name)
		{
			this.name = name;
		}

		protected void setSiteId(String siteId)
		{
			this.siteId = siteId;
		}
	}

	protected class SiteResourceImpl implements SiteResource
	{
		protected Date date;
		protected Long id;
		protected int length;
		protected String mimeType;
		protected String storagePath;

		public SiteResourceImpl(Long id, Date date, int length, String mimeType, String storagePath)
		{
			this.id = id;
			this.date = date;
			this.length = length;
			this.mimeType = mimeType;
			this.storagePath = storagePath;
		}

		public boolean equals(Object obj)
		{
			if (obj == null) return false;
			if (!(obj instanceof SiteResource)) return false;
			return this.id.equals(((SiteResource) obj).getId());
		}

		public byte[] getBytes()
		{
			File file = new File(getFullPath());
			try
			{
				byte[] body = new byte[getLength()];
				FileInputStream in = new FileInputStream(file);

				in.read(body);
				in.close();

				return body;
			}
			catch (Throwable t)
			{
				M_log.warn("getBytes: " + t);
				return new byte[0];
			}
		}

		public Date getDate()
		{
			return this.date;
		}

		public Long getId()
		{
			return this.id;
		}

		public int getLength()
		{
			return this.length;
		}

		public String getMimeType()
		{
			return this.mimeType;
		}

		public String getStoragePath()
		{
			return this.storagePath;
		}

		public InputStream getStream()
		{
			File file = new File(getFullPath());
			try
			{
				FileInputStream in = new FileInputStream(file);
				return in;
			}
			catch (FileNotFoundException e)
			{
				M_log.warn("getStream: " + e);
				return null;
			}
		}

		public String getString()
		{
			try
			{
				String rv = new String(getBytes(), "UTF-8");
				return rv;
			}
			catch (UnsupportedEncodingException e)
			{
				return "";
			}
		}

		public ZipFile getZip()
		{
			try
			{
				ZipFile zip = new ZipFile(getFullPath());
				return zip;
			}
			catch (IOException e)
			{
			}
			return null;

		}

		public int hashCode()
		{
			return this.id.hashCode();
		}

		protected String getFullPath()
		{
			return fileSystemRoot + this.storagePath;
		}

		protected void setId(Long id)
		{
			this.id = id;
		}
	}

	protected class ToolReferenceImpl implements ToolReference
	{
		protected Long id;
		protected String name;
		protected String siteId;
		protected String tool;
		protected Long toolId;

		public ToolReferenceImpl(Long id, String siteId, String tool, Long toolId, String name)
		{
			this.id = id;
			this.name = name;
			this.siteId = siteId;
			this.tool = tool;
			this.toolId = toolId;
		}

		public boolean equals(Object obj)
		{
			if (obj == null) return false;
			if (!(obj instanceof ToolReferenceImpl)) return false;
			if (Different.different(this.id, ((ToolReferenceImpl) obj).getId())) return false;
			if (Different.different(this.name, ((ToolReferenceImpl) obj).getName())) return false;
			if (Different.different(this.siteId, ((ToolReferenceImpl) obj).getSiteId())) return false;
			if (Different.different(this.tool, ((ToolReferenceImpl) obj).getTool())) return false;
			if (Different.different(this.toolId, ((ToolReferenceImpl) obj).getToolId())) return false;

			return true;
		}

		public String getAccessUrl()
		{
			// /resources/access/SITEID/TOOL/TOOLID/PATH.../NAME
			return "/resources/access/" + this.siteId + "/" + this.tool + "/" + this.toolId.toString() + "/" + this.name;
		}

		public String getFileName()
		{
			if (this.name.indexOf("/") == -1) return getName();
			String[] parts = this.name.split("/");
			return parts[parts.length - 1];
		}

		public String[] getFilePath()
		{
			if (this.name.indexOf("/") == -1) return null;
			String[] parts = this.name.split("/");
			return parts;
		}

		public Long getId()
		{
			return this.id;
		}

		public String getName()
		{
			return this.name;
		}

		public String getSiteId()
		{
			return this.siteId;
		}

		public String getTool()
		{
			return this.tool;
		}

		public Long getToolId()
		{
			return this.toolId;
		}

		public int hashCode()
		{
			if (this.id != null) return this.id.hashCode();

			String hash = this.name + this.siteId + this.tool + this.toolId;
			return hash.hashCode();
		}

		protected void setId(Long id)
		{
			this.id = id;
		}

		protected void setName(String name)
		{
			this.name = name;
		}

		protected void setSiteId(String siteId)
		{
			this.siteId = siteId;
		}

		protected void setTool(Long toolId)
		{
			this.toolId = toolId;
		}

		protected void setTool(String tool)
		{
			this.tool = tool;
		}
	}

	/** The chunk size used when streaming (100k). */
	protected static final int STREAM_BUFFER_SIZE = 102400;

	/** Our log. */
	private static Log M_log = LogFactory.getLog(SiteResourcesServiceImpl.class);

	/** Should we run our SQL DDL or not on startup. */
	protected boolean autoDdl = false;

	/** The root path of our files in the file system. */
	protected String fileSystemRoot = null;

	/** Map of tool -> owner. */
	protected Map<String, SiteResourceOwner> owners = new HashMap<String, SiteResourceOwner>();

	/**
	 * Construct
	 */
	public SiteResourcesServiceImpl()
	{
		final SiteResourcesServiceImpl service = this;
		ComponentManager.whenAvailable(ServerConfigurationService.class, new Runnable()
		{
			public void run()
			{
				ComponentManager.whenAvailable(SqlService.class, new Runnable()
				{
					public void run()
					{
						ComponentManager.whenAvailable(SiteImportService.class, new Runnable()
						{
							public void run()
							{
								fileSystemRoot = serverConfigurationService().getString("siteResourcesRoot");
								if (!fileSystemRoot.endsWith("/")) fileSystemRoot += "/";

								String str = serverConfigurationService().getString("auto.ddl");
								boolean auto = autoDdl;
								if (str != null) auto = Boolean.valueOf(str).booleanValue();

								// if we are auto-creating our schema, check and create
								if (auto)
								{
									M_log.warn("running ddl");
									sqlService().ddl(this.getClass().getClassLoader(), "siteresources");
								}

								// register for site import
								siteImportService().registerImporter(service);
							}
						});
					}
				});
			}
		});
	}

	/**
	 * {@inheritDoc}
	 */
	public SitePlacement addSitePlacement(SiteResource resource, String siteId, String name)
	{
		final SitePlacementImpl p = new SitePlacementImpl(null, siteId, name, (SiteResourceImpl) resource);

		// add a record
		sqlService().transact(new Runnable()
		{
			public void run()
			{
				addPlacementTx(p);
			}
		}, "addUse: " + siteId);

		// if there are any existing references to this, inform the owners
		List<ToolReference> refs = getToolReferences(p);
		for (ToolReference ref : refs)
		{
			SiteResourceOwner owner = this.owners.get(ref.getTool());
			if (owner != null)
			{
				owner.informNewResource(ref, p);
			}
		}

		return p;
	}

	/**
	 * {@inheritDoc}
	 */
	public SitePlacement addSiteResource(String type, int length, InputStream contents, String siteId, String name)
	{
		// add the resource
		String fName = name;
		if (fName.indexOf("/") != -1)
		{
			String[] path = fName.split("/");
			fName = path[path.length - 1];
		}

		// TODO: consider scanning existing (site?) resource files, and on exact match, using that site resource instead of a new one

		SiteResource r = addResource(type, length, contents, fName);

		// register use
		SitePlacement p = addSitePlacement(r, siteId, name);

		return p;
	}

	/**
	 * {@inheritDoc}
	 */
	public ToolReference addToolReference(String siteId, String tool, Long toolId, String name)
	{
		final ToolReferenceImpl r = new ToolReferenceImpl(null, siteId, tool, toolId, name);

		// add a record
		sqlService().transact(new Runnable()
		{
			public void run()
			{
				addReferenceTx(r);
			}
		}, "addUse: " + siteId);

		return r;
	}

	/**
	 * {@inheritDoc}
	 */
	public void clearToolReferences(final String siteId, final String tool, final Long toolId)
	{
		// clear all the uses from this item
		sqlService().transact(new Runnable()
		{
			public void run()
			{
				clearToolReferencesTx(siteId, tool, toolId);
			}
		}, "clearToolReferences: " + siteId);
	}

	/**
	 * {@inheritDoc}
	 */
	public Set<ToolReference> collectIndirectReferences(Set<ToolReference> references, String siteId, String tool, Long toolId, String intoPath)
	{
		Set<ToolReference> rv = new HashSet<ToolReference>();

		// references we have processed - no need to process again
		Set<ToolReference> done = new HashSet<ToolReference>();

		// references we need to process
		Set<ToolReference> todo = new HashSet<ToolReference>();
		todo.addAll(references);

		// check each reference in todo - todo can change as we do this
		while (!todo.isEmpty())
		{
			ToolReference ref = todo.iterator().next();
			todo.remove(ref);

			// find the actual resource (it may not exist)
			SitePlacement p = getSitePlacement(ref.getSiteId(), ref.getName());
			if (p != null)
			{
				// we only need to scan html files
				if ("text/html".equalsIgnoreCase(p.getSiteResource().getMimeType()))
				{
					// record we have processed this
					done.add(ref);

					try
					{
						// look only for relative references, assuming that once we accept any html in site resources, we would have converted the URLs to relative, and any that are not relative now should be treated as to a URL in the wild.
						String html = new String(p.getSiteResource().getBytes(), "UTF-8");
						Set<ToolReference> refs = collectRelReferences(html, siteId, tool, toolId, intoPath);
						// TODO: check for collectSiteResourceReferences(), collectChsReferences() and collectChsPrivateDocsReferences() ???

						// add any found to our return set
						rv.addAll(refs);

						// add any found we have not processed yet to the processing queue
						for (ToolReference r : refs)
						{
							if (!done.contains(r))
							{
								todo.add(r);
							}
						}
					}
					catch (UnsupportedEncodingException e)
					{
					}
				}
			}
		}

		return rv;
	}

	/**
	 * {@inheritDoc}
	 */
	public String convertToFragment(String html)
	{
		String lowerHtml = html.toLowerCase();

		// if already a fragment
		if (lowerHtml.indexOf("<html") == -1) return html;

		// find the body
		int start = lowerHtml.indexOf("<body");
		if (start != -1)
		{
			start = lowerHtml.indexOf(">", start);
			if (start != -1) start++;
		}

		if (start == -1) start = 0;

		int end = -1;
		if (start > 0)
		{
			end = lowerHtml.indexOf("</body");
		}
		if (end == -1) end = html.length();

		String rv = html.substring(start, end);
		return rv;
	}

	/**
	 * {@inheritDoc}
	 */
	public String expandRelRefsInChs(String html, String base)
	{
		Pattern p = Pattern.compile("(src|href|value)[\\s]*=[\\s]*\"([^/][^\"]*)\"", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
		Matcher m = p.matcher(html);

		StringBuffer sb = new StringBuffer();

		while (m.find())
		{
			if (m.groupCount() == 2)
			{
				String ref = m.group(2);

				// is this really relative? The pattern would catch "http://"...
				if (ref.indexOf("://") == -1)
				{
					// add to base
					ref = base + ref;
				}

				m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + "=\"" + ref + "\""));
			}
		}

		m.appendTail(sb);

		return sb.toString();
	}

	/**
	 * {@inheritDoc}
	 */
	public SitePlacement getSitePlacement(String siteId, String resourceName)
	{
		// read
		final List<SitePlacement> placements = new ArrayList<SitePlacement>();

		String sql = "SELECT R.ID, R.UPLOAD_DATE, R.STORAGE_PATH, R.FILE_SIZE, R.MIME_TYPE, P.SITE_ID, P.NAME, P.ID FROM SITE_RESOURCES R JOIN SITE_RESOURCE_PLACEMENT P ON (R.ID = P.RESOURCE_ID)"
				+ " WHERE P.SITE_ID = ? AND P.NAME = ?";
		Object[] fields = new Object[2];
		fields[0] = siteId;
		fields[1] = resourceName;

		sqlService().dbRead(sql, fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					Long id = sqlService().readLong(result, 1);
					Date date = sqlService().readDate(result, 2);
					String storagePath = sqlService().readString(result, 3);
					int length = sqlService().readLong(result, 4).intValue();
					String mimeType = sqlService().readString(result, 5);
					String siteId = sqlService().readString(result, 6);
					String name = sqlService().readString(result, 7);
					Long placementId = sqlService().readLong(result, 8);
					SiteResourceImpl r = new SiteResourceImpl(id, date, length, mimeType, storagePath);
					SitePlacementImpl p = new SitePlacementImpl(placementId, siteId, name, r);

					placements.add(p);

					return null;
				}
				catch (SQLException e)
				{
					M_log.warn("getSitePlacement: " + e);
					return null;
				}
			}
		});

		if (placements.size() == 0) return null;
		return placements.get(0);
	}

	/**
	 * {@inheritDoc}
	 */
	public List<SitePlacement> getSitePlacements(String siteId)
	{
		// read
		final List<SitePlacement> placements = new ArrayList<SitePlacement>();

		String sql = "SELECT R.ID, R.UPLOAD_DATE, R.STORAGE_PATH, R.FILE_SIZE, R.MIME_TYPE, P.SITE_ID, P.NAME, P.ID FROM SITE_RESOURCES R JOIN SITE_RESOURCE_PLACEMENT P ON (R.ID = P.RESOURCE_ID)"
				+ " WHERE P.SITE_ID = ?";

		Object[] fields = new Object[1];
		fields[0] = siteId;

		sqlService().dbRead(sql, fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					Long id = sqlService().readLong(result, 1);
					Date date = sqlService().readDate(result, 2);
					String storagePath = sqlService().readString(result, 3);
					int length = sqlService().readLong(result, 4).intValue();
					String mimeType = sqlService().readString(result, 5);
					String siteId = sqlService().readString(result, 6);
					String name = sqlService().readString(result, 7);
					Long placementId = sqlService().readLong(result, 8);
					SiteResourceImpl r = new SiteResourceImpl(id, date, length, mimeType, storagePath);
					SitePlacementImpl p = new SitePlacementImpl(placementId, siteId, name, r);

					placements.add(p);

					return null;
				}
				catch (SQLException e)
				{
					M_log.warn("getSitePlacements: " + e);
					return null;
				}
			}
		});

		return placements;
	}

	/**
	 * {@inheritDoc}
	 */
	public SiteResource getSiteResource(Long id)
	{
		if (id == null) return null;

		// security ?

		// read
		final List<SiteResource> resources = new ArrayList<SiteResource>();

		String sql = "SELECT R.ID, R.UPLOAD_DATE, R.STORAGE_PATH, R.FILE_SIZE, R.MIME_TYPE FROM SITE_RESOURCES R WHERE R.ID = ?";
		Object[] fields = new Object[1];
		fields[0] = id;

		sqlService().dbRead(sql, fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					Long id = sqlService().readLong(result, 1);
					Date date = sqlService().readDate(result, 2);
					String storagePath = sqlService().readString(result, 3);
					int length = sqlService().readLong(result, 4).intValue();
					String mimeType = sqlService().readString(result, 5);
					SiteResourceImpl r = new SiteResourceImpl(id, date, length, mimeType, storagePath);
					resources.add(r);

					return null;
				}
				catch (SQLException e)
				{
					M_log.warn("getSiteResource: " + e);
					return null;
				}
			}
		});

		if (resources.size() == 0) return null;
		return resources.get(0);
	}

	/**
	 * {@inheritDoc}
	 */
	public String getToolId()
	{
		return "e3.resources";
	}

	/**
	 * {@inheritDoc}
	 */
	public ToolReference getToolReference(String siteId, String tool, Long toolId, String name)
	{
		final List<ToolReference> rv = new ArrayList<ToolReference>();

		String sql = "SELECT ID, SITE_ID, TOOL, TOOL_ID, NAME FROM SITE_RESOURCE_REFERENCE WHERE SITE_ID = ? AND TOOL = ? AND TOOL_ID = ? AND NAME = ?";
		Object[] fields = new Object[4];
		fields[0] = siteId;
		fields[1] = tool;
		fields[2] = toolId;
		fields[3] = name;

		sqlService().dbRead(sql, fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					Long id = sqlService().readLong(result, 1);
					String siteId = sqlService().readString(result, 2);
					String tool = sqlService().readString(result, 3);
					Long toolId = sqlService().readLong(result, 4);
					String name = sqlService().readString(result, 5);

					ToolReferenceImpl ref = new ToolReferenceImpl(id, siteId, tool, toolId, name);
					rv.add(ref);

					return null;
				}
				catch (SQLException e)
				{
					M_log.warn("getToolReferences: " + e);
					return null;
				}
			}
		});

		if (rv.isEmpty()) return null;
		return rv.get(0);
	}

	/**
	 * {@inheritDoc}
	 */
	public List<ToolReference> getToolReferences(SitePlacement placement)
	{
		final List<ToolReference> rv = new ArrayList<ToolReference>();

		String sql = "SELECT ID, SITE_ID, TOOL, TOOL_ID, NAME FROM SITE_RESOURCE_REFERENCE WHERE SITE_ID = ? AND NAME = ?";
		Object[] fields = new Object[2];
		fields[0] = placement.getSiteId();
		fields[1] = placement.getName();

		sqlService().dbRead(sql, fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					Long id = sqlService().readLong(result, 1);
					String siteId = sqlService().readString(result, 2);
					String tool = sqlService().readString(result, 3);
					Long toolId = sqlService().readLong(result, 4);
					String name = sqlService().readString(result, 5);
					ToolReferenceImpl ref = new ToolReferenceImpl(id, siteId, tool, toolId, name);
					rv.add(ref);

					return null;
				}
				catch (SQLException e)
				{
					M_log.warn("getToolReferences: " + e);
					return null;
				}
			}
		});

		return rv;
	}

	/**
	 * {@inheritDoc}
	 */
	public List<ToolReference> getToolReferences(String siteId, String tool, Long toolId)
	{
		final List<ToolReference> rv = new ArrayList<ToolReference>();

		String sql = "SELECT ID, SITE_ID, TOOL, TOOL_ID, NAME FROM SITE_RESOURCE_REFERENCE WHERE SITE_ID = ? AND TOOL = ? AND TOOL_ID = ?";
		Object[] fields = new Object[3];
		fields[0] = siteId;
		fields[1] = tool;
		fields[2] = toolId;

		sqlService().dbRead(sql, fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					Long id = sqlService().readLong(result, 1);
					String siteId = sqlService().readString(result, 2);
					String tool = sqlService().readString(result, 3);
					Long toolId = sqlService().readLong(result, 4);
					String name = sqlService().readString(result, 5);
					ToolReferenceImpl ref = new ToolReferenceImpl(id, siteId, tool, toolId, name);
					rv.add(ref);

					return null;
				}
				catch (SQLException e)
				{
					M_log.warn("getToolReferences: " + e);
					return null;
				}
			}
		});

		return rv;
	}

	/**
	 * {@inheritDoc}
	 */
	public void harvestChsResources(Set<HarvestReference> harvest, String siteId, String tool, Long toolId, String intoPath)
	{
		if (harvest == null) return;
		if (siteId == null) return;

		try
		{
			// setup a security advisor
			securityService().pushAdvisor(new SecurityAdvisor()
			{
				public SecurityAdvice isAllowed(String userId, String function, String reference)
				{
					if (reference.startsWith("/content/private/meleteDocs"))
					{
						String parts[] = StringUtil.split(reference, "/");
						if (parts.length >= 5)
						{
							String siteId = parts[4];
							if (checkSecurity(userId, "melete.author", siteId)) return SecurityAdvice.ALLOWED;
							if (checkSecurity(userId, "melete.student", siteId)) return SecurityAdvice.ALLOWED;
							return SecurityAdvice.NOT_ALLOWED;
						}
					}
					else if (reference.startsWith("/content/private/mneme/"))
					{
						String parts[] = StringUtil.split(reference, "/");
						if (parts.length >= 5)
						{
							String siteId = parts[4];
							if (checkSecurity(userId, "mneme.manage", siteId)) return SecurityAdvice.ALLOWED;
							if (checkSecurity(userId, "mneme.grade", siteId)) return SecurityAdvice.ALLOWED;
							return SecurityAdvice.NOT_ALLOWED;
						}
					}
					return SecurityAdvice.PASS;
				}
			});

			while (!harvest.isEmpty())
			{
				// pull one from the harvest
				HarvestReference h = harvest.iterator().next();
				harvest.remove(h);

				// is there a placement? if so, skip this one since we have something by this name already
				SitePlacement p = getSitePlacement(siteId, h.getTo());
				if (p == null)
				{
					// harvest!
					try
					{
						ContentResource resource = contentHostingService().getResource(h.getFrom());
						String type = resource.getContentType();
						byte[] body = resource.getContent();

						// for html, embedded refs to further CHS docs might need cleanup and more harvesting
						if ("text/html".equalsIgnoreCase(type))
						{
							try
							{
								// look only for relative references, assuming that once we accept any html in site resources, we would have converted the URLs to relative, and any that are not relative now should be treated as to a URL in the wild.
								String html = new String(body, "UTF-8");

								// shorten full URLs to full-relative URLs
								html = XrefHelper.shortenFullUrls(html);

								// expand short-relative URLs to become full-relative CHS resource URLs
								String base = "/access" + resource.getReference();
								int end = base.lastIndexOf("/");
								if (end != -1)
								{
									base = base.substring(0, end + 1);
								}
								else
								{
									base = "";
								}
								html = expandRelRefsInChs(html, base);

								html = toolLocalizeEmbeddedChsReferences1(html, siteId, tool, toolId, intoPath, null, harvest, true);
								html = toolLocalizeEmbeddedChsReferences2(html, siteId, tool, toolId, intoPath, null, harvest, true);

								body = html.getBytes("UTF-8");
							}
							catch (UnsupportedEncodingException e)
							{
							}
						}

						ByteArrayInputStream in = new ByteArrayInputStream(body);
						addSiteResource(type, body.length, in, siteId, h.getTo());
					}
					catch (PermissionException e)
					{
						M_log.warn("harvestChsResources: " + h.getFrom() + " " + h.getTo() + " : " + e);
					}
					catch (IdUnusedException e)
					{
						M_log.warn("harvestChsResources: " + h.getFrom() + " " + h.getTo() + " : " + e);
					}
					catch (TypeException e)
					{
						M_log.warn("harvestChsResources: " + h.getFrom() + " " + h.getTo() + " : " + e);
					}
					catch (ServerOverloadException e)
					{
						M_log.warn("harvestChsResources: " + h.getFrom() + " " + h.getTo() + " : " + e);
					}
				}
			}
		}
		finally
		{
			securityService().popAdvisor();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public Boolean hasToolReferences(SitePlacement placement)
	{
		String sql = "SELECT COUNT(*) FROM SITE_RESOURCE_REFERENCE WHERE SITE_ID = ? AND NAME = ?";
		Object[] fields = new Object[2];
		fields[0] = placement.getSiteId();
		fields[1] = placement.getName();

		List<Boolean> rv = (List<Boolean>) sqlService().dbRead(sql, fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					Long count = sqlService().readLong(result, 1);

					return Boolean.valueOf(count > 0);
				}
				catch (SQLException e)
				{
					M_log.warn("hasToolReferences: " + e);
					return Boolean.FALSE;
				}
			}
		});

		if (rv.isEmpty()) return Boolean.FALSE;
		return rv.get(0);
	}

	/**
	 * {@inheritDoc}
	 */
	public void importFromSite(String userId, String fromSite, String toSite)
	{
		// auth?

		// the current site resources, for duplicate checking
		List<SitePlacement> current = getSitePlacements(toSite);

		// anything with a site ref in the fromSite
		List<SitePlacement> placements = getSitePlacements(fromSite);
		for (SitePlacement p : placements)
		{
			// ignore anything in a private folder
			if (p.getName().startsWith(".")) continue;

			// avoid duplicates
			boolean isDuplicate = false;
			for (SitePlacement c : current)
			{
				// if we match name skip as a duplicate
				if (Different.different(p.getName(), c.getName())) continue;

				// TODO: do we care about comparing the site resource?
				// If we have the same name but different resource, it might be considered a different placement, and we can bring it over under a new name?
				// if we have a different name, but the same resource ...?
				// if (Different.different(p.getSiteResource().getId(), c.getSiteResource().getId())) continue;

				// this is a duplicate
				isDuplicate = true;
				break;
			}

			if (!isDuplicate)
			{
				addSitePlacement(p.getSiteResource(), toSite, p.getName());
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public ToolReference parseToolReferenceUrl(String url)
	{
		if (url == null) return null;

		// /resources/access/SITEID/TOOL/TOOLID/PATH.../NAME (len >= 7) or /SITEID/TOOL/TOOLID/PATH.../NAME (len >= 5)
		String[] parts = url.split("/");
		if (parts.length < 5) return null;
		int start = 1;
		if (parts[1].equals("resources") && parts[2].equals("access"))
		{
			start = 3;
			if (parts.length < 7) return null;
		}
		String siteId = parts[start++];
		String tool = parts[start++];
		String toolIdStr = parts[start++];
		Long toolId = Long.parseLong(toolIdStr);
		String name = StringUtil.unsplit(parts, start, parts.length - start, "/");

		// see if this is a registered reference
		ToolReference rv = getToolReference(siteId, tool, toolId, name);
		if (rv != null) return rv;

		// it's not a reference, so return a new ToolReference with the parts parsed
		rv = new ToolReferenceImpl(null, siteId, tool, toolId, name);
		return rv;
	}

	/**
	 * {@inheritDoc}
	 */
	public void registerOwner(SiteResourceOwner owner)
	{
		this.owners.put(owner.getTool(), owner);
	}

	public void removeSitePlacement(final SitePlacement placement)
	{
		// is this the last (only) placement for this resource?
		Boolean isLastPlacement = !hasOtherPlacements(placement);

		// delete the placement
		sqlService().transact(new Runnable()
		{
			public void run()
			{
				// remove the placement
				deleteSitePlacementTx(placement.getId());
			}
		}, "removeSitePlacement: " + placement.getSiteId());

		// if no other placements exist for the resource, delete the resource
		if (isLastPlacement)
		{
			deleteSiteResource(placement.getSiteResource());
		}

		// if there are any existing references to this, inform the owners
		List<ToolReference> refs = getToolReferences(placement);
		for (ToolReference ref : refs)
		{
			SiteResourceOwner owner = this.owners.get(ref.getTool());
			if (owner != null)
			{
				owner.informDeletedResource(ref);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void renameSitePlacement(final SitePlacement placement, final String name)
	{
		// TODO: this is where we would need to propagate renames into the tool item references to this placement.
		// only if we really exist
		if (placement.getId() == null) return;

		// a copy before we rename for owner notification
		SitePlacement orig = new SitePlacementImpl(placement);

		sqlService().transact(new Runnable()
		{
			public void run()
			{
				renamePlacementTx(placement, name);
			}
		}, "renameSitePlacement: " + placement.getId().toString());

		// if there are any existing references to this, inform the owners
		List<ToolReference> refs = getToolReferences(orig);
		for (ToolReference ref : refs)
		{
			SiteResourceOwner owner = this.owners.get(ref.getTool());
			if (owner != null)
			{
				owner.informRenamedResource(ref, placement);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public String toolLocalizeEmbeddedChsReferences(String html, String siteId, String tool, Long toolId, String intoPath, Set<ToolReference> refs,
			Set<HarvestReference> harvest)
	{
		if (html == null) return html;
		String rv = toolLocalizeEmbeddedChsReferences1(html, siteId, tool, toolId, intoPath, refs, harvest, false);
		rv = toolLocalizeEmbeddedChsReferences2(rv, siteId, tool, toolId, intoPath, refs, harvest, false);
		return rv;
	}

	/**
	 * {@inheritDoc}
	 */
	public String toolLocalizeEmbeddedToolReferences(String html, String siteId, String tool, Long toolId, Set<ToolReference> refs)
	{
		if (html == null) return html;
		if (html.startsWith("/resources/access/")) return toolLocalizeEmbeddedToolReferencesUrl(html, siteId, tool, toolId, refs);

		Pattern p = Pattern.compile("(src|href|value)[\\s]*=[\\s]*\"/resources/access/([^#\"]*)/([^#\"]*)/([0-9]*)/([^#\"]*)\"",
				Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
		Matcher m = p.matcher(html);

		StringBuffer sb = new StringBuffer();

		while (m.find())
		{
			if (m.groupCount() == 5)
			{
				// String refSiteId = m.group(2);
				// String refTool = m.group(3);
				// String refToolId = m.group(4);
				String resourceName = m.group(5);

				// decode any URL encoding in resourceName
				resourceName = decodeUrl(resourceName);

				ToolReference u = new ToolReferenceImpl(null, siteId, tool, toolId, resourceName);
				refs.add(u);

				m.appendReplacement(
						sb,
						Matcher.quoteReplacement(m.group(1) + "=\"/resources/access/" + siteId + "/" + tool + "/" + toolId.toString() + "/"
								+ escapeUrl(resourceName) + "\""));
			}
		}

		m.appendTail(sb);

		return sb.toString();
	}

	/**
	 * Compute a resource name based on name that would be unique in the folder of the site.
	 * 
	 * @param folder
	 *        The folder name.
	 * @param name
	 *        The resource name.
	 * @param siteId
	 *        The site id.
	 * @param tool
	 *        The tool name.
	 * @param toolId
	 *        The tool id.
	 * @return The unique name.
	 */
	/**
	 * {@inheritDoc}
	 */
	public String uniqueResourceName(String folder, String name, String siteId)
	{
		// create a variant of name by adding "_n" before the extension
		String[] nameParts = StringUtil.splitFirst(name, ".");
		String nameRoot = nameParts[0];
		String extension = (nameParts.length == 2) ? "." + nameParts[1] : "";

		String nameLower = name.toLowerCase();
		String nameRootLower = nameRoot.toLowerCase();

		// find if the name is in use, and if so, collect any used names based on the name
		boolean nameInUse = false;
		Set<String> names = new HashSet<String>();

		List<SitePlacement> placements = getSitePlacements(siteId);
		for (SitePlacement p : placements)
		{
			// filter based on path
			String[] uPath = p.getFilePath();
			String uName = p.getFileName();
			String uNameLower = uName.toLowerCase();
			if (("/".equals(folder) && (uPath == null)) || ((uPath != null) && (uPath.length == 2) && (folder.equals("/" + uPath[0] + "/"))))
			{
				if (uNameLower.startsWith(nameRootLower))
				{
					if (uNameLower.equals(nameLower))
					{
						// this name is in use
						nameInUse = true;
					}
					else
					{
						names.add(uNameLower);
					}
				}
			}
		}

		if (!nameInUse) return name;

		String candidate = null;
		int counter = 1;
		do
		{
			candidate = nameRoot + "_" + Integer.toString(counter) + extension;
			counter++;
		}
		while (names.contains(candidate.toLowerCase()));

		return candidate;
	}

	/**
	 * {@inheritDoc}
	 */
	public void unregisterOwner(SiteResourceOwner owner)
	{
		this.owners.remove(owner);
	}

	/**
	 * {@inheritDoc}
	 */
	public void updateToolReferences(Collection<ToolReference> refsToRemove, Collection<ToolReference> refsToAdd)
	{
		// adjust the lists to remove any overlap (these we keep)
		Set<ToolReference> remove = new HashSet<ToolReference>();
		remove.addAll(refsToRemove);
		Set<ToolReference> add = new HashSet<ToolReference>();
		add.addAll(refsToAdd);

		// keep those in both lists
		Set<ToolReference> keep = new HashSet<ToolReference>();
		keep.addAll(remove);
		keep.retainAll(add);

		// don't remove keepers
		remove.removeAll(keep);

		// don't add keepers
		add.removeAll(keep);

		// remove
		for (final ToolReference ref : remove)
		{
			// if not really there, skip
			if (ref.getId() == null) continue;

			// remove this reference
			sqlService().transact(new Runnable()
			{
				public void run()
				{
					removeToolReferenceTx(ref.getId());
				}
			}, "updateToolReferences: " + ref.getId());
		}

		// add
		for (final ToolReference ref : add)
		{
			// if already there, skip
			if (ref.getId() != null) continue;

			// add a reference
			sqlService().transact(new Runnable()
			{
				public void run()
				{
					addReferenceTx((ToolReferenceImpl) ref);
				}
			}, "updateToolReferences: " + ref.getSiteId());
		}
	}

	protected void addPlacementTx(SitePlacementImpl placement)
	{
		String sql = "INSERT INTO SITE_RESOURCE_PLACEMENT (RESOURCE_ID, SITE_ID, NAME) values (?,?,?)";

		Object[] fields = new Object[3];
		fields[0] = placement.getSiteResource().getId();
		fields[1] = placement.getSiteId();
		fields[2] = placement.getName();

		Long id = sqlService().dbInsert(null, sql, fields, "ID");
		if (id == null)
		{
			throw new RuntimeException("addPlacementTx: dbInsert failed");
		}

		placement.setId(id);
	}

	protected void addReferenceTx(ToolReferenceImpl ref)
	{
		String sql = "INSERT INTO SITE_RESOURCE_REFERENCE (SITE_ID, TOOL, TOOL_ID, NAME) values (?,?,?,?)";

		Object[] fields = new Object[4];
		fields[0] = ref.getSiteId();
		fields[1] = ref.getTool();
		fields[2] = ref.getToolId();
		fields[3] = ref.getName();

		Long id = sqlService().dbInsert(null, sql, fields, "ID");
		if (id == null)
		{
			throw new RuntimeException("addReferenceTx: dbInsert failed");
		}

		ref.setId(id);
	}

	protected SiteResource addResource(String type, int length, InputStream contents, String name)
	{
		Date uploadDate = new Date();

		// make a file
		String storagePath = pathForNewFile(uploadDate, name);
		String fullPath = this.fileSystemRoot + storagePath;

		OutputStream out = null;
		try
		{
			File resourceFile = new File(fullPath);

			// make sure all directories are there
			File container = resourceFile.getParentFile();
			if (container != null)
			{
				container.mkdirs();
			}

			resourceFile.createNewFile();
			out = new FileOutputStream(resourceFile);

			// chunk
			byte[] chunk = new byte[STREAM_BUFFER_SIZE];
			int lenRead;
			int size = 0;
			while ((lenRead = contents.read(chunk)) != -1)
			{
				size = size + lenRead;
				out.write(chunk, 0, lenRead);
			}
			if (length == 0) length = size;
		}
		catch (IOException e)
		{
		}
		finally
		{
			try
			{
				if (out != null) out.close();
				contents.close();
			}
			catch (IOException e)
			{
			}
		}

		final SiteResourceImpl r = new SiteResourceImpl(null, uploadDate, length, type, storagePath);

		// add a record
		sqlService().transact(new Runnable()
		{
			public void run()
			{
				newSiteResourceTx(r);
			}
		}, "newSiteResource: " + name);

		return r;
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings(
	{ "rawtypes", "unchecked" })
	protected boolean checkSecurity(String userId, String function, String context)
	{
		// check for super user
		if (securityService().isSuperUser(userId)) return true;

		// check for the user / function / context-as-site-authz
		// use the site ref for the security service (used to cache the security calls in the security service)
		String siteRef = siteService().siteReference(context);

		// form the azGroups for a context-as-implemented-by-site
		Collection azGroups = new Vector(2);
		azGroups.add(siteRef);
		azGroups.add("!site.helper");

		boolean rv = securityService().unlock(userId, function, siteRef, azGroups);
		return rv;
	}

	protected void clearToolReferencesTx(String siteId, String tool, Long toolId)
	{
		String sql = "DELETE FROM SITE_RESOURCE_REFERENCE WHERE SITE_ID = ? AND TOOL = ? AND TOOL_ID = ?";
		Object[] fields = new Object[3];
		fields[0] = siteId;
		fields[1] = tool;
		fields[2] = toolId;
		if (!sqlService().dbWrite(sql, fields))
		{
			throw new RuntimeException("clearToolReferencesTx: db write failed: " + siteId);
		}
	}

	/**
	 * Collect any references in URLs embedded in the html to any CHS privateDocs resources.
	 * 
	 * @param html
	 *        The html source.
	 * @param siteId
	 *        The site id for the tool reference.
	 * @param tool
	 *        The tool for the tool reference.
	 * @param toolId
	 *        The toolId for the tool reference.
	 * @param intoPath
	 *        the path where the resource would reside in site resources.
	 * @param refs
	 *        A set of ToolReferences, which found references are added to.
	 */
	protected Set<ToolReference> collectChsPrivateDocsReferences(String html, String siteId, String tool, Long toolId, String intoPath)
	{
		Set<ToolReference> rv = new HashSet<ToolReference>();

		Pattern p = Pattern.compile(
				"(src|href|value)[\\s]*=[\\s]*\"/access/(meleteDocs|mneme)/content/private/(meleteDocs|mneme)/([^/]*)/([^\"]*)\"",
				Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
		Matcher m = p.matcher(html);

		while (m.find())
		{
			if (m.groupCount() == 5)
			{
				// String refTool = m.group(2);
				// String refTool2 = m.group(3);
				// String refSiteId = m.group(4);
				String refName = m.group(5);

				// decode any URL encoding in refName
				refName = decodeUrl(refName);

				// move refName from whatever path into home/
				String[] path = StringUtil.split(refName, "/");
				String reloactedRefName = intoPath + path[path.length - 1];

				// we might need to harvest this
				// HarvestReferenceImpl harvestRef = new HarvestReferenceImpl("/private/" + refTool2 + "/" + refSiteId + "/" + refName, reloactedRefName);
				// harvest.add(harvestRef);

				ToolReference u = new ToolReferenceImpl(null, siteId, tool, toolId, reloactedRefName);
				rv.add(u);
			}
		}

		return rv;
	}

	/**
	 * Collect any references in URLs embedded in the html to any CHS site resources.
	 * 
	 * @param html
	 *        The html source.
	 * @param siteId
	 *        The site id for the tool reference.
	 * @param tool
	 *        The tool for the tool reference.
	 * @param toolId
	 *        The toolId for the tool reference.
	 * @param intoPath
	 *        the path where the resource would reside in site resources.
	 * @return a set of ToolReferences found.
	 */
	protected Set<ToolReference> collectChsReferences(String html, String siteId, String tool, Long toolId, String intoPath)
	{
		Set<ToolReference> rv = new HashSet<ToolReference>();

		Pattern p = Pattern.compile("(src|href|value)[\\s]*=[\\s]*\"/access/content/group/([^/]*)/([^\"]*)\"", Pattern.CASE_INSENSITIVE
				| Pattern.UNICODE_CASE);
		Matcher m = p.matcher(html);

		while (m.find())
		{
			if (m.groupCount() == 3)
			{
				// String refSiteId = m.group(2);
				String refName = m.group(3);

				// decode any URL encoding in refName
				refName = decodeUrl(refName);

				// move refName from whatever path into home/
				String[] path = StringUtil.split(refName, "/");
				String reloactedRefName = intoPath + path[path.length - 1];

				// we might need to harvest this
				// HarvestReferenceImpl harvestRef = new HarvestReferenceImpl("/group/" + refSiteId + "/" + refName, reloactedRefName);
				// harvest.add(harvestRef);

				ToolReference u = new ToolReferenceImpl(null, siteId, tool, toolId, reloactedRefName);
				rv.add(u);
			}
		}

		return rv;
	}

	/**
	 * Collect any references in URLs embedded in the html to any relative resources, assumed to be files in site resources.
	 * 
	 * @param html
	 *        The html source.
	 * @param siteId
	 *        The site id for the tool reference.
	 * @param tool
	 *        The tool for the tool reference.
	 * @param toolId
	 *        The toolId for the tool reference.
	 * @param intoPath
	 *        the path where the resource would reside in site resources.
	 * @return a set of ToolReferences found.
	 */
	protected Set<ToolReference> collectRelReferences(String html, String siteId, String tool, Long toolId, String intoPath)
	{
		Set<ToolReference> rv = new HashSet<ToolReference>();

		Pattern p = Pattern.compile("(src|href|value)[\\s]*=[\\s]*\"([^/][^\"]*)\"", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
		Matcher m = p.matcher(html);

		while (m.find())
		{
			if (m.groupCount() == 2)
			{
				String ref = m.group(2);

				// is this really relative? The pattern would catch "http://"...
				if (ref.indexOf("://") == -1)
				{
					// add the path
					String withPath = intoPath + ref;

					ToolReference u = new ToolReferenceImpl(null, siteId, tool, toolId, withPath);
					rv.add(u);
				}
			}
		}

		return rv;
	}

	/**
	 * Collect any references in URLs embedded in the html to any site resources.
	 * 
	 * @param html
	 *        The html source.
	 * @param siteId
	 *        The site id for the tool reference.
	 * @param tool
	 *        The tool for the tool reference.
	 * @param toolId
	 *        The toolId for the tool reference.
	 * @param intoPath
	 *        the path where the resource would reside in site resources.
	 * @return a set of ToolReferences found.
	 */
	protected Set<ToolReference> collectSiteResourceReferences(String html, String siteId, String tool, Long toolId)
	{
		Set<ToolReference> rv = new HashSet<ToolReference>();

		Pattern p = Pattern.compile("(src|href|value)[\\s]*=[\\s]*\"/resources/access/([^#\"]*)/([^#\"]*)/([0-9]*)/([^#\"]*)\"",
				Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
		Matcher m = p.matcher(html);

		while (m.find())
		{
			if (m.groupCount() == 5)
			{
				// String refSiteId = m.group(2);
				// String refTool = m.group(3);
				// String refToolId = m.group(4);
				String resourceName = m.group(5);

				// decode any URL encoding in resourceName
				resourceName = decodeUrl(resourceName);

				ToolReference u = new ToolReferenceImpl(null, siteId, tool, toolId, resourceName);
				rv.add(u);
			}
		}

		return rv;
	}

	/**
	 * Decode the URL as a browser would.
	 * 
	 * @param url
	 *        The URL.
	 * @return the decoded URL.
	 */
	protected String decodeUrl(String url)
	{
		try
		{
			// these the browser will convert when it's making the URL to send
			String processed = url.replaceAll("&amp;", "&");
			processed = processed.replaceAll("&lt;", "<");
			processed = processed.replaceAll("&gt;", ">");
			processed = processed.replaceAll("&quot;", "\"");

			// if a browser sees a plus, it sends a plus (URLDecoder will change it to a space)
			processed = processed.replaceAll("\\+", "%2b");

			// and the rest of the works, including %20 and + handling
			String decoded = URLDecoder.decode(processed, "UTF-8");

			return decoded;
		}
		catch (UnsupportedEncodingException e)
		{
			M_log.warn("decodeUrl: " + e);
		}
		catch (IllegalArgumentException e)
		{
			M_log.warn("decodeUrl: " + e);
		}

		return url;
	}

	protected void deleteSitePlacementTx(Long id)
	{
		String sql = "DELETE FROM SITE_RESOURCE_PLACEMENT WHERE ID = ?";
		Object[] fields = new Object[1];
		fields[0] = id;
		if (!sqlService().dbWrite(sql, fields))
		{
			throw new RuntimeException("deleteSitePlacementTx: db write failed: " + id);
		}
	}

	/**
	 * Delete this resource and the resource file.
	 * 
	 * @param r
	 *        The site resource.
	 */
	protected void deleteSiteResource(final SiteResource r)
	{
		// delete the resource file
		String fullPath = this.fileSystemRoot + r.getStoragePath();
		File resourceFile = new File(fullPath);
		resourceFile.delete();

		// delete the resource record
		sqlService().transact(new Runnable()
		{
			public void run()
			{
				deleteSiteResourceTx(r.getId());
			}
		}, "deleteSiteResource: " + r.getId().toString());
	}

	protected void deleteSiteResourceTx(Long resourceId)
	{
		String sql = "DELETE FROM SITE_RESOURCES WHERE ID = ?";
		Object[] fields = new Object[1];
		fields[0] = resourceId;
		if (!sqlService().dbWrite(sql, fields))
		{
			throw new RuntimeException("deleteSiteResourceTx: db write failed: " + resourceId.toString());
		}
	}

	/**
	 * Called just before we shutdown.
	 */
	protected void destroy()
	{
		// unregister for site import
		siteImportService().unregisterImporter(this);
	}

	/**
	 * Return a string based on id that is fully escaped using URL rules, using a UTF-8 underlying encoding.
	 * 
	 * @param id
	 *        The string to escape.
	 * @return id fully escaped using URL rules.
	 */
	protected String escapeUrl(String id)
	{
		if (id == null) return "";
		id = id.trim();
		try
		{
			// convert the string to bytes in UTF-8
			byte[] bytes = id.getBytes("UTF-8");

			StringBuilder buf = new StringBuilder();
			for (int index = 0; index < bytes.length; index++)
			{
				byte b = bytes[index];
				char c = (char) b;

				// get this unsigned
				int i = (int) (b & 0xFF);

				if (("$&+,:;=?@ '\"<>#%{}|\\^~[]`^?;".indexOf(c) != -1) || (i <= 0x1F) || (i == 0x7F) || (i >= 0x80))
				{
					buf.append("%");
					buf.append(Integer.toString(i, 16));
				}
				else
				{
					buf.append(c);
				}
			}

			String rv = buf.toString();
			return rv;
		}
		catch (Exception e)
		{
			return id;
		}
	}

	/**
	 * Check if there are any other placements of this placement's site resource.
	 * 
	 * @param placement
	 *        The placement.
	 * @return TRUE if there are other placements, FALSE if not.
	 */
	@SuppressWarnings("unchecked")
	protected Boolean hasOtherPlacements(SitePlacement placement)
	{
		String sql = "SELECT COUNT(*) FROM SITE_RESOURCE_PLACEMENT WHERE RESOURCE_ID = ? AND ID != ?";
		Object[] fields = new Object[2];
		fields[0] = placement.getSiteResource().getId();
		fields[1] = placement.getId();

		List<Boolean> rv = (List<Boolean>) sqlService().dbRead(sql, fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					Long count = sqlService().readLong(result, 1);

					return Boolean.valueOf(count > 0);
				}
				catch (SQLException e)
				{
					M_log.warn("hasOtherPlacements: " + e);
					return Boolean.FALSE;
				}
			}
		});

		if (rv.isEmpty()) return Boolean.FALSE;
		return rv.get(0);
	}

	protected void newSiteResourceTx(SiteResourceImpl resource)
	{
		String sql = "INSERT INTO SITE_RESOURCES (UPLOAD_DATE, STORAGE_PATH, FILE_SIZE, MIME_TYPE) values (?,?,?,?)";

		Object[] fields = new Object[4];
		fields[0] = sqlService().valueForDate(resource.getDate());
		fields[1] = resource.getStoragePath();
		fields[2] = Long.valueOf(resource.getLength());
		fields[3] = resource.getMimeType();

		Long id = sqlService().dbInsert(null, sql, fields, "ID");
		if (id == null)
		{
			throw new RuntimeException("newSiteResourceTx: dbInsert failed");
		}

		((SiteResourceImpl) resource).setId(id);
	}

	protected String pathForNewFile(Date uploadDate, String name)
	{
		// the path will be relative to the configured root path, which stays out of the path returned

		// form the path based on the upload date: /yyyy/DDD/HH (year, day of year, 24-hour), based on UT
		DateFormat dateFormat = new SimpleDateFormat("yyyy/DDD/HH/", Locale.US);
		dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));

		// add a unique id, and the name
		String rv = dateFormat.format(uploadDate) + IdManager.createUuid() + "_" + name;

		return rv;
	}

	protected void removeToolReferenceTx(Long refId)
	{
		String sql = "DELETE FROM SITE_RESOURCE_REFERENCE WHERE ID = ?";
		Object[] fields = new Object[1];
		fields[0] = refId;
		if (!sqlService().dbWrite(sql, fields))
		{
			throw new RuntimeException("removeToolReferenceTx: db write failed: " + refId.toString());
		}
	}

	protected void renamePlacementTx(SitePlacement placement, String name)
	{
		String sql = "UPDATE SITE_RESOURCE_PLACEMENT SET NAME=? WHERE ID=?";

		Object[] fields = new Object[2];
		fields[0] = name;
		fields[1] = placement.getId();

		if (!sqlService().dbWrite(sql, fields))
		{
			throw new RuntimeException("renamePlacementTx: dbWrite failed");
		}

		((SitePlacementImpl) placement).setName(name);
	}

	/**
	 * {@inheritDoc}
	 */
	protected String toolLocalizeEmbeddedChsReferences1(String html, String siteId, String tool, Long toolId, String intoPath,
			Set<ToolReference> refs, Set<HarvestReference> harvest, boolean relRefs)
	{
		if (html.startsWith("/access/content/group/"))
			return toolLocalizeEmbeddedChsReferenceUrl1(html, siteId, tool, toolId, intoPath, refs, harvest);

		Pattern p = Pattern.compile("(src|href|value)[\\s]*=[\\s]*\"/access/content/group/([^/]*)/([^\"]*)\"", Pattern.CASE_INSENSITIVE
				| Pattern.UNICODE_CASE);
		Matcher m = p.matcher(html);

		StringBuffer sb = new StringBuffer();

		while (m.find())
		{
			if (m.groupCount() == 3)
			{
				String refSiteId = m.group(2);
				String refName = m.group(3);

				// decode any URL encoding in refName
				refName = decodeUrl(refName);

				// move refName from whatever path into home/
				String[] path = StringUtil.split(refName, "/");
				String reloactedRefName = intoPath + path[path.length - 1];

				// we might need to harvest this
				HarvestReferenceImpl harvestRef = new HarvestReferenceImpl("/group/" + refSiteId + "/" + refName, reloactedRefName);
				harvest.add(harvestRef);

				if (refs != null)
				{
					ToolReference u = new ToolReferenceImpl(null, siteId, tool, toolId, reloactedRefName);
					refs.add(u);
				}

				if (relRefs)
				{
					m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + "=\"" + escapeUrl(path[path.length - 1]) + "\""));
				}
				else
				{
					m.appendReplacement(
							sb,
							Matcher.quoteReplacement(m.group(1) + "=\"/resources/access/" + siteId + "/" + tool + "/" + toolId.toString() + "/"
									+ escapeUrl(reloactedRefName) + "\""));
				}
			}
		}

		m.appendTail(sb);

		return sb.toString();
	}

	/**
	 * {@inheritDoc}
	 */
	protected String toolLocalizeEmbeddedChsReferences2(String html, String siteId, String tool, Long toolId, String intoPath,
			Set<ToolReference> refs, Set<HarvestReference> harvest, boolean relRefs)
	{
		if (html.startsWith("/access/meleteDocs/content/private/meleteDocs/"))
			return toolLocalizeEmbeddedChsReferenceUrl2(html, siteId, tool, toolId, intoPath, refs, harvest);
		if (html.startsWith("/access/mneme/content/private/mneme/"))
			return toolLocalizeEmbeddedChsReferenceUrl2(html, siteId, tool, toolId, intoPath, refs, harvest);

		Pattern p = Pattern.compile(
				"(src|href|value)[\\s]*=[\\s]*\"/access/(meleteDocs|mneme)/content/private/(meleteDocs|mneme)/([^/]*)/([^\"]*)\"",
				Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
		Matcher m = p.matcher(html);

		StringBuffer sb = new StringBuffer();

		while (m.find())
		{
			if (m.groupCount() == 5)
			{
				// String refTool = m.group(2);
				String refTool2 = m.group(3);
				String refSiteId = m.group(4);
				String refName = m.group(5);

				// decode any URL encoding in refName
				refName = decodeUrl(refName);

				// move refName from whatever path into home/
				String[] path = StringUtil.split(refName, "/");
				String reloactedRefName = intoPath + path[path.length - 1];

				// we might need to harvest this
				HarvestReferenceImpl harvestRef = new HarvestReferenceImpl("/private/" + refTool2 + "/" + refSiteId + "/" + refName, reloactedRefName);
				harvest.add(harvestRef);

				if (refs != null)
				{
					ToolReference u = new ToolReferenceImpl(null, siteId, tool, toolId, reloactedRefName);
					refs.add(u);
				}

				if (relRefs)
				{
					m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + "=\"" + escapeUrl(path[path.length - 1]) + "\""));
				}
				else
				{
					m.appendReplacement(
							sb,
							Matcher.quoteReplacement(m.group(1) + "=\"/resources/access/" + siteId + "/" + tool + "/" + toolId.toString() + "/"
									+ escapeUrl(reloactedRefName) + "\""));
				}
			}
		}

		m.appendTail(sb);

		return sb.toString();
	}

	/**
	 * {@inheritDoc}
	 */
	protected String toolLocalizeEmbeddedChsReferenceUrl1(String html, String siteId, String tool, Long toolId, String intoPath,
			Set<ToolReference> refs, Set<HarvestReference> harvest)
	{
		Pattern p = Pattern.compile("/access/content/group/([^/]*)/([^\"]*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
		Matcher m = p.matcher(html);

		StringBuffer sb = new StringBuffer();

		while (m.find())
		{
			if (m.groupCount() == 2)
			{
				String refSiteId = m.group(1);
				String refName = m.group(2);

				// decode any URL encoding in refName
				refName = decodeUrl(refName);

				// move refName from whatever path into home/
				String[] path = StringUtil.split(refName, "/");
				String reloactedRefName = intoPath + path[path.length - 1];

				// we might need to harvest this
				HarvestReferenceImpl harvestRef = new HarvestReferenceImpl("/group/" + refSiteId + "/" + refName, reloactedRefName);
				harvest.add(harvestRef);

				if (refs != null)
				{
					ToolReference u = new ToolReferenceImpl(null, siteId, tool, toolId, reloactedRefName);
					refs.add(u);
				}

				m.appendReplacement(
						sb,
						Matcher.quoteReplacement("/resources/access/" + siteId + "/" + tool + "/" + toolId.toString() + "/"
								+ escapeUrl(reloactedRefName)));
			}
		}

		m.appendTail(sb);

		return sb.toString();
	}

	/**
	 * {@inheritDoc}
	 */
	protected String toolLocalizeEmbeddedChsReferenceUrl2(String html, String siteId, String tool, Long toolId, String intoPath,
			Set<ToolReference> refs, Set<HarvestReference> harvest)
	{
		Pattern p = Pattern.compile("/access/(meleteDocs|mneme)/content/private/(meleteDocs|mneme)/([^/]*)/([^\"]*)", Pattern.CASE_INSENSITIVE
				| Pattern.UNICODE_CASE);
		Matcher m = p.matcher(html);

		StringBuffer sb = new StringBuffer();

		while (m.find())
		{
			if (m.groupCount() == 4)
			{
				// String refTool = m.group(1);
				String refTool2 = m.group(2);
				String refSiteId = m.group(3);
				String refName = m.group(4);

				// decode any URL encoding in refName
				refName = decodeUrl(refName);

				// move refName from whatever path into home/
				String[] path = StringUtil.split(refName, "/");
				String reloactedRefName = intoPath + path[path.length - 1];

				// we might need to harvest this
				HarvestReferenceImpl harvestRef = new HarvestReferenceImpl("/private/" + refTool2 + "/" + refSiteId + "/" + refName, reloactedRefName);
				harvest.add(harvestRef);

				if (refs != null)
				{
					ToolReference u = new ToolReferenceImpl(null, siteId, tool, toolId, reloactedRefName);
					refs.add(u);
				}

				m.appendReplacement(
						sb,
						Matcher.quoteReplacement("/resources/access/" + siteId + "/" + tool + "/" + toolId.toString() + "/"
								+ escapeUrl(reloactedRefName)));
			}
		}

		m.appendTail(sb);

		return sb.toString();
	}

	/**
	 * {@inheritDoc}
	 */
	protected String toolLocalizeEmbeddedToolReferencesUrl(String url, String siteId, String tool, Long toolId, Set<ToolReference> refs)
	{
		Pattern p = Pattern.compile("/resources/access/([^#\"]*)/([^#\"]*)/([0-9]*)/([^#\"]*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
		Matcher m = p.matcher(url);

		StringBuffer sb = new StringBuffer();

		while (m.find())
		{
			if (m.groupCount() == 4)
			{
				// String refSiteId = m.group(1);
				// String refTool = m.group(2);
				// String refToolId = m.group(3);
				String resourceName = m.group(4);

				// decode any URL encoding in resourceName
				resourceName = decodeUrl(resourceName);

				ToolReference u = new ToolReferenceImpl(null, siteId, tool, toolId, resourceName);
				refs.add(u);
				m.appendReplacement(sb, Matcher.quoteReplacement("/resources/access/" + siteId + "/" + tool + "/" + toolId.toString() + "/"
						+ escapeUrl(resourceName)));
			}
		}

		m.appendTail(sb);

		return sb.toString();
	}

	/**
	 * @return The ContentHostingService, via the component manager.
	 */
	private ContentHostingService contentHostingService()
	{
		return (ContentHostingService) ComponentManager.get(ContentHostingService.class);
	}

	/**
	 * @return The SecurityService, via the component manager.
	 */
	private SecurityService securityService()
	{
		return (SecurityService) ComponentManager.get(SecurityService.class);
	}

	/**
	 * @return The ServerConfigurationService, via the component manager.
	 */
	private ServerConfigurationService serverConfigurationService()
	{
		return (ServerConfigurationService) ComponentManager.get(ServerConfigurationService.class);
	}

	/**
	 * @return The SiteImportService, via the component manager.
	 */
	private SiteImportService siteImportService()
	{
		return (SiteImportService) ComponentManager.get(SiteImportService.class);
	}

	/**
	 * @return The SiteService, via the component manager.
	 */
	private SiteService siteService()
	{
		return (SiteService) ComponentManager.get(SiteService.class);
	}

	/**
	 * @return The SqlService, via the component manager.
	 */
	private SqlService sqlService()
	{
		return (SqlService) ComponentManager.get(SqlService.class);
	}
}
