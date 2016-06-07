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

package com.necla.am.zwutils;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import com.necla.am.zwutils.Config.Container;
import com.necla.am.zwutils.Config.Data;
import com.necla.am.zwutils.Config.DataFile;
import com.necla.am.zwutils.Config.DataMap;
import com.necla.am.zwutils.Logging.DebugLog;
import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.i18n.Messages;


/**
 * Project-wide Static Configurations
 *
 * @author Zhenyu Wu
 * @version 0.1 - Dec. 2010: Initial implementation
 * @version 0.2 - Aug. 2012: Revision
 * @version 0.25 - Dec. 2015: Adopt resource bundle based localization
 * @version 0.25 - Jan. 20 2016: Initial public release
 */
public class GlobalConfig {
	
	static private final String LogGroup = "ZWUtils.GlobalConfig"; //$NON-NLS-1$
	protected static final IGroupLogger CLog = new GroupLogger(LogGroup);
	
	public static class Mutable extends Data.Mutable {
		
		// Turn on additional sanity checks / assertions
		public boolean DEBUG_CHECK;
		
		// Disable all assertions
		public boolean NO_ASSERT;
		
		// Disable all logging
		public boolean DISABLE_LOG;
		
		// File system default directory for storing configuration files
		public String CONFFILE_FSDIR;
		
		// Jar bundle default directory for storing configuration files
		public String CONFFILE_JARDIR;
		
		@Override
		public void loadDefaults() {
			NO_ASSERT = false;
			DEBUG_CHECK = true;
			DISABLE_LOG = false;
			CONFFILE_FSDIR = GlobalConfig.CONFFILE_FSDIR;
			CONFFILE_JARDIR = GlobalConfig.CONFFILE_JARDIR;
		}
		
		private static final String CONFIG_NOASSERT = "NoAssert"; //$NON-NLS-1$
		private static final String CONFIG_DEBUGCHECK = "DebugCheck"; //$NON-NLS-1$
		private static final String CONFIG_DISABLELOG = "DisableLog"; //$NON-NLS-1$
		private static final String CONFIG_CONFFILE_FSDIR = "ConfigFile.FSDir"; //$NON-NLS-1$
		private static final String CONFIG_CONFFILE_JARDIR = "ConfigFile.JarDir"; //$NON-NLS-1$
		
		@Override
		public void loadFields(DataMap confMap) {
			NO_ASSERT = confMap.getBoolDef(CONFIG_NOASSERT, NO_ASSERT);
			DEBUG_CHECK = confMap.getBoolDef(CONFIG_DEBUGCHECK, DEBUG_CHECK);
			DISABLE_LOG = confMap.getBoolDef(CONFIG_DISABLELOG, DISABLE_LOG);
			CONFFILE_FSDIR = confMap.getTextDef(CONFIG_CONFFILE_FSDIR, CONFFILE_FSDIR);
			CONFFILE_JARDIR = confMap.getTextDef(CONFIG_CONFFILE_JARDIR, CONFFILE_JARDIR);
		}
		
	}
	
	public static class ReadOnly extends Data.ReadOnly {
		
		public final boolean DEBUG_CHECK;
		public final boolean NO_ASSERT;
		public final boolean DISABLE_LOG;
		public final String CONFFILE_FSDIR;
		public final String CONFFILE_JARDIR;
		
		public ReadOnly(IGroupLogger Logger, Mutable Source) {
			super(Logger, Source);
			
			// Copy all fields from Source
			NO_ASSERT = Source.NO_ASSERT;
			DEBUG_CHECK = Source.DEBUG_CHECK;
			DISABLE_LOG = Source.DISABLE_LOG;
			CONFFILE_FSDIR = Source.CONFFILE_FSDIR;
			CONFFILE_JARDIR = Source.CONFFILE_JARDIR;
		}
		
	}
	
	public static final File ConfigFile = DataFile.DeriveConfigFile("ZWUtils."); //$NON-NLS-1$
	protected static final String ConfigKeyBase = GlobalConfig.class.getSimpleName() + '.'; // $NON-NLS-1$
	
	protected static Container<Mutable, ReadOnly> Create() throws Throwable {
		return Container.Create(Mutable.class, ReadOnly.class, LogGroup, ConfigFile, ConfigKeyBase);
	}
	
	public static final boolean NO_ASSERT;
	public static final boolean DEBUG_CHECK;
	public static final boolean DISABLE_LOG;
	
	// Boot-strapping values before real ones can load
	public static final String CONFFILE_FSDIR = "private_settings";
	public static final String CONFFILE_JARDIR = "settings";
	
	// Load constant configurations
	static {
		CLog.Entry(Messages.Localize("GlobalConfig.INIT_START")); //$NON-NLS-1$
		
		{
			ReadOnly _Config = null;
			try {
				_Config = Create().reflect();
			} catch (Throwable e) {
				DebugLog.DirectErrOut()
						.println(String.format(Messages.Localize("GlobalConfig.LOAD_CONFIG_FAIL"), //$NON-NLS-1$
								GlobalConfig.class.getSimpleName(), e.getLocalizedMessage()));
				e.printStackTrace(DebugLog.DirectErrOut());
				Misc.CascadeThrow(e);
			}
			NO_ASSERT = _Config.NO_ASSERT;
			DEBUG_CHECK = _Config.DEBUG_CHECK;
			DISABLE_LOG = _Config.DISABLE_LOG;
			
			try {
				Field _Modifiers = Field.class.getDeclaredField("modifiers");
				_Modifiers.setAccessible(true);
				
				Field _CONFFILE_FSDIR = GlobalConfig.class.getDeclaredField("CONFFILE_FSDIR");
				_CONFFILE_FSDIR.setAccessible(true);
				_Modifiers.setInt(_CONFFILE_FSDIR, _CONFFILE_FSDIR.getModifiers() & ~Modifier.FINAL);
				_CONFFILE_FSDIR.set(null, _Config.CONFFILE_FSDIR);
				
				Field _CONFFILE_JARDIR = GlobalConfig.class.getDeclaredField("CONFFILE_JARDIR");
				_CONFFILE_JARDIR.setAccessible(true);
				_Modifiers.setInt(_CONFFILE_JARDIR, _CONFFILE_JARDIR.getModifiers() & ~Modifier.FINAL);
				_CONFFILE_JARDIR.set(null, _Config.CONFFILE_JARDIR);
			} catch (Throwable e) {
				Misc.CascadeThrow(e);
			}
		}
		
		CLog.Exit(Messages.Localize("GlobalConfig.INIT_FINISH")); //$NON-NLS-1$
	}
	
}
