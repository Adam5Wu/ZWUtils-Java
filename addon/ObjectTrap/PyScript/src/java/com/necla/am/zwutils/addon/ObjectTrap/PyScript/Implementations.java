
package com.necla.am.zwutils.addon.ObjectTrap.PyScript;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.python.core.CompileMode;
import org.python.core.CompilerFlags;
import org.python.core.Py;
import org.python.core.PyCode;
import org.python.core.PyInteger;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.python.core.PySystemState;

import com.necla.am.zwutils.Debugging.ObjectTrap.IFork;
import com.necla.am.zwutils.Debugging.ObjectTrap.IForkScript;
import com.necla.am.zwutils.Debugging.ObjectTrap.IForkScriptEngine;
import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.i18n.Messages;


public class Implementations {
	
	//------
	// Python script environment
	//------
	
	static {
		Properties props = new Properties();
		
		//props.put("python.home","path to the Lib folder");
		props.put("python.import.site", "false"); //$NON-NLS-1$ //$NON-NLS-2$
		
		// Used to prevent: console: Failed to install '': java.nio.charset.UnsupportedCharsetException: cp0.
		props.put("python.console.encoding", "UTF-8"); //$NON-NLS-1$ //$NON-NLS-2$
		
		// Don't respect java accessibility, so that we can access protected members on subclasses
		//props.put("python.security.respectJavaAccessibility", "false");
		
		PySystemState.initialize(System.getProperties(), props, new String[0]);
	}
	
	public static class CoObject implements IForkScriptEngine {
		
		public static final String LogGroup = "ZWUtils.Addon.ObjectTrap.PyScript";
		static final IGroupLogger CLog = new GroupLogger(LogGroup);
		
		protected ThreadLocal<PySystemState> _Py_ScriptSystemStates = new ThreadLocal<PySystemState>() {
			@Override
			protected PySystemState initialValue() {
				return new PySystemState();
			}
		};
		protected PyObject _Py_ScriptGlobals = Py.newStringMap();
		protected ThreadLocal<PyObject> _Py_ScriptLocals = new ThreadLocal<PyObject>() {
			@Override
			protected PyObject initialValue() {
				return Py.newStringMap();
			}
		};
		protected CompilerFlags _Py_ScriptCFlags = new CompilerFlags();
		
		protected PyCode _Py_ScriptCompile(String Name, InputStream CodeStr) {
			return Py.compile_flags(CodeStr, Name, CompileMode.exec, _Py_ScriptCFlags);
		}
		
		@FunctionalInterface
		protected static interface _Py_IScriptExecPrep {
			PyObject Perform(PyObject ScriptLocal);
		}
		
		protected PyObject _Py_ScriptPrep(_Py_IScriptExecPrep Prep) {
			return Prep.Perform(_Py_ScriptLocals.get());
		}
		
		public class PythonForkScript implements IForkScript {
			
			final PyCode Code;
			final Set<IFork.Result> Filter;
			
			public PythonForkScript(PyCode code) {
				this(code, EnumSet.of(IFork.Result.Match));
			}
			
			public PythonForkScript(PyCode code, Set<IFork.Result> filter) {
				Code = code;
				Filter = filter;
			}
			
			@Override
			public Object Exec(String N, IFork.Result R, Object O, Object S, IGroupLogger L) {
				if (Filter.contains(R)) {
					Py.setSystemState(_Py_ScriptSystemStates.get());
					PyObject ScriptLocals = _Py_ScriptPrep(Local -> {
						Local.__setitem__("TAP_RESULT", Py.java2py(R.name())); //$NON-NLS-1$
						Local.__setitem__("TAP_OBJECT", Py.java2py(O)); //$NON-NLS-1$
						if (R == IFork.Result.Match) {
							Local.__setitem__("TAP_SCOPED", Py.java2py(S)); //$NON-NLS-1$
						} else {
							Local.__setitem__("TAP_SCOPED", null); //$NON-NLS-1$
						}
						Local.__setitem__("LOG", Py.java2py(L)); //$NON-NLS-1$
						Local.__setitem__("NEXT", null); //$NON-NLS-1$
						return Local;
					});
					Py.exec(Code, _Py_ScriptGlobals, ScriptLocals);
					Py.flushLine();
					
					PyObject ScriptRet = ScriptLocals.__finditem__("NEXT"); //$NON-NLS-1$
					
					if (ScriptRet instanceof PyInteger) return ScriptRet.asInt();
					if (ScriptRet instanceof PyString) return ScriptRet.asString();
				}
				return null;
			}
			
		}
		
		@Override
		public IForkScript Compile(String Name, File CodeCont) {
			try {
				CLog.Config(Messages.Localize("Debugging.ObjectTrap.SCRIPT_COMPILE_FILE"), //$NON-NLS-1$
						CodeCont.getName());
				return new PythonForkScript(_Py_ScriptCompile(Name, new FileInputStream(CodeCont)));
			} catch (Throwable e) {
				Misc.CascadeThrow(e, Messages.Localize("Debugging.ObjectTrap.SCRIPT_COMPILE_FAILURE"), //$NON-NLS-1$
						Name);
			}
			return null;
		}
		
		@Override
		public void ExecEnvPrep(Map<String, Object> Envs) {
			_Py_ScriptPrep(Local -> {
				Envs.forEach((Key, Val) -> {
					Local.__setitem__(Key, Py.java2py(Val));
				});
				return Local;
			});
		}
		
	}
	
}
