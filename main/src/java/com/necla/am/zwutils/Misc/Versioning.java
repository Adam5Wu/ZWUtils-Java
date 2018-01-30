
package com.necla.am.zwutils.Misc;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.regex.Pattern;

import com.necla.am.zwutils.Modeling.ITimeStamp;


public class Versioning {
	
	protected Versioning() {
		Misc.FAIL(IllegalStateException.class, "Do not instantiate!");
	}
	
	protected static Field _Modifiers;
	
	static {
		try {
			_Modifiers = Field.class.getDeclaredField("modifiers");
			_Modifiers.setAccessible(true);
		} catch (Exception e) {
			Misc.CascadeThrow(e);
		}
	}
	
	public static void Namespace_Init(Class<?> Namespace) {
		try {
			Field _VERSION = Namespace.getDeclaredField("VERSION");
			_VERSION.setAccessible(true);
			_Modifiers.setInt(_VERSION, _VERSION.getModifiers() & ~Modifier.FINAL);
			String _VERSION_ = (String) _VERSION.get(null);
			
			Field _BUILD = Namespace.getDeclaredField("BUILD");
			_BUILD.setAccessible(true);
			_Modifiers.setInt(_BUILD, _BUILD.getModifiers() & ~Modifier.FINAL);
			String _BUILD_ = (String) _BUILD.get(null);
			
			String StubVersion = "@VERSION@";
			
			if (_VERSION_.contains(StubVersion)) {
				_VERSION_ = _VERSION_.replaceAll(Pattern.quote(StubVersion), "Snapshot");
				_VERSION.set(null, _VERSION_);
			}
			
			String StubDate = "@DATE@";
			String StubTime = "@TIME@";
			if (_BUILD_.contains(StubDate) || _BUILD_.contains(StubTime)) {
				ITimeStamp Now = ITimeStamp.Impl.Now();
				DateFormat DateConv = new SimpleDateFormat("yyyy-MM-dd");
				DateFormat TimeConv = new SimpleDateFormat("HH:mm:ss");
				_BUILD_ = _BUILD_.replaceAll(Pattern.quote(StubDate), Now.toString(DateConv));
				_BUILD_ = _BUILD_.replaceAll(Pattern.quote(StubTime), Now.toString(TimeConv));
				_BUILD.set(null, _BUILD_);
			}
		} catch (Exception e) {
			Misc.CascadeThrow(e);
		}
	}
	
}
