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

package com.necla.am.zwutils.Misc;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.IGroupLogger;


/**
 * A simple parser interface and reference implementation on basic types
 *
 * @author Zhenyu Wu
 * @version 0.1 - Nov. 2012: Initial implementation
 * @version 0.1 - Jan. 20 2016: Initial public release
 */
public final class Parsers {
	
	protected Parsers() {
		Misc.FAIL(IllegalStateException.class, "Do not instantiate!");
	}
	
	public static final String LOGGROUP = "ZWUtils.Parsers";
	protected static final IGroupLogger CLog = new GroupLogger(LOGGROUP);
	
	/**
	 * Generic parser interface from one object to another
	 */
	public static interface IParse<From extends Object, To extends Object> {
		
		To parseOrFail(From From);
		
		To parseOrDefault(From From, To Default);
	}
	
	public static final String ERROR_NULL_POINTER = "Unable to convert from null pointer";
	public static final String ERROR_PARSE_FALLBACK = "Falling back from parsing '%s'";
	
	/**
	 * String parser from an object
	 */
	public static interface IParseString<From> extends IParse<From, String> {
		// No additional methods
	}
	
	/**
	 * Simple implementation of StringParse which consolidates conversion to a single implementation
	 */
	public abstract static class SimpleParseString<From> implements IParseString<From> {
		
		@Override
		public final String parseOrDefault(From From, String Default) {
			String Ret = Default;
			
			try {
				Ret = parseOrFail(From);
			} catch (Exception e) {
				if (CLog.isLoggable(Level.FINE)) {
					CLog.logExcept(e, ERROR_PARSE_FALLBACK, From);
				}
			}
			
			return Ret;
		}
		
	}
	
	public static class AnyToString<From> extends SimpleParseString<From> {
		
		@Override
		public String parseOrFail(From From) {
			return String.valueOf(From);
		}
		
	}
	
	public static final AnyToString<String> StringFromString = new AnyToString<>();
	public static final AnyToString<Boolean> StringFromBoolean = new AnyToString<>();
	public static final AnyToString<Integer> StringFromInteger = new AnyToString<>();
	public static final AnyToString<Long> StringFromLong = new AnyToString<>();
	public static final AnyToString<Short> StringFromShort = new AnyToString<>();
	public static final AnyToString<Byte> StringFromByte = new AnyToString<>();
	public static final AnyToString<Double> StringFromDouble = new AnyToString<>();
	public static final AnyToString<Float> StringFromFloat = new AnyToString<>();
	public static final AnyToString<Character> StringFromChar = new AnyToString<>();
	
	protected static final Map<Class<?>, SimpleParseString<?>> StringFroms;
	
	public static SimpleParseString<?> StringFrom(Class<?> cls) {
		return StringFroms.get(cls);
	}
	
	static {
		Map<Class<?>, SimpleParseString<?>> _StringFroms = new HashMap<>();
		_StringFroms.put(String.class, StringFromString);
		_StringFroms.put(Boolean.class, StringFromBoolean);
		_StringFroms.put(Integer.class, StringFromInteger);
		_StringFroms.put(Long.class, StringFromLong);
		_StringFroms.put(Short.class, StringFromShort);
		_StringFroms.put(Byte.class, StringFromByte);
		_StringFroms.put(Double.class, StringFromDouble);
		_StringFroms.put(Float.class, StringFromFloat);
		_StringFroms.put(Character.class, StringFromChar);
		_StringFroms.put(Boolean.TYPE, StringFromBoolean);
		_StringFroms.put(Integer.TYPE, StringFromInteger);
		_StringFroms.put(Long.TYPE, StringFromLong);
		_StringFroms.put(Short.TYPE, StringFromShort);
		_StringFroms.put(Byte.TYPE, StringFromByte);
		_StringFroms.put(Double.TYPE, StringFromDouble);
		_StringFroms.put(Float.TYPE, StringFromFloat);
		_StringFroms.put(Character.TYPE, StringFromChar);
		
		StringFroms = _StringFroms;
	}
	
	/**
	 * String parser to an object
	 */
	public static interface IStringParse<To> extends IParse<String, To> {
		// No additional methods
	}
	
	/**
	 * Simple implementation of StringParse which consolidates conversion to a single implementation
	 */
	public abstract static class SimpleStringParse<To> implements IStringParse<To> {
		
		@Override
		public final To parseOrDefault(String From, To Default) {
			To Ret = Default;
			
			try {
				Ret = parseOrFail(From);
			} catch (Exception e) {
				if (CLog.isLoggable(Level.FINE)) {
					CLog.logExcept(e, ERROR_PARSE_FALLBACK, From);
				}
			}
			
			return Ret;
		}
		
	}
	
	public static class StringToString extends SimpleStringParse<String> {
		
		@Override
		public String parseOrFail(String From) {
			if (From == null) {
				Misc.FAIL(NullPointerException.class, ERROR_NULL_POINTER);
				// PERF: code analysis tool doesn't recognize custom throw functions
				throw new IllegalStateException(Misc.MSG_SHOULD_NOT_REACH);
			}
			
			if (From.length() > 1) {
				if ((From.charAt(0) == '"') && (From.charAt(From.length() - 1) == '"'))
					return From.substring(1, From.length() - 1).replaceAll("\"\"", "\"");
			}
			return From;
		}
		
	}
	
	public static class StringToBoolean extends SimpleStringParse<Boolean> {
		
		@Override
		public Boolean parseOrFail(String From) {
			if (From == null) {
				Misc.FAIL(NullPointerException.class, ERROR_NULL_POINTER);
			}
			
			return Boolean.parseBoolean(From);
		}
		
	}
	
	public static class StringToInteger extends SimpleStringParse<Integer> {
		
		@Override
		public Integer parseOrFail(String From) {
			if (From == null) {
				Misc.FAIL(NullPointerException.class, ERROR_NULL_POINTER);
			}
			
			return Integer.parseInt(From);
		}
		
	}
	
	public static class StringToLong extends SimpleStringParse<Long> {
		
		@Override
		public Long parseOrFail(String From) {
			if (From == null) {
				Misc.FAIL(NullPointerException.class, ERROR_NULL_POINTER);
			}
			
			return Long.parseLong(From);
		}
		
	}
	
	public static class StringToShort extends SimpleStringParse<Short> {
		
		@Override
		public Short parseOrFail(String From) {
			if (From == null) {
				Misc.FAIL(NullPointerException.class, ERROR_NULL_POINTER);
			}
			
			return Short.parseShort(From);
		}
		
	}
	
	public static class StringToByte extends SimpleStringParse<Byte> {
		
		@Override
		public Byte parseOrFail(String From) {
			if (From == null) {
				Misc.FAIL(NullPointerException.class, ERROR_NULL_POINTER);
			}
			
			return Byte.parseByte(From);
		}
		
	}
	
	public static class StringToDouble extends SimpleStringParse<Double> {
		
		@Override
		public Double parseOrFail(String From) {
			if (From == null) {
				Misc.FAIL(NullPointerException.class, ERROR_NULL_POINTER);
			}
			
			return Double.parseDouble(From);
		}
		
	}
	
	public static class StringToFloat extends SimpleStringParse<Float> {
		
		@Override
		public Float parseOrFail(String From) {
			if (From == null) {
				Misc.FAIL(NullPointerException.class, ERROR_NULL_POINTER);
			}
			
			return Float.parseFloat(From);
		}
		
	}
	
	public static class StringToChar extends SimpleStringParse<Character> {
		
		@Override
		public Character parseOrFail(String From) {
			if (From == null) {
				Misc.FAIL(NullPointerException.class, ERROR_NULL_POINTER);
				// PERF: code analysis tool doesn't recognize custom throw functions
				throw new IllegalStateException(Misc.MSG_SHOULD_NOT_REACH);
			}
			if (From.length() != 1) {
				Misc.FAIL(IllegalArgumentException.class, From);
			}
			
			return From.charAt(0);
		}
		
	}
	
	public static final StringToString StringToString = new StringToString();
	public static final StringToBoolean StringToBoolean = new StringToBoolean();
	public static final StringToInteger StringToInteger = new StringToInteger();
	public static final StringToLong StringToLong = new StringToLong();
	public static final StringToShort StringToShort = new StringToShort();
	public static final StringToByte StringToByte = new StringToByte();
	public static final StringToDouble StringToDouble = new StringToDouble();
	public static final StringToFloat StringToFloat = new StringToFloat();
	public static final StringToChar StringToChar = new StringToChar();
	
	protected static final Map<Class<?>, SimpleStringParse<?>> StringTos;
	
	public static SimpleStringParse<?> StringTo(Class<?> cls) {
		return StringTos.get(cls);
	}
	
	static {
		Map<Class<?>, SimpleStringParse<?>> _StringTos = new HashMap<>();
		_StringTos.put(String.class, StringToString);
		_StringTos.put(Boolean.class, StringToBoolean);
		_StringTos.put(Integer.class, StringToInteger);
		_StringTos.put(Long.class, StringToLong);
		_StringTos.put(Short.class, StringToShort);
		_StringTos.put(Byte.class, StringToByte);
		_StringTos.put(Double.class, StringToDouble);
		_StringTos.put(Float.class, StringToFloat);
		_StringTos.put(Character.class, StringToChar);
		_StringTos.put(Boolean.TYPE, StringToBoolean);
		_StringTos.put(Integer.TYPE, StringToInteger);
		_StringTos.put(Long.TYPE, StringToLong);
		_StringTos.put(Short.TYPE, StringToShort);
		_StringTos.put(Byte.TYPE, StringToByte);
		_StringTos.put(Double.TYPE, StringToDouble);
		_StringTos.put(Float.TYPE, StringToFloat);
		_StringTos.put(Character.TYPE, StringToChar);
		
		StringTos = _StringTos;
	}
	
}