
package com.necla.am.zwutils.Logging;

import java.util.logging.Handler;
import java.util.logging.Level;


public interface IGroupLogger {
	
	public void Entry();
	
	public void Entry(String Format, Object... args);
	
	public void Exit();
	
	public void Exit(String Format, Object... args);
	
	public void StackTrace();
	
	public void StackTrace(Level LogLevel);
	
	public void StackTrace(String Format, Object... args);
	
	public void StackTrace(Level LogLevel, String Format, Object... args);
	
	public void logExcept(Throwable e);
	
	public void logExcept(Throwable e, String Format, Object... args);
	
	public void Finest(String Format, Object... args);
	
	public void Finer(String Format, Object... args);
	
	public void Fine(String Format, Object... args);
	
	public void Config(String Format, Object... args);
	
	public void Info(String Format, Object... args);
	
	public void Warn(String Format, Object... args);
	
	public void Error(String Format, Object... args);
	
	public void Log(Level logLevel, String Format, Object... args);
	
	public void PushLog(Level logLevel, String Format, Object... args);
	
	public String GroupName();
	
	public void setHandler(Handler LogHandler, boolean propagate);
	
	public void setGroupHandler(Handler LogHandler, boolean propagate);
	
	public void setLevel(Level logLevel);
	
	public void resetLevel();
	
	public void setGroupLevel(Level logLevel);
	
	public void resetGroupLevel();
	
	public Level getLevel();
	
	public Level getGroupLevel();
	
	public Level getEffectiveLevel();
	
	public Level getGroupEffectiveLevel();
	
	public boolean isLoggable(Level logLevel);
	
}
