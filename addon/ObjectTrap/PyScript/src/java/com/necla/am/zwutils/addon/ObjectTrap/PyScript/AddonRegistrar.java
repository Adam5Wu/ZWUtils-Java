
package com.necla.am.zwutils.addon.ObjectTrap.PyScript;

import com.necla.am.zwutils.Debugging.ObjectTrap;


public class AddonRegistrar implements ObjectTrap.IForkScriptEngineFactory {
	
	public static final String ScriptEngineExtension = "PY";
	
	@Override
	public void Register(ObjectTrap OT) {
		
		OT.RegisterScriptEngine(ScriptEngineExtension, new Implementations.CoObject());
		
	}
	
}
