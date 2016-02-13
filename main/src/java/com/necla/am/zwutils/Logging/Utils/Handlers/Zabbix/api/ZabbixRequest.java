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

// Adapted from https://github.com/hengyunabc/zabbix-api

package com.necla.am.zwutils.Logging.Utils.Handlers.Zabbix.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.Gson;
import com.necla.am.zwutils.Misc.Misc;


/**
 * Zabbix Web API command wrapper
 *
 * @author Zhenyu Wu
 * @version 0.1 - Sep. 2015: Initial implementation based on
 *          https://github.com/hengyunabc/zabbix-api
 * @version 0.1 - Jan. 20 2016: Initial public release
 * @version 0.15 - Feb. 03 2016: Code cleanup and simplification
 */
public class ZabbixRequest {
	
	public final String jsonrpc = "2.0";
	
	public String auth;
	
	public final Integer id;
	
	public final String method;
	
	Map<String, Object> params = new HashMap<>();
	
	public ZabbixRequest(Integer id, String method) {
		super();
		this.id = id;
		this.method = method;
	}
	
	public Map<String, Object> getParams() {
		return params;
	}
	
	public void setParams(Map<String, Object> params) {
		this.params = params;
	}
	
	public void putParam(String key, Object value) {
		params.put(key, value);
	}
	
	public Object removeParam(String key) {
		return params.remove(key);
	}
	
	@Override
	public String toString() {
		return new Gson().toJson(this);
	}
	
	protected static class Builder {
		
		private static final AtomicInteger _ID = new AtomicInteger(1);
		
		final ZabbixRequest request;
		
		protected Builder(String method) {
			request = new ZabbixRequest(_ID.getAndIncrement(), method);
		}
		
		public static Builder create(String method) {
			return new Builder(method);
		}
		
		public Builder paramEntry(String key, Object value) {
			request.putParam(key, value);
			return this;
		}
		
		public ZabbixRequest done() {
			return request;
		}
		
	}
	
	public static class Factory {
		
		public static ZabbixRequest Logon(String name, String password) {
			return ZabbixRequest.Builder.create("user.login").paramEntry("user", name)
					.paramEntry("password", password).done();
		}
		
		public static ZabbixRequest UserInfo(String name) {
			Builder RetBuild = ZabbixRequest.Builder.create("user.get").paramEntry("output", "extend");
			if (name != null) RetBuild.paramEntry("filter", Misc.StringMap(Misc.wrap("alias"), name));
			return RetBuild.done();
		}
		
		public static ZabbixRequest APIVersion() {
			return ZabbixRequest.Builder.create("apiinfo.version").done();
		}
		
		public static ZabbixRequest HostGroupInfo(String name) {
			Builder RetBuild =
					ZabbixRequest.Builder.create("hostgroup.get").paramEntry("output", "extend");
			if (name != null) RetBuild.paramEntry("filter", Misc.StringMap(Misc.wrap("name"), name));
			return RetBuild.done();
		}
		
		public static ZabbixRequest HostGroupCreate(String name) {
			return ZabbixRequest.Builder.create("hostgroup.create").paramEntry("name", name).done();
		}
		
		public static ZabbixRequest HostInfo(String name) {
			Builder RetBuild = ZabbixRequest.Builder.create("host.get").paramEntry("output", "extend");
			if (name != null) RetBuild.paramEntry("filter", Misc.StringMap(Misc.wrap("host"), name));
			return RetBuild.done();
		}
		
		public static ZabbixRequest HostCreate(String name, String... groupids) {
			Builder RetBuild = ZabbixRequest.Builder.create("host.create").paramEntry("host", name);
			List<Object> HostGroups = new ArrayList<>();
			for (String groupid : groupids)
				HostGroups.add(Misc.StringMap(Misc.wrap("groupid"), groupid));
			RetBuild.paramEntry("groups", HostGroups);
			return RetBuild.done();
		}
		
		public static ZabbixRequest ApplicationInfo(String name, String HostID) {
			Builder RetBuild =
					ZabbixRequest.Builder.create("application.get").paramEntry("output", "extend");
			if (name != null) RetBuild.paramEntry("filter", Misc.StringMap(Misc.wrap("name"), name));
			if (HostID != null) RetBuild.paramEntry("hostids", HostID);
			return RetBuild.done();
		}
		
		public static ZabbixRequest ApplicationCreate(String name, String HostID) {
			return ZabbixRequest.Builder.create("application.create").paramEntry("name", name)
					.paramEntry("hostid", HostID).done();
		}
		
		public static ZabbixRequest ItemInfo(String Key, String HostID) {
			Builder RetBuild = ZabbixRequest.Builder.create("item.get").paramEntry("output", "extend");
			if (Key != null) RetBuild.paramEntry("filter", Misc.StringMap(Misc.wrap("key_"), Key));
			if (HostID != null) RetBuild.paramEntry("hostids", HostID);
			return RetBuild.done();
		}
		
		public static ZabbixRequest ItemCreateTemplate(String Key, String Name, String HostID) {
			return ZabbixRequest.Builder.create("item.create").paramEntry("key_", Key)
					.paramEntry("name", Name).paramEntry("hostid", HostID).done();
		}
		
		public static ZabbixRequest MediaTypeInfo(String Desc, Integer Type) {
			Builder RetBuild =
					ZabbixRequest.Builder.create("mediatype.get").paramEntry("output", "extend");
			if ((Desc != null) || (Type != null)) {
				Map<String, Object> FilterMap = null;
				if (Desc != null) FilterMap = Misc.StringMap(Misc.wrap("description"), Desc);
				if (Type != null) if (FilterMap != null)
					FilterMap.put("type", Type);
				else
					FilterMap = Misc.StringMap(Misc.wrap("type"), Type);
				RetBuild.paramEntry("filter", FilterMap);
			}
			return RetBuild.done();
		}
		
		public static ZabbixRequest UserUpdateTemplate(String UserID) {
			return ZabbixRequest.Builder.create("user.update").paramEntry("userid", UserID).done();
		}
		
		public static ZabbixRequest UserAddMediaTemplate(String UserID) {
			return ZabbixRequest.Builder.create("user.addmedia")
					.paramEntry("users", Misc.StringMap(Misc.wrap("userid"), UserID)).done();
		}
		
		public static ZabbixRequest ActionInfo(String Name) {
			Builder RetBuild = ZabbixRequest.Builder.create("action.get").paramEntry("output", "extend");
			if (Name != null) RetBuild.paramEntry("filter", Misc.StringMap(Misc.wrap("name"), Name));
			return RetBuild.done();
		}
		
		public static ZabbixRequest ActionCreateTemplate(String Name, int Source, int EscPeriod,
				String ProbSubj, String ProbBody, String RecSubj, String RecBody) {
			Builder RetBuild = ZabbixRequest.Builder.create("action.create").paramEntry("name", Name)
					.paramEntry("esc_period", Math.max(EscPeriod, 60)).paramEntry("eventsource", Source);
					
			if (ProbSubj != null) RetBuild.paramEntry("def_shortdata", ProbSubj);
			if (ProbBody != null) RetBuild.paramEntry("def_longdata", ProbBody);
			if ((RecSubj != null) || (RecBody != null)) {
				if (RecSubj != null) RetBuild.paramEntry("r_shortdata", RecSubj);
				if (RecBody != null) RetBuild.paramEntry("r_longdata", RecBody);
				RetBuild.paramEntry("recovery_msg", 1);
			}
			return RetBuild.done();
		}
		
	}
	
}
