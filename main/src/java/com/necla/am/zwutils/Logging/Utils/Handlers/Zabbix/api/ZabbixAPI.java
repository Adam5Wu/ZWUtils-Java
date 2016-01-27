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

package com.necla.am.zwutils.Logging.Utils.Handlers.Zabbix.api;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.necla.am.zwutils.Config.Container;
import com.necla.am.zwutils.Config.Data;
import com.necla.am.zwutils.Config.DataFile;
import com.necla.am.zwutils.Config.DataMap;
import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.Misc.Misc.SizeUnit;
import com.necla.am.zwutils.Misc.Misc.TimeUnit;


/**
 * Zabbix Web API interface
 *
 * @author Zhenyu Wu
 * @version 0.1 - Sep. 2015: Initial implementation
 * @version 0.1 - Jan. 20 2016: Initial public release
 */
public interface ZabbixAPI {
	
	public String user();
	
	public String apiVersion();
	
	public JsonObject call(ZabbixRequest request);
	
	public JsonObject send(ZabbixReport report);
	
	public class Impl implements ZabbixAPI {
		
		public static final String LogGroup = "ZWUtils.Logging.Zabbix.API";
		
		protected static final IGroupLogger CLog = new GroupLogger(LogGroup);
		
		public static class ConfigData {
			
			protected static final String KEY_PREFIX = "ZabbixAPI.";
			
			protected static final int DEFPORT_HTTP = 80;
			protected static final int DEFPORT_HTTPS = 443;
			protected static final int DEFPORT_REPORT = 10051;
			
			public static class Mutable extends Data.Mutable {
				
				// Declare mutable configurable fields (public)
				public boolean SecureHTTP;
				public String ServerAddr;
				public int APIPort;
				public int ReportPort;
				public String UserName;
				public String Password;
				public String RPCPath;
				public int NetTimeout;
				
				protected URL URL;
				protected InetSocketAddress ReportAddr;
				
				@Override
				public void loadDefaults() {
					SecureHTTP = false;
					ServerAddr = "192.168.58.129";
					APIPort = DEFPORT_HTTP;
					ReportPort = DEFPORT_REPORT;
					UserName = "ZWUtils";
					Password = "ZWUtilsLogger";
					RPCPath = "/api_jsonrpc.php";
					NetTimeout = (int) TimeUnit.SEC.Convert(10, TimeUnit.MSEC);
				}
				
				@Override
				public void loadFields(DataMap confMap) {
					SecureHTTP = confMap.getBoolDef("SecureHTTP", SecureHTTP);
					ServerAddr = confMap.getTextDef("ServerAddr", ServerAddr);
					APIPort = confMap.getIntDef("APIPort", SecureHTTP? DEFPORT_HTTPS : DEFPORT_HTTP);
					ReportPort = confMap.getIntDef("ReportPort", ReportPort);
					UserName = confMap.getTextDef("UserName", UserName);
					Password = confMap.getTextDef("Password", Password);
					RPCPath = confMap.getTextDef("RPCPath", RPCPath);
					NetTimeout = (int) TimeUnit.SEC.Convert(confMap.getIntDef("NetTimeout",
							(int) TimeUnit.MSEC.Convert(NetTimeout, TimeUnit.SEC)), TimeUnit.MSEC);
				}
				
				public static final int MAX_PORTNUM = 0xFFFF;
				public static final int MIN_CNLEN = 8;
				public static final long MIN_TIMEOUT = TimeUnit.SEC.Convert(3, TimeUnit.MSEC);
				public static final long MAX_TIMEOUT = TimeUnit.MIN.Convert(1, TimeUnit.MSEC);
				
				protected class Validation implements Data.Mutable.Validation {
					
					@Override
					public void validateFields() {
						ILog.Fine("Checking Server Address...");
						try {
							InetAddress.getByName(ServerAddr);
						} catch (UnknownHostException e) {
							Misc.CascadeThrow(e, "Problem resolving server address");
						}
						
						ILog.Fine("Checking Ports...");
						if ((APIPort <= 0) || (APIPort > MAX_PORTNUM))
							Misc.ERROR("Invalid API port number (%d)", APIPort);
						if ((ReportPort <= 0) || (ReportPort > MAX_PORTNUM))
							Misc.ERROR("Invalid report port number (%d)", ReportPort);
							
						ILog.Fine("Checking timeout Period...");
						if ((NetTimeout < MIN_TIMEOUT) || (NetTimeout > MAX_TIMEOUT))
							Misc.ERROR("Invalid timeout period (%d)",
									TimeUnit.MSEC.Convert(NetTimeout, TimeUnit.SEC));
					}
					
				}
				
				@Override
				protected Validation needValidation() {
					return new Validation();
				}
				
				protected class Population implements Data.Mutable.Population {
					
					@Override
					public void populateFields() {
						StringBuilder StrBuf = new StringBuilder();
						
						StrBuf.append(SecureHTTP? "https" : "http").append("://");
						StrBuf.append(ServerAddr);
						if (SecureHTTP) {
							if (APIPort != DEFPORT_HTTPS) StrBuf.append(':').append(APIPort);
						} else {
							if (APIPort != DEFPORT_HTTP) StrBuf.append(':').append(APIPort);
						}
						StrBuf.append(RPCPath);
						
						try {
							URL = new URL(StrBuf.toString());
						} catch (Throwable e) {
							Misc.CascadeThrow(e, "Unable to fomulate reporting URI");
						}
						
						ReportAddr = new InetSocketAddress(ServerAddr, ReportPort);
					}
					
				}
				
				@Override
				protected Population needPopulation() {
					return new Population();
				}
				
			}
			
			protected static class ReadOnly extends Data.ReadOnly {
				
				// Declare read-only configurable fields (public)
				public final URL URL;
				public final InetSocketAddress ReportAddr;
				public final String UserName;
				public final String Password;
				public final int NetTimeout;
				
				public ReadOnly(IGroupLogger Logger, Mutable Source) {
					super(Logger, Source);
					
					// Copy all fields from Source
					URL = Source.URL;
					ReportAddr = Source.ReportAddr;
					UserName = Source.UserName;
					Password = Source.Password;
					NetTimeout = Source.NetTimeout;
				}
				
			}
			
			public static final File ConfigFile = DataFile.DeriveConfigFile("ZWUtils.");
			
			public static Container<Mutable, ReadOnly> Create(File xConfigFile) throws Throwable {
				return Container.Create(Mutable.class, ReadOnly.class, LogGroup + ".Config", xConfigFile,
						KEY_PREFIX);
			}
			
		}
		
		ConfigData.ReadOnly Config;
		final String auth;
		
		public Impl() {
			this(null);
		}
		
		public Impl(File ConfigFile) {
			try {
				Config =
						ConfigData.Create(ConfigFile != null? ConfigFile : ConfigData.ConfigFile).reflect();
			} catch (Throwable e) {
				Misc.CascadeThrow(e);
			}
			
			auth = login(Config.UserName, Config.Password);
			sanity_check();
		}
		
		protected String login(String user, String password) {
			ZabbixRequest request = ZabbixRequest.Factory.Logon(user, password);
			JsonObject response = call(request);
			if (!response.has("result")) Misc.FAIL("Unable to logon to Zabbix server - %s", response);
			String auth = response.get("result").getAsString();
			if (auth == null || auth.isEmpty()) Misc.FAIL("Unable to obtain authorization");
			CLog.Config("Logged in as '%s'", Config.UserName);
			return auth;
		}
		
		protected void sanity_check() {
			String UserID;
			ZabbixRequest UserQuery = ZabbixRequest.Factory.UserInfo(Config.UserName);
			UserQuery.putParam("output", Misc.wrap("userid", "autologout"));
			JsonArray Users = call(UserQuery).get("result").getAsJsonArray();
			if (Users.size() != 1) Misc.FAIL("Expect return of 1 entry, received %d", Users.size());
			JsonObject UserData = Users.get(0).getAsJsonObject();
			UserID = UserData.get("userid").getAsString();
			String AutoLogout = UserData.get("autologout").getAsString();
			if (AutoLogout.equals("0")) {
				CLog.Fine("User has auto-logout disabled");
				return;
			}
			CLog.Warn("Auto-logout of %s sec enabled for user '%s'", AutoLogout, Config.UserName);
			ZabbixRequest UserUpdateQuery = ZabbixRequest.Factory.UserUpdateTemplate(UserID);
			UserUpdateQuery.putParam("autologout", "0");
			JsonObject UserUpdateRet = call(UserUpdateQuery).get("result").getAsJsonObject();
			JsonArray UpdateUsers = UserUpdateRet.get("userids").getAsJsonArray();
			if (UpdateUsers.size() != 1)
				Misc.FAIL("Expect return of 1 entry, received %d", UpdateUsers.size());
			String UpdateUserID = UpdateUsers.get(0).getAsString();
			if (!UpdateUserID.equals(UserID))
				Misc.FAIL("Expect update of user #%s, received #%s", UserID, UpdateUserID);
			CLog.Warn("Disabled auto-logout of %s sec for user '%s'", AutoLogout, Config.UserName);
		}
		
		@Override
		public String user() {
			return Config.UserName;
		}
		
		@Override
		public String apiVersion() {
			ZabbixRequest request = ZabbixRequest.Factory.APIVersion();
			JsonObject response = call(request);
			return response.get("result").getAsString();
		}
		
		public static final String HEADER_CONTENTTYPE = "Content-Type";
		public static final String CONTENTTYPE_JSONRPC = "application/json-rpc";
		public static final String HEADER_CONTENTLEN = "Content-Length";
		public static final long RESP_HIGHPAYLOAD = SizeUnit.KB.Convert(16, SizeUnit.BYTE);
		
		@Override
		public JsonObject call(ZabbixRequest request) {
			if (request.getAuth() == null) request.setAuth(auth);
			
			HttpURLConnection Request = null;
			try {
				Request = (HttpURLConnection) Config.URL.openConnection();
				Request.setRequestMethod("POST");
				Request.setRequestProperty(HEADER_CONTENTTYPE, CONTENTTYPE_JSONRPC);
				Request.setConnectTimeout(Config.NetTimeout);
				Request.setReadTimeout(Config.NetTimeout);
				Request.setDoOutput(true);
			} catch (Throwable e) {
				Misc.CascadeThrow(e, "Request preparation failed");
			}
			
			try (DataOutputStream PostOut = new DataOutputStream(Request.getOutputStream())) {
				PostOut.write(request.toString().getBytes(StandardCharsets.UTF_8));
				PostOut.flush();
			} catch (IOException e) {
				Request.disconnect();
				Misc.CascadeThrow(e, "Request data transfer interrupted");
			}
			
			try (InputStream Data = Request.getInputStream()) {
				int RespCode = Request.getResponseCode();
				CLog.Fine("Received response code %d", RespCode);
				
				// Check for content-length header
				String StrRespLen = Request.getHeaderField(HEADER_CONTENTLEN);
				if (StrRespLen == null) Misc.FAIL("Unable to handle reply without content-length");
				int RespLen = 0;
				try {
					RespLen = Integer.valueOf(StrRespLen);
				} catch (Throwable e) {
					Misc.FAIL("Malformed content-length header");
				}
				if ((RespLen < 0) || (RespLen > RESP_HIGHPAYLOAD))
					Misc.FAIL("Invalid payload size (%s)", Misc.FormatSize(RespLen));
					
				// Receive response data
				byte[] Payload = null;
				try {
					DataInputStream Reader = new DataInputStream(Data);
					Payload = new byte[RespLen];
					Reader.readFully(Payload);
				} catch (IOException e) {
					Misc.CascadeThrow(e, "Error receiving payload");
				}
				
				// Try parse response data
				return new JsonParser().parse(new String(Payload, StandardCharsets.UTF_8))
						.getAsJsonObject();
			} catch (IOException e) {
				Request.disconnect();
				Misc.CascadeThrow(e, "Response data processing failed");
			}
			return null;
		}
		
		@Override
		public JsonObject send(ZabbixReport report) {
			JsonObject Ret = null;
			try (Socket socket = new Socket()) {
				socket.setSoTimeout(Config.NetTimeout);
				socket.connect(Config.ReportAddr, Config.NetTimeout);
				
				OutputStream outputStream = socket.getOutputStream();
				// Using new style, without ZBXD\1 header
				outputStream.write(report.toString().getBytes(StandardCharsets.UTF_8));
				outputStream.flush();
				
				InputStream inputStream = socket.getInputStream();
				// Normally response length < 100
				int respLen = 0;
				byte[] respData = new byte[512];
				ByteArrayOutputStream RespStream = null;
				
				while (true) {
					if (RespStream == null) {
						if (respLen < respData.length) {
							int readCount = inputStream.read(respData, respLen, respData.length - respLen);
							if (readCount <= 0) break;
							respLen += readCount;
						} else {
							RespStream = new ByteArrayOutputStream();
							RespStream.write(respData);
							// continue;
						}
					} else {
						int readCount = inputStream.read(respData, 0, respData.length);
						if (readCount <= 0) break;
						RespStream.write(respData, 0, readCount);
						respLen += readCount;
						if (respLen >= RESP_HIGHPAYLOAD) Misc.FAIL("Excessive server response");
					}
				}
				if (RespStream != null) respData = RespStream.toByteArray();
				if (respLen < 5) Misc.FAIL("Unexpected server response of %d bytes", respLen);
				
				String RespJson;
				if (new String(respData, 0, 5).equals("ZBXD\1")) {
					// Legacy style 'ZBXD\1' + (long)len
					// Header 5 + 8 = 13 bytes
					RespJson = new String(respData, 13, respLen - 13, StandardCharsets.UTF_8);
				} else {
					// New style, no header
					RespJson = new String(respData, StandardCharsets.UTF_8);
				}
				Ret = new JsonParser().parse(RespJson).getAsJsonObject();
			} catch (Throwable e) {
				Misc.CascadeThrow(e);
			}
			return Ret;
		}
		
	}
	
}