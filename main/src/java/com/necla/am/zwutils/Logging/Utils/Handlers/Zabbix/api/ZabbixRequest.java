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
 * @version 0.1 - Sep. 2015: Initial implementation
 * @version 0.1 - Jan. 20 2016: Initial public release
 */
public class ZabbixRequest {
	String jsonrpc = "2.0";
	
	Map<String, Object> params = new HashMap<>();
	
	String method;
	
	String auth;
	
	Integer id;
	
	public void putParam(String key, Object value) {
		params.put(key, value);
	}
	
	public Object removeParam(String key) {
		return params.remove(key);
	}
	
	public String getJsonrpc() {
		return jsonrpc;
	}
	
	public void setJsonrpc(String jsonrpc) {
		this.jsonrpc = jsonrpc;
	}
	
	public Map<String, Object> getParams() {
		return params;
	}
	
	public void setParams(Map<String, Object> params) {
		this.params = params;
	}
	
	public String getMethod() {
		return method;
	}
	
	public void setMethod(String method) {
		this.method = method;
	}
	
	public String getAuth() {
		return auth;
	}
	
	public void setAuth(String auth) {
		this.auth = auth;
	}
	
	public Integer getId() {
		return id;
	}
	
	public void setId(Integer id) {
		this.id = id;
	}
	
	@Override
	public String toString() {
		return new Gson().toJson(this);
	}
	
	public static class Builder {
		
		private static final AtomicInteger nextId = new AtomicInteger(1);
		
		ZabbixRequest request = new ZabbixRequest();
		
		private Builder() {}
		
		static public Builder create() {
			return new Builder();
		}
		
		public ZabbixRequest build() {
			if (request.getId() == null) {
				request.setId(nextId.getAndIncrement());
			}
			return request;
		}
		
		public Builder version(String version) {
			request.setJsonrpc(version);
			return this;
		}
		
		public Builder paramEntry(String key, Object value) {
			request.putParam(key, value);
			return this;
		}
		
		/**
		 * Do not necessary to call this method.If don not set id, ZabbixApi will auto set request
		 * auth..
		 *
		 * @param auth
		 * @return
		 */
		public Builder auth(String auth) {
			request.setAuth(auth);
			return this;
		}
		
		public Builder method(String method) {
			request.setMethod(method);
			return this;
		}
		
		/**
		 * Do not necessary to call this method.If don not set id, RequestBuilder will auto generate.
		 *
		 * @param id
		 * @return
		 */
		public Builder id(Integer id) {
			request.setId(id);
			return this;
		}
		
	}
	
	public static class Factory {
		
		public static ZabbixRequest Logon(String name, String password) {
			return ZabbixRequest.Builder.create().method("user.login").paramEntry("user", name)
					.paramEntry("password", password).build();
		}
		
		public static ZabbixRequest UserInfo(String name) {
			ZabbixRequest Ret = ZabbixRequest.Builder.create().method("user.get").build();
			Ret.putParam("output", "extend");
			if (name != null) Ret.putParam("filter", Misc.StringMap(Misc.wrap("alias"), name));
			return Ret;
		}
		
		public static ZabbixRequest APIVersion() {
			return ZabbixRequest.Builder.create().method("apiinfo.version").build();
		}
		
		public static ZabbixRequest HostGroupInfo(String name) {
			ZabbixRequest Ret = ZabbixRequest.Builder.create().method("hostgroup.get").build();
			Ret.putParam("output", "extend");
			if (name != null) Ret.putParam("filter", Misc.StringMap(Misc.wrap("name"), name));
			return Ret;
		}
		
		public static ZabbixRequest HostGroupCreate(String name) {
			ZabbixRequest Ret = ZabbixRequest.Builder.create().method("hostgroup.create")
					.paramEntry("name", name).build();
			return Ret;
		}
		
		public static ZabbixRequest HostInfo(String name) {
			ZabbixRequest Ret = ZabbixRequest.Builder.create().method("host.get").build();
			Ret.putParam("output", "extend");
			if (name != null) Ret.putParam("filter", Misc.StringMap(Misc.wrap("host"), name));
			return Ret;
		}
		
		public static ZabbixRequest HostCreate(String name, String... groupids) {
			ZabbixRequest Ret =
					ZabbixRequest.Builder.create().method("host.create").paramEntry("host", name).build();
			List<Object> HostGroups = new ArrayList<>();
			for (String groupid : groupids)
				HostGroups.add(Misc.StringMap(Misc.wrap("groupid"), groupid));
			Ret.putParam("groups", HostGroups);
			return Ret;
		}
		
		public static ZabbixRequest ApplicationInfo(String name, String HostID) {
			ZabbixRequest Ret = ZabbixRequest.Builder.create().method("application.get").build();
			Ret.putParam("output", "extend");
			if (name != null) Ret.putParam("filter", Misc.StringMap(Misc.wrap("name"), name));
			if (HostID != null) Ret.putParam("hostids", HostID);
			return Ret;
		}
		
		public static ZabbixRequest ApplicationCreate(String name, String HostID) {
			ZabbixRequest Ret = ZabbixRequest.Builder.create().method("application.create")
					.paramEntry("name", name).paramEntry("hostid", HostID).build();
			return Ret;
		}
		
		public static ZabbixRequest ItemInfo(String Key, String HostID) {
			ZabbixRequest Ret = ZabbixRequest.Builder.create().method("item.get").build();
			Ret.putParam("output", "extend");
			if (Key != null) Ret.putParam("filter", Misc.StringMap(Misc.wrap("key_"), Key));
			if (HostID != null) Ret.putParam("hostids", HostID);
			return Ret;
		}
		
		public static ZabbixRequest ItemCreateTemplate(String Key, String Name, String HostID) {
			ZabbixRequest Ret = ZabbixRequest.Builder.create().method("item.create")
					.paramEntry("key_", Key).paramEntry("name", Name).paramEntry("hostid", HostID).build();
			return Ret;
		}
		
		public static ZabbixRequest MediaTypeInfo(String Desc, Integer Type) {
			ZabbixRequest Ret = ZabbixRequest.Builder.create().method("mediatype.get").build();
			Ret.putParam("output", "extend");
			if ((Desc != null) || (Type != null)) {
				Map<String, Object> FilterMap = null;
				if (Desc != null) FilterMap = Misc.StringMap(Misc.wrap("description"), Desc);
				if (Type != null) if (FilterMap != null)
					FilterMap.put("type", Type);
				else
					FilterMap = Misc.StringMap(Misc.wrap("type"), Type);
				Ret.putParam("filter", FilterMap);
			}
			return Ret;
		}
		
		public static ZabbixRequest UserUpdateTemplate(String UserID) {
			ZabbixRequest Ret = ZabbixRequest.Builder.create().method("user.update").build();
			Ret.putParam("userid", UserID);
			return Ret;
		}
		
		public static ZabbixRequest UserAddMediaTemplate(String UserID) {
			ZabbixRequest Ret = ZabbixRequest.Builder.create().method("user.addmedia").build();
			Ret.putParam("users", Misc.StringMap(Misc.wrap("userid"), UserID));
			return Ret;
		}
		
		public static ZabbixRequest ActionInfo(String Name) {
			ZabbixRequest Ret = ZabbixRequest.Builder.create().method("action.get").build();
			Ret.putParam("output", "extend");
			if (Name != null) Ret.putParam("filter", Misc.StringMap(Misc.wrap("name"), Name));
			return Ret;
		}
		
		public static ZabbixRequest ActionCreateTemplate(String Name, int Source, int EscPeriod,
				String ProbSubj, String ProbBody, String RecSubj, String RecBody) {
			ZabbixRequest Ret = ZabbixRequest.Builder.create().method("action.create")
					.paramEntry("name", Name).paramEntry("esc_period", Math.max(EscPeriod, 60))
					.paramEntry("eventsource", Source).build();
					
			if (ProbSubj != null) Ret.putParam("def_shortdata", ProbSubj);
			if (ProbBody != null) Ret.putParam("def_longdata", ProbBody);
			if ((RecSubj != null) || (RecBody != null)) {
				if (RecSubj != null) Ret.putParam("r_shortdata", RecSubj);
				if (RecBody != null) Ret.putParam("r_longdata", RecBody);
				Ret.putParam("recovery_msg", 1);
			}
			return Ret;
		}
		
	}
	
}
