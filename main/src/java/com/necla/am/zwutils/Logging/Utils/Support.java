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

package com.necla.am.zwutils.Logging.Utils;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Pattern;

import com.necla.am.zwutils.Logging.DebugLog;
import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.Misc.Parsers;


/**
 * Misc supporting utilities
 *
 * @author Zhenyu Wu
 * @see DebugLog
 * @version 0.1 - Initial Implementation
 * @version 0.1 - Jan. 20 2016: Initial public release
 */
public class Support {
	
	protected Support() {
		Misc.FAIL(IllegalStateException.class, "Do not instantiate!");
	}
	
	public static final String LOGGROUP = "ZWUtils.Logging.Support";
	protected static final IGroupLogger CLog = new GroupLogger(LOGGROUP);
	
	/**
	 * String to log level parser
	 */
	public static class StringToLevel extends Parsers.SimpleStringParse<Level> {
		
		@Override
		public Level parseOrFail(String From) {
			if (From == null) {
				Misc.FAIL(NullPointerException.class, Parsers.ERROR_NULL_POINTER);
				// PERF: code analysis tool doesn't recognize custom throw functions
				throw new IllegalStateException(Misc.MSG_SHOULD_NOT_REACH);
			}
			return Level.parse(From.toUpperCase());
		}
		
	}
	
	public static final StringToLevel StringToLevel = new StringToLevel();
	public static final Parsers.AnyToString<Level> StringFromLevel = new Parsers.AnyToString<>();
	
	/**
	 * Group log file record
	 */
	public static class GroupLogFile {
		public final String FileName;
		
		public enum Feature {
			FORWARD,
			APPEND,
			DAILYROTATE,
			COMPRESSROTATED
		}
		
		public final Set<Feature> Features;
		
		public GroupLogFile(String fileName, Set<Feature> features) {
			super();
			FileName = fileName;
			Features = Collections.unmodifiableSet(features);
		}
		
		/**
		 * String to group log file record
		 */
		public static class StringToGroupLogFile extends Parsers.SimpleStringParse<GroupLogFile> {
			
			protected static final char GROUPFILE_FORWARD = '~';
			protected static final char GROUPFILE_APPEND = '+';
			protected static final char GROUPFILE_DAILYROTATE = '@';
			protected static final char GROUPFILE_COMPRESSROTATED = '#';
			protected static final Pattern GROUPFILE_DELIM = Pattern.compile("\\$");
			
			@Override
			public GroupLogFile parseOrFail(String From) {
				if (From == null) {
					Misc.FAIL(NullPointerException.class, Parsers.ERROR_NULL_POINTER);
					// PERF: code analysis tool doesn't recognize custom throw functions
					throw new IllegalStateException(Misc.MSG_SHOULD_NOT_REACH);
				}
				
				EnumSet<GroupLogFile.Feature> Features = EnumSet.noneOf(GroupLogFile.Feature.class);
				String[] Tokens = GROUPFILE_DELIM.split(From.trim(), 2);
				if (Tokens.length > 1) {
					final int len = Tokens[1].length();
					for (int i = 0; i < len; i++) {
						switch (Tokens[1].charAt(i)) {
							case GROUPFILE_FORWARD:
								Features.add(GroupLogFile.Feature.FORWARD);
								break;
							case GROUPFILE_APPEND:
								Features.add(GroupLogFile.Feature.APPEND);
								break;
							case GROUPFILE_DAILYROTATE:
								Features.add(GroupLogFile.Feature.DAILYROTATE);
								break;
							case GROUPFILE_COMPRESSROTATED:
								Features.add(GroupLogFile.Feature.COMPRESSROTATED);
								break;
							default:
								CLog.Warn("Ignored unrecognized modifier '%s' for log file '%s'",
										Tokens[1].charAt(i), Tokens[0]);
						}
					}
				} else {
					Features.add(GroupLogFile.Feature.FORWARD);
				}
				
				return new GroupLogFile(Tokens[0], Features);
			}
		}
		
		/**
		 * String from group log file record
		 */
		public static class StringFromGroupLogFile extends Parsers.SimpleParseString<GroupLogFile> {
			
			@Override
			public String parseOrFail(GroupLogFile From) {
				if (From == null) {
					Misc.FAIL(NullPointerException.class, Parsers.ERROR_NULL_POINTER);
					// PERF: code analysis tool doesn't recognize custom throw functions
					throw new IllegalStateException(Misc.MSG_SHOULD_NOT_REACH);
				}
				
				if (From.FileName.isEmpty())
					return "<No File>";
				else
					return String.format("%s [%s]", From.FileName, From.Features);
			}
			
		}
		
		public static final StringToGroupLogFile ParseFromString = new StringToGroupLogFile();
		public static final StringFromGroupLogFile ParseToString = new StringFromGroupLogFile();
	}
	
}
