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

package com.necla.am.zwutils.Debugging;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.file.ProviderNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.esotericsoftware.reflectasm.FieldAccess;
import com.esotericsoftware.reflectasm.MethodAccess;
import com.necla.am.zwutils.GlobalConfig;
import com.necla.am.zwutils.Caching.CanonicalCacheMap;
import com.necla.am.zwutils.Config.DataFile;
import com.necla.am.zwutils.Config.DataMap;
import com.necla.am.zwutils.Logging.DebugLog;
import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Logging.Utils.Support;
import com.necla.am.zwutils.Logging.Utils.Formatters.LogFormatter;
import com.necla.am.zwutils.Logging.Utils.Formatters.SlimFormatter;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.Misc.Misc.TimeUnit;
import com.necla.am.zwutils.Misc.Parsers;
import com.necla.am.zwutils.Misc.Parsers.IParse;
import com.necla.am.zwutils.Modeling.ITimeStamp;
import com.necla.am.zwutils.Reflection.PackageClassIterable;
import com.necla.am.zwutils.Reflection.SuffixClassDictionary;
import com.necla.am.zwutils.Reflection.SuffixClassDictionary.DirectSuffixClassSolver;
import com.necla.am.zwutils.Tasks.ITask;
import com.necla.am.zwutils.Tasks.Samples.Poller;
import com.necla.am.zwutils.Tasks.Wrappers.DaemonRunner;
import com.necla.am.zwutils.i18n.Messages;


/**
 * Run-time object inspection utility
 *
 * @author Zhenyu Wu
 * @version 0.1 - Jul. 2015: Initial implementation
 * @version 0.2 - Oct. 2015: Various bug fix
 * @version 0.25 - Dec. 2015: Adopt resource bundle based localization
 * @version 0.3 - Jan. 2016: Canonicalization-based performance improvement
 * @version 0.3 - Jan. 20 2016: Initial public release
 */
public class ObjectTrap {
	
	public static final String LOGGROUP = "ZWUtils.Debugging.OTap"; //$NON-NLS-1$
	protected static final IGroupLogger CLog = new GroupLogger(LOGGROUP);
	
	protected final IGroupLogger ILog;
	
	protected final SuffixClassDictionary ClassDict;
	protected final Class<?> ObjClass;
	
	// Scope - for extracting data to be matched
	
	public interface IScope {
		
		Class<?> Type();
		
		Object Peek(Object obj);
		
		Throwable LastError();
		
	}
	
	public static class DummyScope implements IScope {
		
		protected final Class<?> DummyType;
		
		public DummyScope(Class<?> type) {
			DummyType = type;
		}
		
		@Override
		public Class<?> Type() {
			return DummyType;
		}
		
		@Override
		public Object Peek(Object obj) {
			return obj;
		}
		
		@Override
		public Throwable LastError() {
			return null;
		}
		
	}
	
	public abstract static class BaseScope implements IScope {
		
		protected ThreadLocal<Throwable> LastError = new ThreadLocal<>();
		
		protected static class PeekRecord {
			
			public Object Source = null;
			public Object Result = null;
			
		}
		
		protected ThreadLocal<PeekRecord> PeekCache = new ThreadLocal<>();
		
		@Override
		public Object Peek(Object obj) {
			PeekRecord PRec = PeekCache.get();
			if (PRec == null) {
				PRec = new PeekRecord();
				PeekCache.set(PRec);
			}
			
			if (PRec.Source == obj) return PRec.Result;
			
			PRec.Source = obj;
			PRec.Result = doPeek(obj);
			return PRec.Result;
		}
		
		protected abstract Object doPeek(Object obj);
		
		@Override
		public Throwable LastError() {
			return LastError.get();
		}
		
	}
	
	public static final char SYM_TYPE_BOOLEAN = 'Z';
	public static final char SYM_TYPE_BYTE = 'B';
	public static final char SYM_TYPE_SHORT = 'S';
	public static final char SYM_TYPE_INTEGER = 'I';
	public static final char SYM_TYPE_LONG = 'J';
	public static final char SYM_TYPE_FLOAT = 'F';
	public static final char SYM_TYPE_DOUBLE = 'D';
	public static final char SYM_TYPE_CHAR = 'C';
	public static final char SYM_TYPE_STRING = '$';
	
	public static enum CastableTypes {
		BOOLEAN(SYM_TYPE_BOOLEAN, java.lang.Boolean.class),
		BYTE(SYM_TYPE_BYTE, java.lang.Byte.class),
		SHORT(SYM_TYPE_SHORT, java.lang.Short.class),
		INTERGER(SYM_TYPE_INTEGER, java.lang.Integer.class),
		LONG(SYM_TYPE_LONG, java.lang.Long.class),
		FLOAT(SYM_TYPE_FLOAT, java.lang.Float.class),
		DOUBLE(SYM_TYPE_DOUBLE, java.lang.Double.class),
		CHAR(SYM_TYPE_CHAR, java.lang.Character.class),
		STRING(SYM_TYPE_STRING, java.lang.String.class);
		
		public final char SYMBOL;
		public final Class<?> CLASS;
		
		CastableTypes(char S, Class<?> C) {
			SYMBOL = S;
			CLASS = C;
		}
		
	}
	
	public class TypeCastScope extends BaseScope {
		
		public final CastableTypes T;
		
		public final Throwable WRONGTYPE;
		
		public TypeCastScope(char type) throws SecurityException {
			switch (type) {
				case SYM_TYPE_BOOLEAN:
					T = CastableTypes.BOOLEAN;
					break;
				case SYM_TYPE_BYTE:
					T = CastableTypes.BYTE;
					break;
				case SYM_TYPE_SHORT:
					T = CastableTypes.SHORT;
					break;
				case SYM_TYPE_INTEGER:
					T = CastableTypes.INTERGER;
					break;
				case SYM_TYPE_LONG:
					T = CastableTypes.LONG;
					break;
				case SYM_TYPE_FLOAT:
					T = CastableTypes.FLOAT;
					break;
				case SYM_TYPE_DOUBLE:
					T = CastableTypes.DOUBLE;
					break;
				case SYM_TYPE_CHAR:
					T = CastableTypes.CHAR;
					break;
				case SYM_TYPE_STRING:
					T = CastableTypes.STRING;
					break;
				default:
					Misc.FAIL(Messages.Localize("Debugging.ObjectTrap.UNKNOWN_TYPE_TOKEN"), type); //$NON-NLS-1$
					T = null;
			}
			WRONGTYPE = new ClassCastException(String
					.format(Messages.Localize("Debugging.ObjectTrap.TYPE_NO_CAST"), Type().getSimpleName())); //$NON-NLS-1$
		}
		
		@Override
		public Class<?> Type() {
			return T.CLASS;
		}
		
		@Override
		public Object doPeek(Object obj) {
			LastError.set(null);
			if ((obj != null) && !Type().isAssignableFrom(obj.getClass())) {
				LastError.set(WRONGTYPE);
				return null;
			}
			
			try {
				return Type().cast(obj);
			} catch (Exception e) {
				if (GlobalConfig.DEBUG_CHECK) {
					ILog.Info(Messages.Localize("Debugging.ObjectTrap.CAST_VALUE_FAILED"), //$NON-NLS-1$
							T.CLASS.getSimpleName());
				}
				LastError.set(e);
				return null;
			}
		}
		
		@Override
		public String toString() {
			StringBuilder StrBuf = new StringBuilder();
			StrBuf.append(Messages.Localize("Debugging.ObjectTrap.SCOPE_PRIMITIVE")) //$NON-NLS-1$
					.append(T.CLASS.getSimpleName());
			
			return StrBuf.toString();
		}
		
	}
	
	public abstract class ClassCastScope extends BaseScope {
		
		public final Class<?> C;
		public final Throwable WRONGCLASS;
		public final Throwable NULLINST;
		
		public ClassCastScope(Class<?> c, String cname)
				throws SecurityException, ClassNotFoundException {
			C = cname != null? ClassDict.Get(cname).toClass() : c;
			WRONGCLASS = new ClassCastException(
					String.format(Messages.Localize("Debugging.ObjectTrap.CLASS_NO_CAST"), C.getName())); //$NON-NLS-1$
			NULLINST = new ClassCastException(
					String.format(Messages.Localize("Debugging.ObjectTrap.CLASS_NULL_CAST"), C.getName())); //$NON-NLS-1$
		}
		
		public boolean ClassCheck(Object obj) {
			LastError.set(null);
			if (obj == null) {
				LastError.set(WRONGCLASS);
				return false;
			}
			if (!C.isAssignableFrom(obj.getClass())) {
				LastError.set(WRONGCLASS);
				return false;
			}
			return true;
		}
		
		@Override
		public String toString() {
			StringBuilder StrBuf = new StringBuilder();
			if (C != null) {
				StrBuf.append(Messages.Localize("Debugging.ObjectTrap.SCOPE_CAST")) //$NON-NLS-1$
						.append(C.getName()).append(' ');
			}
			return StrBuf.toString();
		}
		
	}
	
	protected static Field AccessibleField(Class<?> c, String name) throws SecurityException {
		while (c != null) {
			try {
				Field Ret = c.getDeclaredField(name);
				Ret.setAccessible(true);
				return Ret;
			} catch (NoSuchFieldException e) {
				c = c.getSuperclass();
			}
		}
		return null;
	}
	
	public class FieldScope extends ClassCastScope {
		
		public final Field F;
		
		public FieldScope(Class<?> c, String cname, String field)
				throws SecurityException, ClassNotFoundException {
			super(c, cname);
			F = AccessibleField(C != null? C : c, field);
			if (F == null) {
				Misc.FAIL(NoSuchElementException.class,
						Messages.Localize("Debugging.ObjectTrap.FIELD_NOT_FOUND"), //$NON-NLS-1$
						field, (cname != null? cname : c.getSimpleName()));
			}
		}
		
		@Override
		public Class<?> Type() {
			return F.getType();
		}
		
		@Override
		public Object doPeek(Object obj) {
			if (!ClassCheck(obj)) return null;
			
			try {
				return F.get(obj);
			} catch (Exception e) {
				if (GlobalConfig.DEBUG_CHECK) {
					ILog.Info(Messages.Localize("Debugging.ObjectTrap.NO_FIELD_VALUE"), F.getName()); //$NON-NLS-1$
				}
				LastError.set(e);
				return null;
			}
		}
		
		@Override
		public String toString() {
			StringBuilder StrBuf = new StringBuilder().append(super.toString());
			StrBuf.append(Messages.Localize("Debugging.ObjectTrap.SCOPE_FIELD")).append(F.getName()); //$NON-NLS-1$
			return StrBuf.toString();
		}
		
	}
	
	protected CanonicalCacheMap<Class<?>, FieldAccess> FieldAccessCache =
			new CanonicalCacheMap.Classic<>("OTap-FieldAccess"); //$NON-NLS-1$
	
	public class ASMFieldScope extends ClassCastScope {
		
		public final FieldAccess FA;
		public final int FI;
		
		public ASMFieldScope(Class<?> c, String cname, String field)
				throws SecurityException, ClassNotFoundException {
			super(c, cname);
			
			{
				Class<?> _FC = C != null? C : c;
				FieldAccess _FA = FieldAccessCache.Query(_FC, FieldAccess::get);
				int _FI = -1;
				for (int i = 0, n = _FA.getFieldCount(); i < n; i++) {
					if (_FA.getFieldNames()[i].equals(field)) {
						_FI = i;
						break;
					}
				}
				FA = _FA;
				FI = _FI;
			}
			
			if (FI < 0) {
				Misc.FAIL(NoSuchElementException.class,
						Messages.Localize("Debugging.ObjectTrap.FIELD_NOT_FOUND"), //$NON-NLS-1$
						field, (cname != null? cname : c.getSimpleName()));
			}
		}
		
		@Override
		public Class<?> Type() {
			return FA.getFieldTypes()[FI];
		}
		
		@Override
		public Object doPeek(Object obj) {
			if (!ClassCheck(obj)) return null;
			
			try {
				return FA.get(obj, FI);
			} catch (Exception e) {
				if (GlobalConfig.DEBUG_CHECK) {
					ILog.Info(Messages.Localize("Debugging.ObjectTrap.NO_FIELD_VALUE"), //$NON-NLS-1$
							FA.getFieldNames()[FI]);
				}
				LastError.set(e);
				return null;
			}
		}
		
		@Override
		public String toString() {
			StringBuilder StrBuf = new StringBuilder().append(super.toString());
			StrBuf.append(Messages.Localize("Debugging.ObjectTrap.SCOPE_ASMFIELD")) //$NON-NLS-1$
					.append(FA.getFieldNames()[FI]);
			return StrBuf.toString();
		}
		
	}
	
	// Yes it is complex code, so is the problem, so suck it!
	public static Method AccessibleGetter(Class<?> c, String name) throws SecurityException {
		while (c != null) {
			try {
				Method Ret = c.getDeclaredMethod(name);
				if (Ret.getReturnType().equals(Void.TYPE)) throw new NoSuchMethodException(
						Messages.Localize("Debugging.ObjectTrap.GETTER_NO_RETURN")); //$NON-NLS-1$
				Ret.setAccessible(true);
				return Ret;
			} catch (NoSuchMethodException e) {
				if (c.isInterface()) {
					for (Class<?> i : c.getInterfaces()) {
						Method Ret = AccessibleGetter(i, name);
						if (Ret != null) return Ret;
					}
					break;
				}
				c = c.getSuperclass();
			}
		}
		return null;
	}
	
	public class GetterScope extends ClassCastScope {
		
		public final Method M;
		
		public GetterScope(Class<?> c, String cname, String method)
				throws SecurityException, ClassNotFoundException {
			super(c, cname);
			M = AccessibleGetter(C != null? C : c, method);
			if (M == null) {
				Misc.FAIL(NoSuchElementException.class,
						Messages.Localize("Debugging.ObjectTrap.GETTER_NOT_FOUND"), //$NON-NLS-1$
						method, (cname != null? cname : c.getSimpleName()));
			}
		}
		
		@Override
		public Class<?> Type() {
			return M.getReturnType();
		}
		
		@Override
		public Object doPeek(Object obj) {
			if (!ClassCheck(obj)) return null;
			
			try {
				return M.invoke(obj);
			} catch (InvocationTargetException e) {
				Misc.CascadeThrow(e);
				// PERF: code analysis tool doesn't recognize custom throw functions
				return null;
			} catch (Exception e) {
				if (GlobalConfig.DEBUG_CHECK) {
					ILog.Fine(Messages.Localize("Debugging.ObjectTrap.GETTER_EVAL_FAILED"), M.getName()); //$NON-NLS-1$
				}
				LastError.set(e);
				return null;
			}
		}
		
		@Override
		public String toString() {
			StringBuilder StrBuf = new StringBuilder().append(super.toString());
			StrBuf.append(Messages.Localize("Debugging.ObjectTrap.SCOPE_ASMGETTER")).append(M.getName()); //$NON-NLS-1$
			return StrBuf.toString();
		}
		
	}
	
	protected CanonicalCacheMap<Class<?>, MethodAccess> MethodAccessCache =
			new CanonicalCacheMap.Classic<>("OTap-MethodAccess"); //$NON-NLS-1$
	
	public class ASMGetterScope extends ClassCastScope {
		
		public final MethodAccess MA;
		public final int MI;
		
		// Yes it is complex code, so is the problem, so suck it!
		public ASMGetterScope(Class<?> c, String cname, String method)
				throws SecurityException, ClassNotFoundException {
			super(c, cname);
			
			{
				Class<?> _MC = C != null? C : c;
				MethodAccess _MA = MethodAccessCache.Query(_MC, MethodAccess::get);
				int _MI = -1;
				
				Class<?> _RT = null;
				for (int i = 0, n = _MA.getMethodNames().length; i < n; i++) {
					if (_MA.getMethodNames()[i].equals(method) && (_MA.getParameterTypes()[i].length == 0)) {
						if (_MA.getReturnTypes()[i] != Void.TYPE) {
							// Mimic getDeclaredMethod behavior:
							//   If more than one method with the same parameter types is declared in a class,
							//   and one of these methods has a return type that is more specific than any of
							//   the others, that method is returned; otherwise one of the methods is chosen
							//   arbitrarily.
							if ((_RT == null) || _RT.isAssignableFrom(_MA.getReturnTypes()[i])) {
								_RT = _MA.getReturnTypes()[i];
								_MI = i;
							}
						}
					}
				}
				MA = _MA;
				MI = _MI;
			}
			if (MI < 0) {
				Misc.FAIL(NoSuchElementException.class,
						Messages.Localize("Debugging.ObjectTrap.GETTER_NOT_FOUND"), //$NON-NLS-1$
						method, (cname != null? cname : c.getSimpleName()));
			}
		}
		
		@Override
		public Class<?> Type() {
			return MA.getReturnTypes()[MI];
		}
		
		@Override
		public Object doPeek(Object obj) {
			if (!ClassCheck(obj)) return null;
			
			try {
				return MA.invoke(obj, MI);
			} catch (Exception e) {
				if (GlobalConfig.DEBUG_CHECK) {
					ILog.Fine(Messages.Localize("Debugging.ObjectTrap.GETTER_EVAL_FAILED"), //$NON-NLS-1$
							MA.getMethodNames()[MI]);
				}
				LastError.set(e);
				return null;
			}
		}
		
		@Override
		public String toString() {
			StringBuilder StrBuf = new StringBuilder().append(super.toString());
			StrBuf.append(Messages.Localize("Debugging.ObjectTrap.SCOPE_GETTER")) //$NON-NLS-1$
					.append(MA.getMethodNames()[MI]);
			return StrBuf.toString();
		}
		
	}
	
	public class CascadeScope extends BaseScope {
		
		protected List<IScope> Scopes = new ArrayList<>();
		private IScope LastScope = null;
		
		public void Cascade(IScope scope) {
			Scopes.add(scope);
			LastScope = scope;
		}
		
		public int Size() {
			return Scopes.size();
		}
		
		@Override
		public Class<?> Type() {
			return LastScope == null? null : LastScope.Type();
		}
		
		@Override
		public Object doPeek(Object obj) {
			LastError.set(null);
			
			Object Ret = obj;
			Throwable Error = null;
			for (IScope scope : Scopes) {
				Ret = scope.Peek(Ret);
				if (Ret == null) {
					Error = scope.LastError();
					if (Error != null) {
						break;
					}
				}
			}
			LastError.set(Error);
			
			return Ret;
		}
		
		@Override
		public String toString() {
			StringBuilder StrBuf = new StringBuilder();
			for (IScope scope : Scopes) {
				if (StrBuf.length() > 0) {
					StrBuf.append(System.lineSeparator()).append("-> "); //$NON-NLS-1$
				}
				StrBuf.append(scope.toString());
			}
			return StrBuf.toString();
		}
		
	}
	
	// Hook - for executing matching condition
	
	public interface IHook {
		
		boolean Latch(Object value);
		
	}
	
	public static class HookFactory {
		
		public static final String LOGGROUP = ObjectTrap.LOGGROUP + ".HookFactory"; //$NON-NLS-1$
		protected static final IGroupLogger CLog = new GroupLogger(LOGGROUP);
		
		protected Map<Type, Class<? extends Hook>> HookReg = new HashMap<>();
		
		void Register(Type type, Class<? extends Hook> hookclass) {
			if (HookReg.containsKey(type)) {
				if (GlobalConfig.DEBUG_CHECK) {
					CLog.Warn(Messages.Localize("Debugging.ObjectTrap.TYPE_KNOWN"), type); //$NON-NLS-1$
				}
				return;
			}
			HookReg.put(type, hookclass);
		}
		
		IHook Create(Type type, String condition, SuffixClassDictionary dict) {
			if (condition.isEmpty()) return null;
			
			Class<? extends Hook> hookclass = Lookup(type);
			if (hookclass == null) {
				if (GlobalConfig.DEBUG_CHECK) {
					CLog.Warn(Messages.Localize("Debugging.ObjectTrap.TYPE_UNKNOWN"), type); //$NON-NLS-1$
				}
				hookclass = Lookup(Object.class);
			}
			
			try {
				return hookclass.getConstructor(String.class, SuffixClassDictionary.class)
						.newInstance(condition, dict);
			} catch (Exception e) {
				Misc.CascadeThrow(e, Messages.Localize("Debugging.ObjectTrap.HOOK_CREATE_FAILED")); //$NON-NLS-1$
				// PERF: code analysis tool doesn't recognize custom throw functions
				return null;
			}
		}
		
		public Class<? extends Hook> Lookup(Type type) {
			return HookReg.get(type);
		}
		
		public static class Hook implements IHook {
			
			@Override
			public boolean Latch(Object value) {
				Misc.ERROR(Messages.Localize("Debugging.ObjectTrap.CONDCHECK_NOT_IMPL")); //$NON-NLS-1$
				return false;
			}
			
			public static Collection<String> InputHelp() {
				return Collections.emptyList();
			}
			
		}
		
	}
	
	public static final HookFactory HookMaker = new HookFactory();
	
	public static class BasicHooks {
		
		protected BasicHooks() {
			Misc.FAIL(IllegalStateException.class, "Do not instantiate!");
		}
		
		public static enum LatchOp {
			ACCEPT('Y'),
			ISNULL('X'),
			EQUALTO('='),
			GREATERTHAN('>'),
			LESSTHAN('<'),
			INRANGE('~'),
			ONEOF('@'),
			REGMATCH('*');
			
			public final char OpSym;
			
			LatchOp(char opSym) {
				OpSym = opSym;
			}
			
			public static LatchOp Convert(char SymIn) {
				for (LatchOp I : LatchOp.values())
					if (SymIn == I.OpSym) return I;
				return null;
			}
			
		}
		
		public abstract static class BaseHook extends HookFactory.Hook {
			
			public static final char SYM_NEGATION = '!';
			
			public final boolean Negate;
			public final SuffixClassDictionary Dict;
			
			public final LatchOp Op;
			
			public BaseHook(String cond, SuffixClassDictionary dict) {
				int CondStart = 0;
				char iChar = CondStart >= cond.length()? LatchOp.ACCEPT.OpSym : cond.charAt(CondStart);
				Negate = iChar == SYM_NEGATION;
				if (Negate) {
					CondStart++;
					iChar = CondStart >= cond.length()? LatchOp.ACCEPT.OpSym : cond.charAt(CondStart);
				}
				
				Dict = dict;
				Op = LatchOp.Convert(iChar);
				if (Op == null) {
					Misc.ERROR(Messages.Localize("Debugging.ObjectTrap.UNKNOWN_HOOK_OPERATION"), iChar); //$NON-NLS-1$
				}
				if (CondStart < cond.length()) {
					ParseValue(cond.substring(CondStart + 1));
				}
			}
			
			public static Collection<String> InputHelp_BasicTypes(String typename) {
				Collection<String> HelpStr = new ArrayList<>();
				HelpStr.add(String.format("%c", LatchOp.ACCEPT.OpSym)); //$NON-NLS-1$
				HelpStr.add(String.format("%c", LatchOp.ISNULL.OpSym)); //$NON-NLS-1$
				HelpStr.add(String.format("%c<%s>", LatchOp.EQUALTO.OpSym, typename)); //$NON-NLS-1$
				HelpStr.add(String.format("%c<%s>", LatchOp.GREATERTHAN.OpSym, typename)); //$NON-NLS-1$
				HelpStr.add(String.format("%c<%s>", LatchOp.LESSTHAN.OpSym, typename)); //$NON-NLS-1$
				HelpStr.add(String.format("%c<%s>,<%s2>", LatchOp.INRANGE.OpSym, typename, typename)); //$NON-NLS-1$
				HelpStr.add(String.format("%c<%s>[,<%s2>,...]", LatchOp.ONEOF.OpSym, typename, typename)); //$NON-NLS-1$
				return HelpStr;
			}
			
			protected static void ParseNoArgOperand(String condval) {
				if (!condval.trim().isEmpty()) {
					Misc.ERROR(Messages.Localize("Debugging.ObjectTrap.UNKNOWN_OPERAND"), condval); //$NON-NLS-1$
				}
			}
			
			@SuppressWarnings("unchecked")
			protected static void ParseSetList(String condval, Pattern ListSep, IParse<String, ?> Parser,
					Set<?> CompSet) {
				String[] CondVals = ListSep.split(condval.trim());
				for (String Val : CondVals) {
					if (!((Set<Object>) CompSet).add(Parser.parseOrFail(Val))) {
						Misc.ERROR(Messages.Localize("Debugging.ObjectTrap.DUPLICATE_PARAM"), Val); //$NON-NLS-1$
					}
				}
			}
			
			protected abstract void ParseValue(String condval);
			
			protected boolean StateFilter(boolean opRet, Object value) {
				return opRet ^ Negate? true : false;
			}
			
			@Override
			public String toString() {
				return (Negate? Messages.Localize("Debugging.ObjectTrap.HOOK_ACTION_NOT") : "") + Op.name(); //$NON-NLS-1$//$NON-NLS-2$
			}
			
		}
		
		public static class IntegerHook extends BaseHook {
			
			public IntegerHook(String condition, SuffixClassDictionary dict) {
				super(condition, dict);
			}
			
			protected static IParse<String, Integer> IntParser = Parsers.StringToInteger;
			protected static Pattern IntListSep = Pattern.compile(","); //$NON-NLS-1$
			
			protected Integer CompIntA;
			protected Integer CompIntB;
			protected Set<Integer> CompIntSet;
			
			public static Collection<String> InputHelp() {
				return InputHelp_BasicTypes(int.class.getSimpleName());
			}
			
			@Override
			protected void ParseValue(String condval) {
				switch (Op) {
					case ACCEPT:
					case ISNULL:
						ParseNoArgOperand(condval);
						break;
					case EQUALTO:
					case GREATERTHAN:
					case LESSTHAN:
						CompIntA = IntParser.parseOrFail(condval.trim());
						break;
					case INRANGE: {
						String[] CondVals = IntListSep.split(condval.trim());
						if (CondVals.length != 2) {
							Misc.ERROR(Messages.Localize("Debugging.ObjectTrap.BAD_PARAM_COUNT"), condval.trim()); //$NON-NLS-1$
						}
						CompIntA = IntParser.parseOrFail(CondVals[0].trim());
						CompIntB = IntParser.parseOrFail(CondVals[1].trim());
						if (CompIntA > CompIntB) {
							Misc.ERROR(Messages.Localize("Debugging.ObjectTrap.INVALID_RANGE"), CompIntA, //$NON-NLS-1$
									CompIntB);
						}
						break;
					}
					case ONEOF:
						CompIntSet = new HashSet<>();
						ParseSetList(condval, IntListSep, IntParser, CompIntSet);
						break;
					case REGMATCH:
						Misc.ERROR(Messages.Localize("Debugging.ObjectTrap.UNSUPPORT_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
						break;
					default:
						Misc.FAIL(Messages.Localize("Debugging.ObjectTrap.UNKNOWN_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
				}
			}
			
			protected boolean Oper(Integer value) {
				switch (Op) {
					case ACCEPT:
						return true;
					case ISNULL:
						return value == null;
					case EQUALTO:
						return (value != null) && value.equals(CompIntA);
					case GREATERTHAN:
						return (value != null) && (value > CompIntA);
					case LESSTHAN:
						return (value != null) && (value < CompIntA);
					case ONEOF:
						return (value != null) && CompIntSet.contains(value);
					case INRANGE:
						return (value != null) && (value >= CompIntA) && (value <= CompIntB);
					default:
						Misc.FAIL(Messages.Localize("Debugging.ObjectTrap.UNKNOWN_OPERATOR"),  //$NON-NLS-1$
								Op.OpSym, Op.name());
						return Negate;
				}
			}
			
			@Override
			public boolean Latch(Object value) {
				return StateFilter(Oper((Integer) value), value);
			}
			
			@Override
			public String toString() {
				StringBuilder StrBuf = new StringBuilder();
				StrBuf.append(super.toString());
				
				switch (Op) {
					case ACCEPT:
					case ISNULL:
						break;
					case EQUALTO:
					case GREATERTHAN:
					case LESSTHAN:
						StrBuf.append(' ').append(CompIntA);
						break;
					case INRANGE:
						StrBuf.append(" [").append(CompIntA).append(',').append(CompIntB).append(']'); //$NON-NLS-1$
						break;
					case ONEOF:
						StrBuf.append(" {"); //$NON-NLS-1$
						for (Integer I : CompIntSet) {
							StrBuf.append(I).append(',');
						}
						if (!CompIntSet.isEmpty()) {
							StrBuf.setCharAt(StrBuf.length() - 1, '}');
						}
						break;
					default:
						Misc.FAIL(Messages.Localize("Debugging.ObjectTrap.UNKNOWN_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
				}
				return StrBuf.toString();
			}
			
		}
		
		public static class LongHook extends BaseHook {
			
			public LongHook(String condition, SuffixClassDictionary dict) {
				super(condition, dict);
			}
			
			protected static IParse<String, Long> LongParser = Parsers.StringToLong;
			protected static Pattern LongListSep = Pattern.compile(",");//$NON-NLS-1$
			
			protected Long CompLongA;
			protected Long CompLongB;
			protected Set<Long> CompLongSet;
			
			public static Collection<String> InputHelp() {
				return InputHelp_BasicTypes(long.class.getSimpleName());
			}
			
			@Override
			protected void ParseValue(String condval) {
				switch (Op) {
					case ACCEPT:
					case ISNULL:
						ParseNoArgOperand(condval);
						break;
					case EQUALTO:
					case GREATERTHAN:
					case LESSTHAN:
						CompLongA = LongParser.parseOrFail(condval.trim());
						break;
					case INRANGE: {
						String[] CondVals = LongListSep.split(condval.trim());
						if (CondVals.length != 2) {
							Misc.ERROR(Messages.Localize("Debugging.ObjectTrap.BAD_PARAM_COUNT"), condval.trim()); //$NON-NLS-1$
						}
						
						CompLongA = LongParser.parseOrFail(CondVals[0].trim());
						CompLongB = LongParser.parseOrFail(CondVals[1].trim());
						if (CompLongA > CompLongB) {
							Misc.ERROR(Messages.Localize("Debugging.ObjectTrap.INVALID_RANGE"), CompLongA, //$NON-NLS-1$
									CompLongB);
						}
						break;
					}
					case ONEOF:
						CompLongSet = new HashSet<>();
						ParseSetList(condval, LongListSep, LongParser, CompLongSet);
						break;
					case REGMATCH:
						Misc.ERROR(Messages.Localize("Debugging.ObjectTrap.UNSUPPORT_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
						break;
					default:
						Misc.FAIL(Messages.Localize("Debugging.ObjectTrap.UNKNOWN_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
				}
			}
			
			protected boolean Oper(Long value) {
				switch (Op) {
					case ACCEPT:
						return true;
					case ISNULL:
						return value == null;
					case EQUALTO:
						return (value != null) && value.equals(CompLongA);
					case GREATERTHAN:
						return (value != null) && (value > CompLongA);
					case LESSTHAN:
						return (value != null) && (value < CompLongA);
					case INRANGE:
						return (value != null) && (value >= CompLongA) && (value <= CompLongB);
					case ONEOF:
						return CompLongSet.contains(value);
					default:
						Misc.FAIL(Messages.Localize("Debugging.ObjectTrap.UNKNOWN_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
						return Negate;
				}
			}
			
			@Override
			public boolean Latch(Object value) {
				return StateFilter(Oper((Long) value), value);
			}
			
			@Override
			public String toString() {
				StringBuilder StrBuf = new StringBuilder();
				StrBuf.append(super.toString());
				
				switch (Op) {
					case ACCEPT:
					case ISNULL:
						break;
					case EQUALTO:
					case GREATERTHAN:
					case LESSTHAN:
						StrBuf.append(' ').append(CompLongA);
						break;
					case INRANGE:
						StrBuf.append(" [").append(CompLongA).append(',').append(CompLongB).append(']'); //$NON-NLS-1$
						break;
					case ONEOF:
						StrBuf.append(" {"); //$NON-NLS-1$
						for (Long I : CompLongSet) {
							StrBuf.append(I).append(',');
						}
						if (!CompLongSet.isEmpty()) {
							StrBuf.setCharAt(StrBuf.length() - 1, '}');
						}
						break;
					default:
						Misc.FAIL(Messages.Localize("Debugging.ObjectTrap.UNKNOWN_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
				}
				return StrBuf.toString();
			}
			
		}
		
		public static class ByteHook extends BaseHook {
			
			public ByteHook(String condition, SuffixClassDictionary dict) {
				super(condition, dict);
			}
			
			protected static IParse<String, Byte> ByteParser = Parsers.StringToByte;
			protected static Pattern ByteListSep = Pattern.compile(",");//$NON-NLS-1$
			
			protected Byte CompByteA;
			protected Byte CompByteB;
			protected Set<Byte> CompByteSet;
			
			public static Collection<String> InputHelp() {
				return InputHelp_BasicTypes(byte.class.getSimpleName());
			}
			
			@Override
			protected void ParseValue(String condval) {
				switch (Op) {
					case ACCEPT:
					case ISNULL:
						ParseNoArgOperand(condval);
						break;
					case EQUALTO:
					case GREATERTHAN:
					case LESSTHAN:
						CompByteA = ByteParser.parseOrFail(condval.trim());
						break;
					case INRANGE: {
						String[] CondVals = ByteListSep.split(condval.trim());
						if (CondVals.length != 2) {
							Misc.ERROR(Messages.Localize("Debugging.ObjectTrap.BAD_PARAM_COUNT"), condval.trim());//$NON-NLS-1$
						}
						
						CompByteA = ByteParser.parseOrFail(CondVals[0].trim());
						CompByteB = ByteParser.parseOrFail(CondVals[1].trim());
						if (CompByteA > CompByteB) {
							Misc.ERROR(Messages.Localize("Debugging.ObjectTrap.INVALID_RANGE"), CompByteA, //$NON-NLS-1$
									CompByteB);
						}
						break;
					}
					case ONEOF:
						CompByteSet = new HashSet<>();
						ParseSetList(condval, ByteListSep, ByteParser, CompByteSet);
						break;
					case REGMATCH:
						Misc.ERROR(Messages.Localize("Debugging.ObjectTrap.UNSUPPORT_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
						break;
					default:
						Misc.FAIL(Messages.Localize("Debugging.ObjectTrap.UNKNOWN_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
				}
			}
			
			protected boolean Oper(Byte value) {
				switch (Op) {
					case ACCEPT:
						return true;
					case ISNULL:
						return value == null;
					case EQUALTO:
						return (value != null) && value.equals(CompByteA);
					case GREATERTHAN:
						return (value != null) && (value > CompByteA);
					case LESSTHAN:
						return (value != null) && (value < CompByteA);
					case INRANGE:
						return (value != null) && (value >= CompByteA) && (value <= CompByteB);
					case ONEOF:
						return CompByteSet.contains(value);
					default:
						Misc.FAIL(Messages.Localize("Debugging.ObjectTrap.UNKNOWN_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
						return Negate;
				}
			}
			
			@Override
			public boolean Latch(Object value) {
				return StateFilter(Oper((Byte) value), value);
			}
			
			@Override
			public String toString() {
				StringBuilder StrBuf = new StringBuilder();
				StrBuf.append(super.toString());
				
				switch (Op) {
					case ACCEPT:
					case ISNULL:
						break;
					case EQUALTO:
					case GREATERTHAN:
					case LESSTHAN:
						StrBuf.append(' ').append(CompByteA);
						break;
					case INRANGE:
						StrBuf.append(" [").append(CompByteA).append(',').append(CompByteB).append(']'); //$NON-NLS-1$
						break;
					case ONEOF:
						StrBuf.append(" {"); //$NON-NLS-1$
						for (Byte I : CompByteSet) {
							StrBuf.append(I).append(',');
						}
						if (!CompByteSet.isEmpty()) {
							StrBuf.setCharAt(StrBuf.length() - 1, '}');
						}
						break;
					default:
						Misc.FAIL(Messages.Localize("Debugging.ObjectTrap.UNKNOWN_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
				}
				return StrBuf.toString();
			}
			
		}
		
		public static class ShortHook extends BaseHook {
			
			public ShortHook(String condition, SuffixClassDictionary dict) {
				super(condition, dict);
			}
			
			protected static IParse<String, Short> ShortParser = Parsers.StringToShort;
			protected static Pattern ShortListSep = Pattern.compile(",");//$NON-NLS-1$
			
			protected Short CompShortA;
			protected Short CompShortB;
			protected Set<Short> CompShortSet;
			
			public static Collection<String> InputHelp() {
				return InputHelp_BasicTypes(short.class.getSimpleName());
			}
			
			@Override
			protected void ParseValue(String condval) {
				switch (Op) {
					case ACCEPT:
					case ISNULL:
						ParseNoArgOperand(condval);
						break;
					case EQUALTO:
					case GREATERTHAN:
					case LESSTHAN:
						CompShortA = ShortParser.parseOrFail(condval.trim());
						break;
					case INRANGE: {
						String[] CondVals = ShortListSep.split(condval.trim());
						if (CondVals.length != 2) {
							Misc.ERROR(Messages.Localize("Debugging.ObjectTrap.BAD_PARAM_COUNT"), condval.trim()); //$NON-NLS-1$
						}
						CompShortA = ShortParser.parseOrFail(CondVals[0].trim());
						CompShortB = ShortParser.parseOrFail(CondVals[1].trim());
						if (CompShortA > CompShortB) {
							Misc.ERROR(Messages.Localize("Debugging.ObjectTrap.INVALID_RANGE"), CompShortA, //$NON-NLS-1$
									CompShortB);
						}
						break;
					}
					case ONEOF:
						CompShortSet = new HashSet<>();
						ParseSetList(condval, ShortListSep, ShortParser, CompShortSet);
						break;
					case REGMATCH:
						Misc.ERROR(Messages.Localize("Debugging.ObjectTrap.UNSUPPORT_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
						break;
					default:
						Misc.FAIL(Messages.Localize("Debugging.ObjectTrap.UNKNOWN_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
				}
			}
			
			protected boolean Oper(Short value) {
				switch (Op) {
					case ACCEPT:
						return true;
					case ISNULL:
						return value == null;
					case EQUALTO:
						return (value != null) && value.equals(CompShortA);
					case GREATERTHAN:
						return (value != null) && (value > CompShortA);
					case LESSTHAN:
						return (value != null) && (value < CompShortA);
					case INRANGE:
						return (value != null) && (value >= CompShortA) && (value <= CompShortB);
					case ONEOF:
						return CompShortSet.contains(value);
					default:
						Misc.FAIL(Messages.Localize("Debugging.ObjectTrap.UNKNOWN_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
						return Negate;
				}
			}
			
			@Override
			public boolean Latch(Object value) {
				return StateFilter(Oper((Short) value), value);
			}
			
			@Override
			public String toString() {
				StringBuilder StrBuf = new StringBuilder();
				StrBuf.append(super.toString());
				
				switch (Op) {
					case ACCEPT:
					case ISNULL:
						break;
					case EQUALTO:
					case GREATERTHAN:
					case LESSTHAN:
						StrBuf.append(' ').append(CompShortA);
						break;
					case INRANGE:
						StrBuf.append(" [").append(CompShortA).append(',').append(CompShortB).append(']'); //$NON-NLS-1$
						break;
					case ONEOF:
						StrBuf.append(" {"); //$NON-NLS-1$
						for (Short I : CompShortSet) {
							StrBuf.append(I).append(',');
						}
						if (!CompShortSet.isEmpty()) {
							StrBuf.setCharAt(StrBuf.length() - 1, '}');
						}
						break;
					default:
						Misc.FAIL(Messages.Localize("Debugging.ObjectTrap.UNKNOWN_OPERATOR"),//$NON-NLS-1$
								Op.OpSym, Op.name());
				}
				return StrBuf.toString();
			}
			
		}
		
		public static class FloatHook extends BaseHook {
			
			public FloatHook(String condition, SuffixClassDictionary dict) {
				super(condition, dict);
			}
			
			protected static IParse<String, Float> FloatParser = Parsers.StringToFloat;
			protected static Pattern FloatListSep = Pattern.compile(",");//$NON-NLS-1$
			
			protected Float CompFloatA;
			protected Float CompFloatB;
			protected Set<Float> CompFloatSet;
			
			public static Collection<String> InputHelp() {
				return InputHelp_BasicTypes(float.class.getSimpleName());
			}
			
			@Override
			protected void ParseValue(String condval) {
				switch (Op) {
					case ACCEPT:
					case ISNULL:
						ParseNoArgOperand(condval);
						break;
					case EQUALTO:
					case GREATERTHAN:
					case LESSTHAN:
						CompFloatA = FloatParser.parseOrFail(condval.trim());
						break;
					case INRANGE: {
						String[] CondVals = FloatListSep.split(condval.trim());
						if (CondVals.length != 2) {
							Misc.ERROR(Messages.Localize("Debugging.ObjectTrap.BAD_PARAM_COUNT"), condval.trim()); //$NON-NLS-1$
						}
						CompFloatA = FloatParser.parseOrFail(CondVals[0].trim());
						CompFloatB = FloatParser.parseOrFail(CondVals[1].trim());
						if (CompFloatA > CompFloatB) {
							Misc.ERROR(Messages.Localize("Debugging.ObjectTrap.INVALID_RANGE"), CompFloatA, //$NON-NLS-1$
									CompFloatB);
						}
						break;
					}
					case ONEOF:
						CompFloatSet = new HashSet<>();
						ParseSetList(condval, FloatListSep, FloatParser, CompFloatSet);
						break;
					case REGMATCH:
						Misc.ERROR(Messages.Localize("Debugging.ObjectTrap.UNSUPPORT_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
						break;
					default:
						Misc.FAIL(Messages.Localize("Debugging.ObjectTrap.UNKNOWN_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
				}
			}
			
			protected boolean Oper(Float value) {
				switch (Op) {
					case ACCEPT:
						return true;
					case ISNULL:
						return value == null;
					case EQUALTO:
						return (value != null) && value.equals(CompFloatA);
					case GREATERTHAN:
						return (value != null) && (value > CompFloatA);
					case LESSTHAN:
						return (value != null) && (value < CompFloatA);
					case INRANGE:
						return (value >= CompFloatA) && (value <= CompFloatB);
					case ONEOF:
						return CompFloatSet.contains(value);
					default:
						Misc.FAIL(Messages.Localize("Debugging.ObjectTrap.UNKNOWN_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
						return Negate;
				}
			}
			
			@Override
			public boolean Latch(Object value) {
				return StateFilter(Oper((Float) value), value);
			}
			
			@Override
			public String toString() {
				StringBuilder StrBuf = new StringBuilder();
				StrBuf.append(super.toString());
				
				switch (Op) {
					case ACCEPT:
					case ISNULL:
						break;
					case EQUALTO:
					case GREATERTHAN:
					case LESSTHAN:
						StrBuf.append(' ').append(CompFloatA);
						break;
					case INRANGE:
						StrBuf.append(" [").append(CompFloatA).append(',').append(CompFloatB).append(']'); //$NON-NLS-1$
						break;
					case ONEOF:
						StrBuf.append(" {"); //$NON-NLS-1$
						for (Float I : CompFloatSet) {
							StrBuf.append(I).append(',');
						}
						if (!CompFloatSet.isEmpty()) {
							StrBuf.setCharAt(StrBuf.length() - 1, '}');
						}
						break;
					default:
						Misc.FAIL(Messages.Localize("Debugging.ObjectTrap.UNKNOWN_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
				}
				return StrBuf.toString();
			}
			
		}
		
		public static class DoubleHook extends BaseHook {
			
			public DoubleHook(String condition, SuffixClassDictionary dict) {
				super(condition, dict);
			}
			
			protected static IParse<String, Double> DoubleParser = Parsers.StringToDouble;
			protected static Pattern DoubleListSep = Pattern.compile(",");//$NON-NLS-1$
			
			protected Double CompDoubleA;
			protected Double CompDoubleB;
			protected Set<Double> CompDoubleSet;
			
			public static Collection<String> InputHelp() {
				return InputHelp_BasicTypes(double.class.getSimpleName());
			}
			
			@Override
			protected void ParseValue(String condval) {
				switch (Op) {
					case ACCEPT:
					case ISNULL:
						ParseNoArgOperand(condval);
						break;
					case EQUALTO:
					case GREATERTHAN:
					case LESSTHAN:
						CompDoubleA = DoubleParser.parseOrFail(condval.trim());
						break;
					case INRANGE: {
						String[] CondVals = DoubleListSep.split(condval.trim());
						if (CondVals.length != 2) {
							Misc.ERROR(Messages.Localize("Debugging.ObjectTrap.BAD_PARAM_COUNT"), condval.trim()); //$NON-NLS-1$
						}
						CompDoubleA = DoubleParser.parseOrFail(CondVals[0].trim());
						CompDoubleB = DoubleParser.parseOrFail(CondVals[1].trim());
						if (CompDoubleA > CompDoubleB) {
							Misc.ERROR(Messages.Localize("Debugging.ObjectTrap.INVALID_RANGE"), CompDoubleA, //$NON-NLS-1$
									CompDoubleB);
						}
						break;
					}
					case ONEOF:
						CompDoubleSet = new HashSet<>();
						ParseSetList(condval, DoubleListSep, DoubleParser, CompDoubleSet);
						break;
					case REGMATCH:
						Misc.ERROR(Messages.Localize("Debugging.ObjectTrap.UNSUPPORT_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
						break;
					default:
						Misc.FAIL(Messages.Localize("Debugging.ObjectTrap.UNKNOWN_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
				}
			}
			
			protected boolean Oper(Double value) {
				switch (Op) {
					case ACCEPT:
						return true;
					case ISNULL:
						return value == null;
					case EQUALTO:
						return (value != null) && value.equals(CompDoubleA);
					case GREATERTHAN:
						return (value != null) && (value > CompDoubleA);
					case LESSTHAN:
						return (value != null) && (value < CompDoubleA);
					case INRANGE:
						return (value != null) && (value >= CompDoubleA) && (value <= CompDoubleB);
					case ONEOF:
						return CompDoubleSet.contains(value);
					default:
						Misc.FAIL(Messages.Localize("Debugging.ObjectTrap.UNKNOWN_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
						return Negate;
				}
			}
			
			@Override
			public boolean Latch(Object value) {
				return StateFilter(Oper((Double) value), value);
			}
			
			@Override
			public String toString() {
				StringBuilder StrBuf = new StringBuilder();
				StrBuf.append(super.toString());
				
				switch (Op) {
					case ACCEPT:
					case ISNULL:
						break;
					case EQUALTO:
					case GREATERTHAN:
					case LESSTHAN:
						StrBuf.append(' ').append(CompDoubleA);
						break;
					case INRANGE:
						StrBuf.append(" [").append(CompDoubleA).append(',').append(CompDoubleB).append(']'); //$NON-NLS-1$
						break;
					case ONEOF:
						StrBuf.append(" {"); //$NON-NLS-1$
						for (Double I : CompDoubleSet) {
							StrBuf.append(I).append(',');
						}
						if (!CompDoubleSet.isEmpty()) {
							StrBuf.setCharAt(StrBuf.length() - 1, '}');
						}
						break;
					default:
						Misc.FAIL(Messages.Localize("Debugging.ObjectTrap.UNKNOWN_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
				}
				return StrBuf.toString();
			}
			
		}
		
		public static class CharHook extends BaseHook {
			
			public CharHook(String condition, SuffixClassDictionary dict) {
				super(condition, dict);
			}
			
			protected static IParse<String, Character> CharParser = Parsers.StringToChar;
			protected static IParse<String, String> CharSetParser = Parsers.StringToString;
			protected static Pattern CharListSep = Pattern.compile(",");//$NON-NLS-1$
			
			protected Character CompCharA;
			protected Character CompCharB;
			protected String CompCharSet;
			
			public static Collection<String> InputHelp() {
				return InputHelp_BasicTypes(char.class.getSimpleName());
			}
			
			@Override
			protected void ParseValue(String condval) {
				switch (Op) {
					case ACCEPT:
					case ISNULL:
						ParseNoArgOperand(condval);
						break;
					case EQUALTO:
					case GREATERTHAN:
					case LESSTHAN:
						CompCharA = CharParser.parseOrFail(condval.trim());
						break;
					case INRANGE: {
						String[] CondVals = CharListSep.split(condval.trim());
						if (CondVals.length != 2) {
							Misc.ERROR(Messages.Localize("Debugging.ObjectTrap.BAD_PARAM_COUNT"), condval.trim()); //$NON-NLS-1$
						}
						CompCharA = CharParser.parseOrFail(CondVals[0].trim());
						CompCharB = CharParser.parseOrFail(CondVals[1].trim());
						if (CompCharA > CompCharB) {
							Misc.ERROR(Messages.Localize("Debugging.ObjectTrap.INVALID_RANGE"), CompCharA, //$NON-NLS-1$
									CompCharB);
						}
						break;
					}
					case ONEOF: {
						CompCharSet = CharSetParser.parseOrFail(condval.trim());
						break;
					}
					case REGMATCH:
						Misc.ERROR(Messages.Localize("Debugging.ObjectTrap.UNSUPPORT_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
						break;
					default:
						Misc.FAIL(Messages.Localize("Debugging.ObjectTrap.UNKNOWN_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
				}
			}
			
			protected boolean Oper(Character value) {
				switch (Op) {
					case ACCEPT:
						return true;
					case ISNULL:
						return value == null;
					case EQUALTO:
						return (value != null) && value.equals(CompCharA);
					case GREATERTHAN:
						return (value != null) && (value > CompCharA);
					case LESSTHAN:
						return (value != null) && (value < CompCharA);
					case INRANGE:
						return (value != null) && (value >= CompCharA) && (value <= CompCharB);
					case ONEOF:
						return CompCharSet.indexOf(value) >= 0;
					default:
						Misc.FAIL(Messages.Localize("Debugging.ObjectTrap.UNKNOWN_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
						return Negate;
				}
			}
			
			@Override
			public boolean Latch(Object value) {
				return StateFilter(Oper((Character) value), value);
			}
			
			@Override
			public String toString() {
				StringBuilder StrBuf = new StringBuilder();
				StrBuf.append(super.toString());
				
				switch (Op) {
					case ACCEPT:
					case ISNULL:
						break;
					case EQUALTO:
					case GREATERTHAN:
					case LESSTHAN:
						StrBuf.append(' ').append(CompCharA);
						break;
					case INRANGE:
						StrBuf.append(" [").append(CompCharA).append(',').append(CompCharB).append(']'); //$NON-NLS-1$
						break;
					case ONEOF:
						StrBuf.append(" \"").append(CompCharSet).append('"'); //$NON-NLS-1$
						break;
					default:
						Misc.FAIL(Messages.Localize("Debugging.ObjectTrap.UNKNOWN_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
				}
				return StrBuf.toString();
			}
			
		}
		
		public static class BooleanHook extends BaseHook {
			
			public BooleanHook(String condition, SuffixClassDictionary dict) {
				super(condition, dict);
			}
			
			protected static IParse<String, Boolean> BoolParser = Parsers.StringToBoolean;
			
			protected Boolean CompBoolVal;
			
			public static Collection<String> InputHelp() {
				Collection<String> HelpStr = new ArrayList<>();
				HelpStr.add(String.format("%c", LatchOp.ACCEPT.OpSym)); //$NON-NLS-1$
				HelpStr.add(String.format("%c", LatchOp.ISNULL.OpSym)); //$NON-NLS-1$
				HelpStr.add(String.format("%c<bool>", LatchOp.EQUALTO.OpSym)); //$NON-NLS-1$
				return HelpStr;
			}
			
			@Override
			protected void ParseValue(String condval) {
				switch (Op) {
					case ACCEPT:
					case ISNULL:
						ParseNoArgOperand(condval);
						break;
					case EQUALTO:
						CompBoolVal = BoolParser.parseOrFail(condval.trim());
						break;
					case GREATERTHAN:
					case LESSTHAN:
					case INRANGE:
					case ONEOF:
					case REGMATCH:
						Misc.ERROR(Messages.Localize("Debugging.ObjectTrap.UNSUPPORT_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
						break;
					default:
						Misc.FAIL(Messages.Localize("Debugging.ObjectTrap.UNKNOWN_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
				}
			}
			
			protected boolean Oper(Boolean value) {
				switch (Op) {
					case ACCEPT:
						return true;
					case ISNULL:
						return value == null;
					case EQUALTO:
						return (value != null) && (value == CompBoolVal);
					default:
						Misc.FAIL(Messages.Localize("Debugging.ObjectTrap.UNKNOWN_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
						return Negate;
				}
			}
			
			@Override
			public boolean Latch(Object value) {
				return StateFilter(Oper((Boolean) value), value);
			}
			
			@Override
			public String toString() {
				StringBuilder StrBuf = new StringBuilder();
				StrBuf.append(super.toString());
				
				switch (Op) {
					case ACCEPT:
					case ISNULL:
						break;
					case EQUALTO:
						StrBuf.append(' ').append(CompBoolVal);
						break;
					default:
						Misc.FAIL(Messages.Localize("Debugging.ObjectTrap.UNKNOWN_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
				}
				return StrBuf.toString();
			}
			
		}
		
		public static class StringHook extends BaseHook {
			
			public StringHook(String condition, SuffixClassDictionary dict) {
				super(condition, dict);
			}
			
			protected static IParse<String, String> StrParser = Parsers.StringToString;
			protected static Pattern StrListSep = Pattern.compile(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"); //$NON-NLS-1$
			
			protected String CompStrA;
			protected String CompStrB;
			protected Set<String> CompStrSet;
			protected Pattern RegStrPtn;
			
			public static Collection<String> InputHelp() {
				Collection<String> HelpStr = new ArrayList<>();
				HelpStr.add(String.format("%c", LatchOp.ACCEPT.OpSym)); //$NON-NLS-1$
				HelpStr.add(String.format("%c", LatchOp.ISNULL.OpSym)); //$NON-NLS-1$
				HelpStr.add(String.format("%c\"string\"", LatchOp.EQUALTO.OpSym)); //$NON-NLS-1$
				HelpStr.add(String.format("%c\"string\"", LatchOp.GREATERTHAN.OpSym)); //$NON-NLS-1$
				HelpStr.add(String.format("%c\"string\"", LatchOp.LESSTHAN.OpSym)); //$NON-NLS-1$
				HelpStr.add(String.format("%c\"string1\",\"string2\"", LatchOp.INRANGE.OpSym)); //$NON-NLS-1$
				HelpStr.add(String.format("%c\"string1\"[,\"string2\",...]", LatchOp.ONEOF.OpSym)); //$NON-NLS-1$
				HelpStr.add(String.format("%c\"regexp\"", LatchOp.REGMATCH.OpSym)); //$NON-NLS-1$
				return HelpStr;
			}
			
			@Override
			protected void ParseValue(String condval) {
				switch (Op) {
					case ACCEPT:
					case ISNULL:
						ParseNoArgOperand(condval);
						break;
					case EQUALTO:
					case GREATERTHAN:
					case LESSTHAN:
						CompStrA = StrParser.parseOrFail(condval.trim());
						break;
					case INRANGE: {
						String[] CondVals = StrListSep.split(condval.trim());
						if (CondVals.length != 2) {
							Misc.ERROR(Messages.Localize("Debugging.ObjectTrap.BAD_PARAM_COUNT"), condval.trim()); //$NON-NLS-1$
						}
						CompStrA = StrParser.parseOrFail(CondVals[0].trim());
						CompStrB = StrParser.parseOrFail(CondVals[1].trim());
						if (CompStrA.compareTo(CompStrB) > 0) {
							Misc.ERROR(Messages.Localize("Debugging.ObjectTrap.INVALID_RANGE_STR"), //$NON-NLS-1$
									CompStrA, CompStrB);
						}
						break;
					}
					case ONEOF:
						CompStrSet = new HashSet<>();
						ParseSetList(condval, StrListSep, StrParser, CompStrSet);
						break;
					case REGMATCH:
						RegStrPtn = Pattern.compile(StrParser.parseOrFail(condval.trim()));
						break;
					default:
						Misc.FAIL(Messages.Localize("Debugging.ObjectTrap.UNKNOWN_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
				}
			}
			
			protected boolean Oper(String value) {
				switch (Op) {
					case ACCEPT:
						return true;
					case ISNULL:
						return value == null;
					case EQUALTO:
						return (value != null) && (value.compareTo(CompStrA) == 0);
					case GREATERTHAN:
						return (value != null) && (value.compareTo(CompStrA) > 0);
					case LESSTHAN:
						return (value != null) && (value.compareTo(CompStrA) < 0);
					case INRANGE:
						return (value != null)&& (value.compareTo(CompStrA) >= 0)
										&& (value.compareTo(CompStrB) <= 0);
					case ONEOF:
						return (value != null) && CompStrSet.contains(value);
					case REGMATCH:
						return (value != null) && RegStrPtn.matcher(value).find();
					default:
						Misc.FAIL(Messages.Localize("Debugging.ObjectTrap.UNKNOWN_OPERATOR"), Op.OpSym, //$NON-NLS-1$
								Op.name());
						return Negate;
				}
			}
			
			@Override
			public boolean Latch(Object value) {
				return StateFilter(Oper((String) value), value);
			}
			
			@Override
			public String toString() {
				StringBuilder StrBuf = new StringBuilder();
				StrBuf.append(super.toString());
				
				switch (Op) {
					case ACCEPT:
					case ISNULL:
						break;
					case EQUALTO:
					case GREATERTHAN:
					case LESSTHAN:
						StrBuf.append(" \"").append(CompStrA).append('"'); //$NON-NLS-1$
						break;
					case INRANGE:
						StrBuf.append(" [\"").append(CompStrA).append("\",\"").append(CompStrB).append("\"]"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						break;
					case ONEOF:
						StrBuf.append(" {"); //$NON-NLS-1$
						for (String I : CompStrSet) {
							StrBuf.append('"').append(I).append("\","); //$NON-NLS-1$
						}
						if (!CompStrSet.isEmpty()) {
							StrBuf.setCharAt(StrBuf.length() - 1, '}');
						}
						break;
					case REGMATCH:
						StrBuf.append(" /").append(RegStrPtn).append('/'); //$NON-NLS-1$
						break;
					default:
						Misc.FAIL(Messages.Localize("Debugging.ObjectTrap.UNKNOWN_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
				}
				return StrBuf.toString();
			}
			
		}
		
		public static class ObjectHook extends BaseHook {
			
			public ObjectHook(String condition, SuffixClassDictionary dict) {
				super(condition, dict);
			}
			
			protected static IParse<String, String> StrParser = Parsers.StringToString;
			protected static Pattern ClsListSep = Pattern.compile(","); //$NON-NLS-1$
			
			protected Set<Class<?>> CastClsSet;
			protected Pattern RegClsPtn;
			
			public static Collection<String> InputHelp() {
				Collection<String> HelpStr = new ArrayList<>();
				HelpStr.add(String.format("%c", LatchOp.ACCEPT.OpSym)); //$NON-NLS-1$
				HelpStr.add(String.format("%c", LatchOp.ISNULL.OpSym)); //$NON-NLS-1$
				HelpStr.add(String.format("%c<class1>[,<class2>,...]", LatchOp.ONEOF.OpSym)); //$NON-NLS-1$
				HelpStr.add(String.format("%c\"regexp\"", LatchOp.REGMATCH.OpSym)); //$NON-NLS-1$
				return HelpStr;
			}
			
			@Override
			protected void ParseValue(String condval) {
				switch (Op) {
					case ACCEPT:
					case ISNULL:
						ParseNoArgOperand(condval);
						break;
					case ONEOF: {
						String[] CondVals = ClsListSep.split(condval.trim());
						CastClsSet = new HashSet<>();
						for (String Val : CondVals) {
							try {
								if (!CastClsSet.add(Dict.Get(Val).toClass())) {
									Misc.ERROR(Messages.Localize("Debugging.ObjectTrap.DUPLICATE_PARAM"), Val); //$NON-NLS-1$
								}
							} catch (ClassNotFoundException e) {
								Misc.CascadeThrow(e);
							}
						}
						break;
					}
					case EQUALTO:
					case GREATERTHAN:
					case LESSTHAN:
					case INRANGE:
						Misc.ERROR(Messages.Localize("Debugging.ObjectTrap.UNSUPPORT_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
						break;
					case REGMATCH:
						RegClsPtn = Pattern.compile(StrParser.parseOrFail(condval.trim()));
						break;
					default:
						Misc.FAIL(Messages.Localize("Debugging.ObjectTrap.UNKNOWN_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
				}
			}
			
			protected boolean Oper(Object value) {
				switch (Op) {
					case ACCEPT:
						return true;
					case ISNULL:
						return value == null;
					case ONEOF:
						if (value != null) {
							for (Class<?> Cast : CastClsSet)
								if (Cast.isAssignableFrom(value.getClass())) return true;
						}
						return false;
					case REGMATCH:
						return (value != null) && RegClsPtn.matcher(value.getClass().getName()).find();
					default:
						Misc.FAIL(Messages.Localize("Debugging.ObjectTrap.UNKNOWN_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
						return Negate;
				}
			}
			
			@Override
			public boolean Latch(Object value) {
				return StateFilter(Oper(value), value);
			}
			
			@Override
			public String toString() {
				StringBuilder StrBuf = new StringBuilder();
				StrBuf.append(super.toString());
				
				switch (Op) {
					case ACCEPT:
					case ISNULL:
						break;
					case ONEOF:
						StrBuf.append(" {"); //$NON-NLS-1$
						for (Class<?> Cast : CastClsSet) {
							StrBuf.append(Cast.getName()).append(',');
						}
						if (!CastClsSet.isEmpty()) {
							StrBuf.setCharAt(StrBuf.length() - 1, '}');
						}
						break;
					case REGMATCH:
						StrBuf.append(" /").append(RegClsPtn).append('/'); //$NON-NLS-1$
						break;
					default:
						Misc.FAIL(Messages.Localize("Debugging.ObjectTrap.UNKNOWN_OPERATOR"), //$NON-NLS-1$
								Op.OpSym, Op.name());
				}
				return StrBuf.toString();
			}
			
		}
		
	}
	
	static {
		HookMaker.Register(String.class, BasicHooks.StringHook.class);
		HookMaker.Register(Object.class, BasicHooks.ObjectHook.class);
		HookMaker.Register(Integer.TYPE, BasicHooks.IntegerHook.class);
		HookMaker.Register(Long.TYPE, BasicHooks.LongHook.class);
		HookMaker.Register(Byte.TYPE, BasicHooks.ByteHook.class);
		HookMaker.Register(Short.TYPE, BasicHooks.ShortHook.class);
		HookMaker.Register(Float.TYPE, BasicHooks.FloatHook.class);
		HookMaker.Register(Double.TYPE, BasicHooks.DoubleHook.class);
		HookMaker.Register(Character.TYPE, BasicHooks.CharHook.class);
		HookMaker.Register(Boolean.TYPE, BasicHooks.BooleanHook.class);
		HookMaker.Register(Integer.class, BasicHooks.IntegerHook.class);
		HookMaker.Register(Long.class, BasicHooks.LongHook.class);
		HookMaker.Register(Byte.class, BasicHooks.ByteHook.class);
		HookMaker.Register(Short.class, BasicHooks.ShortHook.class);
		HookMaker.Register(Float.class, BasicHooks.FloatHook.class);
		HookMaker.Register(Double.class, BasicHooks.DoubleHook.class);
		HookMaker.Register(Boolean.class, BasicHooks.BooleanHook.class);
		HookMaker.Register(Character.class, BasicHooks.CharHook.class);
	}
	
	// Fork - Scope + Hook
	
	public interface IFork {
		
		static enum Result {
			MATCH,
			UNMATCH,
			ERROR
		}
		
		static class ExecRec {
			public Result State;
			public int FIPDelta;
		}
		
		String Name();
		
		void Stab(Object obj, ExecRec Context);
		
		boolean CustomScript();
		
	}
	
	@FunctionalInterface
	public static interface IForkScript {
		Object Exec(String N, IFork.Result R, Object O, Object S, IGroupLogger L);
	}
	
	protected Map<String, IForkScript> Scripts = null;
	
	public static interface IForkScriptCompiler {
		
		IForkScript Compile(String Name, File CodeCont);
		
	}
	
	public static interface IForkScriptExecuter {
		
		void ExecEnvPrep(Map<String, Object> Envs);
		
	}
	
	public static interface IForkScriptEngine extends IForkScriptCompiler, IForkScriptExecuter {}
	
	protected Map<String, IForkScriptEngine> ScriptEngines = new HashMap<>();
	protected Set<IForkScriptEngine> ActiveScriptEngines = new HashSet<>();
	
	IForkScript Script_AssignScope = (N, R, O, S, L) -> {
		if ((Scripts != null) && (R == IFork.Result.MATCH)) {
			ActiveScriptEngines.forEach(ENG -> {
				ENG.ExecEnvPrep(Collections.singletonMap(N, S));
			});
		}
		return null;
	};
	
	public class Fork implements IFork {
		
		protected final IGroupLogger ILog;
		
		public final String N;
		public final IScope S;
		public final IHook H;
		public final IForkScript FS;
		
		protected int ThisIP;
		protected int MatchNext;
		protected int UnmatchNext;
		
		public Fork(String name, IScope scope, IHook hook, IForkScript fs) {
			ILog = new GroupLogger.PerInst(ObjectTrap.this.ILog.GroupName() + '.' + name);
			N = name.intern();
			S = scope;
			H = hook;
			FS = fs;
			MatchNext = 0;
			UnmatchNext = 1;
		}
		
		@Override
		public void Stab(Object obj, ExecRec Context) {
			try {
				Object Scoped = S.Peek(obj);
				Throwable Error = S.LastError();
				
				if (Error == null) {
					if ((H != null) && !H.Latch(Scoped)) {
						Context.State = Result.UNMATCH;
					} else {
						Context.State = Result.MATCH;
					}
				} else {
					Context.State = Result.ERROR;
				}
				
				Context.FIPDelta = Next(Context.State);
				if (FS != null) {
					ExecuteForkScript(obj, Context, Scoped);
				}
			} catch (Exception e) {
				if (ILog.isLoggable(Level.FINER)) {
					ILog.logExcept(e, Messages.Localize("Debugging.ObjectTrap.HOOK_EXCEPTION")); //$NON-NLS-1$
				} else if (ILog.isLoggable(Level.FINE)) {
					ILog.Warn(Messages.Localize("Debugging.ObjectTrap.HOOK_EXCEPTION_LT"), e); //$NON-NLS-1$
				}
				Context.State = Result.ERROR;
				Context.FIPDelta = -100;
			}
		}
		
		private void ExecuteForkScript(Object obj, ExecRec Context, Object Scoped) {
			Object ScriptRet = FS.Exec(N, Context.State, obj, Scoped, ILog);
			if (ScriptRet != null) {
				if (ScriptRet instanceof Integer) {
					Context.FIPDelta = (int) ScriptRet;
				} else if (ScriptRet instanceof String) {
					Integer NextIP = TrapMap.get(ScriptRet);
					if (NextIP == null) {
						Misc.ERROR(Messages.Localize("Debugging.ObjectTrap.SCRIPT_RETURN_BADADDR"), //$NON-NLS-1$
								ScriptRet);
					}
					Context.FIPDelta = NextIP - ThisIP;
				} else {
					Misc.ERROR(Messages.Localize("Debugging.ObjectTrap.SCRIPT_RETURN_UNKNOWN"), //$NON-NLS-1$
							ScriptRet);
				}
			}
		}
		
		public int Next(Result R) {
			switch (R) {
				case MATCH:
					return MatchNext;
				case UNMATCH:
				case ERROR:
					return UnmatchNext;
				default:
					Misc.FAIL(Messages.Localize("Debugging.ObjectTrap.UNKNOWN_FORK_RET"), R.name()); //$NON-NLS-1$
			}
			return -1;
		}
		
		@Override
		public boolean CustomScript() {
			return FS != null;
		}
		
		@Override
		public String Name() {
			return N;
		}
		
		@Override
		public String toString() {
			StringBuilder StrBuf = new StringBuilder();
			StrBuf.append(Messages.Localize("Debugging.ObjectTrap.FORK_ACTION_ITEM")) //$NON-NLS-1$
					.append(N).append(": ");  //$NON-NLS-1$
			String ScopeStr = S.toString();
			boolean MultilineScope = ScopeStr.indexOf(System.lineSeparator()) >= 0;
			if (MultilineScope) {
				StrBuf.append(System.lineSeparator());
			}
			StrBuf.append(ScopeStr);
			if (MultilineScope) {
				StrBuf.append(System.lineSeparator());
			} else {
				StrBuf.append(' ');
			}
			StrBuf.append(Messages.Localize("Debugging.ObjectTrap.FORK_ACTION_EVAL")) //$NON-NLS-1$
					.append(H.toString());
			if (MultilineScope) {
				StrBuf.append(System.lineSeparator());
			} else {
				StrBuf.append(' ');
			}
			if (MatchNext == 0) {
				StrBuf.append(Messages.Localize("Debugging.ObjectTrap.FORK_ACTION_ACCEPT")); //$NON-NLS-1$
			} else if (MatchNext == 1) {
				StrBuf.append(Messages.Localize("Debugging.ObjectTrap.FORK_ACTION_NEXT")); //$NON-NLS-1$
			} else {
				StrBuf.append(Messages.Localize("Debugging.ObjectTrap.FORK_ACTION_SKIP")) //$NON-NLS-1$
						.append(MatchNext - 1);
			}
			StrBuf.append(Messages.Localize("Debugging.ObjectTrap.FORK_ACTION_OTHERWISE")); //$NON-NLS-1$
			if (UnmatchNext == 1) {
				StrBuf.append(Messages.Localize("Debugging.ObjectTrap.FORK_ACTION_NEXT")); //$NON-NLS-1$
			} else {
				StrBuf.append(Messages.Localize("Debugging.ObjectTrap.FORK_ACTION_SKIP")) //$NON-NLS-1$
						.append(UnmatchNext - 1);
			}
			return StrBuf.toString();
		}
		
	}
	
	List<IFork> Trap = null;
	Map<String, Integer> TrapMap = null;
	
	@SuppressWarnings("serial")
	public static class SuffixClassNameSolver extends ArrayList<String> {
		
		protected final String CName;
		protected int Level = 1;
		
		protected static final Pattern ClassTok = Pattern.compile("\\."); //$NON-NLS-1$
		
		public SuffixClassNameSolver(String cname) {
			super();
			
			CName = cname;
			addAll(Arrays.asList(ClassTok.split(cname)));
		}
		
		public void NotUnique() {
			if (Level >= size()) {
				Misc.FAIL(IllegalStateException.class,
						Messages.Localize("Debugging.ObjectTrap.ALREADY_AT_ROOT")); //$NON-NLS-1$
			}
			Level++;
		}
		
		public Class<?> toClass() throws ClassNotFoundException {
			return Class.forName(CName);
		}
		
		@Override
		public String toString() {
			StringBuilder StrBuf = new StringBuilder();
			for (int i = Level; i > 0; i--) {
				if (i < Level) {
					StrBuf.append('.');
				}
				StrBuf.append(get(size() - i));
			}
			return StrBuf.toString();
		}
		
	}
	
	public ObjectTrap(Class<?> c) {
		this(c, c.getPackage());
	}
	
	public ObjectTrap(Class<?> c, Package pkg) {
		this(c, pkg.getName());
	}
	
	public ObjectTrap(Class<?> c, String pkgname) {
		this(c, pkgname, c.getClassLoader());
	}
	
	public ObjectTrap(Class<?> c, String pkgname, ClassLoader loader) {
		ILog = new GroupLogger.PerInst(LOGGROUP + '@' + c.getSimpleName());
		ObjClass = c;
		
		ClassDict = new SuffixClassDictionary(pkgname, loader);
		DirectSuffixClassSolver.EnumBaseClassSolvers(ClassDict::Add);
		try {
			PackageClassIterable.Create(pkgname, loader).forEach(ClassDict::Add);
		} catch (IOException e) {
			Misc.CascadeThrow(e);
		}
		
		ForkScriptEngineInit(this);
	}
	
	public void RegisterScriptEngine(String Name, IForkScriptEngine ENG) {
		ILog.Fine("Installing ForkScript addon '%s'...", Name);
		IForkScriptEngine Existing = ScriptEngines.putIfAbsent(Name, ENG);
		if (Existing != null) {
			Misc.ERROR("Addon '%s' already assigned to '%s'", Name, Existing);
		}
	}
	
	@FunctionalInterface
	public static interface IForkScriptEngineFactory {
		void Register(ObjectTrap OT);
	}
	
	protected static Collection<IForkScriptEngineFactory> ForkScriptEngineLoader = new ArrayList<>();
	
	protected static final String ADDON_PACKAGE = "com.necla.am.zwutils.addon.ObjectTrap";
	static {
		try {
			for (String CName : PackageClassIterable.Create(ADDON_PACKAGE, ClassSolver -> {
				try {
					return IForkScriptEngineFactory.class.isAssignableFrom(ClassSolver.toClass());
				} catch (Exception e) {
					// Eat exception
					return false;
				}
			})) {
				CLog.Info("Found ForkScript addon class '%s'...", CName);
				LoadAddon(CName);
			}
		} catch (ProviderNotFoundException e) {
			CLog.Info("No ForkScript addon present");
		} catch (Exception e) {
			CLog.Warn("Unable to scan ForkScript addon class - %s", e);
			if (GlobalConfig.DEBUG_CHECK) {
				CLog.logExcept(e);
			}
		}
	}
	
	private static void LoadAddon(String CName) {
		try {
			@SuppressWarnings("unchecked")
			Class<? extends IForkScriptEngineFactory> CLS =
					(Class<? extends IForkScriptEngineFactory>) Class.forName(CName);
			ForkScriptEngineLoader.add(CLS.newInstance());
		} catch (Exception e) {
			CLog.Warn("Unable to instantiate ForkScript addon class '%s' - %s", CName, e);
			if (GlobalConfig.DEBUG_CHECK) {
				CLog.logExcept(e);
			}
		}
	}
	
	protected static void ForkScriptEngineInit(ObjectTrap OT) {
		ForkScriptEngineLoader.forEach(Init -> Init.Register(OT));
	}
	
	@FunctionalInterface
	public static interface ITrapNotifiable {
		void Trapped(Object obj, IFork f);
	}
	
	public void Flow(Object obj, ITrapNotifiable notifier) {
		List<IFork> InstTrap = Trap;
		if (InstTrap == null) return;
		
		int FIP = 0;
		IFork.ExecRec Context = new IFork.ExecRec();
		while (FIP < InstTrap.size()) {
			IFork F = InstTrap.get(FIP);
			F.Stab(obj, Context);
			
			if (Context.FIPDelta <= 0) {
				if (Context.FIPDelta == 0) {
					notifier.Trapped(obj, F);
				}
				break;
			}
			FIP += Context.FIPDelta;
		}
	}
	
	public static final char SYM_CGROUP = '$';
	
	public static final char SYM_ASCLASS = '?';
	public static final char SYM_ASTYPE = '!';
	
	public static final char SYM_FIELD = '@';
	public static final char SYM_GETTER = '>';
	
	public static final char SYM_SCOPES = '+';
	public static final char SYM_SCOPEOP = ':';
	
	public static final Pattern DEM_CGROUP = Pattern.compile("\\$"); //$NON-NLS-1$
	public static final Pattern DEM_SCOPEOP =
			Pattern.compile("((?<![\\\\]):)(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"); //$NON-NLS-1$
	public static final Pattern DEM_ASCLASS = Pattern.compile("[@>]"); //$NON-NLS-1$
	public static final Pattern PATTERN_SYNTAX_V1 = Pattern.compile("([^+]+)\\+?"); //$NON-NLS-1$
	public static final Pattern PATTERN_SYNTAX_V2 =
			Pattern.compile("(!.|(\\?[^!?@>]+)?[>.][^!?@>]+)"); //$NON-NLS-1$
	
	protected CanonicalCacheMap<String, IScope> ScopePathCache =
			new CanonicalCacheMap.Classic<>("OTap-ScopePath"); //$NON-NLS-1$
	
	protected IScope CreateScopePath(String ScopePath) {
		return ScopePathCache.Query(ScopePath, scopepath -> {
			Class<?> BaseClass = ObjClass;
			IScope Scope = new DummyScope(ObjClass);
			CascadeScope CScope = new CascadeScope();
			
			Pattern ScopeSplitter =
					scopepath.indexOf(SYM_SCOPES) >= 0? PATTERN_SYNTAX_V1 : PATTERN_SYNTAX_V2;
			
			Matcher DescMatcher = ScopeSplitter.matcher(scopepath);
			while (DescMatcher.find()) {
				Scope = CreateScope(DescMatcher.group(1), BaseClass);
				CScope.Cascade(Scope);
				BaseClass = Scope.Type();
			}
			if (!DescMatcher.hitEnd()) {
				Misc.FAIL(Messages.Localize("Debugging.ObjectTrap.SCOPE_PARSE_FAILURE"), //$NON-NLS-1$
						scopepath.substring(DescMatcher.end()));
			}
			return (CScope.Size() > 1)? CScope : Scope;
		});
	}
	
	protected static class ScopeContext {
		
		public final String ScopeDesc;
		public final Class<?> BaseClass;
		
		public ScopeContext(String ScopeDesc, Class<?> BaseClass) {
			this.ScopeDesc = ScopeDesc;
			this.BaseClass = BaseClass;
		}
		
		@Override
		public int hashCode() {
			return BaseClass.hashCode() ^ ScopeDesc.hashCode();
		}
		
		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			// PERF: The usage context determines obj is always a valid ScopeContext instance
			//if (obj == null) return false;
			//if (ScopeContext.class.isAssignableFrom(obj.getClass())) return false;
			
			ScopeContext sobj = (ScopeContext) obj;
			return BaseClass.equals(sobj.BaseClass) && ScopeDesc.equals(sobj.ScopeDesc);
		}
		
	}
	
	protected CanonicalCacheMap<ScopeContext, IScope> ScopeCache =
			new CanonicalCacheMap.Classic<>("OTap-Scope"); //$NON-NLS-1$
	
	protected IScope CreateScope(String ScopeDesc, Class<?> BaseClass) {
		return ScopeCache.Query(new ScopeContext(ScopeDesc, BaseClass), Key -> {
			String cname = null;
			if (Key.ScopeDesc.charAt(0) == SYM_ASTYPE) {
				if (Key.ScopeDesc.length() != 2) {
					Misc.ERROR(Messages.Localize("Debugging.ObjectTrap.BAD_TYPE_SCOPE_DESC"), Key.ScopeDesc); //$NON-NLS-1$
				}
				return new TypeCastScope(Key.ScopeDesc.charAt(1));
			}
			
			String Desc;
			if (Key.ScopeDesc.charAt(0) == SYM_ASCLASS) {
				String[] token = DEM_ASCLASS.split(Key.ScopeDesc);
				if (token.length > 2) {
					Misc.ERROR(Messages.Localize("Debugging.ObjectTrap.BAD_SCOPE_DESC"), Key.ScopeDesc); //$NON-NLS-1$
				}
				
				cname = token[0].substring(1);
				Desc = Key.ScopeDesc.substring(token[0].length());
			} else {
				Desc = Key.ScopeDesc;
			}
			
			try {
				switch (Desc.charAt(0)) {
					case SYM_FIELD:
						return CreateFieldScope(Key, cname, Desc);
					case SYM_GETTER:
						return CreateGetterScope(Key, cname, Desc);
					default:
						Misc.ERROR(Messages.Localize("Debugging.ObjectTrap.UNKNOWN_SCOPE_MEMBER"), Desc); //$NON-NLS-1$
				}
			} catch (Exception e) {
				Misc.CascadeThrow(e);
			}
			return null;
		});
	}
	
	private IScope CreateGetterScope(ScopeContext Key, String cname, String Desc)
			throws ClassNotFoundException {
		try {
			// Try create a faster scope, but cannot access private method
			return new ASMGetterScope(Key.BaseClass, cname, Desc.substring(1));
		} catch (Exception e) {
			return new GetterScope(Key.BaseClass, cname, Desc.substring(1));
		}
	}
	
	private IScope CreateFieldScope(ScopeContext Key, String cname, String Desc)
			throws ClassNotFoundException {
		try {
			// Try create a faster scope, but cannot access private fields
			return new ASMFieldScope(Key.BaseClass, cname, Desc.substring(1));
		} catch (Exception e) {
			return new FieldScope(Key.BaseClass, cname, Desc.substring(1));
		}
	}
	
	protected IForkScript ParseProcDesc(String Name, String Desc, Map<String, IForkScript> _Scripts,
			Set<IForkScriptEngine> _Engines) {
		if (Desc.isEmpty()) return Script_AssignScope;
		
		IForkScript Ret = _Scripts.get(Name);
		if (Ret == null) {
			IForkScriptEngine Engine = ScriptEngines.get(Misc.stripPackageName(Desc).toUpperCase());
			if (Engine != null) {
				Ret = Engine.Compile(Desc, new File(Desc));
				_Scripts.put(Name, Ret);
				_Engines.add(Engine);
			} else {
				Misc.ERROR("Unrecognized extension for script file '%s'", Desc);
			}
		}
		return Ret;
	}
	
	public void Update(DataMap config) {
		List<IFork> InstTrap = new ArrayList<>();
		Map<String, Integer> InstTrapMap = new HashMap<>();
		Set<IForkScriptEngine> InstEngines = new HashSet<>();
		Map<String, IForkScript> InstScripts = new HashMap<>();
		Map<String, Collection<IFork>> ForkGroups = new HashMap<>();
		ILog.Config(Messages.Localize("Debugging.ObjectTrap.TRAP_LOAD_START")); //$NON-NLS-1$
		for (String name : new TreeSet<>(config.keySet())) {
			LoadFork(config, InstTrap, InstTrapMap, InstEngines, InstScripts, ForkGroups, name);
		}
		for (Collection<IFork> FG : ForkGroups.values()) {
			int GIdx = FG.size();
			for (IFork F : FG) {
				Fork GF = (Fork) F;
				GF.MatchNext = GIdx > 1? 1 : 0;
				GF.UnmatchNext = GIdx > 1? GIdx : 1;
				ILog.Config(Messages.Localize("Debugging.ObjectTrap.FORK_GROUP_LABEL"), GF); //$NON-NLS-1$
				InstTrap.add(GF);
				GF.ThisIP = InstTrap.size();
				InstTrapMap.put(GF.N, InstTrap.size());
				GIdx--;
			}
		}
		ILog.Config(Messages.Localize("Debugging.ObjectTrap.TRAP_LOAD_FINISH"), //$NON-NLS-1$
				InstTrap.size(), ForkGroups.size());
		Trap = InstTrap.isEmpty()? null : InstTrap;
		TrapMap = InstTrapMap.isEmpty()? null : InstTrapMap;
		Scripts = InstScripts.isEmpty()? null : InstScripts;
		ActiveScriptEngines = InstEngines.isEmpty()? null : InstEngines;
	}
	
	private void LoadFork(DataMap config, List<IFork> InstTrap, Map<String, Integer> InstTrapMap,
			Set<IForkScriptEngine> InstEngines, Map<String, IForkScript> InstScripts,
			Map<String, Collection<IFork>> ForkGroups, String name) {
		String[] keytoks = DEM_CGROUP.split(name, 2);
		String Group = keytoks.length > 1? keytoks[0] : null;
		String[] payload = DEM_SCOPEOP.split(config.getText(name).trim(), 3);
		
		try {
			IScope Scope = CreateScopePath(payload[0].trim());
			IHook Hook =
					payload.length > 1? HookMaker.Create(Scope.Type(), payload[1].trim(), ClassDict) : null;
			IForkScript Script = payload.length > 2? ParseProcDesc(name, payload[2].trim(), InstScripts,
					InstEngines) : null;
			Fork F = new Fork(name, Scope, Hook, Script);
			if (Group != null) {
				if (!ForkGroups.containsKey(Group)) {
					ForkGroups.put(Group, new Stack<>());
				}
				Collection<IFork> FG = ForkGroups.get(Group);
				FG.add(F);
			} else {
				InstTrap.add(F);
				F.ThisIP = InstTrap.size();
				InstTrapMap.put(name, InstTrap.size());
			}
			ILog.Config(":%s", F); //$NON-NLS-1$
		} catch (Exception e) {
			Misc.CascadeThrow(e, Messages.Localize("Debugging.ObjectTrap.FORK_GENERAL_CREATE_FAILURE"), //$NON-NLS-1$
					name);
		}
	}
	
	public int Count() {
		List<IFork> InstTrap = Trap;
		if (InstTrap == null) return 0;
		
		return InstTrap.size();
	}
	
	public abstract static class Dynamic implements AutoCloseable, ITrapNotifiable {
		
		protected final IGroupLogger ILog;
		public static final int DEF_POLLINTERVAL = 5;
		
		protected final ObjectTrap TheTrap;
		
		public Dynamic(Class<?> c, String ConfigLogName) {
			this(new ObjectTrap(c), ConfigLogName);
		}
		
		public Dynamic(Class<?> c, Package pkg, String ConfFileName) {
			this(new ObjectTrap(c, pkg), ConfFileName);
		}
		
		public Dynamic(Class<?> c, String pkgname, String ConfFileName) {
			this(new ObjectTrap(c, pkgname), ConfFileName);
		}
		
		public Dynamic(Class<?> c, String pkgname, ClassLoader loader, String ConfFileName) {
			this(new ObjectTrap(c, pkgname, loader), ConfFileName);
		}
		
		protected Dynamic(ObjectTrap Trap, String ConfFileName) {
			this(Trap, ConfFileName, DEF_POLLINTERVAL);
		}
		
		ITask.TaskRun Watcher = null;
		
		public synchronized void WatchConfig(String WatchFileName, int PollInterval) {
			if (Watcher != null) {
				if (WatchFileName != null) {
					Misc.FAIL(Messages.Localize("Debugging.ObjectTrap.CONFIG_WATCH_DEFINED")); //$NON-NLS-1$
				}
				try {
					Watcher.Stop(-1);
				} catch (InterruptedException e) {
					ILog.logExcept(e, Messages.Localize("Debugging.ObjectTrap.CONFIG_WATCH_STOP_ERROR")); //$NON-NLS-1$
					Thread.currentThread().interrupt();
				}
				Watcher = null;
			} else {
				if (WatchFileName == null) {
					ILog.Warn(Messages.Localize("Debugging.ObjectTrap.CONFIG_WATCH_UNDEFINED")); //$NON-NLS-1$
					return;
				}
				
				StartConfigWatcher(WatchFileName, PollInterval);
			}
		}
		
		private void StartConfigWatcher(String WatchFileName, int PollInterval) {
			Poller ConfigPoller = CreatePollerTask(WatchFileName);
			
			try {
				ConfigPoller
						.setConfiguration(String.format("%s=%d", Poller.ConfigData.Mutable.CONFIG_TIMERES, //$NON-NLS-1$
								TimeUnit.SEC.Convert(PollInterval, TimeUnit.MSEC)), ""); //$NON-NLS-1$
			} catch (Exception e) {
				Misc.CascadeThrow(e);
			}
			
			Watcher = new DaemonRunner(ConfigPoller);
			try {
				Watcher.Start(-1);
			} catch (InterruptedException e) {
				ILog.logExcept(e, Messages.Localize("Debugging.ObjectTrap.CONFIG_WATCH_START_FAILED")); //$NON-NLS-1$
				while (!Watcher.tellState().hasTerminated()) {
					if (Thread.interrupted()) {
						ILog.Warn(Messages.Localize("Debugging.ObjectTrap.CONFIG_LOAD_STOP_PROGRESS")); //$NON-NLS-1$
					}
					try {
						Watcher.Stop(-1);
					} catch (InterruptedException e1) {
						Thread.currentThread().interrupt();
					}
				}
				Thread.currentThread().interrupt();
				Watcher = null;
			}
		}
		
		private Poller CreatePollerTask(String WatchFileName) {
			return new Poller(ILog.GroupName()) {
				
				ITimeStamp ConfigTS = new ITimeStamp.Impl(0);
				
				@Override
				protected boolean Poll() {
					DataFile TapConfig = new DataFile(ILog.GroupName(), WatchFileName);
					ITimeStamp TapTS = TapConfig.lastModified();
					if (!TapTS.equals(ConfigTS)) {
						ILog.Info(Messages.Localize("Debugging.ObjectTrap.CONFIG_LOAD_START"),  //$NON-NLS-1$
								WatchFileName, TapTS);
						try {
							ConfigTS = TapTS;
							DataMap TapDesc = new DataMap(TapConfig);
							TapDesc.SetEnvSubstitution(false);
							TheTrap.Update(TapDesc);
							ILog.Info(Messages.Localize("Debugging.ObjectTrap.CONFIG_LOAD_FINISH"), //$NON-NLS-1$
									TheTrap.Count());
							Configured(TheTrap.Count());
						} catch (Exception e) {
							ILog.logExcept(e, Messages.Localize("Debugging.ObjectTrap.CONFIG_LOAD_FAILED")); //$NON-NLS-1$
						}
					}
					return true;
				}
				
			};
		}
		
		public void Configured(int ForkCount) {
			if (ForkCount > 0) {
				ILog.Info(Messages.Localize("Debugging.ObjectTrap.LOG_BANNER_NEW_TRAP"), //$NON-NLS-1$
						ForkCount);
			} else {
				ILog.Info(Messages.Localize("Debugging.ObjectTrap.LOG_BANNER_NO_TRAP")); //$NON-NLS-1$
			}
		}
		
		protected Dynamic(ObjectTrap Trap, String ConfFileName, int PollInterval) {
			ILog = new GroupLogger(LOGGROUP + '-' + ConfFileName);
			
			TheTrap = Trap;
			WatchConfig(ConfFileName + ".config", PollInterval); //$NON-NLS-1$
			
		}
		
		@Override
		public void close() {
			WatchConfig(null, 0);
		}
		
		protected AtomicLong nanoRunInst = new AtomicLong(0);
		protected AtomicLong nanoRunTime = new AtomicLong(0);
		
		public void Flow(Object obj) {
			nanoRunInst.incrementAndGet();
			long StartTime = System.nanoTime();
			TheTrap.Flow(obj, this);
			long runTime = nanoRunTime.addAndGet(System.nanoTime() - StartTime);
			if ((runTime > 1000000000) && nanoRunTime.compareAndSet(runTime, 0)) {
				long runInst = nanoRunInst.getAndSet(0);
				ILog.Info(Messages.Localize("Debugging.ObjectTrap.PROCESSED_PER_INTERVAL"), runInst, //$NON-NLS-1$
						runTime / 1000000);
			}
		}
		
	}
	
	public static class EasyLogger extends Dynamic {
		
		protected final String LogGroupName;
		protected GroupLogger TrapLogger = null;
		protected Handler LogHandler = null;
		
		public EasyLogger(Class<?> c, String ConfigLogName) {
			this(new ObjectTrap(c), ConfigLogName);
		}
		
		public EasyLogger(Class<?> c, Package pkg, String ConfFileName) {
			this(new ObjectTrap(c, pkg), ConfFileName);
		}
		
		public EasyLogger(Class<?> c, String pkgname, String ConfFileName) {
			this(new ObjectTrap(c, pkgname), ConfFileName);
		}
		
		public EasyLogger(Class<?> c, String pkgname, ClassLoader loader, String ConfFileName) {
			this(new ObjectTrap(c, pkgname, loader), ConfFileName);
		}
		
		protected EasyLogger(ObjectTrap Trap, String ConfFileName) {
			this(Trap, ConfFileName, DEF_POLLINTERVAL);
		}
		
		protected EasyLogger(ObjectTrap Trap, String ConfFileName, int PollInterval) {
			super(Trap, ConfFileName, PollInterval);
			LogGroupName = ConfFileName;
		}
		
		@Override
		public void close() {
			super.close();
			StopLogging();
		}
		
		public synchronized void StopLogging() {
			if (TrapLogger != null) {
				TrapLogger.setHandler(null, false);
				LogHandler.close();
				LogHandler = null;
				TrapLogger = null;
			}
		}
		
		@Override
		public void Configured(int ForkCount) {
			if (ForkCount > 0) {
				if (TrapLogger == null) {
					TrapLogger = new GroupLogger(LogGroupName);
					TrapLogger.setLevel(Level.INFO);
					Support.GroupLogFile LogGroupFile = new Support.GroupLogFile(LogGroupName + ".log", //$NON-NLS-1$
							EnumSet.of(Support.GroupLogFile.Feature.APPEND));
					LogHandler = DebugLog.createFileHandler(LogGroupFile, TrapLogger.GroupName());
					TrapLogger.setHandler(LogHandler, false);
					LogFormatter.ConfigHandler.SendConfigurationMsg(TrapLogger, //
							SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_GROUPWIDTH, "0"); //$NON-NLS-1$
					LogFormatter.ConfigHandler.SendConfigurationMsg(TrapLogger, //
							SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_METHODWIDTH, "0"); //$NON-NLS-1$
				}
				TrapLogger.Warn(Messages.Localize("Debugging.ObjectTrap.LOG_BANNER_NEW_TRAP"), //$NON-NLS-1$
						ForkCount);
			} else {
				if (TrapLogger != null) {
					TrapLogger.Warn(Messages.Localize("Debugging.ObjectTrap.LOG_BANNER_NO_TRAP")); //$NON-NLS-1$
				}
				StopLogging();
			}
		}
		
		@Override
		public void Trapped(Object obj, IFork f) {
			if (!f.CustomScript()) {
				TrapLogger.Info(Messages.Localize("Debugging.ObjectTrap.LOG_PREFIX"), f.Name(), obj); //$NON-NLS-1$
			}
		}
		
	}
	
}
