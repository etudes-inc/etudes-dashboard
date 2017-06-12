/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/dashboard/trunk/dashboard-webapp/webapp/src/java/org/etudes/dashboard/cdp/DashboardCdpHandler.java $
 * $Id: DashboardCdpHandler.java 7406 2014-02-15 02:22:32Z ggolden $
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

package org.etudes.dashboard.cdp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.cdp.api.CdpHandler;
import org.etudes.cdp.api.CdpStatus;
import org.etudes.cdp.util.CdpResponseHelper;
import org.etudes.cdp.util.StringHtml;
import org.etudes.coursemap.api.CourseMapItem;
import org.etudes.coursemap.api.CourseMapMap;
import org.etudes.coursemap.api.CourseMapService;
import org.sakaiproject.announcement.api.AnnouncementMessage;
import org.sakaiproject.announcement.api.AnnouncementService;
import org.sakaiproject.calendar.api.Calendar;
import org.sakaiproject.calendar.api.CalendarEvent;
import org.sakaiproject.calendar.api.CalendarService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.entity.api.EntityPropertyNotDefinedException;
import org.sakaiproject.entity.api.EntityPropertyTypeException;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.event.api.NotificationService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.time.api.Time;
import org.sakaiproject.time.api.TimeRange;
import org.sakaiproject.time.api.TimeService;
import org.sakaiproject.user.api.Preferences;
import org.sakaiproject.user.api.PreferencesService;
import org.sakaiproject.util.StringUtil;

/**
 */
public class DashboardCdpHandler implements CdpHandler
{
	protected class EventInSite
	{
		public CalendarEvent event;
		public Site site;

		public EventInSite(CalendarEvent event, Site site)
		{
			this.event = event;
			this.site = site;
		}
	}

	protected class MessageInSite
	{
		public AnnouncementMessage msg;
		public Site site;

		public MessageInSite(AnnouncementMessage msg, Site site)
		{
			this.msg = msg;
			this.site = site;
		}
	}

	class CmEvent
	{
		String actionEdit;
		String actionPerform;
		String actionReview;
		List<Reference> attachments;
		String cmType;
		String description;
		Date end;
		String id;
		String location;
		Site site;
		Date start;
		String title;
		String type;
	}

	/** Our log (commons). */
	private static Log M_log = LogFactory.getLog(DashboardCdpHandler.class);

	protected static String[] announcementTool =
	{ "sakai.announcements" };

	protected static String[] calendarTool =
	{ "sakai.schedule" };

	protected static String[] coursemapTool =
	{ "sakai.coursemap" };

	protected static final int DASHBOARD_SITE_LIMIT = 12;

	public String getPrefix()
	{
		return "dashboard";
	}

	public Map<String, Object> handle(HttpServletRequest req, HttpServletResponse res, Map<String, Object> parameters, String requestPath,
			String path, String authenticatedUserId) throws ServletException, IOException
	{
		// if no authenticated user, we reject all requests
		if (authenticatedUserId == null)
		{
			Map<String, Object> rv = new HashMap<String, Object>();
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.notLoggedIn.getId());
			return rv;
		}

		else if (requestPath.equals("dashboard"))
		{
			return dispatchDashboard(req, res, parameters, path, authenticatedUserId);
		}
		else if (requestPath.equals("events"))
		{
			return dispatchEvents(req, res, parameters, path, authenticatedUserId);
		}
		else if (requestPath.equals("announcements"))
		{
			return dispatchAnnouncements(req, res, parameters, path, authenticatedUserId);
		}
		else if (requestPath.equals("eventsDays"))
		{
			return dispatchEventsDays(req, res, parameters, path, authenticatedUserId);
		}

		return null;
	}

	/**
	 * @return The CourseMapService, via the component manager.
	 */
	private CourseMapService courseMapService()
	{
		return (CourseMapService) ComponentManager.get(CourseMapService.class);
	}

	/**
	 * @return The AuthenticationManager, via the component manager.
	 */
	private PreferencesService preferencesService()
	{
		return (PreferencesService) ComponentManager.get(PreferencesService.class);
	}

	/**
	 * @return The TimeService, via the component manager.
	 */
	private TimeService timeService()
	{
		return (TimeService) ComponentManager.get(TimeService.class);
	}

	/**
	 * @return The AnnouncementService, via the component manager.
	 */
	protected AnnouncementService announcementService()
	{
		return (AnnouncementService) ComponentManager.get(AnnouncementService.class);
	}

	/**
	 * @return The CalendarService, via the component manager.
	 */
	protected CalendarService calendarService()
	{
		return (CalendarService) ComponentManager.get(CalendarService.class);
	}

	@SuppressWarnings("unchecked")
	protected Map<String, Object> dispatchAnnouncements(HttpServletRequest req, HttpServletResponse res, Map<String, Object> parameters, String path,
			String userId) throws ServletException, IOException
	{
		Map<String, Object> rv = new HashMap<String, Object>();

		// if not authenticated
		if (userId == null)
		{
			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.accessDenied.getId());
			return rv;
		}

		List<Site> dashboardSites = new ArrayList<Site>();

		// we might get the list of site ids for the user's dashboard sites as a parameter
		String sites = (String) parameters.get("sites");
		if (sites != null)
		{
			String[] sitesArray = StringUtil.split(sites, ",");
			for (String siteId : sitesArray)
			{
				try
				{
					dashboardSites.add(siteService().getSite(siteId));
				}
				catch (IdUnusedException e)
				{
				}
			}
		}

		// otherwise compute them
		else
		{
			// collect the user's sites
			List<Site> visibleSites = new ArrayList<Site>();
			List<Site> hiddenSites = new ArrayList<Site>();
			siteService().getOrderedSites(userId, visibleSites, hiddenSites);

			// collect our limited number of dashboard sites
			int count = 0;
			for (Site site : visibleSites)
			{
				if (count == DASHBOARD_SITE_LIMIT) break;
				dashboardSites.add(site);
				count++;
			}
		}

		// one announcement from each of the user's visible (and published) sites
		List<Map<String, Object>> announcements = new ArrayList<Map<String, Object>>();
		rv.put("announcements", announcements);
		try
		{
			for (Site site : dashboardSites)
			{
				// if the site does NOT include the announcement tool, or if the site is not published, skip it
				if (site.getTools(announcementTool).isEmpty()) continue;
				if (!site.isPublished()) continue;

				String channelId = announcementService().channelReference(site.getId(), SiteService.MAIN_CONTAINER);

				// the Evaluator role might not have permission to announcements
				if (!announcementService().allowGetChannel(channelId)) continue;

				List<AnnouncementMessage> messages = announcementService().getRecentMessages(channelId, 1);
				for (AnnouncementMessage m : messages)
				{
					Map<String, Object> announcement = new HashMap<String, Object>();
					announcements.add(announcement);
					loadAnnouncement(m, announcement, true, false, site);

				}
			}
		}
		catch (PermissionException e)
		{
			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.accessDenied.getId());
			return rv;
		}

		List<Map<String, Object>> motds = new ArrayList<Map<String, Object>>();
		rv.put("motd", motds);
		try
		{
			List<AnnouncementMessage> messages = (List<AnnouncementMessage>) announcementService().getMessages("/announcement/channel/!site/motd",
					null, 0, false, false, false);
			for (AnnouncementMessage m : messages)
			{
				Map<String, Object> motd = new HashMap<String, Object>();
				motds.add(motd);
				loadAnnouncement(m, motd, true, false, null);
			}
		}
		catch (PermissionException e)
		{
			// not permitted to motd? ignore
		}

		// add status parameter
		rv.put(CdpStatus.CDP_STATUS, CdpStatus.success.getId());

		return rv;
	}

	@SuppressWarnings("unchecked")
	protected Map<String, Object> dispatchDashboard(HttpServletRequest req, HttpServletResponse res, Map<String, Object> parameters, String path,
			String userId) throws ServletException, IOException
	{
		Map<String, Object> rv = new HashMap<String, Object>();

		// if not authenticated
		if (userId == null)
		{
			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.accessDenied.getId());
			return rv;
		}

		// year month and day are expressed in the user's local (browser) time zone: year ~ 2013, month ~ (1..12), day ~ (1..31)
		String yearStr = (String) parameters.get("year");
		String monthStr = (String) parameters.get("month");
		String dayStr = (String) parameters.get("day");

		// If missing, use the "current" based on server time, user's time zone
		if ((yearStr == null) && (monthStr == null) && (dayStr == null))
		{
			String[] now = CdpResponseHelper.dateBreakdownInUserZone(System.currentTimeMillis());
			yearStr = now[0];
			monthStr = now[1];
			dayStr = now[2];
		}

		if (yearStr == null)
		{
			M_log.warn("dispatchDashboard - no year parameter");

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}
		int year = 0;
		try
		{
			year = Integer.valueOf(yearStr);
		}
		catch (NumberFormatException e)
		{
			M_log.warn("dispatchDashboard - year not int: " + yearStr);

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}

		if (monthStr == null)
		{
			M_log.warn("dispatchDashboard - no month parameter");

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}
		int month = 0;
		try
		{
			month = Integer.valueOf(monthStr);
		}
		catch (NumberFormatException e)
		{
			M_log.warn("dispatchDashboard - month not int: " + monthStr);

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}

		if (dayStr == null)
		{
			M_log.warn("dispatchDashboard - no day parameter");

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}
		int day = 0;
		try
		{
			day = Integer.valueOf(dayStr);
		}
		catch (NumberFormatException e)
		{
			M_log.warn("dispatchDashboard - day not int: " + dayStr);

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}

		// collect the user's sites
		List<Site> visibleSites = new ArrayList<Site>();
		List<Site> hiddenSites = new ArrayList<Site>();
		siteService().getOrderedSites(userId, visibleSites, hiddenSites);

		// collect our limited number of dashboard sites
		List<Site> dashboardSites = new ArrayList<Site>();
		int count = 0;
		for (Site site : visibleSites)
		{
			if (count == DASHBOARD_SITE_LIMIT) break;
			dashboardSites.add(site);
			count++;
		}

		// one announcement from each of the user's visible (and published) sites
		List<Map<String, Object>> announcements = new ArrayList<Map<String, Object>>();
		rv.put("announcements", announcements);
		try
		{
			for (Site site : dashboardSites)
			{
				// if the site does NOT include the announcement tool, or if the site is not published, skip it
				if (site.getTools(announcementTool).isEmpty()) continue;
				if (!site.isPublished()) continue;

				String channelId = announcementService().channelReference(site.getId(), SiteService.MAIN_CONTAINER);

				// the Evaluator role might not have permission to announcements
				if (!announcementService().allowGetChannel(channelId)) continue;

				List<AnnouncementMessage> messages = announcementService().getRecentMessages(channelId, 1);
				for (AnnouncementMessage m : messages)
				{
					Map<String, Object> announcement = new HashMap<String, Object>();
					announcements.add(announcement);
					loadAnnouncement(m, announcement, true, false, site);

				}
			}
		}
		catch (PermissionException e)
		{
			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.accessDenied.getId());
			return rv;
		}

		List<Map<String, Object>> motds = new ArrayList<Map<String, Object>>();
		rv.put("motd", motds);
		try
		{
			List<AnnouncementMessage> messages = (List<AnnouncementMessage>) announcementService().getMessages("/announcement/channel/!site/motd",
					null, 0, false, false, false);
			for (AnnouncementMessage m : messages)
			{
				Map<String, Object> motd = new HashMap<String, Object>();
				motds.add(motd);
				loadAnnouncement(m, motd, true, false, null);
			}
		}
		catch (PermissionException e)
		{
			// not permitted to motd? ignore
		}

		doEvents(rv, year, month, day, dashboardSites, userId);

		doEvenstDays(rv, year, month, dashboardSites, userId);

		Preferences prefs = preferencesService().getPreferences(userId);
		ResourceProperties props = prefs.getProperties("sakai:time");
		String val = props.getProperty("timezone");
		if (val == null) val = TimeZone.getDefault().getID();
		rv.put("timezone", val);

		// add status parameter
		rv.put(CdpStatus.CDP_STATUS, CdpStatus.success.getId());

		return rv;
	}

	protected Map<String, Object> dispatchEvents(HttpServletRequest req, HttpServletResponse res, Map<String, Object> parameters, String path,
			String userId) throws ServletException, IOException
	{
		Map<String, Object> rv = new HashMap<String, Object>();

		// if not authenticated
		if (userId == null)
		{
			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.accessDenied.getId());
			return rv;
		}

		// year month and day are expressed in the user's local (browser) time zone: year ~ 2013, month ~ (1..12), day ~ (1..31)
		String yearStr = (String) parameters.get("year");
		String monthStr = (String) parameters.get("month");
		String dayStr = (String) parameters.get("day");

		// If missing, use the "current" based on server time, user's time zone
		if ((yearStr == null) && (monthStr == null) && (dayStr == null))
		{
			String[] now = CdpResponseHelper.dateBreakdownInUserZone(System.currentTimeMillis());
			yearStr = now[0];
			monthStr = now[1];
			dayStr = now[2];
		}

		if (yearStr == null)
		{
			M_log.warn("dispatchEvents - no year parameter");

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}
		int year = 0;
		try
		{
			year = Integer.valueOf(yearStr);
		}
		catch (NumberFormatException e)
		{
			M_log.warn("dispatchEvents - year not int: " + yearStr);

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}

		if (monthStr == null)
		{
			M_log.warn("dispatchEvents - no month parameter");

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}
		int month = 0;
		try
		{
			month = Integer.valueOf(monthStr);
		}
		catch (NumberFormatException e)
		{
			M_log.warn("dispatchEvents - month not int: " + monthStr);

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}

		if (dayStr == null)
		{
			M_log.warn("dispatchEvents - no day parameter");

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}
		int day = 0;
		try
		{
			day = Integer.valueOf(dayStr);
		}
		catch (NumberFormatException e)
		{
			M_log.warn("dispatchEvents - day not int: " + dayStr);

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}

		String includeDaysStr = (String) parameters.get("includeDays");
		boolean includeDays = "1".equals(includeDaysStr);

		List<Site> dashboardSites = new ArrayList<Site>();

		// we might get the list of site ids for the user's dashboard sites as a parameter
		String sites = (String) parameters.get("sites");
		if (sites != null)
		{
			String[] sitesArray = StringUtil.split(sites, ",");
			for (String siteId : sitesArray)
			{
				try
				{
					dashboardSites.add(siteService().getSite(siteId));
				}
				catch (IdUnusedException e)
				{
				}
			}
		}

		// otherwise compute them
		else
		{
			// collect the user's sites
			List<Site> visibleSites = new ArrayList<Site>();
			List<Site> hiddenSites = new ArrayList<Site>();
			siteService().getOrderedSites(userId, visibleSites, hiddenSites);

			// collect our limited number of dashboard sites
			int count = 0;
			for (Site site : visibleSites)
			{
				if (count == DASHBOARD_SITE_LIMIT) break;
				dashboardSites.add(site);
				count++;
			}
		}
		doEvents(rv, year, month, day, dashboardSites, userId);

		if (includeDays)
		{
			doEvenstDays(rv, year, month, dashboardSites, userId);
		}

		Preferences prefs = preferencesService().getPreferences(userId);
		ResourceProperties props = prefs.getProperties("sakai:time");
		String val = props.getProperty("timezone");
		if (val == null) val = TimeZone.getDefault().getID();
		rv.put("timezone", val);

		// add status parameter
		rv.put(CdpStatus.CDP_STATUS, CdpStatus.success.getId());

		return rv;
	}

	protected Map<String, Object> dispatchEventsDays(HttpServletRequest req, HttpServletResponse res, Map<String, Object> parameters, String path,
			String userId) throws ServletException, IOException
	{
		Map<String, Object> rv = new HashMap<String, Object>();

		// if not authenticated
		if (userId == null)
		{
			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.accessDenied.getId());
			return rv;
		}

		// year and month are expressed in the user's local (browser) time zone: year ~ 2013, month ~ (1..12).
		String yearStr = (String) parameters.get("year");
		String monthStr = (String) parameters.get("month");

		// If missing, use the "current" based on server time, user's time zone
		if ((yearStr == null) && (monthStr == null))
		{
			String[] now = CdpResponseHelper.dateBreakdownInUserZone(System.currentTimeMillis());
			yearStr = now[0];
			monthStr = now[1];
		}

		if (yearStr == null)
		{
			M_log.warn("dispatchEventsDays - no year parameter");

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}
		int year = 0;
		try
		{
			year = Integer.valueOf(yearStr);
		}
		catch (NumberFormatException e)
		{
			M_log.warn("dispatchEventsDays - year not int: " + yearStr);

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}

		if (monthStr == null)
		{
			M_log.warn("dispatchEventsDays - no month parameter");

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}
		int month = 0;
		try
		{
			month = Integer.valueOf(monthStr);
		}
		catch (NumberFormatException e)
		{
			M_log.warn("dispatchEventsDays - month not int: " + monthStr);

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}

		List<Site> dashboardSites = new ArrayList<Site>();

		// we might get the list of site ids for the user's dashboard sites as a parameter
		String sites = (String) parameters.get("sites");
		if (sites != null)
		{
			String[] sitesArray = StringUtil.split(sites, ",");
			for (String siteId : sitesArray)
			{
				try
				{
					dashboardSites.add(siteService().getSite(siteId));
				}
				catch (IdUnusedException e)
				{
				}
			}
		}

		// otherwise compute them
		else
		{
			// collect the user's sites
			List<Site> visibleSites = new ArrayList<Site>();
			List<Site> hiddenSites = new ArrayList<Site>();
			siteService().getOrderedSites(userId, visibleSites, hiddenSites);

			// collect our limited number of dashboard sites
			int count = 0;
			for (Site site : visibleSites)
			{
				if (count == DASHBOARD_SITE_LIMIT) break;
				dashboardSites.add(site);
				count++;
			}
		}

		doEvenstDays(rv, year, month, dashboardSites, userId);

		// add status parameter
		rv.put(CdpStatus.CDP_STATUS, CdpStatus.success.getId());

		return rv;
	}

	/**
	 * Process the eventsDays response.
	 * 
	 * @param rv
	 * @param year
	 * @param month
	 * @param sites
	 */
	protected void doEvenstDays(Map<String, Object> rv, int year, int month, List<Site> sites, String userId)
	{
		// build up a map to return
		Map<String, Object> map = new HashMap<String, Object>();
		rv.put("eventsDays", map);

		map.put("year", Integer.toString(year));
		map.put("month", CdpResponseHelper.twoDigits(month));

		List<Integer> days = new ArrayList<Integer>();
		map.put("days", days);

		// get events
		List<CmEvent> events = getMonthEvents(sites, year, month);

		// get the CM events
		List<CmEvent> cmEvents = getMonthCmEvents(sites, userId, year, month);

		// combine
		cmEvents.addAll(events);

		// check each day
		for (int day = 1; day <= 31; day++)
		{
			// make a time range for this day in the month / year, in the user's zone
			TimeRange dayRange = CdpResponseHelper.dayInUserZone(year, month, day);

			if (dayRange == null) break;

			// do any events overlap this range?
			for (CmEvent event : cmEvents)
			{
				long duration = 1000;
				if (event.end != null) duration = event.end.getTime() - event.start.getTime();
				TimeRange range = timeService().newTimeRange(event.start.getTime(), duration);
				if (range.overlaps(dayRange))
				{
					days.add(day);
					break;
				}
			}
		}
	}

	protected void doEvents(Map<String, Object> rv, int year, int month, int day, List<Site> sites, String userId)
	{
		// the date
		// rv.put("eventsDate", months[month - 1] + " " + twoDigits(day) + ", " + year);

		TimeRange dayRange = CdpResponseHelper.dayInUserZone(year, month, day);
		rv.put("eventsDate", CdpResponseHelper.dateDisplayInUserZone(dayRange.firstTime().getTime()));
		rv.put("eventsDateZone", CdpResponseHelper.zoneDisplayInUserZone(dayRange.firstTime().getTime()));

		// the events
		List<Map<String, Object>> events = new ArrayList<Map<String, Object>>();
		rv.put("events", events);

		// get events for this day
		List<CmEvent> dayEvents = getDayEvents(sites, year, month, day);

		// get the CM events for this day
		List<CmEvent> cmDayEvents = getDayCmEvents(sites, userId, year, month, day);

		// combine
		cmDayEvents.addAll(dayEvents);

		// sort by time ascending
		Collections.sort(cmDayEvents, new Comparator<CmEvent>()
		{
			public int compare(CmEvent arg0, CmEvent arg1)
			{
				return arg0.start.compareTo(arg1.start);
			}
		});

		for (CmEvent e : cmDayEvents)
		{
			Map<String, Object> event = new HashMap<String, Object>();
			events.add(event);

			event.put("siteId", e.site.getId());
			event.put("siteTitle", e.site.getTitle());

			if (e.end != null)
			{
				event.put("time", CdpResponseHelper.timeDisplayInUserZone(e.start.getTime(), e.end.getTime()));
			}
			else
			{
				event.put("time", CdpResponseHelper.timeDisplayInUserZone(e.start.getTime()));
			}
			event.put("type", e.type);
			if (e.description != null) event.put("description", e.description);
			if (e.location != null) event.put("location", e.location);
			event.put("title", e.title);

			if (e.cmType != null) event.put("cmType", e.cmType);
			if (e.actionEdit != null) event.put("actionEdit", e.actionEdit);
			if (e.actionPerform != null) event.put("actionPerform", e.actionPerform);
			if (e.actionReview != null) event.put("actionReview", e.actionReview);

			if ((e.attachments != null) && (!e.attachments.isEmpty()))
			{
				List<Map<String, String>> attachments = new ArrayList<Map<String, String>>();
				event.put("attachments", attachments);

				for (Reference a : (List<Reference>) (e.attachments))
				{
					Map<String, String> attachment = new HashMap<String, String>();
					attachments.add(attachment);

					String url = a.getUrl();
					attachment.put("url", url);
					String description = a.getProperties().getPropertyFormatted("DAV:displayname");
					attachment.put("description", description);
				}
			}
		}
	}

	protected List<CmEvent> getDayCmEvents(List<Site> sites, String userId, int year, int month, int day)
	{
		List<CmEvent> rv = new ArrayList<CmEvent>();
		TimeRange dayRange = CdpResponseHelper.dayInUserZone(year, month, day);

		for (Site site : sites)
		{
			boolean mayEdit = courseMapService().allowEditMap(site.getId(), userId);

			// if the site does NOT include the coursemap tool, or is not published, skip it
			if (site.getTools(coursemapTool).isEmpty()) continue;
			if (!site.isPublished()) continue;

			// Note: even if this is for the (mayEdit) instructor, we are using the map, not the getMapEdit(). The map is cached, and for the dashboard request, needed again for the month events
			CourseMapMap map = courseMapService().getMap(site.getId(), userId);

			for (CourseMapItem item : map.getItems())
			{
				Date open = item.getOpen();
				if ((open != null) && (dayRange.contains(timeService().newTime(open.getTime()))))
				{
					CmEvent event = new CmEvent();
					rv.add(event);
					event.id = item.getMapId();
					event.title = "Open Date for " + item.getType().getDisplayString() + ": " + item.getTitle();
					event.type = "CourseMap Date";
					event.start = open;
					event.site = site;
					event.cmType = item.getType().toString();
					if (mayEdit && (item.getEditLink() != null))
					{
						event.actionEdit = "/" + item.getToolId() + item.getEditLink();
					}
					if ((item.getPerformLink() != null) && ((!item.getBlocked()) || mayEdit))
					{
						event.actionPerform = "/" + item.getToolId() + item.getPerformLink();
					}
					if (item.getReviewLink() != null)
					{
						event.actionReview = "/" + item.getToolId() + item.getReviewLink();
					}
				}
				Date due = item.getDue();
				if ((due != null) && (dayRange.contains(timeService().newTime(due.getTime()))))
				{
					CmEvent event = new CmEvent();
					rv.add(event);
					event.id = item.getMapId();
					event.title = "Due Date for " + item.getType().getDisplayString() + ": " + item.getTitle();
					event.type = "CourseMap Date";
					event.start = due;
					event.site = site;
					event.cmType = item.getType().toString();
					if (mayEdit && (item.getEditLink() != null))
					{
						event.actionEdit = "/" + item.getToolId() + item.getEditLink();
					}
					if ((item.getPerformLink() != null) && ((!item.getBlocked()) || mayEdit))
					{
						event.actionPerform = "/" + item.getToolId() + item.getPerformLink();
					}
					if (item.getReviewLink() != null)
					{
						event.actionReview = "/" + item.getToolId() + item.getReviewLink();
					}
				}
			}
		}

		return rv;
	}

	/**
	 * Get the user's events from all sites for this month in this year (in user's time zone terms).
	 * 
	 * @param sites
	 *        The user's sites.
	 * @param year
	 *        The year (~2013)
	 * @param month
	 *        The month (~1..12)
	 * @param day
	 *        The day (~1..31)
	 * @return The events.
	 */
	@SuppressWarnings("unchecked")
	protected List<CmEvent> getDayEvents(List<Site> sites, int year, int month, int day)
	{
		TimeRange dayRange = CdpResponseHelper.dayInUserZone(year, month, day);
		List<CmEvent> rv = new ArrayList<CmEvent>();

		for (Site site : sites)
		{
			// if the site does NOT include the calendar tool, or is not published, skip it
			if (site.getTools(calendarTool).isEmpty()) continue;
			if (!site.isPublished()) continue;

			try
			{
				Calendar cal = calendarService().getCalendar(calendarService().calendarReference(site.getId(), SiteService.MAIN_CONTAINER));
				List<CalendarEvent> events = cal.getEvents(dayRange, null);

				for (CalendarEvent e : events)
				{
					CmEvent event = new CmEvent();
					event.start = new Date(e.getRange().firstTime().getTime());
					event.end = new Date(e.getRange().lastTime().getTime());
					event.type = e.getType();
					event.description = e.getDescriptionFormatted();
					event.location = e.getLocation();
					event.title = e.getDisplayName();
					event.attachments = e.getAttachments();
					event.site = site;
					rv.add(event);
				}
			}
			catch (IdUnusedException e)
			{
				// no calendar for the site
			}
			catch (PermissionException e)
			{
				// not permitted? Evaluator may not be permitted. Ignore
			}
		}

		return rv;
	}

	/**
	 * Get the user's events from in this site's CM for this month in this year (in user's time zone terms).
	 * 
	 * @param site
	 *        The site.
	 * @param userId
	 *        The user id.
	 * @param year
	 *        The year (~2013)
	 * @param month
	 *        The month (~1..12)
	 * @return The events.
	 */
	protected List<CmEvent> getMonthCmEvents(List<Site> sites, String userId, int year, int month)
	{
		TimeRange monthRange = CdpResponseHelper.monthInUserZone(year, month);
		List<CmEvent> rv = new ArrayList<CmEvent>();
		for (Site site : sites)
		{
			// if the site does NOT include the calendar tool, or is not published, skip it
			if (site.getTools(coursemapTool).isEmpty()) continue;
			if (!site.isPublished()) continue;

			CourseMapMap map = courseMapService().getMap(site.getId(), userId);
			for (CourseMapItem item : map.getItems())
			{
				Date open = item.getOpen();
				if ((open != null) && (monthRange.contains(timeService().newTime(open.getTime()))))
				{
					CmEvent event = new CmEvent();
					rv.add(event);
					event.start = open;
				}
				Date due = item.getDue();
				if ((due != null) && (monthRange.contains(timeService().newTime(due.getTime()))))
				{
					CmEvent event = new CmEvent();
					rv.add(event);
					event.start = due;
				}
			}
		}

		return rv;
	}

	/**
	 * Get the user's events from all sites for this month in this year (in user's time zone terms).
	 * 
	 * @param sites
	 *        The user's sites.
	 * @param year
	 *        The year (~2013)
	 * @param month
	 *        The month (~1..12)
	 * @return The events.
	 */
	protected List<CmEvent> getMonthEvents(List<Site> sites, int year, int month)
	{
		TimeRange monthRange = CdpResponseHelper.monthInUserZone(year, month);
		List<CmEvent> rv = new ArrayList<CmEvent>();

		for (Site site : sites)
		{
			// if the site does NOT include the calendar tool, or is not published, skip it
			if (site.getTools(calendarTool).isEmpty()) continue;
			if (!site.isPublished()) continue;

			try
			{
				Calendar cal = calendarService().getCalendar(calendarService().calendarReference(site.getId(), SiteService.MAIN_CONTAINER));
				@SuppressWarnings("unchecked")
				List<CalendarEvent> events = cal.getEvents(monthRange, null);

				for (CalendarEvent e : events)
				{
					CmEvent event = new CmEvent();
					event.start = new Date(e.getRange().firstTime().getTime());
					event.end = new Date(e.getRange().lastTime().getTime());
					event.site = site;
					rv.add(event);
				}
			}
			catch (IdUnusedException e)
			{
				// no calendar for the site
			}
			catch (PermissionException e)
			{
				// not permitted - Evaluator may not be permitted. Ignore.
			}
		}

		return rv;
	}

	@SuppressWarnings("unchecked")
	protected void loadAnnouncement(AnnouncementMessage msg, Map<String, Object> messageMap, boolean bodyNotPath, boolean plainText, Site site)
	{
		messageMap.put("subject", msg.getAnnouncementHeader().getSubject());
		messageMap.put("messageId", msg.getReference());
		long date = msg.getAnnouncementHeader().getDate().getTime();
		try
		{
			date = msg.getProperties().getTimeProperty(AnnouncementService.RELEASE_DATE).getTime();
		}
		catch (EntityPropertyTypeException e)
		{
		}
		catch (EntityPropertyNotDefinedException e)
		{
		}
		messageMap.put("date", CdpResponseHelper.dateTimeDisplayInUserZone(date));
		messageMap.put("day", CdpResponseHelper.dateDisplayInUserZone(date));
		messageMap.put("from", msg.getAnnouncementHeader().getFrom().getDisplayName());
		messageMap.put("fromUserId", msg.getAnnouncementHeader().getFrom().getId());

		if (bodyNotPath)
		{
			String body = msg.getBody();
			if (plainText)
			{
				body = StringHtml.plainFromHtml(body);
			}
			else
			{
				body = CdpResponseHelper.accessToCdpDoc(body, false);
			}
			messageMap.put("body", body);
		}
		else
		{
			messageMap.put("bodyPath", "/cdp/doc/announcement" + msg.getReference());
		}

		messageMap.put("unread", CdpResponseHelper.formatBoolean(false));
		messageMap.put("draft", CdpResponseHelper.formatBoolean(msg.getHeader().getDraft()));

		String priorityStr = msg.getProperties().getProperty(AnnouncementService.NOTIFICATION_LEVEL);
		Boolean priority = ((priorityStr == null) ? Boolean.FALSE : Boolean.valueOf(priorityStr.equals("r")));
		messageMap.put("priority", CdpResponseHelper.formatBoolean(priority));

		try
		{
			Time releaseDate = msg.getProperties().getTimeProperty(AnnouncementService.RELEASE_DATE);
			if (releaseDate != null) messageMap.put("releaseDate", CdpResponseHelper.dateTimeDisplayInUserZone(releaseDate.getTime()));
		}
		catch (EntityPropertyNotDefinedException e)
		{
		}
		catch (EntityPropertyTypeException e)
		{
		}

		if (!msg.getAnnouncementHeader().getAttachments().isEmpty())
		{
			List<Map<String, String>> attachments = new ArrayList<Map<String, String>>();
			messageMap.put("attachments", attachments);

			for (Reference a : (List<Reference>) (msg.getAnnouncementHeader().getAttachments()))
			{
				if ((a == null) || (a.getUrl() == null) || (a.getProperties() == null)) continue;

				Map<String, String> attachment = new HashMap<String, String>();
				attachments.add(attachment);

				String url = a.getUrl();
				attachment.put("url", url);
				String description = a.getProperties().getPropertyFormatted("DAV:displayname");
				attachment.put("description", description);
			}
		}

		if (site != null)
		{
			messageMap.put("siteId", site.getId());
			messageMap.put("siteTitle", site.getTitle());
		}

		// assuming MOTD
		else
		{
			messageMap.put("siteTitle", "Etudes");
		}
	}

	/**
	 * @return The SiteService, via the component manager.
	 */
	protected SiteService siteService()
	{
		return (SiteService) ComponentManager.get(SiteService.class);
	}
}
