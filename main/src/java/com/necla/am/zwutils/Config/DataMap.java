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

package com.necla.am.zwutils.Config;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Misc.Parsers;
import com.necla.am.zwutils.i18n.Messages;


/**
 * Unified configuration data object
 *
 * @author Zhenyu Wu
 * @version 0.1 - Oct. 2012: Refactored from DataFile
 * @version 0.2 - Dec. 2014: Environmental variable delayed expansion
 * @version 0.25 - Dec. 2015: Adopt resource bundle based localization
 * @version 0.25 - Jan. 20 2016: Initial public release
 */
public class DataMap {
	
	protected final IGroupLogger ILog;
	protected final Map<String, String> DataMap = new HashMap<>();
	
	/**
	 * Create empty configuration data
	 */
	public DataMap(String Name) {
		ILog = new GroupLogger.PerInst(Name + '.' + getClass().getSimpleName());
	}
	
	public DataMap(IGroupLogger RefLog, String Suffix) {
		ILog = Suffix == null? RefLog : new GroupLogger.PerInst(RefLog.GroupName() + '.' + Suffix);
	}
	
	/**
	 * Create configuration data from a configuration file with prefix key filter
	 */
	public DataMap(String Name, DataFile confFile, String Prefix) {
		this(Name);
		
		final String KeyPfx = Prefix != null? Prefix : ""; //$NON-NLS-1$
		confFile.keySet().forEach(fileKey -> {
			String confKey = (String) fileKey;
			String Key = confKey.trim();
			if (Key.startsWith(KeyPfx)) {
				Key = Key.substring(KeyPfx.length()).trim();
				DataMap.put(Key, confFile.getProperty(confKey).trim());
			}
		});
	}
	
	public DataMap(String Name, DataFile confFile) {
		this(Name, confFile, null);
	}
	
	public DataMap(DataFile confFile) {
		this(confFile.getName(), confFile, null);
	}
	
	private static final Pattern KeyValueToken = Pattern.compile("(?<!\\\\)="); //$NON-NLS-1$
	
	/**
	 * Create configuration data from an argument string list with prefix key filter
	 */
	public DataMap(String Name, String[] confArgs, String Prefix) {
		this(Name);
		
		for (String DataTok : confArgs) {
			if (!DataTok.isEmpty()) {
				String[] CmdKeyValue = KeyValueToken.split(DataTok.trim(), 2);
				String confKey = CmdKeyValue[0].trim();
				if (confKey.startsWith(Prefix)) {
					String Key = confKey.substring(Prefix.length()).trim();
					if (CmdKeyValue.length > 1) {
						DataMap.put(Key, CmdKeyValue[1].trim());
					} else {
						DataMap.put(Key, null);
					}
				}
			}
		}
	}
	
	public DataMap(String Name, String[] confArgs) {
		this(Name, confArgs, ""); //$NON-NLS-1$
	}
	
	private static final Pattern KeyItemToken = Pattern.compile("(?<!\\\\)\\|"); //$NON-NLS-1$
	
	/**
	 * Create configuration data from a configuration string with prefix key filter
	 */
	public DataMap(String Name, String confStr, String Prefix) {
		this(Name, KeyItemToken.split(confStr), Prefix);
	}
	
	public DataMap(String Name, String confStr) {
		this(Name, confStr, ""); //$NON-NLS-1$
	}
	
	/**
	 * Create configuration data from a configuration map with prefix key filter
	 */
	public DataMap(String Name, Map<String, String> confMap, String Prefix) {
		this(Name);
		
		confMap.forEach((confKey, V) -> {
			String Key = confKey.trim();
			if (Key.startsWith(Prefix)) {
				Key = Key.substring(Prefix.length()).trim();
				DataMap.put(Key, V.trim());
			}
		});
	}
	
	public DataMap(String Name, Map<String, String> confMap) {
		this(Name, confMap, ""); //$NON-NLS-1$
	}
	
	/**
	 * Create configuration data from another configuration data with prefix key filter
	 */
	public DataMap(String LogGroupSuffix, DataMap dataMap, String Prefix) {
		this(dataMap.ILog, LogGroupSuffix);
		
		if (Prefix != null) {
			dataMap.keySet(Prefix).forEach(confKey -> {
				String Key = confKey.substring(Prefix.length()).trim();
				DataMap.put(Key, dataMap.getText(confKey));
			});
		} else
			DataMap.putAll(dataMap.getDataMap());
	}
	
	public Map<String, String> getDataMap() {
		return DataMap;
	}
	
	/**
	 * Dump configuration data into a configuration file with prefixed keys
	 */
	public void DumpToFile(DataFile DataFile, String Prefix) {
		DataMap.forEach((Key, V) -> {
			String confKey = Prefix == null? Key : Prefix + Key;
			DataFile.setProperty(confKey, V);
		});
	}
	
	private static final char KeyValueDelim = '=';
	
	/**
	 * Dump configuration data into an argument string list with prefixed keys
	 */
	public String[] DumpToArgs(String Prefix) {
		List<String> RetArgs = new ArrayList<>();
		DataMap.forEach((Key, V) -> {
			String confKey = Prefix == null? Key : Prefix + Key;
			if (V != null) {
				RetArgs.add(confKey + KeyValueDelim + V);
			} else {
				RetArgs.add(confKey);
			}
		});
		return RetArgs.toArray(new String[RetArgs.size()]);
	}
	
	private static final char KeyItemDelim = '|';
	
	/**
	 * Dump configuration data into a configuration string with prefixed keys
	 */
	public String DumpToString(String Prefix) {
		String[] RetArgs = DumpToArgs(Prefix);
		
		StringWriter RetStr = null;
		for (String DataTok : RetArgs) {
			if (RetStr == null) {
				RetStr = new StringWriter();
			} else {
				RetStr.append(KeyItemDelim);
			}
			RetStr.append(DataTok);
		}
		if (RetStr != null)
			return RetStr.toString();
		else
			return ""; //$NON-NLS-1$
	}
	
	/**
	 * Dump configuration data into a configuration map with prefixed keys
	 */
	public Map<String, String> DumpToMap(String Prefix) {
		Map<String, String> RetMap = new HashMap<>();
		DataMap.forEach((Key, V) -> {
			String confKey = Prefix == null? Key : Prefix + Key;
			RetMap.put(confKey, V);
		});
		return RetMap;
	}
	
	public boolean containsKey(String Key) {
		return DataMap.containsKey(Key);
	}
	
	public Collection<String> keySet() {
		return DataMap.keySet();
	}
	
	/**
	 * Get the set of keys with given prefix
	 */
	public Collection<String> keySet(String Prefix) {
		List<String> Keys = new ArrayList<>();
		DataMap.keySet().forEach(Key -> {
			if (Key.startsWith(Prefix)) {
				Keys.add(Key);
			}
		});
		return Keys;
	}
	
	@Override
	public String toString() {
		return DumpToString(null);
	}
	
	protected boolean EnvSubstitute = true;
	
	public void SetEnvSubstitution(boolean Enable) {
		if (EnvSubstitute != Enable) {
			EnvSubstitute = Enable;
			ILog.Config(Messages.Localize("Config.DataMap.ENV_EXPANSION_STATE"), //$NON-NLS-1$
					Enable? Messages.Localize("Config.DataMap.STATE_ON") : Messages //$NON-NLS-1$
							.Localize("Config.DataMap.STATE_OFF"));   //$NON-NLS-1$
		}
	}
	
	private static final Pattern EnvDelayToken = Pattern.compile("!([^!\\s]+)!"); //$NON-NLS-1$
	private static final Matcher EnvDelayMatcher = EnvDelayToken.matcher(""); //$NON-NLS-1$
	private static final Pattern EnvToken = Pattern.compile("%([^%\\s|]+)(\\|[^%]*)?%"); //$NON-NLS-1$
	private static final Matcher EnvMatcher = EnvToken.matcher(""); //$NON-NLS-1$
	
	protected String GetValue(String Key) {
		String Value = DataMap.get(Key);
		
		if ((Value != null) && EnvSubstitute) {
			boolean Matched = false;
			while (true) {
				EnvMatcher.reset(Value);
				if (!EnvMatcher.find()) {
					break;
				}
				
				if (!Matched) {
					Matched = true;
					ILog.Finer(Messages.Localize("Config.DataMap.ENV_EXPANSION_START")); //$NON-NLS-1$
				}
				String EnvMatch = EnvMatcher.group(1);
				String RepMatch = EnvMatcher.group();
				ILog.Finest(Messages.Localize("Config.DataMap.MATCH_ENV"), EnvMatch); //$NON-NLS-1$
				String EnvReplace = System.getenv(EnvMatch);
				if (EnvReplace != null) {
					EnvMatcher.reset(EnvReplace);
					if (EnvMatcher.find()) {
						ILog.Warn(Messages.Localize("Config.DataMap.RECURSIVE_ENV_EXPAND"), EnvMatch, //$NON-NLS-1$
								EnvMatcher.group(1));
						ILog.Warn(Messages.Localize("Config.DataMap.RECURSIVE_ENV_EXPAND_FAIL")); //$NON-NLS-1$
						break;
					}
				} else {
					String EnvDefault = EnvMatcher.group(2);
					if (EnvDefault != null) {
						Value = Value.replace(RepMatch, EnvDefault.substring(1));
						ILog.Fine(Messages.Localize("Config.DataMap.ENV_EXPANSION_DEFAULT"), //$NON-NLS-1$
								EnvDefault.substring(1));
					} else {
						Value = null;
						ILog.Warn(Messages.Localize("Config.DataMap.ENV_NOT_FOUND"), EnvMatch); //$NON-NLS-1$
					}
					break;
				}
				ILog.Finer(Messages.Localize("Config.DataMap.ENV_EXPAND_VALUE"), EnvMatch, EnvReplace); //$NON-NLS-1$
				Value = Value.replace(RepMatch, EnvReplace);
			}
			if (Matched) {
				ILog.Finer("*@<"); //$NON-NLS-1$
			}
			
			// Uncover delayed environmental variable expansion
			if (Value != null) {
				Matched = false;
				while (true) {
					EnvDelayMatcher.reset(Value);
					if (!EnvDelayMatcher.find()) {
						break;
					}
					
					if (!Matched) {
						Matched = true;
						ILog.Finer(Messages.Localize("Config.DataMap.ENV_DELAY_ENPANSION")); //$NON-NLS-1$
					}
					String DelayEnvMatch = EnvDelayMatcher.group();
					String DelayEnvReplace = '%' + EnvDelayMatcher.group(1) + '%';
					ILog.Finer(Messages.Localize("Config.DataMap.ENV_DELAY_ENPANSION_RECOVER"), DelayEnvMatch, //$NON-NLS-1$
							DelayEnvReplace);
					Value = Value.replace(DelayEnvMatch, DelayEnvReplace);
				}
				if (Matched) {
					ILog.Finer("*@<"); //$NON-NLS-1$
				}
			}
		}
		return Value;
	}
	
	/**
	 * Generic function for retrieving any object, returns null if key is missing
	 *
	 * @param Key
	 *          - Configuration key
	 * @param Parser
	 *          - Parser to generate object from string
	 * @return Configuration object or null
	 */
	public <T> T getObject(String Key, Parsers.IStringParse<T> Parser,
			Parsers.IParseString<T> RevParser) {
		String Value = GetValue(Key);
		T Ret = null;
		if (Value != null) {
			Ret = Parser.parseOrFail(Value);
			ILog.Config(":%s => '%s' (%s)", Key, Value, RevParser.parseOrFail(Ret)); //$NON-NLS-1$
		}
		return Ret;
	}
	
	/**
	 * Generic function for retrieving any object, returns default object if key is missing
	 *
	 * @param Key
	 *          - Configuration key
	 * @param Default
	 *          - Default object
	 * @param Parser
	 *          - Parser to generate object from string
	 * @param RevParser
	 *          - Parser to generate string from object
	 * @return Configuration object
	 */
	public <T> T getObjectDef(String Key, T Default, Parsers.IStringParse<T> Parser,
			Parsers.IParseString<T> RevParser) {
		String Value = GetValue(Key);
		T Ret = null;
		if (Value != null) {
			Ret = Parser.parseOrDefault(Value, Default);
			ILog.Config(":%s => '%s' (%s)", Key, Value, RevParser.parseOrFail(Ret)); //$NON-NLS-1$
		} else {
			Ret = Default;
			ILog.Config(":%s :: [%s]", Key, RevParser.parseOrFail(Ret)); //$NON-NLS-1$
		}
		return Ret;
	}
	
	/**
	 * Generic function for storing any object
	 *
	 * @param Key
	 *          - Configuration key
	 * @param Value
	 *          - Configuration object
	 * @param RevParser
	 *          - Parser to generate string from object
	 */
	public <T> void setObject(String Key, T Value, Parsers.IParseString<T> RevParser) {
		DataMap.put(Key, RevParser.parseOrFail(Value));
		ILog.Config(":%s <= '%s'", Key, RevParser.parseOrFail(Value)); //$NON-NLS-1$
	}
	
	// Getter and setting of basic types
	public String getText(String Key) {
		return getObject(Key, Parsers.StringToString, Parsers.StringFromString);
	}
	
	public String getTextDef(String Key, String Default) {
		return getObjectDef(Key, Default, Parsers.StringToString, Parsers.StringFromString);
	}
	
	public void setText(String Key, String Value) {
		setObject(Key, Value, Parsers.StringFromString);
	}
	
	public Boolean getBool(String Key) {
		return getObject(Key, Parsers.StringToBoolean, Parsers.StringFromBoolean);
	}
	
	public Boolean getBoolDef(String Key, Boolean Default) {
		return getObjectDef(Key, Default, Parsers.StringToBoolean, Parsers.StringFromBoolean);
	}
	
	public void setBool(String Key, Boolean Value) {
		setObject(Key, Value, Parsers.StringFromBoolean);
	}
	
	public Integer getInt(String Key) {
		return getObject(Key, Parsers.StringToInteger, Parsers.StringFromInteger);
	}
	
	public Integer getIntDef(String Key, Integer Default) {
		return getObjectDef(Key, Default, Parsers.StringToInteger, Parsers.StringFromInteger);
	}
	
	public void setInt(String Key, Integer Value) {
		setObject(Key, Value, Parsers.StringFromInteger);
	}
	
	public Long getLong(String Key) {
		return getObject(Key, Parsers.StringToLong, Parsers.StringFromLong);
	}
	
	public Long getLongDef(String Key, Long Default) {
		return getObjectDef(Key, Default, Parsers.StringToLong, Parsers.StringFromLong);
	}
	
	public void setLong(String Key, Long Value) {
		setObject(Key, Value, Parsers.StringFromLong);
	}
	
	public Short getShort(String Key) {
		return getObject(Key, Parsers.StringToShort, Parsers.StringFromShort);
	}
	
	public Short getShortDef(String Key, Short Default) {
		return getObjectDef(Key, Default, Parsers.StringToShort, Parsers.StringFromShort);
	}
	
	public void setShort(String Key, Short Value) {
		setObject(Key, Value, Parsers.StringFromShort);
	}
	
	public Byte getByte(String Key) {
		return getObject(Key, Parsers.StringToByte, Parsers.StringFromByte);
	}
	
	public Byte getByteDef(String Key, Byte Default) {
		return getObjectDef(Key, Default, Parsers.StringToByte, Parsers.StringFromByte);
	}
	
	public void setByte(String Key, Byte Value) {
		setObject(Key, Value, Parsers.StringFromByte);
	}
	
	public Double getDouble(String Key) {
		return getObject(Key, Parsers.StringToDouble, Parsers.StringFromDouble);
	}
	
	public Double getDoubleDef(String Key, Double Default) {
		return getObjectDef(Key, Default, Parsers.StringToDouble, Parsers.StringFromDouble);
	}
	
	public void setDouble(String Key, Double Value) {
		setObject(Key, Value, Parsers.StringFromDouble);
	}
	
	public Float getFloat(String Key) {
		return getObject(Key, Parsers.StringToFloat, Parsers.StringFromFloat);
	}
	
	public Float getFloatDef(String Key, Float Default) {
		return getObjectDef(Key, Default, Parsers.StringToFloat, Parsers.StringFromFloat);
	}
	
	public void setFloat(String Key, Float Value) {
		setObject(Key, Value, Parsers.StringFromFloat);
	}
	
}
