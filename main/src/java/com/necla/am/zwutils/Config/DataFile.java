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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.logging.Level;

import com.necla.am.zwutils.GlobalConfig;
import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.Modeling.ITimeStamp;
import com.necla.am.zwutils.i18n.Messages;


/**
 * Configuration loader/saver
 *
 * @author Zhenyu Wu
 * @version 0.1 - Sep. 2012: Initial Implementation
 * @version ...
 * @version 0.25 - Oct. 2012: Minor Revision
 * @version 0.3 - Dec. 2015: Adopt resource bundle based localization
 * @version 0.3 - Jan. 20 2016: Initial public release
 */
public class DataFile extends Properties {
	
	private static final long serialVersionUID = 0L;
	
	/**
	 * Generate configuration file from caller class name
	 * 
	 * @return Configuration file
	 */
	public static File DeriveConfigFile() {
		return DeriveConfigFile("", 1);
	}
	
	/**
	 * Generate configuration file from caller class name, with optional prefix
	 *
	 * @param Prefix
	 *          - Prefix to use
	 * @return Configuration file
	 * @since 0.25
	 */
	public static File DeriveConfigFile(String Prefix) {
		return DeriveConfigFile(Prefix, 1);
	}
	
	/**
	 * Generate configuration file from caller class name, with optional prefix
	 *
	 * @param Prefix
	 *          - Prefix to use
	 * @return Configuration file
	 * @since 0.25
	 */
	public static File DeriveConfigFile(String Prefix, int PopFrame) {
		StackTraceElement CallerFrame = Misc.getCallerStackFrame(PopFrame + 1);
		String ClassName = CallerFrame.getClassName();
		int SubClassIdx = ClassName.indexOf('$');
		if (SubClassIdx > 0) {
			ClassName = ClassName.substring(0, SubClassIdx);
		}
		return new File(Prefix + Misc.stripPackageName(ClassName) + ".properties"); //$NON-NLS-1$
	}
	
	protected final IGroupLogger ILog;
	protected ITimeStamp lastModified;
	
	public DataFile(String Name) {
		super();
		ILog = new GroupLogger.PerInst(Name + '.' + getClass().getSimpleName());
		lastModified = ITimeStamp.Impl.Now();
	}
	
	public DataFile(String Name, String INIFileName) {
		this(Name, INIFileName, null, null, null);
	}
	
	public DataFile(String Name, String INIFileName, String FSDir, String JarDir) {
		this(Name, INIFileName, FSDir, JarDir, null);
	}
	
	public DataFile(String Name, String INIFileName, ClassLoader Loader) {
		this(Name, INIFileName, null, null, Loader);
	}
	
	public DataFile(String Name, String INIFileName, String FSDir, String JarDir,
			ClassLoader Loader) {
		this(Name);
		
		try {
			if (FSDir == null) {
				FSDir = GlobalConfig.CONFFILE_FSDIR;
			}
			if (JarDir == null) {
				JarDir = GlobalConfig.CONFFILE_JARDIR;
			}
			
			InputStream Conf = null;
			try {
				ILog.Finer(Messages.Localize("Config.DataFile.CHECK_CURDIR")); //$NON-NLS-1$
				
				boolean ConfigRelAppRoot = INIFileName.startsWith("?");
				
				// First probe FSDir+INIFileName (local file override)
				String ConfigFileName =
						ConfigRelAppRoot? INIFileName.substring(1) : Misc.appendPathName(FSDir, INIFileName);
				File ConfigFile = new File(ConfigFileName);
				if (ConfigFile.exists() && ConfigFile.canRead()) {
					INIFileName = ConfigFile.getPath();
					lastModified = new ITimeStamp.Impl(ConfigFile.lastModified());
					Conf = new FileInputStream(ConfigFile);
				} else {
					// If not exist, probe within Jar bundle
					ConfigFileName =
							ConfigRelAppRoot? INIFileName.substring(1) : Misc.appendPathName(JarDir, INIFileName);
					String ResourceName = Misc.appendPathName(JarDir, INIFileName);
					if (Misc.PATH_DELIMITER != File.separatorChar) {
						ResourceName = ResourceName.replace(File.separatorChar, Misc.PATH_DELIMITER);
					}
					if (Loader != null) {
						Conf = Loader.getResourceAsStream(ResourceName);
					}
					
					if (Conf == null) {
						Conf = getClass()
								.getResourceAsStream(Misc.appendPathName(Misc.PATH_DELIMITER_STR, ResourceName));
					}
				}
				
				if (Conf != null) {
					load(Conf);
					ILog.Fine(Messages.Localize("Config.DataFile.OPEN_FILE"), INIFileName, //$NON-NLS-1$
							(ConfigFile != null? "file" : "resource")); //$NON-NLS-1$ //$NON-NLS-2$
				} else {
					if (ILog.isLoggable(Level.CONFIG)) {
						ILog.Warn(Messages.Localize("Config.DataFile.OPEN_FILE_WARN"), INIFileName); //$NON-NLS-1$
					}
					lastModified = new ITimeStamp.Impl(0);
				}
			} finally {
				if (Conf != null) {
					Conf.close();
				}
			}
		} catch (Throwable e) {
			if (ILog.isLoggable(Level.FINE)) {
				ILog.logExcept(e, Messages.Localize("Config.DataFile.OPEN_FILE_FAIL"), INIFileName); //$NON-NLS-1$
			} else if (ILog.isLoggable(Level.CONFIG)) {
				ILog.Warn(Messages.Localize("Config.DataFile.OPEN_FILE_FAIL_LT"), INIFileName, e); //$NON-NLS-1$
			}
		}
	}
	
	public DataFile(String Name, InputStream INIData) {
		this(Name);
		
		try {
			load(INIData);
			ILog.Fine(Messages.Localize("Config.DataFile.LOADED_STREAM")); //$NON-NLS-1$
		} catch (Throwable e) {
			if (ILog.isLoggable(Level.FINE)) {
				ILog.logExcept(e, Messages.Localize("Config.DataFile.LOAD_STREAM_FAIL")); //$NON-NLS-1$
			} else if (ILog.isLoggable(Level.CONFIG)) {
				ILog.Warn(Messages.Localize("Config.DataFile.LOAD_STREAM_FAIL_LT"), e); //$NON-NLS-1$
			}
		}
	}
	
	public ITimeStamp lastModified() {
		return lastModified;
	}
	
	public String getName() {
		return ILog.GroupName();
	}
	
	public void saveAs(String INIFileName, String Comments) throws IOException {
		boolean ConfigRelAppRoot = INIFileName.startsWith("?");
		String ConfigFileName = ConfigRelAppRoot? INIFileName.substring(1) : Misc
				.appendPathName(GlobalConfig.CONFFILE_FSDIR, INIFileName);
		File FSConf = new File(ConfigFileName);
		
		// Make sure configuration file have proper parent directory
		if (FSConf.getParentFile().mkdirs()) {
			ILog.Fine(Messages.Localize("Config.DataFile.SAVE_FILE_MKDIR"), //$NON-NLS-1$
					Misc.stripFileName(INIFileName));
		}
		if (FSConf.createNewFile()) {
			ILog.Fine(Messages.Localize("Config.DataFile.SAVE_FILE_NEW"), //$NON-NLS-1$
					Misc.stripPathName(INIFileName));
		}
		store(new FileOutputStream(FSConf), Comments);
		ILog.Fine(Messages.Localize("Config.DataFile.SAVE_FILE"), Misc.stripPathName(INIFileName)); //$NON-NLS-1$
	}
	
	public void saveAs(OutputStream INIData, String Comments) throws IOException {
		store(INIData, Comments);
		ILog.Fine(Messages.Localize("Config.DataFile.SAVE_STREAM")); //$NON-NLS-1$
	}
	
}
