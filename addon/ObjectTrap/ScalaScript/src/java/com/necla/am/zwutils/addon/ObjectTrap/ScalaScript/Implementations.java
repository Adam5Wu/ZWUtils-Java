
package com.necla.am.zwutils.addon.ObjectTrap.ScalaScript;

import java.io.File;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.necla.am.zwutils.Debugging.ObjectTrap.IFork;
import com.necla.am.zwutils.Debugging.ObjectTrap.IForkScript;
import com.necla.am.zwutils.Debugging.ObjectTrap.IForkScriptEngine;
import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.i18n.Messages;

import scala.collection.Iterator;
import scala.collection.JavaConversions;
import scala.collection.Seq;
import scala.io.Codec;
import scala.reflect.internal.util.AbstractFileClassLoader;
import scala.reflect.internal.util.BatchSourceFile;
import scala.reflect.internal.util.SourceFile;
import scala.reflect.io.AbstractFile;
import scala.runtime.AbstractFunction1;
import scala.runtime.BoxedUnit;
import scala.tools.nsc.GenericRunnerSettings;
import scala.tools.nsc.interpreter.IMain;


public class Implementations {
	
	//------
	// Native script environment
	//------
	
	public static class CoObject implements IForkScriptEngine {
		
		public static final String LogGroup = "ZWUtils.Addon.ObjectTrap.ScalaScript";
		static final IGroupLogger CLog = new GroupLogger(LogGroup);
		
		protected ConcurrentMap<String, Object> _N_ScriptGlobals = new ConcurrentHashMap<>();
		protected ThreadLocal<Map<String, Object>> _N_ScriptLocals =
				new ThreadLocal<Map<String, Object>>() {
					@Override
					protected Map<String, Object> initialValue() {
						return new HashMap<>();
					}
				};
		
		@FunctionalInterface
		protected static interface _N_IScriptExecPrep {
			Map<String, Object> Perform(Map<String, Object> ScriptLocal);
		}
		
		protected Map<String, Object> _N_ScriptPrep(_N_IScriptExecPrep Prep) {
			return Prep.Perform(_N_ScriptLocals.get());
		}
		
		public class NativeForkScript implements IForkScript {
			
			final INativeForkScript Code;
			final Set<IFork.Result> Filter;
			
			public NativeForkScript(INativeForkScript code) {
				this(code, EnumSet.of(IFork.Result.Match));
			}
			
			public NativeForkScript(INativeForkScript code, Set<IFork.Result> filter) {
				Code = code;
				Filter = filter;
			}
			
			@Override
			public Object Exec(String N, IFork.Result R, Object O, Object S, IGroupLogger L) {
				if (Filter.contains(R))
					return Code.Exec(N, R, O, S, L, _N_ScriptGlobals, _N_ScriptLocals.get());
				return null;
			}
			
		}
		
		protected class _S_ErrorHandler extends AbstractFunction1<String, BoxedUnit> {
			@Override
			public BoxedUnit apply(String message) {
				CLog.Warn("Scala interpreter error: " + message);
				return BoxedUnit.UNIT;
			}
		}
		
		protected IMain _S_NewInterpreter() {
			// Setup the compiler/interpreter
			GenericRunnerSettings settings = new GenericRunnerSettings(new _S_ErrorHandler());
			
			// In scala this is settings.usejavacp.value = true;
			// It it through this setting that the compiled code is able to reference the
			// `MustConform` interface. The runtime classpath leaks into the compiler classpath, but
			// we're OK with that in this use case.
			settings.usejavacp().v_$eq(true);
			return new IMain(settings);
		}
		
		@Override
		public IForkScript Compile(String Name, File CodeCont) {
			try {
				CLog.Config(Messages.Localize("Debugging.ObjectTrap.SCRIPT_COMPILE_FILE"), //$NON-NLS-1$
						CodeCont.getName());
				List<SourceFile> sources = Collections.singletonList(new BatchSourceFile(
						AbstractFile.getFile(new scala.reflect.io.File(CodeCont, Codec.UTF8()))));
				Seq<SourceFile> seq = JavaConversions.collectionAsScalaIterable(sources).toSeq();
				
				IMain Interpreter = _S_NewInterpreter();
				Interpreter.compileSources(seq);
				
				AbstractFileClassLoader CL = Interpreter.classLoader();
				AbstractFile CLRoot = CL.root();
				Iterator<AbstractFile> it = CLRoot.iterator();
				while (it.hasNext()) {
					AbstractFile classFile = it.next();
					String name = classFile.name().replace(".class", "");
					Class<?> clazz = CL.findClass(name);
					if (INativeForkScript.class.isAssignableFrom(clazz))
						return new NativeForkScript((INativeForkScript) clazz.newInstance());
				}
				Misc.FAIL("No class found implements 'INativeForkScript' interface");
			} catch (Throwable e) {
				Misc.CascadeThrow(e, Messages.Localize("Debugging.ObjectTrap.SCRIPT_COMPILE_FAILURE"), //$NON-NLS-1$
						Name);
			}
			return null;
		}
		
		@Override
		public void ExecEnvPrep(Map<String, Object> Envs) {
			_N_ScriptPrep(Local -> {
				Local.putAll(Envs);
				return Local;
			});
		}
		
	}
	
}
