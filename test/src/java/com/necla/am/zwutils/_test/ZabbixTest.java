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

package com.necla.am.zwutils._test;

import com.google.gson.JsonObject;
import com.necla.am.zwutils.Logging.DebugLog;
import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.Utils.Formatters.LogFormatter;
import com.necla.am.zwutils.Logging.Utils.Formatters.SlimFormatter;
import com.necla.am.zwutils.Logging.Utils.Handlers.Zabbix.ZabbixHandler;
import com.necla.am.zwutils.Logging.Utils.Handlers.Zabbix.api.ZabbixAPI;
import com.necla.am.zwutils.Logging.Utils.Handlers.Zabbix.api.ZabbixRequest;
import com.necla.am.zwutils.Misc.Misc;


public class ZabbixTest {

	public static final String LogGroup = "Main";

	protected static final GroupLogger ClassLog = new GroupLogger(LogGroup);

	public void Go(String[] args) {

		try {
			LogFormatter.ConfigData.Mutable LogFormatterConfig = LogFormatter.Config.mirror();
			LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_GROUPWIDTH,
					"48");
			LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_METHODWIDTH,
					"0");
			LogFormatter.Config.set(LogFormatterConfig);
		} catch (Throwable e) {
			Misc.CascadeThrow(e);
		}

		ZabbixAPI Test1 = new ZabbixAPI.Impl();

		ZabbixRequest request = ZabbixRequest.Factory.HostGroupInfo(null);
		JsonObject response = Test1.call(request);
		ClassLog.Info("Host Groups: %s", response.get("result"));

		try (ZabbixHandler ZH1 = new ZabbixHandler("ZWUtils", "ZabbixTest", Test1, null)) {
			ZH1.DoLog("Test1", Misc.wrap("Test", 1));
			ZH1.DoLog("ZabbixTest.Test1", Misc.wrap("Test", 2));
			ZH1.DoLog("ZWUtils.ZabbixTest.Test1", Misc.wrap("Test1", "Test"));
			ZH1.DoLog("ZWUtils.ZabbixTest.Test2", Misc.wrap("Test1", 1));
			ClassLog.Info("Expect Zabbix log failure");
			ZH1.DoLog("ZWUtils.ZabbixTest.Test2", Misc.wrap("Test1", "1"));
		}
		ClassLog.Info("$ZBX$", "Test1", 3, "Test2", 4);
	}

	public static void main(String[] args) {
		ClassLog.Info("========== Zabbix Test");
		try {
			ZabbixTest Main = new ZabbixTest();
			Main.Go(args);
		} catch (Throwable e) {
			DebugLog.Log.logExcept(e);
		}
		ClassLog.Info("#@~<");
		ClassLog.Info("========== Done");
	}

}
