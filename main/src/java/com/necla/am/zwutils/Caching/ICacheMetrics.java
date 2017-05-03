
package com.necla.am.zwutils.Caching;

public interface ICacheMetrics {
	
	public long Insertions();
	
	public long Expirations();
	
	public long HitCount();
	
}
