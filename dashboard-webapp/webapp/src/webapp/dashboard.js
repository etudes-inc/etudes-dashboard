tool_obj =
{
	title: "DASHBOARD",
	showReset: true,

	eventsDays :
	{
		year: 0,
		month: 0,
		days: []
	},

	timezone : null,

	selectedDate : null,

	sites : null,

	start: function(obj, data)
	{		
		setTitle(obj.title);

		$("#e3_tool_configure").removeClass("e3_offstage").unbind("click").click(function(){obj.editTabs(obj);return false;});

		$("#prefs_tabs_removeAllTabs").unbind("click").click(function(){obj.removeAllTabs(obj);return false;});
		$("#prefs_tabs_addAllTabs").unbind("click").click(function(){obj.addAllTabs(obj);return false;});
		$("#prefs_tabs_removeSelectedTabs").unbind("click").click(function(){obj.removeSelectedTabs(obj);return false;});
		$("#prefs_tabs_addSelectedTabs").unbind("click").click(function(){obj.addSelectedTabs(obj);return false;});
		$("#prefs_tabs_moveTabsStart").unbind("click").click(function(){obj.moveTabsStart(obj);return false;});
		$("#prefs_tabs_moveTabsUp").unbind("click").click(function(){obj.moveTabsUp(obj);return false;});
		$("#prefs_tabs_moveTabsDown").unbind("click").click(function(){obj.moveTabsDown(obj);return false;});
		$("#prefs_tabs_moveTabsEnd").unbind("click").click(function(){obj.moveTabsEnd(obj);return false;});
		setupDialog("prefs_zone_dialog", "Done", function(){return obj.saveZone(obj);});
		$("#prefs_zone_link").unbind("click").click(function(){obj.editZone(obj);return false;});

		$("#dashboard_help").unbind("click").click(function(){openAlert("dashboard_alertHelp");return false;});
		$("#dashboard_alertHelp_configure").unbind("click").click(function(){$("#dashboard_alertHelp").dialog("close");obj.editTabs(obj);return false;});

		setupAlert("dashboard_display_event");
		setupAlert("dashboard_display_announcement");
		setupDialog("prefs_tabs_dialog", "Done", function(){return obj.saveTabs(obj);});

		$("#dashboard_calendar").datepicker(
		{
			dayNamesMin: ["Sun", "Mon" ,"Tue", "Wed", "Thu", "Fri", "Sat"],
			// changeMonth: true,
			// changeYear: true,
			// showButtonPanel: true,
			// showOtherMonths: true,
			onSelect: function(date, inst)
			{
				obj.selectedDate = $("#dashboard_calendar").datepicker("getDate");
				obj.loadEvents(obj);
			},
			beforeShowDay: function(date, inst)
			{
				return obj.checkDate(obj, date);
			},
			onChangeMonthYear: function(year, month, inst)
			{
				// year ~ 2013, month ~ 1..12
				obj.loadEventsDays(obj, year, month);
			},
			dateFormat: "M dd, yy"
		});

		obj.reset(obj);

		startHeartbeat();
	},

	stop: function(obj, save)
	{
		stopHeartbeat();
	},

	reset: function(obj)
	{
		userSites.withStatus = true;
		userSites.statusLimit = 12;
		userSites.load(true, function()
		{
			obj.setSites(obj, userSites.inOrder());

			var anncParams = new Object();
			anncParams.sites = obj.sites;
			requestCdp("dashboard_announcements", anncParams, function(anncData)
			{
				var eventParams = new Object();
				if (obj.selectedDate != null)	
				{
					eventParams.year = obj.selectedDate.getFullYear().toString();
					eventParams.month = (obj.selectedDate.getMonth()+1).toString();
					eventParams.day = obj.selectedDate.getDate().toString();
				}
				eventParams.includeDays = "1";
				eventParams.sites = obj.sites;
				requestCdp("dashboard_events", eventParams, function(eventData)
				{
					obj.timezone = eventData.timezone;
					$("#dashboard_calendar").datepicker("setDate", eventData.eventsDate);
					obj.populateEvents(obj, eventData.eventsDate, eventData.eventsDateZone, eventData.events);
					obj.eventsDays = eventData.eventsDays;
					$("#dashboard_calendar").datepicker("refresh");
					adjustForNewHeight();
				});

				obj.populateAnnouncements(obj, anncData.announcements, anncData.motd);
				adjustForNewHeight();
			});

			obj.populateSiteTabs(obj, userSites.inOrder());
			adjustForNewHeight();
		});
	},

	// respond to browser window resize
	resize: function(obj)
	{
		// the site tabs may have caused a height change
		adjustForNewHeight();
	},

	setSites: function(obj, sites)
	{
		obj.sites = "";
		if (sites != null)
		{
			var count = 0;
			$.each(sites, function(index, value)
			{
				// skip non-favorites
				if (value.visible == 0) return;
				
				// stop after we hit 12
				if (count == 12) return;

				obj.sites += value.siteId + ",";
			});
		}
		
		if (obj.sites != "")
		{
			obj.sites = obj.sites.substring(0, obj.sites.length-1);
		}
	},

	loadEvents: function(obj)
	{
		var data = new Object();
		if (obj.selectedDate != null)	
		{
			data.year = obj.selectedDate.getFullYear().toString();
			data.month = (obj.selectedDate.getMonth()+1).toString();
			data.day = obj.selectedDate.getDate().toString();
		}
		data.sites = obj.sites;
		requestCdp("dashboard_events", data, function(data)
		{
			obj.populateEvents(obj, data.eventsDate, data.eventsDateZone, data.events);
			adjustForNewHeight();
		});
	},

	loadEventsDays: function(obj, year, month)
	{
		obj.eventsDays.days = [];
		var data = new Object();
		data.year = year.toString();
		data.month = month.toString();
		data.sites = obj.sites;
		requestCdp("dashboard_eventsDays", data, function(data)
		{
			obj.eventsDays = data.eventsDays;
			$("#dashboard_calendar").datepicker("refresh");
			adjustForNewHeight();
		});
	},

	goToSite: function(siteId)
	{
		selectSite(siteId);
		return false;
	},

	goToSiteTool: function(siteId, toolId)
	{
		selectSiteTool(siteId, toolId);
		return false;
	},

	populateSiteTabs: function(obj, sites)
	{
		$("#siteTabs").empty();
		$("#noSiteTabs").addClass("e3_offstage");

		var any = false;
		if (sites != null)
		{
			var count = 0;
			$.each(sites, function(index, value)
			{
				// skip non-favorites
				if (value.visible == 0) return;
				
				// stop after we hit 12
				if (count == 12) return;

				any = true;

				var el = $('<div class="aFavorite" />');
				if (value.published == 1)
				{
					obj.populatePublished(obj, value, el);				
				}
				else
				{
					obj.populateUnpublished(obj, value, el);			
				}
				$("#siteTabs").append(el);
				last = el;
				count++;
			});			
		}

		if (!any)
		{
			$("#noSiteTabs").removeClass("e3_offstage");
		}
	},
	
	badgeValue : function(value)
	{
		if (value < 1000)
		{
			return value.toString();
		}
		return "!!!";
	},

	populatePublished: function(obj, site, target)
	{
		var bd = $('<div class="aFavoriteBody aFavorite_hot" />');
		$(bd).click(function(){return obj.goToSite(site.siteId);return false;});

		var bdIcons = $('<div class="aFavoriteBodyIcons" />');

		var a = $('<a href="" />').attr("title","users online").addClass("badged");
		var i = $('<img class="e3_no_border" src="support/icons/users-present.png" />');
		var s = $('<span />');
		$(a).append(i);
		$(a).append(s);
		$(bdIcons).append(a);
		if (site.online > 0)
		{
			$(s).text(obj.badgeValue(site.online));
		}
		else
		{
			$(a).addClass("dimBadged");
			$(s).addClass("e3_offstage");
		}

		a = $('<a href="" />').attr("title","unread private messages").addClass("badged");
		i = $('<img class="e3_no_border" src="support/icons/mail.png" />');
		s = $('<span />');
		$(a).append(i);
		$(a).append(s);
		$(bdIcons).append(a);
		if (site.unreadMessages > 0)
		{
			$(s).text(obj.badgeValue(site.unreadMessages));
		}
		else
		{
			$(a).addClass("dimBadged");
			$(s).addClass("e3_offstage");
		}

		a = $('<a href="" />').attr("title","unread discussion topics").addClass("badged");
		i = $('<img class="e3_no_border" src="support/icons/posts.png" />');
		s = $('<span />');
		$(a).append(i);
		$(a).append(s);
		$(bdIcons).append(a);
		if (site.unreadPosts > 0)
		{
			$(s).text(obj.badgeValue(site.unreadPosts));
		}
		else
		{
			$(a).addClass("dimBadged");
			$(s).addClass("e3_offstage");
		}

		if ((site.instructorPrivileges == 1))
		{
			a = $('<a href="" />').attr("title","early alert students").addClass("badged");
			i = $('<img class="e3_no_border" src="support/icons/early-alert-students.png" />');
			s = $('<span />');
			$(a).append(i);
			$(a).append(s);
			$(bdIcons).append(a);
			if (site.notVisitAlerts > 0)
			{
				$(s).text(obj.badgeValue(site.notVisitAlerts));
			}
			else
			{
				$(a).addClass("dimBadged");
				$(s).addClass("e3_offstage");
			}
		}
		else
		{
			a = $('<a href="" />').attr("title","items needing review").addClass("badged");
			i = $('<img class="e3_no_border" src="support/icons/reviewed.png" />');
			s = $('<span />');
			$(a).append(i);
			$(a).append(s);
			$(bdIcons).append(a);
			var reviewCount = parseInt(site.reviewCountMneme) + parseInt(site.reviewCountJForum);
			if (reviewCount > 0)
			{
				$(s).text(obj.badgeValue(reviewCount));
			}
			else
			{
				$(a).addClass("dimBadged");
				$(s).addClass("e3_offstage");
			}
		}

		var title = $('<div class="aFavorite_title" />').text(site.title);

		$(bd).append(bdIcons);
		$(bd).append(title);

		$(target).empty();
		$(target).append(bd);
	},

	populateUnpublished: function(obj, site, target)
	{
		var bd = $('<div class="aFavoriteBody" />');
		var bdIcons = $('<div class="aFavoriteBodyIcons" />');

		if (site.visitUnpublished == 1)
		{
			$(bd).addClass("aFavorite_hot");
			$(bd).click(function(){return obj.goToSite(site.siteId);return false;});
		}
		else
		{
			$(bd).addClass("aFavorite_disabled");
		}

		if ((site.willPublish == 1))
		{
			var i = $('<img src="support/icons/calendar32x32.png" style="width:32px;height:32px; vertical-align:middle; margin-right:8px;" />');
			$(bdIcons).append(i);

			if (site.publishOn !== undefined)
			{
				$(bdIcons).append('<span>Opens ' + site.publishOn + '</span>');
			}
			else
			{
				$(bdIcons).append('<span>Opens Soon</span>');
			}
		}
		else
		{
			var i = $('<img src="support/icons/No.png" style="width:32px;height:32px; vertical-align:middle; margin-right:8px;" />');
			$(bdIcons).append(i);
			$(bdIcons).append('<span>Closed</span>');
		}

		var title = $('<div class="aFavorite_title" />');
		$(title).text(site.title);
		if (site.visitUnpublished == 0)
		{
			$(title).addClass("aFavorite_title_unp");
		}

		$(bd).append(bdIcons);
		$(bd).append(title);

		$(target).empty();
		$(target).append(bd);
	},

	populateEvents: function(obj, date, zone, events)
	{
		$("#dashboard_timezone").empty().text(zone);
		$("#dashboard_event_date").empty().text(date);

		var div = $("#dashboard_events").empty();
		var option = 0;

		if (events.length > 0)
		{
			$.each(events, function(index, value)
			{
				var aDiv = $('<div class="dashboard_item" />');
				div.append(aDiv);
				var typeClass = obj.classForType(obj, value);
				if (typeClass != null)
				{
					typeClass = 'class="e3_toolUiLinkU ' + typeClass + '" ';
				}
				else
				{
					typeClass = 'class="e3_toolUiLinkU" ';
				}
				var aLink = $('<a ' + typeClass + 'style="font-weight:bold" href="" />').text(value.title).click(function(){obj.showEvent(obj, value);return false;});
				aDiv.append(aLink);
				var aTime = $('<div class="dashboard_item_info_gotcha" />').text(value.time);
				aDiv.append(aTime);
				
				var a = $('<a href="" />').text(value.siteTitle).addClass("e3_toolUiLinkU").click(function(){selectSite(value.siteId);return false;});
				var aFrom = $('<div class="dashboard_item_info" />').text(obj.displayTextForType(obj, value) + " - ").append(a);
				aDiv.append(aFrom);

				var aBody = $('<div />').html(value.description);
				if (option == 9)
				{
					$(aBody).addClass("dashboard_item_body");
					aDiv.append(aBody);
				}
				else if (option == 1)
				{
					var bodyHolder = $('<div />').addClass("dashboard_item_body_continues");
					$(aBody).addClass("dashboard_item_body_1");
					$(bodyHolder).append(aBody);
					aDiv.append(bodyHolder);
				}
				else if (option == 2)
				{
					var bodyHolder = $('<div />').addClass("dashboard_item_body_continues");
					$(aBody).addClass("dashboard_item_body_2");
					$(bodyHolder).append(aBody);
					aDiv.append(bodyHolder);
				}
				
				// else no body
			});
		}
		else
		{
			$(div).append('<div class="dashboard_item"><i>none</i></div>');
		}
	},

	classForType: function(obj, event)
	{
		if (event.type == "Academic Calendar") return "dashboard_academic_calendar";
		if (event.type == "Activity") return "dashboard_activity";
		if (event.type == "Cancellation") return "dashboard_cancellation";
		if (event.type == "Class section - Discussion") return "dashboard_class_section_discussion";
		if (event.type == "Class section - Lab") return "dashboard_class_section_lab";
		if (event.type == "Class section - Lecture") return "dashboard_class_section_lecture";
		if (event.type == "Class section - Small Group") return "dashboard_class_section_small_group";
		if (event.type == "Class session") return "dashboard_class_session";
		if (event.type == "Computer Session") return "dashboard_computer_session";
		if (event.type == "Deadline") return "dashboard_deadline";
		if (event.type == "Exam") return "dashboard_exam";
		if (event.type == "Meeting") return "dashboard_meeting";
		if (event.type == "Multidisciplinary Conference") return "dashboard_multidisciplinary_conference";
		if (event.type == "Quiz") return "dashboard_quiz";
		if (event.type == "Special event") return "dashboard_special_event";
		if (event.type == "Web Assignment") return "dashboard_web_assignment";
		if (event.type == "Holiday") return "dashboard_holiday";
		if (event.type == "CourseMap Date")
		{
			if (event.cmType == "assignment") return "dashboard_cm_assignment";
			if (event.cmType == "category") return "dashboard_cm_jforum";
			if (event.cmType == "forum") return "dashboard_cm_jforum";
			if (event.cmType == "module") return "dashboard_cm_melete";
			if (event.cmType == "survey") return "dashboard_cm_survey";
			if (event.cmType == "test") return "dashboard_cm_test";
			if (event.cmType == "topic") return "dashboard_cm_jforum";

			return "dashboard_coursemap";
		}

		return null;
	},

	displayTextForType: function(obj, event)
	{
		if (event.type == "CourseMap Date")
		{
			if (event.cmType == "assignment") return "AT&S";
			if (event.cmType == "category") return "Discussions";
			if (event.cmType == "forum") return "Discussions";
			if (event.cmType == "module") return "Modules";
			if (event.cmType == "survey") return "AT&S";
			if (event.cmType == "test") return "AT&S";
			if (event.cmType == "topic") return "Discussions";
		}
		
		return event.type;
	},

	populateAnnouncements: function(obj, announcements, motd)
	{
		var option = 0;

		var div = $("#dashboard_motd").empty();
		if (motd.length > 0)
		{
			$.each(motd, function(index, value)
			{
				obj.populateAnnouncement(obj, div, value, 2, false);
			});
		}

		div = $("#dashboard_announcements").empty();
		if (announcements.length > 0)
		{
			$.each(announcements, function(index, value)
			{
				obj.populateAnnouncement(obj, div, value, option, true);
			});
		}
		else
		{
			$(div).append('<div class="dashboard_item"><i>none</i></div>');
		}
	},

	populateAnnouncement: function(obj, div, value, option, includeSite)
	{
		var aDiv = $('<div class="dashboard_item" />');
		div.append(aDiv);
		var aLink = $('<a class="e3_toolUiLinkU" style="font-weight:bold" href="" />').text(value.subject).click(function(){obj.showAnnouncement(obj, value, includeSite);return false;});
		aDiv.append(aLink);
		var aFrom = $('<div class="dashboard_item_info" />').append(value.day);

		if (includeSite)
		{
			var a = $('<a href="" />').text(value.siteTitle).addClass("e3_toolUiLinkU").click(function(){selectSite(value.siteId);return false;});
			$(aFrom).append(" - ").append(a);
		}

		aDiv.append(aFrom);

		if (option == 9)
		{
			$(aBody).addClass("dashboard_item_body");
			var aBody = $('<div />').html(value.body);
			aDiv.append(aBody);
		}
		else if (option == 1)
		{
			var aBody = $('<div />').html(value.body);
			aDiv.append(aBody);
			$(aBody).addClass("dashboard_item_body_1");
		}
		else if (option == 2)
		{
			var aBody = $('<div />').html(value.body);
			aDiv.append(aBody);
			$(aBody).addClass("dashboard_item_body_2");
		}
		
		// else no body
	},

	showEvent: function(obj, event)
	{
		$("#dashboard_display_event_title").empty().text(event.title);
		$("#dashboard_display_event_title").removeClass();
		$("#dashboard_display_event_title").addClass("dashboard_display_event_title");
		var typeClass = obj.classForType(obj, event);
		if (typeClass != null) $("#dashboard_display_event_title").addClass(typeClass);
		$("#dashboard_display_event_time").empty().text(event.time);

		var a = $('<a href="" />');
		$(a).text(event.siteTitle);
		$(a).addClass("e3_toolUiLinkU");
		$(a).click(function(){selectSite(event.siteId);return false;});
		$("#dashboard_display_event_from").empty().text(obj.displayTextForType(obj, event) + " - ").append(a);

		$("#dashboard_display_event_body").empty().html(event.description);
		processMathMl();
		if ((event.location != null) && (event.location != ""))
		{
			$("#dashboard_display_event_location").empty().text(event.location);
			$("#dashboard_display_event_location_div").removeClass("e3_offstage");
		}
		else
		{
			$("#dashboard_display_event_location_div").addClass("e3_offstage");			
		}
		if (event.attachments != null)
		{
			$("#dashboard_display_event_attachments").removeClass("e3_offstage");
			var ul = $("#dashboard_display_event_attachments_list");
			$(ul).empty();
			$.each(event.attachments, function(index, value)
			{
				var li = $("<li />");
				ul.append(li);
				var aLink = $('<a target="_blank" />').text(value.description).attr("href", value.url);
				li.append(aLink);
			});
		}
		else
		{
			$("#dashboard_display_event_attachments").addClass("e3_offstage");
		}

		$("#dashboard_display_event_perform").addClass("e3_offstage");
		$("#dashboard_display_event_review").addClass("e3_offstage");
		$("#dashboard_display_event_edit").addClass("e3_offstage");
		$("#dashboard_display_actions").addClass("e3_offstage");
		if (event.actionPerform != null)
		{
			$("#dashboard_display_actions").removeClass("e3_offstage");
			$("#dashboard_display_event_perform").removeClass("e3_offstage");
			$("#dashboard_display_event_perform").empty().text("Go to " + event.cmType.capitalize());
			$("#dashboard_display_event_perform").unbind("click").click(function(){selectDirectTool(event.actionPerform);return false;});
		}
		if (event.actionReview != null)
		{
			$("#dashboard_display_actions").removeClass("e3_offstage");
			$("#dashboard_display_event_review").removeClass("e3_offstage");
			$("#dashboard_display_event_review").unbind("click").click(function(){selectDirectTool(event.actionReview);return false;});
		}
		if (event.actionEdit != null)
		{
			$("#dashboard_display_actions").removeClass("e3_offstage");
			$("#dashboard_display_event_edit").removeClass("e3_offstage");
			$("#dashboard_display_event_edit").unbind("click").click(function(){selectDirectTool(event.actionEdit);return false;});
		}

		$("#dashboard_display_event").dialog('open');
	},
	
	showAnnouncement: function(obj, annc, includeSite)
	{
		$("#dashboard_display_announcement_subject").empty().text(annc.subject);		
		$("#dashboard_display_announcement_from").empty().text(annc.from + ", " + annc.date);

		if (includeSite)
		{
			var a = $('<a href="" />').text(annc.siteTitle).addClass("e3_toolUiLinkU").click(function(){selectSite(annc.siteId);return false;});
			$("#dashboard_display_announcement_from").append(" - ").append(a);
		}

		$("#dashboard_display_announcement_body").empty().html(annc.body);
		processMathMl();
		if (annc.attachments != null)
		{
			$("#dashboard_display_announcement_attachments").removeClass("e3_offstage");
			var ul = $("#dashboard_display_announcement_attachments_list");
			$(ul).empty();
			$.each(annc.attachments, function(index, value)
			{
				var li = $("<li />");
				ul.append(li);
				var aLink = $('<a target="_blank" />').text(value.description).attr("href", value.url);
				li.append(aLink);
			});
		}
		else
		{
			$("#dashboard_display_announcement_attachments").addClass("e3_offstage");
		}

		$("#dashboard_display_announcement").dialog('open');
	},

	checkDate: function(obj, date)
	{
		if ((obj.eventsDays.year = date.getFullYear()) && (obj.eventsDays.month = (date.getMonth()+1)) && (obj.eventsDays.days.indexOf(date.getDate()) != -1)) return [true, "hasEvent", "event"];
		return [false, "", ""];
	},
	
	// from preferences.js
	editTabs: function(obj)
	{
		obj.populateDialogTabsLists(obj);
		$("#prefs_tabs_dialog").dialog('open');
	},
	
	saveTabs: function(obj)
	{
		var data = new Object();
		data.order = "";
		$("#prefs_tabs_tabs option").each(function()
		{
			data.order += $(this).val() + "\t";
		});

		requestCdp("preferences_setFavoriteSiteOrder", data, function(data)
		{
			resetPortal();
		});

		return true;
	},

	populateDialogTabsLists: function(obj)
	{
		$("#prefs_tabs_tabs").empty();
		var inOrder = userSites.inOrder();
		if (inOrder != null)
		{
			$.each(inOrder, function(index, value)
			{
				if (value.visible == 1)
				{
					var option = $('<option />').attr('value', value.siteId).text(value.title);
					$("#prefs_tabs_tabs").append(option);
				}				
			});
		}

		$("#prefs_tabs_others").empty();
		var byTerm = userSites.byTerm();
		if (byTerm != null)
		{
			$.each(byTerm, function(index, value)
			{
				if (value.visible == 0)
				{
					var option = $('<option />').attr('value', value.siteId).text(value.title);
					$("#prefs_tabs_others").append(option);
				}				
			});
		}
	},

	populateTabsLists: function(obj)
	{
		$("#prefs_tab_sites").empty();
		var inOrder = userSites.inOrder();
		if (inOrder != null)
		{
			$.each(inOrder, function(index, value)
			{
				if (value.visible == 1)
				{
					$("#prefs_tab_sites").append(value.title);
					$("#prefs_tab_sites").append("<br />");
				}				
			});
		}

		$("#prefs_other_sites").empty();
		var byTerm = userSites.byTerm();
		if (byTerm != null)
		{
			$.each(byTerm, function(index, value)
			{
				if (value.visible == 0)
				{
					$("#prefs_other_sites").append(value.title);
					$("#prefs_other_sites").append("<br />");
				}				
			});
		}
	},
	
	removeAllTabs: function(obj)
	{
		$("#prefs_tabs_tabs option").appendTo("#prefs_tabs_others");
	},

	addAllTabs: function(obj)
	{
		$("#prefs_tabs_others option").appendTo("#prefs_tabs_tabs");
	},

	removeSelectedTabs: function(obj)
	{
		$("#prefs_tabs_tabs option:selected").appendTo("#prefs_tabs_others");
	},

	addSelectedTabs: function(obj)
	{
		$("#prefs_tabs_others option:selected").appendTo("#prefs_tabs_tabs");
	},

	moveTabsStart: function(obj)
	{
		$("#prefs_tabs_tabs option:selected").prependTo("#prefs_tabs_tabs");
	},

	moveTabsUp: function(obj)
	{
		$('#prefs_tabs_tabs option:selected').each(function()
		{
			$(this).insertBefore($(this).prev());
		});
	},

	moveTabsDown: function(obj)
	{
		$('#prefs_tabs_tabs option:selected').reverse().each(function()
		{
			$(this).insertAfter($(this).next());
		});
	},

	moveTabsEnd: function(obj)
	{
		$("#prefs_tabs_tabs option:selected").appendTo("#prefs_tabs_tabs");
	},

	editZone: function(obj)
	{
		$("#prefs_zone_option").val(obj.timezone);

		$("#prefs_zone_dialog").dialog('open');
	},
	
	saveZone: function(obj)
	{
		var data = new Object();
		data.timezone = $("#prefs_zone_option").val();
		requestCdp("preferences_setPreferences", data, function(data)
		{
			obj.reset(obj);
		});

		return true;
	}
};

completeToolLoad();
