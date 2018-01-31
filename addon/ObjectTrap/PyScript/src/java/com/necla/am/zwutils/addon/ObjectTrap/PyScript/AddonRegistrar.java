
package com.necla.am.zwutils.addon.ObjectTrap.PyScript;

import com.necla.am.zwutils.Debugging.ObjectTrap;


public class AddonRegistrar implements ObjectTrap.IForkScriptEngineFactory {
	
	public static final String SCRIPTENGINE_EXTENSION = "PY";
	
	@Override
	public void Register(ObjectTrap OT) {
		
		OT.RegisterScriptEngine(SCRIPTENGINE_EXTENSION, new Implementations.CoObject());
		
	}
	
}
