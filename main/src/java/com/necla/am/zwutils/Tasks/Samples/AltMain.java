
package com.necla.am.zwutils.Tasks.Samples;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import com.martiansoftware.jsap.CommandLineTokenizer;
import com.necla.am.zwutils.Config.Data;
import com.necla.am.zwutils.Config.DataMap;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.Misc.Misc.TimeUnit;
import com.necla.am.zwutils.Tasks.ConfigurableTask;


public class AltMain
		extends ConfigurableTask<AltMain.ConfigData.Mutable, AltMain.ConfigData.ReadOnly> {
	
	public static final String LogGroup = Poller.class.getSimpleName();
	
	public static class ConfigData {
		
		public static class Mutable extends Data.Mutable {
			
			public String MainClassName;
			public String EntryMethodName;
			public String[] MainArgs;
			
			protected Method MainEntry;
			protected static String[] NOARG = new String[0];
			
			public static final String DEFAULT_MAINENTRY = "main";
			
			@Override
			public void loadDefaults() {
				MainClassName = null;
				MainArgs = NOARG;
				EntryMethodName = DEFAULT_MAINENTRY;
				MainEntry = null;
			}
			
			public static final String CONFIG_MAINCLASS = "MainClass";
			public static final String CONFIG_MAINARGS = "Arguments";
			public static final String CONFIG_MAINENTRY = "EntryMethod";
			
			@Override
			public void loadFields(DataMap confMap) {
				MainClassName = confMap.getText(CONFIG_MAINCLASS);
				MainArgs = CommandLineTokenizer.tokenize(confMap.getTextDef(CONFIG_MAINARGS, ""));
				EntryMethodName = confMap.getTextDef(CONFIG_MAINENTRY, EntryMethodName);
			}
			
			public static final long MIN_TIMERES = 100;
			public static final long MAX_TIMERES = TimeUnit.MIN.Convert(30, TimeUnit.MSEC);
			
			protected class Validation implements Data.Mutable.Validation {
				
				@Override
				public void validateFields() throws Throwable {
					ILog.Fine("Checking main class...");
					if (MainClassName == null) {
						Misc.ERROR("Missing main class specification");
					}
					
					try {
						Class<?> CLS = Class.forName(MainClassName);
						Method ENT = CLS.getDeclaredMethod(EntryMethodName, String[].class);
						ENT.setAccessible(true);
						if ((ENT.getModifiers() & Modifier.STATIC) == 0) {
							Misc.ERROR("Entry method must be static");
						}
						MainEntry = ENT;
					} catch (Throwable e) {
						Misc.CascadeThrow(e, "Unable to load main class");
					}
				}
			}
			
			@Override
			protected Validation needValidation() {
				return new Validation();
			}
			
		}
		
		public static class ReadOnly extends Data.ReadOnly {
			
			public final Method MainEntry;
			public final String[] MainArgs;
			
			public ReadOnly(IGroupLogger Logger, Mutable Source) {
				super(Logger, Source);
				
				MainEntry = Source.MainEntry;
				MainArgs = Source.MainArgs;
			}
			
		}
		
	}
	
	public AltMain(String Name) {
		super(Name);
	}
	
	@Override
	protected Class<? extends ConfigData.Mutable> MutableConfigClass() {
		return ConfigData.Mutable.class;
	}
	
	@Override
	protected Class<? extends ConfigData.ReadOnly> ReadOnlyConfigClass() {
		return ConfigData.ReadOnly.class;
	}
	
	@Override
	protected void preTask() {
		super.preTask();
		
		if (Config.MainEntry == null) {
			Misc.ERROR("No main entry assigned");
		}
	}
	
	@Override
	protected void doTask() {
		if (!tellState().isTerminating()) {
			try {
				Config.MainEntry.invoke(null, (Object) Config.MainArgs);
			} catch (InvocationTargetException e) {
				Misc.CascadeThrow(e.getTargetException(), "Unhandled exception in main entry");
			} catch (Throwable e) {
				Misc.CascadeThrow(e, "Failed to invoke main entry");
			}
		}
	}
	
}
