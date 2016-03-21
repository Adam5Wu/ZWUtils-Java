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
import java.io.IOException;
import java.util.Map;

import com.necla.am.zwutils.Subscriptions.Dispatchers;


/**
 * Dynamic Configuration Support
 *
 * @author Zhenyu Wu
 * @version 0.1 - Sep. 2012: Initial implementation
 * @version 0.2 - Oct. 2012: Major generic revision
 * @version 0.22 - Oct. 2012: Functionality expansion
 * @version 0.25 - Nov. 2012: Functionality expansion
 * @version 0.3 - Nov. 2012: Major restructure
 * @version 0.35 - Dec. 2012: Minor functionality expansion
 * @version 0.4 - Dec. 2012: Data loading unification
 * @version 0.45 - Dec. 2015: Adopt resource bundle based localization
 * @version 0.45 - Jan. 20 2016: Initial public release
 */
public class Container<M extends Data.Mutable, R extends Data.ReadOnly>
		extends Dispatchers.Dispatcher<R> {
	
	protected final Class<? extends M> MCLASS;
	protected final Class<? extends R> RCLASS;
	
	private Container(Class<? extends M> MClass, Class<? extends R> RClass, String Name) {
		super(Name);
		
		MCLASS = MClass;
		RCLASS = RClass;
	}
	
	/**
	 * Create and load configuration
	 *
	 * @param confMap
	 *          - Configuration data
	 * @throws Throwable
	 *           - If configuration data failed to validate
	 */
	public Container(Class<M> MClass, Class<R> RClass, String Name, DataMap confMap)
			throws Throwable {
		this(MClass, RClass, Name);
		
		load(confMap);
	}
	
	public static <M extends Data.Mutable, R extends Data.ReadOnly> Container<M, R>
			Create(Class<M> MClass, Class<R> RClass, String Name, File confFile, String Prefix)
					throws Throwable {
		return Create(MClass, RClass, Name, new DataFile(Name, confFile.getPath()), Prefix);
	}
	
	public static <M extends Data.Mutable, R extends Data.ReadOnly> Container<M, R>
			Create(Class<M> MClass, Class<R> RClass, String Name, DataFile confFile, String Prefix)
					throws Throwable {
		return new Container<>(MClass, RClass, Name, new DataMap(Name, confFile, Prefix));
	}
	
	public static <M extends Data.Mutable, R extends Data.ReadOnly> Container<M, R>
			Create(Class<M> MClass, Class<R> RClass, String Name, String confStr, String Prefix)
					throws Throwable {
		return new Container<>(MClass, RClass, Name, new DataMap(Name, confStr, Prefix));
	}
	
	public static <M extends Data.Mutable, R extends Data.ReadOnly> Container<M, R>
			Create(Class<M> MClass, Class<R> RClass, String Name, String[] confArgs, String Prefix)
					throws Throwable {
		return new Container<>(MClass, RClass, Name, new DataMap(Name, confArgs, Prefix));
	}
	
	public static <M extends Data.Mutable, R extends Data.ReadOnly> Container<M, R> Create(
			Class<M> MClass, Class<R> RClass, String Name, Map<String, String> confMap, String Prefix)
			throws Throwable {
		return new Container<>(MClass, RClass, Name, new DataMap(Name, confMap, Prefix));
	}
	
	/**
	 * Create configuration from given read-only configuration data
	 *
	 * @throws Throwable
	 *           - If read-only configuration failed to validate as mutable
	 * @since 0.22
	 */
	@SuppressWarnings("unchecked")
	public Container(Class<M> MClass, R Config, String Name) throws Throwable {
		this(MClass, (Class<? extends R>) Config.getClass(), Name);
		set(Config);
	}
	
	public static <M extends Data.Mutable, R extends Data.ReadOnly> Container<M, R>
			CreateFromReadOnly(Class<M> MClass, R Config, String Name) throws Throwable {
		return new Container<>(MClass, Config, Name);
	}
	
	/**
	 * Create configuration from given mutable configuration data
	 *
	 * @throws Throwable
	 *           - If mutable configuration failed to validate
	 * @since 0.22
	 */
	@SuppressWarnings("unchecked")
	public Container(M Config, Class<R> RClass, String Name) throws Throwable {
		this((Class<? extends M>) Config.getClass(), RClass, Name);
		set(Config);
	}
	
	public static <M extends Data.Mutable, R extends Data.ReadOnly> Container<M, R>
			CreateFromMutable(M Config, Class<R> RClass, String Name) throws Throwable {
		return new Container<>(Config, RClass, Name);
	}
	
	/**
	 * Load configurations
	 *
	 * @param confMap
	 *          - Configuration data
	 * @since 0.4
	 */
	public final void load(DataMap confMap) throws Throwable {
		M Source = Data.load(MCLASS, confMap, ILog);
		SetPayload(Data.reflect(Source, RCLASS, ILog));
	}
	
	/**
	 * Save configurations
	 *
	 * @since 0.4
	 */
	public final DataMap save() {
		R Payload = CommonSubscriptions.LockGetPayload();
		try {
			return Payload.save();
		} finally {
			CommonSubscriptions.UnlockPayload();
		}
	}
	
	public static <M extends Data.Mutable, R extends Data.ReadOnly> void SaveToFile(
			Container<M, R> Container, String Prefix, String Name, String FileName, String Comments)
			throws IOException {
		DataMap confMap = Container.save();
		DataFile ConfigFile = new DataFile(Name, FileName);
		confMap.DumpToFile(ConfigFile, Prefix);
		ConfigFile.saveAs(FileName, Comments);
	}
	
	public static <M extends Data.Mutable, R extends Data.ReadOnly> String
			SaveToString(Container<M, R> Container, String Prefix) {
		DataMap confMap = Container.save();
		return confMap.DumpToString(Prefix);
	}
	
	public static <M extends Data.Mutable, R extends Data.ReadOnly> String[]
			SaveToArgs(Container<M, R> Container, String Prefix) {
		DataMap confMap = Container.save();
		return confMap.DumpToArgs(Prefix);
	}
	
	public static <M extends Data.Mutable, R extends Data.ReadOnly> Map<String, String>
			SaveToMap(Container<M, R> Container, String Prefix) {
		DataMap confMap = Container.save();
		return confMap.DumpToMap(Prefix);
	}
	
	/**
	 * Create a read-only copy of the configuration
	 *
	 * @return ReadOnly configuration
	 */
	public final R reflect() {
		// Note: Technically it should be done this way ...
		// R Payload = CommonSubscriptions.TellPayload();
		// return Data.reflect(Payload, Log);
		
		// ... but assume all fields in the read-only configuration is really immutable,
		// then it is not necessary to create a separate copy :)
		return CommonSubscriptions.TellPayload();
	}
	
	/**
	 * Create a mutable copy of the configuration
	 *
	 * @return Mutable configuration
	 */
	public final M mirror() {
		R Payload = CommonSubscriptions.TellPayload();
		return Data.mirror(Payload, MCLASS, ILog);
	}
	
	/**
	 * Modify the current configuration
	 * <p>
	 * We don't trust the externally supplied read-only configuration, since it may not be generated
	 * from a validated mutable, so we do a round-trip conversion to re-validate its content
	 *
	 * @param Source
	 *          - ReadOnly configuration
	 */
	public final void set(R Source) throws Throwable {
		SetPayload(Data.reflect(Data.mirror(Source, MCLASS, ILog), RCLASS, ILog));
	}
	
	/**
	 * Modify the current configuration
	 *
	 * @param Source
	 *          - Mutable configuration
	 */
	public final void set(M Source) throws Throwable {
		SetPayload(Data.reflect(Source, RCLASS, ILog));
	}
	
}
