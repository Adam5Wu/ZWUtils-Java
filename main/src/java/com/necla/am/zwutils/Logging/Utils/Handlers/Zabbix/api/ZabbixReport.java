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

// Adapted from https://github.com/hengyunabc/zabbix-sender

package com.necla.am.zwutils.Logging.Utils.Handlers.Zabbix.api;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.necla.am.zwutils.Misc.Misc.TimeSystem;
import com.necla.am.zwutils.Misc.Misc.TimeUnit;
import com.necla.am.zwutils.Modeling.ITimeStamp;


/**
 * Zabbix push-logging data container
 *
 * @author Zhenyu Wu
 * @version 0.1 - Sep. 2015: Initial implementation
 * @version 0.1 - Jan. 20 2016: Initial public release
 */
public class ZabbixReport {
	
	public class Item {
		
		String host;
		String key;
		String value;
		long clock;
		
		public Item(String host, String key, String value) {
			this(host, key, value, ITimeStamp.Impl.Now().VALUE(TimeSystem.UNIX, TimeUnit.SEC));
		}
		
		public Item(String host, String key, String value, long clock) {
			this.clock = clock;
			this.host = host;
			this.key = key;
			this.value = value;
		}
		
		public long getClock() {
			return clock;
		}
		
		public void setClock(long clock) {
			this.clock = clock;
		}
		
		public String getHost() {
			return host;
		}
		
		public void setHost(String host) {
			this.host = host;
		}
		
		public String getKey() {
			return key;
		}
		
		public void setKey(String key) {
			this.key = key;
		}
		
		public String getValue() {
			return value;
		}
		
		public void setValue(String value) {
			this.value = value;
		}
	}
	
	String request = "sender data";
	long clock;
	List<Item> data = new ArrayList<>();
	
	public void Add(String host, String key, Object value) {
		data.add(new Item(host, key, String.valueOf(value)));
	}
	
	public void Add(String host, String key, Object value, long clock) {
		data.add(new Item(host, key, String.valueOf(value), clock));
	}
	
	@Override
	public String toString() {
		// Get the latest time before serialization
		clock = ITimeStamp.Impl.Now().VALUE(TimeSystem.UNIX, TimeUnit.SEC);
		return new Gson().toJson(this);
	}
	
}
