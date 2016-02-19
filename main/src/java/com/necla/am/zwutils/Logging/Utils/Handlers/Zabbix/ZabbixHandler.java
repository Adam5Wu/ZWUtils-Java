/*
 * Copyright (c) 2011 - 2016, Zhenyu Wu, NEC Labs America Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of ZWUtils-Java nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * // @formatter:on
 */

package com.necla.am.zwutils.Logging.Utils.Handlers.Zabbix;

import java.io.File;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.necla.am.zwutils.Config.Container;
import com.necla.am.zwutils.Config.Data;
import com.necla.am.zwutils.Config.DataFile;
import com.necla.am.zwutils.Config.DataMap;
import com.necla.am.zwutils.Logging.DebugLog;
import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Logging.Utils.Handlers.Zabbix.api.ZabbixAPI;
import com.necla.am.zwutils.Logging.Utils.Handlers.Zabbix.api.ZabbixReport;
import com.necla.am.zwutils.Logging.Utils.Handlers.Zabbix.api.ZabbixRequest;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.Misc.Misc.TimeUnit;
import com.necla.am.zwutils.Misc.Parsers;
import com.necla.am.zwutils.Tasks.ITask;
import com.necla.am.zwutils.Tasks.RunnableTask;
import com.necla.am.zwutils.Tasks.Wrappers.DaemonRunner;


/*
!! PATCH REQUIRED !!
// @formatter:off
- For Zabbix 2.2.2
Z222PATCH=LS0tIC91c3Ivc2hhcmUvemFiYml4L2FwaS9jbGFzc2VzL0NNZWRpYXR5cGUucGhwCTIwMTUtMDkt\
MTAgMTQ6NDc6MzUuMDk0OTQ2MDIyIC0wNDAwCisrKyAvdXNyL3NoYXJlL3phYmJpeC9hcGkvY2xh\
c3Nlcy9DTWVkaWF0eXBlLnBocC5uZXcJMjAxNS0wOS0wMiAxNzo0NzoxOC40OTA2NzE5NTMgLTA0\
MDAKQEAgLTg3LDkgKzg3LDExIEBACiAJCS8vIHBlcm1pc3Npb24gY2hlY2sKIAkJaWYgKFVTRVJf\
VFlQRV9TVVBFUl9BRE1JTiA9PSAkdXNlclR5cGUpIHsKIAkJfQotCQllbHNlaWYgKGlzX251bGwo\
JG9wdGlvbnNbJ2VkaXRhYmxlJ10pICYmIHNlbGY6OiR1c2VyRGF0YVsndHlwZSddID09IFVTRVJf\
VFlQRV9aQUJCSVhfQURNSU4pIHsKLQkJfQotCQllbHNlaWYgKCFpc19udWxsKCRvcHRpb25zWydl\
ZGl0YWJsZSddKSB8fCBzZWxmOjokdXNlckRhdGFbJ3R5cGUnXSAhPSBVU0VSX1RZUEVfU1VQRVJf\
QURNSU4pIHsKKwkJLy9lbHNlaWYgKGlzX251bGwoJG9wdGlvbnNbJ2VkaXRhYmxlJ10pICYmIHNl\
bGY6OiR1c2VyRGF0YVsndHlwZSddID09IFVTRVJfVFlQRV9aQUJCSVhfQURNSU4pIHsKKwkJLy99\
CisJCS8vZWxzZWlmICghaXNfbnVsbCgkb3B0aW9uc1snZWRpdGFibGUnXSkgfHwgc2VsZjo6JHVz\
ZXJEYXRhWyd0eXBlJ10gIT0gVVNFUl9UWVBFX1NVUEVSX0FETUlOKSB7CisJCS8vIFpoZW55dSBX\
dTogTm9uIHN1cGVyLWFkbWluIHNob3VsZCBiZSBhYmxlIHRvIGdldCBpbmZvcm1hdGlvbiBhcyBs\
b25nIGFzIHRoZXkgZG8gbm90IHJlcXVlc3QgZWRpdGluZyEKKwkJZWxzZWlmICghaXNfbnVsbCgk\
b3B0aW9uc1snZWRpdGFibGUnXSkgJiYgJG9wdGlvbnNbJ2VkaXRhYmxlJ10pIHsKIAkJCXJldHVy\
biBhcnJheSgpOwogCQl9CiAKLS0tIC91c3Ivc2hhcmUvemFiYml4L2FwaS9jbGFzc2VzL0NVc2Vy\
LnBocAkyMDE1LTA5LTEwIDE0OjQ4OjE5LjQyNjk0NDk5MiAtMDQwMAorKysgL3Vzci9zaGFyZS96\
YWJiaXgvYXBpL2NsYXNzZXMvQ1VzZXIucGhwLm5ldwkyMDE1LTA5LTAyIDE3OjQ2OjQ4Ljk3ODY3\
MjYzOSAtMDQwMApAQCAtNjY1LDkgKzY2NSwxMCBAQAogCSAqIEBwYXJhbSBzdHJpbmcgJGRhdGFb\
J21lZGlhcyddWydwZXJpb2QnXQogCSAqLwogCXByb3RlY3RlZCBmdW5jdGlvbiB2YWxpZGF0ZUFk\
ZE1lZGlhKGFycmF5ICRkYXRhKSB7Ci0JCWlmIChzZWxmOjokdXNlckRhdGFbJ3R5cGUnXSA8IFVT\
RVJfVFlQRV9aQUJCSVhfQURNSU4pIHsKLQkJCXNlbGY6OmV4Y2VwdGlvbihaQlhfQVBJX0VSUk9S\
X1BBUkFNRVRFUlMsIF8oJ09ubHkgWmFiYml4IEFkbWlucyBjYW4gYWRkIHVzZXIgbWVkaWEuJykp\
OwotCQl9CisJCS8vIFpoZW55dSBXdTogUmVsYXggdGhlIHNlY3VyaXR5IGEgbGl0dGxlIGJpdAor\
CQkvL2lmIChzZWxmOjokdXNlckRhdGFbJ3R5cGUnXSA8IFVTRVJfVFlQRV9aQUJCSVhfQURNSU4p\
IHsKKwkJLy8Jc2VsZjo6ZXhjZXB0aW9uKFpCWF9BUElfRVJST1JfUEFSQU1FVEVSUywgXygnT25s\
eSBaYWJiaXggQWRtaW5zIGNhbiBhZGQgdXNlciBtZWRpYS4nKSk7CisJCS8vfQogCiAJCWlmICgh\
aXNzZXQoJGRhdGFbJ3VzZXJzJ10pIHx8ICFpc3NldCgkZGF0YVsnbWVkaWFzJ10pKSB7CiAJCQlz\
ZWxmOjpleGNlcHRpb24oWkJYX0FQSV9FUlJPUl9QQVJBTUVURVJTLCBfKCdJbnZhbGlkIG1ldGhv\
ZCBwYXJhbWV0ZXJzLicpKTsK
( cd / && echo $Z222PATCH | base64 -d | patch -p0 --dry-run )
# Check for error and remove --dry-run when comfortable
// @formatter:on
*/

/**
 * Zabbix push logging JUL handler
 *
 * @author Zhenyu Wu
 * @see DebugLog
 * @version 0.1 - Sep. 2015: Initial implementation
 * @version 0.1 - Jan. 20 2016: Initial public release
 */
public class ZabbixHandler extends Handler implements AutoCloseable {
	
	public static final String LogGroup = "ZWUtils.Logging.Zabbix.Handler";
	protected static final IGroupLogger CLog = new GroupLogger(LogGroup);
	
	public static class ConfigData {
		
		protected static final String KEY_PREFIX = "ZabbixHandler.";
		protected static final String KEY_PREFIX_RP = "RP.";
		protected static final String KEY_PREFIX_TRG = "Trigger.";
		
		protected static final int DEFPORT_HTTP = 80;
		protected static final int DEFPORT_HTTPS = 443;
		protected static final int DEFPORT_REPORT = 10051;
		
		public static enum Severity {
			Default(0, "not classified"),
			Info(1, "information"),
			Warn(2, "warning"),
			Normal(3, "average"),
			High(4, "high"),
			Critical(5, "disaster");
			
			public final int Priority;
			public final String Description;
			
			Severity(int priority, String description) {
				Priority = priority;
				Description = description;
			}
			
			protected static Map<String, Severity> StrMap;
			protected static Map<Integer, Severity> PtyMap;
			
			static {
				
				Map<String, Severity> _StrMap = new HashMap<>();
				Map<Integer, Severity> _PtyMap = new HashMap<>();
				for (Severity Item : Severity.values()) {
					_StrMap.put(Item.name().toUpperCase(), Item);
					_PtyMap.put(Item.Priority, Item);
				}
				StrMap = _StrMap;
				PtyMap = _PtyMap;
				
			}
			
			public static Severity MapIdent(String Ident) {
				return StrMap.get(Ident.toUpperCase());
			}
			
			public static Severity MapPriority(int Priority) {
				return PtyMap.get(Priority);
			}
			
			public static class StringToSeverity extends Parsers.SimpleStringParse<Severity> {
				
				@Override
				public Severity parseOrFail(String From) {
					if (From == null) {
						Misc.FAIL(NullPointerException.class, Parsers.ERROR_NULL_POINTER);
					}
					
					int Priority = Parsers.StringToInteger.parseOrDefault(From, -1);
					Severity Ret = (Priority >= 0)? MapPriority(Priority) : MapIdent(From);
					if (Ret == null) Misc.FAIL(IllegalArgumentException.class,
							"Unrecognized severity descriptor '%s'", From);
					return Ret;
				}
				
			}
			
			public static class StringFromSeverity extends Parsers.SimpleParseString<Severity> {
				
				@Override
				public String parseOrFail(Severity From) {
					if (From == null) {
						Misc.FAIL(NullPointerException.class, Parsers.ERROR_NULL_POINTER);
					}
					
					return From.name();
				}
				
			}
			
			public static final StringToSeverity FromString = new StringToSeverity();
			public static final StringFromSeverity ToString = new StringFromSeverity();
			
		}
		
		public static class SeveritySet {
			
			protected EnumSet<Severity> EnumValues;
			protected BitSet BitValues;
			
			public SeveritySet(Severity... values) {
				EnumValues = EnumSet.noneOf(Severity.class);
				BitValues = new BitSet(32);
				
				for (Severity value : values) {
					EnumValues.add(value);
					BitValues.set(value.Priority);
				}
			}
			
			public SeveritySet(int values) {
				EnumValues = EnumSet.noneOf(Severity.class);
				BitValues = BitSet.valueOf(new long[] {
						values
				});
				
				for (Severity Item : Severity.values()) {
					if (BitValues.get(Item.Priority)) EnumValues.add(Item);
				}
			}
			
			public Set<Severity> Set() {
				return Collections.unmodifiableSet(EnumValues);
			}
			
			public int Value() {
				return (int) BitValues.toLongArray()[0];
			}
			
			public static class StringToSeveritySet extends Parsers.SimpleStringParse<SeveritySet> {
				
				protected static final String SVT_TOKDELIM = ",";
				
				@Override
				public SeveritySet parseOrFail(String From) {
					if (From == null) {
						Misc.FAIL(NullPointerException.class, Parsers.ERROR_NULL_POINTER);
					}
					
					List<Severity> SeverityList = new ArrayList<>();
					String[] StrSeverities = From.split(SVT_TOKDELIM);
					if (StrSeverities.length == 1) {
						int NumPriorities = Parsers.StringToInteger.parseOrDefault(From, -1);
						if (NumPriorities >= 0) return new SeveritySet(NumPriorities);
						if (From.equals("*")) return new SeveritySet(Severity.values());
					}
					
					for (String StrSeverity : StrSeverities)
						SeverityList.add(Severity.FromString.parseOrFail(StrSeverity));
					return new SeveritySet(SeverityList.toArray(new Severity[SeverityList.size()]));
				}
				
			}
			
			public static class StringFromSeveritySet extends Parsers.SimpleParseString<SeveritySet> {
				
				protected static final char SVT_TOKDELIM = ',';
				
				@Override
				public String parseOrFail(SeveritySet From) {
					if (From == null) {
						Misc.FAIL(NullPointerException.class, Parsers.ERROR_NULL_POINTER);
					}
					
					StringBuilder StrBuf = new StringBuilder();
					Set<Severity> Items = From.Set();
					for (Severity Item : Items)
						StrBuf.append(Item.name()).append(',');
					if (!Items.isEmpty()) StrBuf.deleteCharAt(StrBuf.length() - 1);
					
					return StrBuf.toString();
				}
				
			}
			
			public static final StringToSeveritySet FromString = new StringToSeveritySet();
			public static final StringFromSeveritySet ToString = new StringFromSeveritySet();
			
		}
		
		public static class ResponsiblePerson {
			public final SeveritySet Severities;
			public final String Email;
			
			public ResponsiblePerson(SeveritySet severities, String email) {
				Severities = severities;
				Email = email;
			}
			
			public static class StringToResponsiblePerson
					extends Parsers.SimpleStringParse<ResponsiblePerson> {
					
				protected static final String RP_TOKDELIM = ";";
				protected static final String RP_KVDELIM = "=";
				protected static final String RP_EMAIL_KEY = "EMAIL";
				
				@Override
				public ResponsiblePerson parseOrFail(String From) {
					if (From == null) {
						Misc.FAIL(NullPointerException.class, Parsers.ERROR_NULL_POINTER);
					}
					
					String[] Tokens = From.split(RP_TOKDELIM);
					SeveritySet Severities = SeveritySet.FromString.parseOrFail(Tokens[0]);
					String Email = null;
					for (int idx = 1; idx < Tokens.length; idx++) {
						String[] KV = Tokens[idx].trim().split(RP_KVDELIM, 2);
						switch (KV[0].toUpperCase()) {
							case RP_EMAIL_KEY:
								if (!KV[1].isEmpty()) Email = KV[1];
								break;
								
							default:
								Misc.FAIL(IllegalArgumentException.class,
										"Unrecognized responsible persion attribute '%s'", KV[0]);
						}
					}
					
					return new ResponsiblePerson(Severities, Email);
				}
			}
			
			public static class StringFromResponsiblePerson
					extends Parsers.SimpleParseString<ResponsiblePerson> {
					
				protected static final char RP_TOKDELIM = ';';
				protected static final char RP_KVDELIM = '=';
				protected static final String RP_EMAIL_KEY = "EMAIL";
				
				@Override
				public String parseOrFail(ResponsiblePerson From) {
					if (From == null) {
						Misc.FAIL(NullPointerException.class, Parsers.ERROR_NULL_POINTER);
					}
					
					StringBuilder StrBuf = new StringBuilder();
					StrBuf.append(SeveritySet.ToString.parseOrFail(From.Severities));
					if (From.Email != null)
						StrBuf.append(RP_TOKDELIM).append(RP_EMAIL_KEY).append(RP_KVDELIM).append(From.Email);
					return StrBuf.toString();
				}
				
			}
			
			public static final StringToResponsiblePerson FromString = new StringToResponsiblePerson();
			public static final StringFromResponsiblePerson ToString = new StringFromResponsiblePerson();
		}
		
		public static class AutoTriggerInfo {
			public final Pattern MetricPattern;
			public final Severity Severity;
			public final String Expression;
			public final String Comments;
			
			public AutoTriggerInfo(String patternstr, Severity severity, String expression,
					String comments) {
				MetricPattern = Pattern.compile(patternstr);
				Severity = severity;
				Expression = expression;
				Comments = comments;
			}
			
			public static class StringToAutoTriggerInfo
					extends Parsers.SimpleStringParse<AutoTriggerInfo> {
					
				protected static final String TRG_TOKDELIM = ";";
				
				@Override
				public AutoTriggerInfo parseOrFail(String From) {
					if (From == null) {
						Misc.FAIL(NullPointerException.class, Parsers.ERROR_NULL_POINTER);
					}
					
					String[] Tokens = From.split(TRG_TOKDELIM);
					if ((Tokens.length < 3) || (Tokens.length > 4)) Misc.FAIL(IllegalArgumentException.class,
							"Malformed auto-trigger info, found %d tokens", Tokens.length);
					String PatternStr = Tokens[0];
					Severity Severity = ConfigData.Severity.FromString.parseOrFail(Tokens[1]);
					String Expression = Tokens[2];
					String Comments = (Tokens.length > 3)? Tokens[3] : null;
					
					return new AutoTriggerInfo(PatternStr, Severity, Expression, Comments);
				}
			}
			
			public static class StringFromAutoTriggerInfo
					extends Parsers.SimpleParseString<AutoTriggerInfo> {
					
				protected static final char TRG_TOKDELIM = ';';
				
				@Override
				public String parseOrFail(AutoTriggerInfo From) {
					if (From == null) {
						Misc.FAIL(NullPointerException.class, Parsers.ERROR_NULL_POINTER);
					}
					
					StringBuilder StrBuf = new StringBuilder();
					StrBuf.append(From.MetricPattern.pattern());
					StrBuf.append(TRG_TOKDELIM).append(From.Severity);
					StrBuf.append(TRG_TOKDELIM).append(From.Expression);
					if (From.Comments != null) StrBuf.append(TRG_TOKDELIM).append(From.Comments);
					return StrBuf.toString();
				}
				
			}
			
			public static final StringToAutoTriggerInfo FromString = new StringToAutoTriggerInfo();
			public static final StringFromAutoTriggerInfo ToString = new StringFromAutoTriggerInfo();
			
		}
		
		public static class Mutable extends Data.Mutable {
			
			// Declare mutable configurable fields (public)
			public Map<String, ResponsiblePerson> RPs;
			public Map<String, AutoTriggerInfo> Triggers;
			public String ProblemSubject;
			public String ProblemBody;
			
			@Override
			public void loadDefaults() {
				RPs = new HashMap<>();
				Triggers = new HashMap<>();
				ProblemSubject = "{TRIGGER.STATUS}: {TRIGGER.NAME}";
				ProblemBody = "Trigger: {TRIGGER.NAME}\r\n"+ "Trigger status: {TRIGGER.STATUS}\r\n"
											+ "Trigger severity: {TRIGGER.SEVERITY}\r\n"
											+ "Trigger URL: {TRIGGER.URL}\r\n\r\n" + "Item values:\r\n\r\n"
											+ "1. {ITEM.NAME1} ({HOST.NAME1}:{ITEM.KEY1}): {ITEM.VALUE1}\r\n"
											+ "2. {ITEM.NAME2} ({HOST.NAME2}:{ITEM.KEY2}): {ITEM.VALUE2}\r\n"
											+ "3. {ITEM.NAME3} ({HOST.NAME3}:{ITEM.KEY3}): {ITEM.VALUE3}\r\n\r\n"
											+ "Original event ID: {EVENT.ID}";
			}
			
			@Override
			public void loadFields(DataMap confMap) {
				Map<String, ResponsiblePerson> xRPs = new HashMap<>();
				DataMap RPMap = new DataMap("RP", confMap, KEY_PREFIX_RP);
				for (String RPName : RPMap.getDataMap().keySet()) {
					ResponsiblePerson RP =
							RPMap.getObject(RPName, ResponsiblePerson.FromString, ResponsiblePerson.ToString);
					if (RP.Email == null) {
						ILog.Warn("Ignoring Resiponsible Persion '%s' without any means of contact", RPName);
						RP = null;
					}
					if (RP != null) xRPs.put(RPName, RP);
				}
				
				Map<String, AutoTriggerInfo> xTriggers = new HashMap<>();
				DataMap TRGMap = new DataMap("TRG", confMap, KEY_PREFIX_TRG);
				for (String TRGName : TRGMap.getDataMap().keySet()) {
					AutoTriggerInfo TRG =
							TRGMap.getObject(TRGName, AutoTriggerInfo.FromString, AutoTriggerInfo.ToString);
					if (TRG != null) xTriggers.put(TRGName, TRG);
				}
				
				RPs = xRPs;
				Triggers = xTriggers;
				
				ProblemSubject = confMap.getTextDef("ProblemSubject", ProblemSubject);
				ProblemBody = confMap.getTextDef("ProblemBody", ProblemBody);
			}
			
		}
		
		protected static class ReadOnly extends Data.ReadOnly {
			
			// Declare read-only configurable fields (public)
			public final Map<String, ResponsiblePerson> RPs;
			public final Map<String, AutoTriggerInfo> Triggers;
			public final String ProblemSubject;
			public final String ProblemBody;
			
			public ReadOnly(IGroupLogger Logger, Mutable Source) {
				super(Logger, Source);
				
				// Copy all fields from Source
				RPs = Collections.unmodifiableMap(Source.RPs);
				Triggers = Collections.unmodifiableMap(Source.Triggers);
				ProblemSubject = Source.ProblemSubject;
				ProblemBody = Source.ProblemBody;
			}
			
		}
		
		public static final File ConfigFile = DataFile.DeriveConfigFile("ZWUtils.");
		
		public static Container<Mutable, ReadOnly> Create(File xConfigFile) throws Throwable {
			return Container.Create(Mutable.class, ReadOnly.class, LogGroup + ".Config", xConfigFile,
					KEY_PREFIX);
		}
		
	}
	
	public final String Project;
	public final String Component;
	
	protected final String HostGroupID;
	protected final String HostID;
	
	protected final ZabbixAPI ZAPI;
	
	private Daemon DaemonTask = null;
	private ITask.Run ReportDaemon = null;
	
	ConfigData.ReadOnly Config;
	
	public ZabbixHandler(String Project, String Component) {
		this(Project, Component, null, null);
	}
	
	protected final String StripPfxProject;
	protected final String StripPfxComponent;
	
	public ZabbixHandler(String Project, String Component, ZabbixAPI ZAPI, File ConfigFile) {
		this.Project = Project;
		this.Component = Component;
		
		if (ZAPI == null) {
			try {
				ZAPI = new ZabbixAPI.Impl();
			} catch (Throwable e) {
				CLog.Warn("Unable to initialize Zabbix API - %s", e);
			}
		}
		
		StripPfxProject = Project + '.';
		StripPfxComponent = (Component.startsWith(StripPfxProject)? Component
				.substring(StripPfxProject.length()) : Component) + '.';
				
		String HostGroupID = null;
		String HostID = null;
		
		try {
			if (ZAPI != null) {
				ZabbixRequest HGQuery = ZabbixRequest.Factory.HostGroupInfo(Project);
				HGQuery.putParam("output", Misc.wrap("groupid"));
				JsonArray HostGroups = ZAPI.call(HGQuery).get("result").getAsJsonArray();
				if (HostGroups.size() != 1) {
					if (HostGroups.size() > 1)
						Misc.FAIL("Expect return of 1 entry, received %d", HostGroups.size());
						
					// Cannot create host group by ourselves
					// (unless we are super-admin, not likely, and not safe!)
					
					CLog.Error("Please contact Zabbix administrator to create host group '%s'", Project);
					CLog.Error("And / or please give user '%s' read/write access right to this host group",
							ZAPI.user());
					Misc.FAIL("Missing project (host group) '%s'", Project);
				} else {
					HostGroupID = HostGroups.get(0).getAsJsonObject().get("groupid").getAsString();
					CLog.Config("Found project (host group) '%s' with ID #%s", Project, HostGroupID);
				}
				
				ZabbixRequest HostQuery = ZabbixRequest.Factory.HostInfo(Component);
				HostQuery.putParam("output", Misc.wrap("hostid"));
				HostQuery.putParam("selectGroups", Misc.wrap("groupid"));
				JsonArray Hosts = ZAPI.call(HostQuery).get("result").getAsJsonArray();
				if (Hosts.size() != 1) {
					if (Hosts.size() > 1) Misc.FAIL("Expect return of 1 entry, received %d", Hosts.size());
					// Try to create the host on-the-fly
					ZabbixRequest HCQuery = ZabbixRequest.Factory.HostCreate(Component, HostGroupID);
					HCQuery.putParam("interfaces",
							Misc.StringMap(Misc.wrap("type", "main", "useip", "ip", "dns", "port"),
									(Object[]) Misc.wrap(1L, 1L, 1L, "0.0.0.0", "", "0")));
					JsonObject Result = ZAPI.call(HCQuery);
					if (Result.has("error")) Misc.FAIL("Failed to create project (host group) '%s': %s",
							Project, Result.get("error"));
					JsonObject HCreate = Result.get("result").getAsJsonObject();
					HostID = HCreate.get("hostids").getAsJsonArray().get(0).getAsString();
					CLog.Info("Created component (host) '%s' with ID #%s", Component, HostID);
				} else {
					JsonObject Host = Hosts.get(0).getAsJsonObject();
					// Host exists, check if it is in the right group
					JsonArray RHostGroups = Host.get("groups").getAsJsonArray();
					if (RHostGroups.size() != 1)
						Misc.FAIL("Expect return of 1 entry, received %d", RHostGroups.size());
					String RHGID = RHostGroups.get(0).getAsJsonObject().get("groupid").getAsString();
					if (!RHGID.equals(HostGroupID)) Misc.FAIL("Component already assigned to project '%s'",
							RHostGroups.get(0).getAsJsonObject().get("name").getAsString());
					// Everything check up, we are good to go!
					HostID = Host.get("hostid").getAsString();
					CLog.Config("Found component (host) '%s' with ID #%s", Component, HostID);
				}
			}
		} catch (Throwable e) {
			CLog.Warn("Unable to initialize Zabbix logging handler - %s", e);
			ZAPI = null;
		}
		
		this.ZAPI = ZAPI;
		this.HostGroupID = HostGroupID;
		this.HostID = HostID;
		
		CLog.Fine("Starting Zabbix report daemon thread...");
		DaemonTask = new Daemon(LogGroup + '.' + Daemon.class.getSimpleName());
		ReportDaemon = DaemonRunner.LowPriorityTaskDaemon(DaemonTask);
		try {
			ReportDaemon.Start(-1);
		} catch (InterruptedException e) {
			Misc.CascadeThrow(e);
		}
		
		try {
			Config = ConfigData.Create(ConfigFile != null? ConfigFile : ConfigData.ConfigFile).reflect();
		} catch (Throwable e) {
			Misc.CascadeThrow(e);
		}
		
		if (ZAPI != null) {
			// Process RP and Trigger configurations
			if (!Config.RPs.isEmpty()) {
				try {
					String MediaTypeID = null;
					
					// Check the medias (require email, others optional -- and not implemented)
					String MediaTypeName = Project + "-Email";
					{
						ZabbixRequest MTQuery = ZabbixRequest.Factory.MediaTypeInfo(MediaTypeName, null);
						MTQuery.putParam("output", "mediatypeid");
						JsonArray MediaTypes = ZAPI.call(MTQuery).get("result").getAsJsonArray();
						if (MediaTypes.size() != 1) {
							if (MediaTypes.size() > 1)
								Misc.FAIL("Expect return of 1 entry, received %d", MediaTypes.size());
								
							// Cannot create media type by ourselves
							// (unless we are super-admin, not likely, and not safe!)
							
							CLog.Error(
									"Please contact Zabbix administrator to create and configure media type '%s'",
									MediaTypeName);
							Misc.FAIL("Missing email media type '%s'", MediaTypeName);
						} else {
							MediaTypeID = MediaTypes.get(0).getAsJsonObject().get("mediatypeid").getAsString();
							CLog.Config("Found email media type '%s' with ID #%s", MediaTypeName, MediaTypeID);
						}
					}
					
					// Check if all RPs are recorded for current user, and update if necessary
					String UserID = null;
					{
						ZabbixRequest UserQuery = ZabbixRequest.Factory.UserInfo(ZAPI.user());
						UserQuery.putParam("output", Misc.wrap("userid"));
						UserQuery.putParam("selectMedias",
								Misc.wrap("mediaid", "mediatypeid", "active", "sendto", "severity"));
						JsonArray Users = ZAPI.call(UserQuery).get("result").getAsJsonArray();
						if (Users.size() != 1) Misc.FAIL("Expect return of 1 entry, received %d", Users.size());
						JsonObject UserData = Users.get(0).getAsJsonObject();
						UserID = UserData.get("userid").getAsString();
						JsonArray UserMedias = UserData.get("medias").getAsJsonArray();
						
						Map<String, ConfigData.ResponsiblePerson> xRPs = new HashMap<>(Config.RPs);
						for (JsonElement UserMedia : UserMedias) {
							JsonObject UMInfo = UserMedia.getAsJsonObject();
							String UMType = UMInfo.get("mediatypeid").getAsString();
							if (UMType.equals(MediaTypeID)) {
								String UMEmail = UMInfo.get("sendto").getAsString();
								int UMSeverity = UMInfo.get("severity").getAsInt();
								String UMID = UMInfo.get("mediaid").getAsString();
								String UMState = (UMInfo.get("active").getAsInt() == 0)? "Enabled" : "Disabled";
								for (Map.Entry<String, ConfigData.ResponsiblePerson> xRP : xRPs.entrySet()) {
									ConfigData.ResponsiblePerson RP = xRP.getValue();
									if (RP.Email.equalsIgnoreCase(UMEmail)) {
										CLog.Config("Found responsible person (user media) '%s' "
																+ "with Severity %d [%s], ID #%s, %s", //
												UMEmail, UMSeverity, ConfigData.SeveritySet.ToString
														.parseOrFail(new ConfigData.SeveritySet(UMSeverity)),
												UMID, UMState);
										xRPs.remove(xRP.getKey());
										break;
									}
								}
							}
						}
						if (!xRPs.isEmpty()) {
							JsonArray NewUserMedias = new JsonArray();
							for (Map.Entry<String, ConfigData.ResponsiblePerson> xRP : xRPs.entrySet()) {
								ConfigData.ResponsiblePerson RP = xRP.getValue();
								CLog.Info("Creating responsible person (user media) '%s' with Severity %d [%s]",
										RP.Email, RP.Severities.Value(),
										ConfigData.SeveritySet.ToString.parseOrFail(RP.Severities));
								JsonObject NewUserMedia = new JsonObject();
								NewUserMedia.addProperty("active", 0);
								NewUserMedia.addProperty("mediatypeid", MediaTypeID);
								NewUserMedia.addProperty("period", "1-7,00:00-24:00");
								NewUserMedia.addProperty("sendto", RP.Email);
								NewUserMedia.addProperty("severity", RP.Severities.Value());
								NewUserMedias.add(NewUserMedia);
							}
							ZabbixRequest UMAddQuery = ZabbixRequest.Factory.UserAddMediaTemplate(UserID);
							UMAddQuery.putParam("medias", NewUserMedias);
							JsonObject UMAddRet = ZAPI.call(UMAddQuery);
							if (!UMAddRet.has("result"))
								Misc.FAIL("Unable to create responsible persons (user medias) - %s", UMAddRet);
							CLog.Info("Created responsible persons (user medias) with IDs %s",
									UMAddRet.get("result").getAsJsonObject().get("mediaids"));
						}
					}
					
					// Check if notification action is configured, add if necessary
					String ActionID = null;
					String ActionName = Project + "-Notification";
					{
						ZabbixRequest ActionQuery = ZabbixRequest.Factory.ActionInfo(ActionName);
						ActionQuery.putParam("selectConditions", "extend");
						ActionQuery.putParam("selectOperations", "extend");
						// ActionQuery.putParam("output", Misc.wrap("actionid", "status"));
						JsonArray Actions = ZAPI.call(ActionQuery).get("result").getAsJsonArray();
						if (Actions.size() != 1) {
							if (Actions.size() > 1)
								Misc.FAIL("Expect return of 1 entry, received %d", Actions.size());
								
							// Create default action
							ZabbixRequest ActionCreateQuery = ZabbixRequest.Factory.ActionCreateTemplate(
									ActionName, 0, 600, Config.ProblemSubject, Config.ProblemBody, null, null);
							// Conditions
							ActionCreateQuery.putParam("evaltype", 1); // AND
							Gson gson = new Gson();
							{
								JsonArray ActionConds = new JsonArray();
								ActionCreateQuery.putParam("conditions", ActionConds);
								ActionConds.add(gson.toJsonTree(Misc.StringMap( // Host = <HostID>
										Misc.wrap("conditiontype", "value"), 1, HostID)));
								ActionConds.add(gson.toJsonTree(Misc.StringMap( // Trigger Value = Problem
										Misc.wrap("conditiontype", "value"), 5, 1)));
								ActionConds.add(gson.toJsonTree(Misc.StringMap( // Not in maintenance mode
										Misc.wrap("conditiontype", "operator"), 16, 7)));
							}
							JsonArray ActionOps = new JsonArray();
							ActionCreateQuery.putParam("operations", ActionOps);
							{
								JsonObject ActionOp1 = new JsonObject();
								ActionOps.add(ActionOp1);
								// Send message
								ActionOp1.addProperty("operationtype", 0);
								JsonArray OpUsers = new JsonArray();
								// To responsible persons of this application
								ActionOp1.add("opmessage_usr", OpUsers);
								OpUsers.add(new Gson().toJsonTree(Misc.StringMap(Misc.wrap("userid"), UserID)));
								// Default message content, sent with any media type
								ActionOp1.add("opmessage",
										new Gson().toJsonTree(Misc.StringMap(Misc.wrap("mediatypeid"), "0")));
								// Conditions
								JsonArray OpConds = new JsonArray();
								ActionOp1.add("opconditions", OpConds);
								// Event acknowledged = not acknowledged
								OpConds.add(new Gson()
										.toJsonTree(Misc.StringMap(Misc.wrap("conditiontype", "value"), 14, 0)));
							}
							JsonObject ActionCreateRet = ZAPI.call(ActionCreateQuery);
							if (!ActionCreateRet.has("result"))
								Misc.FAIL("Unable to create notification action - %s", ActionCreateRet);
							JsonObject ActionInfo = ActionCreateRet.get("result").getAsJsonObject();
							ActionID = ActionInfo.get("actionids").getAsJsonArray().get(0).getAsString();
							CLog.Info("Created notification action with IDs %s", ActionID);
						} else {
							JsonObject ActionInfo = Actions.get(0).getAsJsonObject();
							ActionID = ActionInfo.get("actionid").getAsString();
							String ActionState =
									(ActionInfo.get("status").getAsInt() == 0)? "Enabled" : "Disabled";
							CLog.Config("Found notification action '%s' with ID #%s, %s", ActionName, ActionID,
									ActionState);
						}
					}
				} catch (Throwable e) {
					CLog.Warn("Notification configuration error detected - %s", e);
					CLog.Warn("You may not receive notification for your triggers");
				}
			} else if (!Config.Triggers.isEmpty()) {
				CLog.Warn("No responsible personnel defined");
				CLog.Warn("You may not receive notification for your triggers");
			}
		}
	}
	
	public static final String LOG_TRIGGER = "$ZBX$";
	
	@Override
	public void publish(LogRecord record) {
		String LogMessage = record.getMessage();
		if ((LogMessage != null) && LogMessage.equals(LOG_TRIGGER))
			DoLog(record.getLoggerName(), record.getParameters());
	}
	
	protected Map<String, String> AppMap = new HashMap<>();
	
	protected String EnsureApplication(String Application) {
		String Ret = AppMap.get(Application);
		if (Ret == null) {
			ZabbixRequest AppQuery = ZabbixRequest.Factory.ApplicationInfo(Application, HostID);
			AppQuery.putParam("output", Misc.wrap("applicationid"));
			JsonArray Apps = ZAPI.call(AppQuery).get("result").getAsJsonArray();
			if (Apps.size() != 1) {
				if (Apps.size() > 1) Misc.FAIL("Expect return of 1 entry, received %d", Apps.size());
				// Try to create the application on-the-fly
				ZabbixRequest ACQuery = ZabbixRequest.Factory.ApplicationCreate(Application, HostID);
				JsonObject Result = ZAPI.call(ACQuery);
				if (Result.has("error")) Misc.FAIL("Failed to create log group (application) '%s': %s",
						Application, Result.get("error"));
				JsonObject ACreate = Result.get("result").getAsJsonObject();
				Ret = ACreate.get("applicationids").getAsJsonArray().get(0).getAsString();
				CLog.Info("Created log group (application) '%s' with ID #%s", Application, Ret);
			} else {
				Ret = Apps.get(0).getAsJsonObject().get("applicationid").getAsString();
				CLog.Config("Found log group (application) '%s' with ID #%s", Application, Ret);
			}
			AppMap.put(Application, Ret);
		}
		return Ret;
	}
	
	static class TItemInfo {
		public final String ID;
		public final Class<?> OType;
		public final int VType;
		
		public TItemInfo(String id, Class<?> otype, int vtype) {
			super();
			ID = id;
			OType = otype;
			VType = vtype;
		}
		
		public static TItemInfo Create(String ID, Class<?> OType, int VType) {
			return new TItemInfo(ID, OType, VType);
		}
		
	}
	
	protected Map<String, TItemInfo> ItemMap = new HashMap<>();
	
	public static final int ZABBIX_TYPE_TRAPPER = 2;
	
	public static final int ZABBIX_VTYPE_FLOAT_NUMERIC = 0;
	public static final int ZABBIX_VTYPE_CHARACTER = 1;
	public static final int ZABBIX_VTYPE_LOG = 2;
	public static final int ZABBIX_VTYPE_UNSIGNED_NUMERIC = 3;
	public static final int ZABBIX_VTYPE_TEXT = 4;
	
	protected static String StrVType(int VType) {
		switch (VType) {
			case ZABBIX_VTYPE_FLOAT_NUMERIC:
				return "numeric unsigned";
			case ZABBIX_VTYPE_CHARACTER:
				return "character";
			case ZABBIX_VTYPE_LOG:
				return "log";
			case ZABBIX_VTYPE_UNSIGNED_NUMERIC:
				return "numeric unsigned";
			case ZABBIX_VTYPE_TEXT:
				return "text";
			default:
				return String.format("(unknown type %d)", VType);
		}
	}
	
	protected static int VTypeMap(Class<?> Type) {
		switch (Type.getName()) {
			case "char":
			case "java.lang.Character":
				// Character is stored as unsigned ordinal
				return ZABBIX_VTYPE_UNSIGNED_NUMERIC;
				
			case "int":
			case "java.lang.Integer":
			case "short":
			case "java.lang.Short":
			case "long":
			case "java.lang.Long":
				// Because of the lack of negative numeric support in Zabbix,
				// we store all integer values as floating point
			case "float":
			case "java.lang.Float":
			case "double":
			case "java.lang.Double":
				return ZABBIX_VTYPE_FLOAT_NUMERIC;
				
			case "java.lang.Enum":
				// Enum is stored using their text name
				return ZABBIX_VTYPE_CHARACTER;
				
			case "java.lang.String":
				// String as stored as variable length text
			default:
				// Everything else could be stored as "stringified" text
				CheckStringifySupport(Type);
				return ZABBIX_VTYPE_TEXT;
		}
	}
	
	protected static Map<Class<?>, Class<?>> StringifySupport = new ConcurrentHashMap<>();
	
	protected static void CheckStringifySupport(Class<?> Type) {
		if (StringifySupport.get(Type) == null) {
			if (!StringifySupport.containsKey(Type)) {
				try {
					Class<?> ImplClass = Type.getMethod("toString").getDeclaringClass();
					if (!ImplClass.equals(Object.class)) {
						StringifySupport.put(Type, ImplClass);
						return;
					}
					StringifySupport.put(Type, null);
				} catch (Throwable e) {
					CLog.Warn("Unable to locate stringification implementation for class '%s'", Type);
					StringifySupport.put(Type, null);
				}
			}
			CLog.Warn("No custom stringification implementation for class '%s'", Type);
		}
	}
	
	// We only accept the unquoted string form
	protected String[] ParseItemKey(String ItemKey) {
		try {
			String[] Ret = ItemKey.split(",");
			// Sanity check
			for (String Entry : Ret) {
				if (Entry.isEmpty()) Misc.FAIL("Empty parameter is not allowed");
				if (Entry.startsWith("\"") || Entry.endsWith("\""))
					Misc.FAIL("Quoted parameter not supported");
				if (Entry.indexOf(']') >= 0) Misc.FAIL("Illegal character '%s'", "]");
			}
			return Ret;
		} catch (Throwable e) {
			Misc.CascadeThrow(e, "Failed to parse metric '%s'", ItemKey);
			return null;
		}
	}
	
	protected static final String ItemDispNameBase = "Metric '$1'";
	
	protected String EnsureItem(String AppID, String AppKey, Class<?> Type) {
		TItemInfo iRet = ItemMap.get(AppKey);
		if (iRet == null) {
			int VType = VTypeMap(Type);
			
			ZabbixRequest ItemQuery = ZabbixRequest.Factory.ItemInfo(AppKey, HostID);
			ItemQuery.putParam("output", Misc.wrap("name", "type", "value_type", "itemid"));
			JsonArray Items = ZAPI.call(ItemQuery).get("result").getAsJsonArray();
			if (Items.size() != 1) {
				if (Items.size() > 1) Misc.FAIL("Expect return of 1 entry, received %d", Items.size());
				// Try to create the application on-the-fly
				String ParamStr = AppKey.split("\\[")[1];
				ParamStr = ParamStr.substring(0, ParamStr.length() - 1);
				String[] ItemParams = ParseItemKey(ParamStr);
				String DispName;
				if (ItemParams.length > 1) {
					StringBuilder StrBuf = new StringBuilder().append(ItemDispNameBase).append(" (");
					for (int idx = 2; idx <= ItemParams.length; idx++)
						StrBuf.append('$').append(idx).append(',');
					StrBuf.setCharAt(StrBuf.length() - 1, ')');
					DispName = StrBuf.toString();
				} else
					DispName = ItemDispNameBase;
					
				ZabbixRequest ICQuery = ZabbixRequest.Factory.ItemCreateTemplate(AppKey, DispName, HostID);
				ICQuery.putParam("type", ZABBIX_TYPE_TRAPPER);
				ICQuery.putParam("value_type", VType);
				ICQuery.putParam("applications", Misc.wrap(AppID));
				JsonObject Result = ZAPI.call(ICQuery);
				if (Result.has("error"))
					Misc.FAIL("Failed to create metric (item) '%s': %s", AppKey, Result.get("error"));
				JsonObject ICreate = Result.get("result").getAsJsonObject();
				
				String ItemID = ICreate.get("itemids").getAsJsonArray().get(0).getAsString();
				iRet = TItemInfo.Create(ItemID, Type, VType);
				CLog.Info("Created metric (item) '%s' with ID #%s", AppKey, iRet.ID);
			} else {
				// Item exists, validate type, value_type
				JsonObject ItemInfo = Items.get(0).getAsJsonObject();
				int RType = ItemInfo.get("type").getAsInt();
				if (RType != ZABBIX_TYPE_TRAPPER)
					Misc.ERROR("Configured item '%s' has incompatble type %d, expect %d (Trapper)", AppKey,
							RType, ZABBIX_TYPE_TRAPPER);
				int RVType = ItemInfo.get("value_type").getAsInt();
				if (RVType != VType)
					Misc.ERROR("Configured metric (item) '%s' has incompatble value_type %d, expect %d (%s)",
							AppKey, RVType, VType, StrVType(VType));
				// Everything check up, we are good to go!
				iRet = TItemInfo.Create(ItemInfo.get("itemid").getAsString(), Type, VType);
				String ItemName = ItemInfo.get("name").getAsString();
				CLog.Config("Found metric (item) '%s'[%s] with ID #%s", AppKey, ItemName, iRet.ID);
			}
			ItemMap.put(AppKey, iRet);
		} else {
			if (!iRet.OType.equals(Type)) {
				int VType = VTypeMap(Type);
				if (VType != iRet.VType)
					Misc.ERROR("Log value for metric '%s' has incompatble type '%s', expecting %s types",
							AppKey, Type.getName(), StrVType(iRet.VType));
			}
		}
		return iRet.ID;
	}
	
	public void DoLog(String LoggerName, Object[] LogRecords) {
		if (ZAPI == null) {
			CLog.Warn("Zabbix API initialization failed, data not logged!");
			return;
		}
		
		ZabbixReport Report = null;
		try {
			String Application = LoggerName;
			if (Application.startsWith(StripPfxProject))
				Application = Application.substring(StripPfxProject.length());
			if (Application.startsWith(StripPfxComponent))
				Application = Application.substring(StripPfxComponent.length());
			if (Application.isEmpty()) Misc.ERROR("Empty application name not allowed");
			
			String AppID = EnsureApplication(Application);
			
			if ((LogRecords.length & 1) != 0) Misc.ERROR("Unbalanced log records");
			
			Report = new ZabbixReport();
			for (int Idx = 0; Idx < LogRecords.length; Idx += 2) {
				String ItemKey = (String) LogRecords[Idx];
				if (ItemKey.isEmpty()) Misc.ERROR("Empty metric name not allowed");
				String AppKey = Application + '[' + ItemKey + ']';
				Object ItemVal = LogRecords[Idx + 1];
				Class<?> IType = ItemVal.getClass();
				try {
					EnsureItem(AppID, AppKey, IType);
					Report.Add(Component, AppKey, ItemVal);
				} catch (Throwable e) {
					Misc.CascadeThrow(e, "Metric [%s] Value '%s'", ItemKey, IType.getName(),
							String.valueOf(ItemVal));
				}
			}
		} catch (Throwable e) {
			CLog.Warn("Report preparation failed - %s", e.getMessage());
			if (e.getCause() != null) CLog.logExcept(e.getCause());
			return;
		}
		
		DaemonTask.Queue.publish(Report);
	}
	
	@Override
	public void flush() {
		DaemonTask.Queue.flush();
	}
	
	@Override
	public void close() {
		CLog.Fine("Flusing Zabbix report daemon queue...");
		DaemonTask.Queue.flush();
		DaemonTask.Queue.close();
		
		try {
			ReportDaemon.Join(-1);
		} catch (Throwable e) {
			CLog.logExcept(e, "Zabbix report daemon termination join failed");
		}
	}
	
	/**
	 * Zabbix log worker
	 * <p>
	 * Handles zabbix logging in the background
	 *
	 * @author Zhenyu Wu
	 * @see DebugLog
	 * @version 0.1 - Initial implementation
	 */
	class Daemon extends RunnableTask {
		
		private Thread ReportThread = null;
		private volatile boolean Waiting = false;
		
		class ReportHandler implements AutoCloseable {
			
			private volatile boolean Closed = false;
			private Queue<ZabbixReport> Container = new ConcurrentLinkedQueue<>();
			
			@Override
			synchronized public void close() {
				if (isClosed()) Misc.FAIL("Handler already closed");
				
				if (!Closed) {
					Closed = true;
					_flush();
				}
			}
			
			public boolean isClosed() {
				return Closed;
			}
			
			synchronized public void flush() {
				if (isClosed()) Misc.FAIL("Handler already closed");
				
				_flush();
			}
			
			protected void _flush() {
				LockSupport.unpark(ReportThread);
				try {
					wait();
				} catch (InterruptedException e) {
					Misc.CascadeThrow(e);
				}
			}
			
			public void publish(ZabbixReport report) {
				if (isClosed()) Misc.FAIL("Handler already closed");
				
				Container.add(report);
				if (Waiting) {
					Waiting = false;
					LockSupport.unpark(ReportThread);
				}
			}
			
			protected ZabbixReport handle() {
				return Container.poll();
			}
			
		}
		
		public final ReportHandler Queue = new ReportHandler();
		
		public Daemon(String Name) {
			super(Name);
		}
		
		@Override
		protected void preTask() {
			super.preTask();
			
			ReportThread = Thread.currentThread();
		}
		
		@Override
		protected void doTask() {
			CLog.Fine("Zabbix report daemon thread started");
			while (!tellState().isTerminating() || !Queue.Container.isEmpty()) {
				ZabbixReport report = Queue.handle();
				if (report == null) {
					synchronized (Queue) {
						// Check and notify flush waiters
						Queue.notifyAll();
						// Check if queue has been closed
						if (Queue.isClosed() && tellState().isRunning()) EnterState(State.TERMINATING);
					}
					if (tellState().isRunning()) {
						Waiting = true;
						// Wait for new message with a timeout
						LockSupport.parkNanos(this, TimeUnit.MSEC.Convert(100, TimeUnit.NSEC));
						Waiting = false;
					}
				} else
					doReport(report);
			}
			CLog.Fine("Zabbix report daemon thread terminated");
		}
		
		protected void doReport(ZabbixReport report) {
			try {
				JsonObject Reply = ZAPI.send(report);
				if (Reply.get("response").getAsString().equals("success"))
					CLog.Fine("Successful report: %s", Reply.get("info").getAsString());
				else
					CLog.Warn("Unsuccessful report: %s", Reply);
			} catch (Throwable e) {
				CLog.Warn("Failed report - %s", e);
			}
		}
		
	}
	
}
