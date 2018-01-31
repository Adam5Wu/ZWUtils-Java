
package com.necla.am.zwutils.addon.ObjectTrap.ScalaScript;

import com.necla.am.zwutils.Debugging.ObjectTrap;


public class AddonRegistrar implements ObjectTrap.IForkScriptEngineFactory {
	
	public static final String SCRIPTENGINE_EXTENSION = "SCALA";
	
	@Override
	public void Register(ObjectTrap OT) {
		
		OT.RegisterScriptEngine(SCRIPTENGINE_EXTENSION, new Implementations.CoObject());
		
	}
	
}