// NEC Labs America Inc. CONFIDENTIAL

package com.necla.am.zwutils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.regex.Pattern;

import com.necla.am.zwutils.Modeling.ITimeStamp;


public class _Namespace {
	
	public static final String PRODUCT = "ZWUtils - Jack of All Trades Utility Libraries";
	public static final String COMPONENT = "ZWUtils for Java";
	public static String VERSION = "@VERSION@";
	public static String BUILD = "@DATE@ @TIME@";
	
	static {
		{
			String StubVersion = String.format("@%s@", "VERSION");
			VERSION = VERSION.replaceAll(Pattern.quote(StubVersion), "Snapshot");
		}
		{
			ITimeStamp Now = ITimeStamp.Impl.Now();
			String StubDate = String.format("@%s@", "DATE");
			DateFormat DateConv = new SimpleDateFormat("yyyy-MM-dd");
			BUILD = BUILD.replaceAll(Pattern.quote(StubDate), Now.toString(DateConv));
			String StubTime = String.format("@%s@", "TIME");
			DateFormat TimeConv = new SimpleDateFormat("HH:mm:ss");
			BUILD = BUILD.replaceAll(Pattern.quote(StubTime), Now.toString(TimeConv));
		}
	}
	
}
