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

package com.necla.am.zwutils.Caching;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.i18n.Messages;


/**
 * Auto-magic Canonicalization Cache Manager
 *
 * @author Zhenyu Wu
 * @version 0.10 - Jul. 2015: Initial Implementation
 * @version ...
 * @version 0.50 - Aug. 2015: Performance and usage style improvements
 * @version 0.55 - Dec. 2015: Adopt resource bundle based localization
 * @version 0.55 - Jan. 20 2016: Initial public release
 */
public class Canonicalizer {
	
	public static final String LOGGROUP = "ZWUtils.Caching.Canonicalizer"; //$NON-NLS-1$
	protected final IGroupLogger ILog;
	
	protected static final Set<Class<?>> PClass = new HashSet<>();
	protected Map<Class<?>, Constructor<?>> CClass = new HashMap<>();
	protected Set<Class<?>> AClass = new HashSet<>();
	
	public Canonicalizer(String Name) {
		ILog = new GroupLogger.PerInst(LOGGROUP + (Name != null? '.' + Name : "")); //$NON-NLS-1$
		
		// Some objects are supported out-of-box
		Register(String.class, String.class);
	}
	
	@Inherited
	@Target(ElementType.CONSTRUCTOR)
	@Retention(RetentionPolicy.RUNTIME)
	public @interface Hint {}
	
	public void Register(Class<?> C) {
		Register(C, new HashSet<>(Collections.singletonList(C)));
	}
	
	public void Register(Class<?> C, Class<?>... CParams) {
		Constructor<?> CC = null;
		if ((CParams.length != 1) || !C.equals(CParams[0])) {
			try {
				CC = C.getConstructor(CParams);
			} catch (NoSuchMethodException | SecurityException e) {
				Misc.CascadeThrow(e);
			}
		}
		Register(C, CC, new HashSet<>(Collections.singletonList(C)));
	}
	
	protected void Register(Class<?> C, Constructor<?> CC, Set<Class<?>> Pending) {
		if (CC != null) {
			Class<?>[] CCP = CC.getParameterTypes();
			if (CCP.length == 0) {
				Misc.FAIL(IllegalArgumentException.class,
						Messages.Localize("Caching.Canonicalizer.NOAC_DEFAULTCONSTRUCT"), C.getName()); //$NON-NLS-1$
			}
			HandleParameters(Pending, CCP);
			CC.setAccessible(true);
		} else {
			if (ILog.isLoggable(Level.CONFIG)) {
				ILog.Warn(Messages.Localize("Caching.Canonicalizer.SELF_CANONICAL"), C.getName()); //$NON-NLS-1$
			}
			// Detected self-canonicalizing class
			AClass.add(C);
		}
		CClass.put(C, CC);
	}
	
	private void HandleParameters(Set<Class<?>> Pending, Class<?>[] CCP) {
		for (int PIdx = 0; PIdx < CCP.length; PIdx++) {
			Class<?> CP = CCP[PIdx];
			if (!PClass.contains(CP) && !CClass.containsKey(CP)) {
				if (CP.equals(Object.class)) {
					Misc.FAIL(IllegalStateException.class,
							Messages.Localize("Caching.Canonicalizer.NOAC_GENERICPARAM"), PIdx); //$NON-NLS-1$
				}
				
				if (Pending.contains(CP)) {
					Misc.FAIL(IllegalStateException.class,
							Messages.Localize("Caching.Canonicalizer.NOAC_TYPERECURSIVE"), PIdx, CP.getName()); //$NON-NLS-1$
				}
				
				ILog.Config(Messages.Localize("Caching.Canonicalizer.AUTOREG_PARAM"), PIdx, CP.getName()); //$NON-NLS-1$
				Pending.add(CP);
				Register(CP, Pending);
				Pending.remove(CP);
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	protected void Register(Class<?> C, Set<Class<?>> Pending) {
		if (PClass.contains(C)) {
			if (ILog.isLoggable(Level.CONFIG)) {
				ILog.Info(Messages.Localize("Caching.Canonicalizer.PRIMITIVE_TYPE"), C.getSimpleName()); //$NON-NLS-1$
			}
			return;
		}
		if (CClass.containsKey(C)) {
			if (ILog.isLoggable(Level.CONFIG)) {
				ILog.Info(Messages.Localize("Caching.Canonicalizer.KNOWN_CLASS"), C.getName()); //$NON-NLS-1$
			}
			return;
		}
		
		Constructor<?>[] CCs = C.getDeclaredConstructors();
		Object CCC = FilterConstructors(CCs);
		if (CCC == null) {
			ILog.Warn(Messages.Localize("Caching.Canonicalizer.NOAC_NOCONSTRUCT"), C.getName()); //$NON-NLS-1$
		} else if (CCC.getClass().equals(Constructor.class)) {
			RegisterSelectedConstructor(C, (Constructor<?>) CCC, Pending);
			return;
		} else {
			LogMultipleConstructors(C, (List<Constructor<?>>) CCC);
		}
		Misc.FAIL(IllegalArgumentException.class,
				Messages.Localize("Caching.Canonicalizer.NOAC_CONSTRUCT_ERROR"), C.getName(), CCs.length); //$NON-NLS-1$
	}
	
	private void LogMultipleConstructors(Class<?> C, List<Constructor<?>> CCL) {
		ILog.Warn(Messages.Localize("Caching.Canonicalizer.NOAC_MULTICONSTRUCT"), C.getName()); //$NON-NLS-1$
		if (ILog.isLoggable(Level.CONFIG)) {
			for (int CIdx = 0; CIdx < CCL.size(); CIdx++) {
				Constructor<?> CC = CCL.get(CIdx);
				List<String> CPS = new ArrayList<>();
				for (Class<?> CP : CC.getParameterTypes()) {
					CPS.add(CP.getSimpleName());
				}
				ILog.Info("%d. %s", CIdx, CPS); //$NON-NLS-1$
			}
		}
	}
	
	private void RegisterSelectedConstructor(Class<?> C, Constructor<?> CC, Set<Class<?>> Pending) {
		Hint CHint = CC.getAnnotation(Hint.class);
		if (CHint == null) {
			if (ILog.isLoggable(Level.CONFIG)) {
				List<String> CPS = new ArrayList<>();
				for (Class<?> CP : CC.getParameterTypes()) {
					CPS.add(CP.getSimpleName());
				}
				ILog.Config(Messages.Localize("Caching.Canonicalizer.CONSTRUCT_AUTOSEL"), CPS); //$NON-NLS-1$
			}
		}
		Register(C, CC, Pending);
	}
	
	@SuppressWarnings("unchecked")
	private Object FilterConstructors(Constructor<?>[] CCs) {
		Object CCC = null;
		for (Constructor<?> CC : CCs) {
			Hint CHint = CC.getAnnotation(Hint.class);
			if (CHint != null) return CC;
			
			// Default construction is not eligible for auto-canonicalization
			if (CC.getParameterTypes().length == 0) {
				continue;
			}
			// Certain types of parameter are not eligible
			CC = ExcludeParams(CC);
			if (CC != null) {
				if (CCC == null) {
					CCC = CC;
				} else {
					if (!CCC.getClass().equals(Constructor.class)) {
						CCC = new ArrayList<Constructor<?>>(Collections.singletonList((Constructor<?>) CCC));
					}
					((List<Constructor<?>>) CCC).add(CC);
				}
			}
		}
		return CCC;
	}
	
	private Constructor<?> ExcludeParams(Constructor<?> CC) {
		for (Class<?> CP : CC.getParameterTypes()) {
			if (CP.equals(Object.class) || CP.isArray() || CP.isInterface()) {
				CC = null;
				break;
			}
		}
		return CC;
	}
	
	/**
	 * Auto-Magic Canonicalization Instance
	 *
	 * @note Guarantees perfect canonicalization within instance scope
	 */
	@FunctionalInterface
	public interface AutoMagic<T> {
		/**
		 * Get a canonicalized object from parameters
		 */
		T Cast(Object... Params);
	}
	
	/**
	 * Container for Auto-Magic Construction Parameters
	 */
	protected static final class TParamContainer {
		
		public final Object[] Values;
		
		public final int _HashCode;
		
		protected TParamContainer(Object[] values) {
			Values = values;
			
			int hashcode = 0;
			for (Object value : values) {
				hashcode = hashcode ^ value.hashCode();
			}
			_HashCode = hashcode;
		}
		
		@Override
		public int hashCode() {
			return _HashCode;
		}
		
		@Override
		public boolean equals(Object obj) {
			// PERF: The usage context of this container determines that we will never encounter same reference
			//if (this == obj) return true;
			// PERF: The usage context determines obj is always a valid TParamContainer instance
			//if (obj == null) return false;
			//if (TParamContainer.class.isAssignableFrom(obj.getClass())) return false;
			
			TParamContainer p = (TParamContainer) obj;
			if (_HashCode != p._HashCode) return false;
			for (int i = 0; i < Values.length; i++) {
				// We know each element is either a primitive type or canonicalized type
				// So we can directly use reference comparison instead of value comparison
				if (Values[i] != p.Values[i]) return false;
			}
			return true;
		}
		
		@Override
		public String toString() {
			StringBuilder StrBuf = new StringBuilder();
			StrBuf.append('(');
			for (Object value : Values) {
				StrBuf.append(value).append(',');
			}
			if (Values.length > 0) {
				StrBuf.deleteCharAt(StrBuf.length() - 1);
			}
			StrBuf.append(')');
			return StrBuf.toString();
		}
		
		public static TParamContainer wrap(Object[] values, int size) {
			if (values.length != size) {
				Misc.FAIL(IllegalArgumentException.class,
						Messages.Localize("Caching.Canonicalizer.BADAC_PARAMCOUNT"), size, values.length); //$NON-NLS-1$
			}
			return new TParamContainer(values);
		}
		
	}
	
	@SuppressWarnings("unchecked")
	public <T> AutoMagic<T> Instance(Class<T> C) {
		if (!CClass.containsKey(C)) {
			ILog.Config(Messages.Localize("Caching.Canonicalizer.AUTOREG_CLASS"), C.getName()); //$NON-NLS-1$
			Register(C);
		}
		
		if (AClass.contains(C)) {
			CanonicalCacheMap.Auto<T> ACMap =
					new CanonicalCacheMap.Auto<>(LOGGROUP + '@' + C.getSimpleName());
			return Params -> ACMap.Query((T) Params[0]);
		} else {
			Constructor<?> CX = CClass.get(C);
			CanonicalCacheMap<TParamContainer, T> CMap =
					new CanonicalCacheMap.Classic<>(LOGGROUP + '@' + C.getSimpleName());
			return Params -> CMap._Query(TParamContainer.wrap(Params, CX.getParameterTypes().length),
					Key -> {
						try {
							return (T) CX.newInstance(Key.Values);
						} catch (Exception e) {
							Misc.CascadeThrow(e);
						}
						return null;
					});
		}
	}
	
	protected final Map<Class<?>, AutoMagic<?>> MagicStore = new HashMap<>();
	
	protected AutoMagic<?> Lookup(Class<?> C) {
		AutoMagic<?> GMagic;
		synchronized (MagicStore) {
			GMagic = MagicStore.computeIfAbsent(C, this::Instance);
		}
		return GMagic;
	}
	
	@SuppressWarnings("unchecked")
	public <T> T CCreate(Class<T> C, Object... Params) {
		return (T) Lookup(C).Cast(Params);
	}
	
	public static final Canonicalizer Global = new Canonicalizer(null);
	
	static {
		// Primitives type support
		PClass.add(Integer.TYPE);
		PClass.add(Float.TYPE);
		PClass.add(Double.TYPE);
		PClass.add(Byte.TYPE);
		PClass.add(Short.TYPE);
		PClass.add(Long.TYPE);
		PClass.add(Character.TYPE);
		PClass.add(Integer.class);
		PClass.add(Float.class);
		PClass.add(Double.class);
		PClass.add(Byte.class);
		PClass.add(Short.class);
		PClass.add(Long.class);
		PClass.add(Character.class);
	}
	
}
