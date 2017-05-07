
package com.necla.am.zwutils.addon.ObjectTrap.ScalaScript;

import com.necla.am.zwutils.Debugging.ObjectTrap;


public class AddonRegistrar implements ObjectTrap.IForkScriptEngineFactory {
	
	public static final String ScriptEngineExtension = "SCALA";
	
	@Override
	public void Register(ObjectTrap OT) {
		
		OT.RegisterScriptEngine(ScriptEngineExtension, new Implementations.CoObject());
		
	}
	
}