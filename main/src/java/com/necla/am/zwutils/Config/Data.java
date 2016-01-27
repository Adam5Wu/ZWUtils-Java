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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.i18n.Messages;


/**
 * Configuration data abstract classes
 *
 * @author Zhenyu Wu
 * @version 0.1 - Sep. 2012: Initial Implementation
 * @version ...
 * @version 0.25 - Oct. 2012: Major Restructure
 * @version 0.3 - Nov. 2012: Minor functionality addition
 * @version 0.35 - Dec. 2015: Adopt resource bundle based localization
 * @version 0.35 - Jan. 20 2016: Initial public release
 */
public class Data {
	
	/**
	 * Mutable copy of the configuration
	 */
	public static class Mutable {
		
		protected final IGroupLogger ILog;
		
		// Declare mutable configurable fields (public)
		// Declare automatic populated fields (protected)
		
		/**
		 * Dummy constructor
		 *
		 * @note This constructor should NOT be overriden, or called directly, it is just a reflection
		 *       based object construction stub for internal generic function use
		 */
		protected Mutable() {
			// This field will be filled by reflection
			ILog = null;
		}
		
		/**
		 * Create a default configuration
		 *
		 * @note This constructor will NOT be called directly, it is just an illustration of how this
		 *       type of construction will be performed. <br>
		 *       To implemented the relevant functionality, override the invoked method.
		 */
		private Mutable(IGroupLogger Logger) {
			ILog = Logger;
			loadDefaults();
		}
		
		/**
		 * Set configurable fields to default values
		 */
		public void loadDefaults() {
			Misc.FAIL(UnsupportedOperationException.class, Messages.Localize("Config.Data.UNIMPL_FUNC")); //$NON-NLS-1$
		}
		
		/**
		 * Load configurations from a specified data map
		 *
		 * @note This constructor will NOT be called directly, it is just an illustration of how this
		 *       type of construction will be performed. <br>
		 *       To implemented the relevant functionality, override the invoked method.
		 */
		@SuppressWarnings("unused")
		private Mutable(IGroupLogger Logger, DataMap confMap) {
			this(Logger);
			ILog.Config(Messages.Localize("Config.Data.LOADING")); //$NON-NLS-1$
			loadFields(confMap);
			ILog.Config(Messages.Localize("Config.Data.LOADED")); //$NON-NLS-1$
		}
		
		/**
		 * Load configurable fields from a given data map
		 */
		public void loadFields(DataMap confMap) {
			Misc.FAIL(UnsupportedOperationException.class, Messages.Localize("Config.Data.UNIMPL_FUNC")); //$NON-NLS-1$
		}
		
		/**
		 * Internal configuration field validation interface
		 */
		protected interface Validation {
			/**
			 * Validate configurable field values
			 *
			 * @throws Throwable
			 */
			void validateFields() throws Throwable;
		}
		
		/**
		 * Assign to allow integrated configuration validation
		 */
		protected Validation needValidation() {
			return null;
		}
		
		/**
		 * Internal configuration field population interface
		 */
		protected interface Population {
			/**
			 * Fill the automatic populated fields
			 */
			void populateFields();
		}
		
		/**
		 * Assign to allow integrated configuration population
		 */
		protected Population needPopulation() {
			return null;
		}
		
		/**
		 * Create a mutable duplicate of a configuration
		 *
		 * @param Source
		 * @note This constructor will NOT be called directly, it is just an illustration of how this
		 *       type of construction will be performed. <br>
		 *       To implemented the relevant functionality, override the invoked method.
		 */
		@SuppressWarnings("unused")
		private Mutable(IGroupLogger Logger, Mutable Source) {
			ILog = Logger;
			copyFields(Source);
		}
		
		/**
		 * Copy all fields from a mutable source
		 *
		 * @param Source
		 */
		public void copyFields(Mutable Source) {
			Misc.FAIL(UnsupportedOperationException.class, Messages.Localize("Config.Data.UNIMPL_FUNC")); //$NON-NLS-1$
		}
		
		/**
		 * Create a mutable copy of a read-only configuration
		 *
		 * @param Source
		 * @note This constructor will NOT be called directly, it is just an illustration of how this
		 *       type of construction will be performed. <br>
		 *       To implemented the relevant functionality, override the invoked method.
		 */
		@SuppressWarnings("unused")
		private Mutable(IGroupLogger Logger, ReadOnly Source) {
			ILog = Logger;
			copyFields(Source);
		}
		
		/**
		 * Copy all fields from a read-only source
		 *
		 * @param Source
		 */
		public void copyFields(ReadOnly Source) {
			Misc.FAIL(UnsupportedOperationException.class, Messages.Localize("Config.Data.UNIMPL_FUNC")); //$NON-NLS-1$
		}
		
		/**
		 * Fully validate and populate configuration fields
		 */
		protected final void Sanitize() throws Throwable {
			Validation Validation = needValidation();
			if (Validation != null) {
				ILog.Config(Messages.Localize("Config.Data.VALIDATING")); //$NON-NLS-1$
				Validation.validateFields();
			}
			Population Population = needPopulation();
			if (Population != null) {
				if (Validation != null) {
					ILog.Config("*@<"); //$NON-NLS-1$
				}
				ILog.Config(Messages.Localize("Config.Data.POPULATE")); //$NON-NLS-1$
				Population.populateFields();
			}
			if ((Validation != null) || (Population != null)) {
				ILog.Config(Messages.Localize("Config.Data.VALIDATED")); //$NON-NLS-1$
			}
		}
		
	}
	
	/**
	 * Read-only copy of the configuration
	 */
	public static class ReadOnly {
		
		// Declare read-only configurable fields (public)
		// Declare read-only automatic populated fields (public)
		
		protected final IGroupLogger ILog;
		
		private ReadOnly(IGroupLogger Logger) {
			ILog = Logger;
		}
		
		/**
		 * Create the read-only configuration based on a mutable source
		 *
		 * @param Source
		 * @note This constructor assumes the source has been fully validated and populated
		 * @note The reflection based configuration construction will ONLY invoke the two declared
		 *       constructors.<br>
		 *       To implemented the relevant functionality, override this method.
		 */
		protected ReadOnly(IGroupLogger Logger, Mutable Source) {
			this(Logger);
			// Copy all fields from Source
		}
		
		/**
		 * Create a read-only duplicate of a configuration
		 *
		 * @param Source
		 * @note The reflection based configuration construction will ONLY invoke the two declared
		 *       constructors.<br>
		 *       To implemented the relevant functionality, override this method.
		 */
		protected ReadOnly(IGroupLogger Logger, ReadOnly Source) {
			this(Logger);
			// Copy all fields from Source
		}
		
		/**
		 * Save configurations to a data map
		 */
		public final DataMap save() {
			ILog.Config(Messages.Localize("Config.Data.SAVING")); //$NON-NLS-1$
			DataMap confMap = new DataMap(ILog.GroupName());
			putFields(confMap);
			ILog.Config(Messages.Localize("Config.Data.SAVED")); //$NON-NLS-1$
			return confMap;
		}
		
		/**
		 * Assign configurable fields into a given data map
		 */
		protected void putFields(DataMap confMap) {
			Misc.FAIL(UnsupportedOperationException.class, Messages.Localize("Config.Data.UNIMPL_FUNC")); //$NON-NLS-1$
		}
		
	}
	
	// ======= Static Service Functions ======= //
	
	/**
	 * Template creation of a mutable configuration instance using default values
	 *
	 * @return A mutable configuration instance with all default values
	 * @since 0.3
	 */
	public static <M extends Mutable> M defaults(Class<M> MClass, IGroupLogger Logger) {
		M Ret = null;
		try {
			Ret = MClass.newInstance();
			Field LogField = Mutable.class.getDeclaredField("ILog"); //$NON-NLS-1$
			LogField.setAccessible(true);
			LogField.set(Ret, Logger);
			Method LoadDefaults = Mutable.class.getDeclaredMethod("loadDefaults"); //$NON-NLS-1$
			LoadDefaults.invoke(Ret);
		} catch (Throwable e) {
			if (e instanceof InvocationTargetException)
				e = ((InvocationTargetException) e).getTargetException();
			Misc.CascadeThrow(e);
		}
		return Ret;
	}
	
	/**
	 * Template loading of a mutable configuration instance from given file
	 *
	 * @return A mutable configuration instance loaded from file
	 * @since 0.25
	 */
	public static <M extends Mutable> M load(Class<M> MClass, DataMap confMap, IGroupLogger Logger) {
		M Ret = defaults(MClass, Logger);
		Logger.Config(Messages.Localize("Config.Data.LOADING")); //$NON-NLS-1$
		try {
			Method LoadFields = Mutable.class.getDeclaredMethod("loadFields", DataMap.class); //$NON-NLS-1$
			LoadFields.invoke(Ret, confMap);
		} catch (Throwable e) {
			if (e instanceof InvocationTargetException)
				e = ((InvocationTargetException) e).getTargetException();
			Misc.CascadeThrow(e);
		}
		Logger.Config("*@<"); //$NON-NLS-1$
		return Ret;
	}
	
	/**
	 * Template duplicate of a mutable configuration instance
	 *
	 * @param Source
	 * @return A duplicated instance of given configuration
	 * @since 0.2
	 */
	@SuppressWarnings("unchecked")
	public static <M extends Mutable> M mirror(M Source, IGroupLogger Logger) {
		Class<M> MClass = (Class<M>) Source.getClass();
		M Ret = null;
		try {
			Ret = MClass.newInstance();
			Field LogField = Mutable.class.getDeclaredField("ILog"); //$NON-NLS-1$
			LogField.setAccessible(true);
			LogField.set(Ret, Logger);
			Method CopyFields = Mutable.class.getDeclaredMethod("copyFields", Mutable.class); //$NON-NLS-1$
			CopyFields.invoke(Ret, Source);
		} catch (Throwable e) {
			if (e instanceof InvocationTargetException)
				e = ((InvocationTargetException) e).getTargetException();
			Misc.CascadeThrow(e);
		}
		return Ret;
	}
	
	/**
	 * Template copy of a read-only configuration from a mutable instance
	 *
	 * @param Source
	 * @param RClass
	 *          - the ReadOnly configuration class
	 * @return A duplicated instance of given ReadOnly configuration class, created from a sanitized
	 *         mutable configuration
	 * @since 0.2
	 */
	public static <R extends ReadOnly, M extends Mutable> R reflect(M Source, Class<R> RClass,
			IGroupLogger Logger) throws Throwable {
		R Ret = null;
		Source.Sanitize();
		try {
			Ret = RClass.getDeclaredConstructor(IGroupLogger.class, Source.getClass()).newInstance(Logger,
					Source);
		} catch (Throwable e) {
			if (e instanceof InvocationTargetException)
				e = ((InvocationTargetException) e).getTargetException();
			Misc.CascadeThrow(e);
		}
		return Ret;
	}
	
	/**
	 * Template copy of a mutable configuration from a read-only instance
	 *
	 * @param Source
	 * @param MClass
	 *          - the Mutable configuration class
	 * @return A duplicated instance of given Mutable configuration class, created from a read-only
	 *         configuration
	 * @since 0.2
	 */
	public static <M extends Mutable, R extends ReadOnly> M mirror(R Source, Class<M> MClass,
			IGroupLogger Logger) {
		M Ret = null;
		try {
			Ret = MClass.newInstance();
			Field LogField = Mutable.class.getDeclaredField("ILog"); //$NON-NLS-1$
			LogField.setAccessible(true);
			LogField.set(Ret, Logger);
			Method CopyFields = Mutable.class.getDeclaredMethod("copyFields", ReadOnly.class); //$NON-NLS-1$
			CopyFields.invoke(Ret, Source);
		} catch (Throwable e) {
			if (e instanceof InvocationTargetException)
				e = ((InvocationTargetException) e).getTargetException();
			Misc.CascadeThrow(e);
		}
		return Ret;
	}
	
	/**
	 * Template duplicate of a read-only configuration instance
	 *
	 * @param Source
	 * @return A duplicated instance of given configuration
	 * @since 0.2
	 */
	@SuppressWarnings("unchecked")
	public static <R extends ReadOnly> R reflect(R Source, IGroupLogger Logger) {
		Class<R> RClass = (Class<R>) Source.getClass();
		R Ret = null;
		try {
			Ret = RClass.getDeclaredConstructor(IGroupLogger.class, RClass).newInstance(Logger, Source);
		} catch (Throwable e) {
			if (e instanceof InvocationTargetException)
				e = ((InvocationTargetException) e).getTargetException();
			Misc.CascadeThrow(e);
		}
		return Ret;
	}
	
}
