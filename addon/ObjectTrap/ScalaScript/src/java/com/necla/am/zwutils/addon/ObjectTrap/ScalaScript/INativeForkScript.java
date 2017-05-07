
package com.necla.am.zwutils.addon.ObjectTrap.ScalaScript;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import com.necla.am.zwutils.Debugging.ObjectTrap.IFork;
import com.necla.am.zwutils.Logging.IGroupLogger;


public interface INativeForkScript {
	
	Object Exec(String N, IFork.Result R, Object O, Object S, IGroupLogger L,
			ConcurrentMap<String, Object> GlobalScope, Map<String, Object> LocalScope);
	
}
