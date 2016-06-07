// NEC Labs America Inc. CONFIDENTIAL

package com.necla.am.zwutils;

import com.necla.am.zwutils.Misc.Versioning;


public class _Namespace {
	
	public static final String PRODUCT = "ZWUtils - Jack of All Trades Utility Libraries";
	public static final String COMPONENT = "ZWUtils for Java";
	public static final String VERSION = "@VERSION@".toString();
	public static final String BUILD = "@DATE@ @TIME@".toString();
	
	static {
		Versioning.Namespace_Init(_Namespace.class);
	}
	
}
