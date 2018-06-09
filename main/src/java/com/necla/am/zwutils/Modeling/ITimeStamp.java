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

package com.necla.am.zwutils.Modeling;

import java.text.DateFormat;

import com.necla.am.zwutils.GlobalConfig;
import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.Misc.Misc.TimeSystem;
import com.necla.am.zwutils.Misc.Misc.TimeUnit;


/**
 * Timestamp Identifier
 * <p>
 * Handles time-stamps of any defined time system and unit.<br>
 * Capability:
 * <ul>
 * <li>Containment
 * <li>Conversion
 * <li>Computation
 * <li>String formatting
 * </ul>
 *
 * @note Internally the time is kept in UNIX.MSec format
 * @author Zhenyu Wu
 * @version 0.1 - Oct. 2014: Initial implementation
 * @version 0.3 - Jan. 20 2016: Initial public release
 */
public interface ITimeStamp extends IIdentifier, Comparable<ITimeStamp> {
	
	/**
	 * Obtain an integer value of the time-stamp in a specified time system and unit
	 */
	long VALUE(TimeSystem timeSystem, TimeUnit timeUnit);
	
	/**
	 * Short form of VALUE(TimeSystem.UNIX, TimeUnit.MSEC)
	 */
	long UNIXMS();
	
	/**
	 * Computes milliseconds time-span from a given time-stamp
	 */
	long MillisecondsFrom(ITimeStamp from);
	
	/**
	 * Computes milliseconds time-span to a given time-stamp
	 */
	long MillisecondsTo(ITimeStamp to);
	
	/**
	 * Check whether current time-stamp is before a given time-stamp
	 */
	boolean Before(ITimeStamp timestamp);
	
	/**
	 * Check whether current time-stamp is after a given time-stamp
	 */
	boolean After(ITimeStamp timestamp);
	
	/**
	 * Create a new time-stamp with given offset in milliseconds
	 */
	ITimeStamp Offset(Long deltams);
	
	/**
	 * Return the string representation of the time-stamp using given format
	 */
	String toString(DateFormat format);
	
	public static class Impl extends IIdentifier.Impl implements ITimeStamp {
		
		protected static final String LOGGROUP = "ZWUtils.Modeling.TimeStamp";
		protected static final IGroupLogger CLog = new GroupLogger(LOGGROUP);
		
		public final long _VALUE;
		
		public final TimeSystem SYSTEM;
		public final TimeUnit UNIT;
		
		public Impl(long value) {
			this(value, TimeSystem.UNIX, TimeUnit.MSEC);
		}
		
		public Impl(long value, TimeSystem timeSystem, TimeUnit timeUnit) {
			_VALUE = value;
			SYSTEM = timeSystem;
			UNIT = timeUnit;
		}
		
		@Override
		public long VALUE(TimeSystem timeSystem, TimeUnit timeUnit) {
			return SYSTEM.Convert(UNIT.Convert(_VALUE, timeUnit), timeUnit, timeSystem);
		}
		
		@Override
		public long UNIXMS() {
			return VALUE(TimeSystem.UNIX, TimeUnit.MSEC);
		}
		
		@Override
		public long MillisecondsFrom(ITimeStamp from) {
			return UNIXMS() - from.UNIXMS();
		}
		
		@Override
		public long MillisecondsTo(ITimeStamp to) {
			return to.UNIXMS() - UNIXMS();
		}
		
		@Override
		public ITimeStamp Offset(Long deltams) {
			return new ITimeStamp.Impl(UNIXMS() + deltams);
		}
		
		@Override
		public String toString() {
			try {
				return Misc.FormatTS(_VALUE, SYSTEM, UNIT);
			} catch (Exception e) {
				if (GlobalConfig.DEBUG_CHECK) {
					CLog.Warn("Failed to format timestamp (%d,%s,%s) - %s", _VALUE, SYSTEM, UNIT, e);
				}
				return "(bad-timestamp)";
			}
		}
		
		@Override
		public String toString(DateFormat format) {
			return Misc.FormatTS(_VALUE, SYSTEM, UNIT, format);
		}
		
		protected boolean _equals(ITimeStamp timestamp) {
			return Long.valueOf(UNIXMS()).equals(timestamp.UNIXMS());
		}
		
		protected final boolean _equals(ITimeStamp.Impl timestamp) {
			if ((SYSTEM == timestamp.SYSTEM) && (UNIT == timestamp.UNIT))
				return _VALUE == timestamp._VALUE;
			return _equals((ITimeStamp) timestamp);
		}
		
		@Override
		protected final boolean _equals(IIdentifier ident) {
			if (!super._equals(ident)) return false;
			if (ident instanceof ITimeStamp.Impl) return _equals((ITimeStamp.Impl) ident);
			return _equals((ITimeStamp) ident);
		}
		
		@Override
		protected int _hashCode() {
			// A faster but potentially less accurate implementation than Long.valueOf(UNIXMS()).hashCode()
			return (int) _VALUE;
		}
		
		@Override
		public boolean Before(ITimeStamp timestamp) {
			return UNIXMS() < timestamp.UNIXMS();
		}
		
		@Override
		public boolean After(ITimeStamp timestamp) {
			return UNIXMS() > timestamp.UNIXMS();
		}
		
		/**
		 * @return A time-stamp reflecting current time
		 */
		public static ITimeStamp Now() {
			return new ITimeStamp.Impl(System.currentTimeMillis());
		}
		
		@Override
		public int compareTo(ITimeStamp oTime) {
			return Before(oTime)? -1 : (After(oTime)? 1 : 0);
		}
		
	}
	
}
