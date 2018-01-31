/*
 * Copyright (c) 2011 - 2016, Zhenyu Wu, NEC Labs America Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of ZWUtils-Java nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * // @formatter:on
 */

package com.necla.am.zwutils.Logging.Utils.Formatters;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.regex.Pattern;

import com.necla.am.zwutils.Caching.CanonicalCacheMap;
import com.necla.am.zwutils.Logging.DebugLog;
import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.Utils.Handlers.Zabbix.ZabbixHandler;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.Misc.Misc.TimeSystem;
import com.necla.am.zwutils.Misc.Misc.TimeUnit;


/**
 * Custom log message formatter
 *
 * @author Zhenyu Wu
 * @see DebugLog
 * @version 0.1 - Nov. 2010: Initial implementation
 * @version ...
 * @version 0.91 - Oct. 2012: Major revision
 * @version 0.91 - Jan. 20 2016: Initial public release
 */
public final class SlimFormatter extends LogFormatter {
	
	public SlimFormatter(Handler LogHandler, String LogTargetName) {
		super(LogHandler, LogTargetName);
	}
	
	public static final String CONFIG_PFX = "Slim.";
	
	public static final String CONFIG_MSGHDR = "MsgHdr";
	public static final String CONFIG_MSGSRC = "MsgSrc";
	public static final String CONFIG_MSGTIME = "MsgTime";
	public static final String CONFIG_AUTOWARN = "AutoWarn";
	public static final String CONFIG_EXCEPTSTACK = "ExceptStack";
	public static final String CONFIG_GROUPWIDTH = "GroupWidth";
	public static final String CONFIG_METHODWIDTH = "MethodWidth";
	public static final String CONFIG_NESTDEPTH = "NestDepth";
	public static final String CONFIG_SHORTHANDFMT = "ShortHandFmt";
	
	private static final String LINESEP = System.getProperty("line.separator");
	private static final Pattern REG_LINESEPS = Pattern.compile("\\r\\n|\\n");
	
	private static final String EXCEPTION_STACK_START = "===== Exception =====";
	private static final String EXCEPTION_STACK_END = "=====================";
	private static final String PRINT_STACK_START = "==== Stack trace ====";
	private static final String PRINT_STACK_END = EXCEPTION_STACK_END;
	
	protected static final String MSG_STATE_ENABLED = "enabled";
	protected static final String MSG_STATE_DISABLED = "disabled";
	
	private static final String DEF_NOGROUP_NAME = "";
	private static final int DEF_TS_WIDTH = 10 + 1 + 12;
	
	private static int MIN_LogGroupWidth = 15;
	private static int MIN_MethodIdentWidth = 31;
	private static int DEF_LogGroupWidth = 18;
	private static int DEF_MethodIdentWidth = 36;
	private static int DEF_NestDepthMax = 16;
	
	private boolean MsgHdr = true;
	private boolean MsgSrc = false;
	private boolean MsgTime = true;
	private boolean AutoWarn = true;
	private boolean ExceptStack = true;
	private int LogGroupWidth = DEF_LogGroupWidth;
	private int MethodIdentWidth = DEF_MethodIdentWidth;
	private int NestDepthMax = DEF_NestDepthMax;
	private boolean ShortHandFmt = true;
	
	/**
	 * Configuration handler
	 * <p>
	 * Handles configurations valid for this formatter
	 */
	@Override
	public synchronized void ConfigLogMessage(String Key, String Value) {
		if (Key.startsWith(CONFIG_PFX)) {
			String SubKey = Key.substring(CONFIG_PFX.length());
			switch (SubKey) {
				case CONFIG_AUTOWARN:
					setAutoWarn(Boolean.parseBoolean(Value));
					break;
				case CONFIG_MSGHDR:
					setMsgHdr(Boolean.parseBoolean(Value));
					break;
				case CONFIG_MSGSRC:
					setMsgSrc(Boolean.parseBoolean(Value));
					break;
				case CONFIG_MSGTIME:
					setMsgTime(Boolean.parseBoolean(Value));
					break;
				case CONFIG_EXCEPTSTACK:
					setExceptStack(Boolean.parseBoolean(Value));
					break;
				case CONFIG_GROUPWIDTH:
					setLogGroupWidth(Integer.parseInt(Value));
					break;
				case CONFIG_METHODWIDTH:
					setMethodIdentWidth(Integer.parseInt(Value));
					break;
				case CONFIG_NESTDEPTH:
					setNestDepthMax(Integer.parseInt(Value));
					break;
				case CONFIG_SHORTHANDFMT:
					setShortHandFmt(Boolean.parseBoolean(Value));
					break;
				default:
					super.ConfigLogMessage(Key, Value);
			}
		} else {
			// Ignore unrelated configurations
		}
	}
	
	public void setNestDepthMax(int Val) {
		if (Val < 0) {
			Val = DEF_NestDepthMax;
		}
		if (NestDepthMax != Val) {
			NestDepthMax = Val;
			ILog.Config("Log message level nesting limit: %d", NestDepthMax);
		}
	}
	
	public void setMethodIdentWidth(int Val) {
		if (Val < 0) {
			Val = DEF_MethodIdentWidth;
		}
		if (MethodIdentWidth != Val) {
			LogPad = null;
			EllipsisMethod = null;
			MethodIdentWidth = Val;
			ILog.Config("Log message method identifier %s",
					MethodIdentWidth < MIN_MethodIdentWidth? MSG_STATE_DISABLED : String
							.format("clip width: %d", MethodIdentWidth));
		}
	}
	
	public void setLogGroupWidth(int Val) {
		if (Val < 0) {
			Val = DEF_LogGroupWidth;
		}
		if (LogGroupWidth != Val) {
			LogPad = null;
			EllipsisGroup = null;
			LogGroupWidth = Val;
			ILog.Config("Log message group %s",
					LogGroupWidth < MIN_LogGroupWidth? MSG_STATE_DISABLED : String.format("clip width: %d",
							LogGroupWidth));
		}
	}
	
	public void setExceptStack(boolean Val) {
		if (ExceptStack != Val) {
			ExceptStack = Val;
			ILog.Config("Exception stack trace printing %s",
					ExceptStack? MSG_STATE_ENABLED : MSG_STATE_DISABLED);
		}
	}
	
	public void setMsgTime(boolean Val) {
		if (MsgTime != Val) {
			MsgTime = Val;
			LogPad = null;
			ILog.Config("Message time stamp %s", MsgTime? MSG_STATE_ENABLED : MSG_STATE_DISABLED);
		}
	}
	
	public void setMsgHdr(boolean Val) {
		if (MsgHdr != Val) {
			MsgHdr = Val;
			ILog.Config("Message header information %s", MsgHdr? MSG_STATE_ENABLED : MSG_STATE_DISABLED);
		}
	}
	
	public void setMsgSrc(boolean Val) {
		if (MsgSrc != Val) {
			MsgSrc = Val;
			ILog.Config("Message source information %s", MsgSrc? MSG_STATE_ENABLED : MSG_STATE_DISABLED);
		}
	}
	
	public void setAutoWarn(boolean Val) {
		if (AutoWarn != Val) {
			AutoWarn = Val;
			ILog.Config("Automatic warning enhancement %s",
					AutoWarn? MSG_STATE_ENABLED : MSG_STATE_DISABLED);
		}
	}
	
	public void setShortHandFmt(boolean Val) {
		if (ShortHandFmt != Val) {
			ShortHandFmt = Val;
			ILog.Config("Short-hand format notation %s",
					AutoWarn? MSG_STATE_ENABLED : MSG_STATE_DISABLED);
		}
	}
	
	private String LogPad = null;
	
	private void GenerateLogPad() {
		boolean EnableLogGroup = LogGroupWidth >= MIN_LogGroupWidth;
		boolean EnableMethodIdent = MethodIdentWidth >= MIN_MethodIdentWidth;
		
		if (EnableLogGroup) {
			if (EnableMethodIdent) {
				LogPad = String.format("%" + LogGroupWidth + "s | %" + MethodIdentWidth + "s | ", " ", " ");
			} else {
				LogPad = String.format("%" + LogGroupWidth + "s | ", " ");
			}
		} else {
			if (EnableMethodIdent) {
				LogPad = String.format("%" + MethodIdentWidth + "s | ", " ");
			} else {
				LogPad = "";
			}
		}
		if (MsgTime) {
			LogPad = String.format("%" + DEF_TS_WIDTH + "s | ", " ") + LogPad;
		}
	}
	
	private int NestDepth = 0;
	private static final char NO_INTERP_PREFX = '!';
	private static final char NEST_START = '+';
	private static final char NEST_PREFX = '|';
	private static final char NEXT_TERM = '*';
	private static final char NEXT_ALLTERM = '#';
	private static final char NOLINEHDR_PREFX = '@';
	private static final char TIME_NOLINEHDR_PREFX = '$';
	private static final char COND_NOLINEHDR_PREFX = '?';
	private static final char PROBE_NEWLINE_PREFX = '~';
	private static final char MORE_NEWLINE_PREFX = '>';
	private static final char NO_NEWLINE_PREFX = '<';
	private static final char MORE_NEWLINE_POSTFX = '~';
	
	private static final char DISP_NESTTERM_INDENT = '^';
	private static final char DISP_NEXTLIMIT = '>';
	private static final String DISP_OMITDATA_ELPS = "..";
	
	private static final int DISP_OMITDATA_PREELPS_LEN = 7;
	private static final int DISP_OMITDATA_MINTAIL_LEN = 5;
	private static final int DISP_OMITDATA_ELPS_HEAD;
	private static final int DISP_OMITDATA_MINLEN;
	
	static {
		DISP_OMITDATA_ELPS_HEAD = DISP_OMITDATA_PREELPS_LEN + DISP_OMITDATA_ELPS.length();
		DISP_OMITDATA_MINLEN = DISP_OMITDATA_ELPS_HEAD + DISP_OMITDATA_MINTAIL_LEN;
	}
	
	private boolean LastNewLine = true;
	
	private CanonicalCacheMap<String, String> EllipsisGroup = null;
	private CanonicalCacheMap<String, String> EllipsisMethod = null;
	
	/**
	 * Format a log record
	 * <p>
	 * There are two different printing formats:
	 * <ol>
	 * <li>Normal log message - ClassName::MethodName (Message)
	 * <li>Exception message - [ClassName::MethodName] (Exception Stack Trace)
	 * </ol>
	 */
	@Override
	// Well, every mountain has a peak...
	public synchronized String FormatLogMessage(LogRecord record) {
		StringWriter LogLine = new StringWriter();
		String MsgTimeStr = "";
		
		if (MsgHdr) {
			PrintOptMessageSource(record, LogLine);
			String DISPLogGroup = PrepareDisplayLogGroup(record);
			String DISPMethodIdent = PrepareDisplayMethodIdentifier(record);
			MsgTimeStr = PrepareDisplayTimestamp(record, MsgTimeStr);
			PrintMessageHeader(LogLine, MsgTimeStr, DISPLogGroup, DISPMethodIdent);
		}
		
		boolean NoInterp = !ShortHandFmt;
		boolean NoLineHdr = false;
		boolean TimeNoLineHdr = false;
		boolean CondNoLineHdr = false;
		int PreNewLine = 0;
		int PostNewLine = 1;
		int TermCnt = 0;
		int NestCnt = 0;
		
		String Message = record.getMessage();
		if ((Message != null) && (Message.length() != 0)) {
			if (Message.charAt(0) == NO_INTERP_PREFX) {
				NoInterp = !NoInterp;
				Message = Message.substring(1);
			} else {
				Message = Message.trim();
			}
		} else {
			Message = "";
		}
		
		String MessagePfx = "";
		String[] MessageLines;
		Throwable Exception = record.getThrown();
		if (Exception == null) {
			// For non-exception log
			if (Message.equals(ZabbixHandler.LOG_TRIGGER)) {
				MessageLines = PrintZabbixLogging(record);
			} else {
				if (NoInterp) {
					MessageLines = REG_LINESEPS.split(Message, -1);
				} else {
					// Parse for nest termination reset control sequence
					String PMsg = Misc.StripRepeat(Message, NEXT_ALLTERM, true);
					int CharCount = Message.length() - PMsg.length();
					Message = PMsg;
					
					if (CharCount > 0) {
						TermCnt = NestDepth;
					}
					
					// Parse for nest termination control sequence
					PMsg = Misc.StripRepeat(Message, NEXT_TERM, true);
					CharCount = Message.length() - PMsg.length();
					Message = PMsg;
					
					if (TermCnt == 0) {
						TermCnt += CharCount;
						if (TermCnt > NestDepth) {
							TermCnt = NestDepth;
						}
					}
					NestDepth -= TermCnt;
					
					// Parse for nest start control sequence
					PMsg = Misc.StripRepeat(Message, NEST_START, true);
					CharCount = Message.length() - PMsg.length();
					Message = PMsg;
					
					NestCnt = CharCount;
					
					// Apply nest level decrease
					TermCnt -= NestCnt;
					
					// Parse preambles
					PMsg = Misc.StripRepeat(Message, NOLINEHDR_PREFX, true);
					CharCount = Message.length() - PMsg.length();
					Message = PMsg;
					
					NoLineHdr = CharCount > 0;
					
					if (NoLineHdr) {
						PMsg = Misc.StripRepeat(Message, TIME_NOLINEHDR_PREFX, true);
						CharCount = Message.length() - PMsg.length();
						Message = PMsg;
						
						TimeNoLineHdr = CharCount > 0;
						
						PMsg = Misc.StripRepeat(Message, COND_NOLINEHDR_PREFX, true);
						CharCount = Message.length() - PMsg.length();
						Message = PMsg;
						
						CondNoLineHdr = CharCount > 0;
					}
					
					PMsg = Misc.StripRepeat(Message, PROBE_NEWLINE_PREFX, true);
					CharCount = Message.length() - PMsg.length();
					Message = PMsg;
					
					if ((CharCount > 0) && !LastNewLine) {
						PreNewLine++;
					}
					
					PMsg = Misc.StripRepeat(Message, MORE_NEWLINE_PREFX, true);
					CharCount = Message.length() - PMsg.length();
					Message = PMsg;
					
					PreNewLine += CharCount;
					
					PMsg = Misc.StripRepeat(Message, NO_NEWLINE_PREFX, false);
					CharCount = Message.length() - PMsg.length();
					Message = PMsg;
					
					if (CharCount > 0) {
						PostNewLine = 0;
					} else {
						PMsg = Misc.StripRepeat(Message, MORE_NEWLINE_POSTFX, false);
						CharCount = Message.length() - PMsg.length();
						Message = PMsg;
						PostNewLine += CharCount;
					}
					
					// Split multi-line messages
					MessageLines = REG_LINESEPS.split(trimOverrides(Message.trim()), -1);
				}
				
				PrintMsgNestingPrefixes(LogLine, TermCnt, NestCnt);
				// Apply nest level update
				NestDepth = NestDepth + NestCnt;
				
				// Print automatic log level prefix
				Level LogLevel = record.getLevel();
				if (AutoWarn&& (LogLevel.intValue() >= Level.WARNING.intValue())
						&& !LogLevel.equals(Level.OFF)) {
					MessagePfx = String.format("[%s] ", LogLevel.getLocalizedName());
					if (!LastNewLine && (PreNewLine == 0)) {
						PreNewLine++;
					}
				}
			}
		} else {
			StringWriter ExceptMsg = PrepareExceptionMessage(Message, Exception);
			
			// Split multi-line messages
			MessageLines = REG_LINESEPS.split(ExceptMsg.toString(), -1);
			// Do not print line header for exception messages
			NoLineHdr = true;
			// But do print time for exception message
			TimeNoLineHdr = true;
			
			if (!LastNewLine && (PreNewLine == 0)) {
				PreNewLine++;
			}
		}
		
		StringBuilder LogContent = new StringBuilder();
		Message = MessageLines[0];
		
		if (PreNewLine > 0) {
			LogContent.append(new String(new char[PreNewLine]).replace("\0", LINESEP));
			LastNewLine = true;
		}
		
		boolean NeedNoHdr = NoLineHdr && !(CondNoLineHdr && LastNewLine);
		boolean NeedTimeStamp = TimeNoLineHdr && LastNewLine;
		PrintMessageFirstLine(LogLine, MsgTimeStr, NeedNoHdr, NeedTimeStamp, Message, MessagePfx,
				LogContent);
		
		if (MessageLines.length > 1) {
			PrintMessageMoreLines(MsgTimeStr, NoLineHdr, TimeNoLineHdr, TermCnt, MessagePfx, MessageLines,
					LogContent);
		}
		
		DeriveLastNewLine(PostNewLine, LogContent);
		return LogContent.toString();
	}
	
	private void PrintMessageMoreLines(String MsgTimeStr, boolean NoLineHdr, boolean TimeNoLineHdr,
			int TermCnt, String MessagePfx, String[] MessageLines, StringBuilder LogContent) {
		// Generate nest prefix
		String NestPfxStr = GenerateMultilinePrefix(MsgTimeStr, NoLineHdr, TimeNoLineHdr, TermCnt);
		
		// Print messages
		for (int i = 1; i < MessageLines.length; i++) {
			LogContent.append(LINESEP);
			LogContent.append(NestPfxStr);
			LogContent.append(MessagePfx);
			LogContent.append(MessageLines[i]);
		}
	}
	
	private void DeriveLastNewLine(int PostNewLine, StringBuilder LogContent) {
		if (PostNewLine > 0) {
			LastNewLine = true;
			LogContent.append(new String(new char[PostNewLine]).replace("\0", LINESEP));
		} else {
			if (LogContent.length() > 0) {
				LastNewLine = LogContent.substring(LogContent.length() - LINESEP.length()).equals(LINESEP);
			}
		}
	}
	
	private String GenerateMultilinePrefix(String MsgTimeStr, boolean NoLineHdr,
			boolean TimeNoLineHdr, int TermCnt) {
		String NestPfxStr;
		if (!NoLineHdr) {
			StringWriter NestPfx = new StringWriter();
			if (LogPad == null) {
				GenerateLogPad();
			}
			NestPfx.write(LogPad);
			PrintNestLevelPrefix(TermCnt, NestPfx);
			
			NestPfxStr = NestPfx.toString();
		} else {
			if (TimeNoLineHdr && !MsgTimeStr.isEmpty()) {
				NestPfxStr = String.format("%" + DEF_TS_WIDTH + "s | ", " ");
			} else {
				NestPfxStr = "";
			}
		}
		return NestPfxStr;
	}
	
	private void PrintNestLevelPrefix(int TermCnt, StringWriter NestPfx) {
		int NestBudget = NestDepthMax;
		for (int i = 0; i < NestDepth; i++)
			if (NestBudget-- > 0) {
				NestPfx.write(NEST_PREFX);
			} else {
				break;
			}
		// Temporary indent of multi-line nest termination log
		for (int i = 0; i < TermCnt; i++)
			if (NestBudget-- > 0) {
				NestPfx.write(DISP_NESTTERM_INDENT);
			} else {
				break;
			}
		if (NestBudget < 0) {
			NestPfx.write(DISP_NEXTLIMIT);
		}
		if ((NestDepth | TermCnt) != 0) {
			NestPfx.write(' ');
		}
	}
	
	private void PrintMessageFirstLine(StringWriter LogLine, String MsgTimeStr, boolean NeedNoHdr,
			boolean NeedTimeStamp, String Message, String MessagePfx, StringBuilder LogContent) {
		// If "no header" is requested, get rid of already printed header
		if (NeedNoHdr) {
			LogLine = new StringWriter();
			if (NeedTimeStamp) {
				LogLine.write(MsgTimeStr);
			}
		}
		
		// Really print header
		LogContent.append(LogLine.toString());
		if (!Message.isEmpty()) {
			LogContent.append(MessagePfx);
			LogContent.append(Message);
		}
	}
	
	private void PrintOptMessageSource(LogRecord record, StringWriter LogLine) {
		if (MsgSrc && !record.getSourceClassName().isEmpty()) {
			LogLine.write("- Log message from (");
			LogLine.write(record.getSourceClassName());
			LogLine.write("):");
			LogLine.write(LINESEP);
		}
	}
	
	private StringWriter PrepareExceptionMessage(String Message, Throwable Exception) {
		// For exception log
		StringWriter ExceptMsg = new StringWriter();
		boolean StackTraceCarrier = Exception.getClass().equals(GroupLogger.StackTraceThrowable.class);
		if (StackTraceCarrier) {
			PrintRegularStacktraces(Message, Exception, ExceptMsg);
		} else {
			PrintExceptionMessage(Message, Exception, ExceptMsg);
			PrintDiagnosticInfo(Exception, ExceptMsg);
		}
		return ExceptMsg;
	}
	
	private void PrintDiagnosticInfo(Throwable Exception, StringWriter ExceptMsg) {
		if (ExceptStack) {
			Exception.printStackTrace(new PrintWriter(ExceptMsg));
			ExceptMsg.write(EXCEPTION_STACK_END);
		} else {
			StackTraceElement StackTop = Exception.getStackTrace()[0];
			ExceptMsg.write(" @ ");
			ExceptMsg.write(StackTop.getClassName());
			ExceptMsg.write('.');
			ExceptMsg.write(StackTop.getMethodName());
			ExceptMsg.write('(');
			ExceptMsg.write(StackTop.getFileName());
			ExceptMsg.write(':');
			ExceptMsg.write(String.valueOf(StackTop.getLineNumber()));
			ExceptMsg.write(')');
		}
	}
	
	private void PrintExceptionMessage(String Message, Throwable Exception, StringWriter ExceptMsg) {
		if (!ExceptStack) {
			ExceptMsg.write("[Exception] ");
			ExceptMsg.write(Exception.getClass().getName());
			String ExcMsg = Exception.getLocalizedMessage();
			if (ExcMsg != null) {
				ExceptMsg.write(" ('");
				ExceptMsg.write(ExcMsg);
				ExceptMsg.write("')");
			}
		} else {
			ExceptMsg.write(EXCEPTION_STACK_START);
			ExceptMsg.write(LINESEP);
		}
		if (!Message.isEmpty()) {
			if (!ExceptStack) {
				ExceptMsg.write(" - ");
			}
			ExceptMsg.write(Message);
			if (ExceptStack) {
				ExceptMsg.write(LINESEP);
			}
		}
	}
	
	private void PrintRegularStacktraces(String Message, Throwable Exception,
			StringWriter ExceptMsg) {
		ExceptMsg.write(PRINT_STACK_START);
		ExceptMsg.write(LINESEP);
		if (!Message.isEmpty()) {
			ExceptMsg.write(Message);
			ExceptMsg.append(LINESEP);
		}
		StackTraceElement[] StackTrace = Exception.getStackTrace();
		for (StackTraceElement StackFrame : StackTrace) {
			ExceptMsg.write(StackFrame.toString());
			ExceptMsg.append(LINESEP);
		}
		ExceptMsg.write(PRINT_STACK_END);
	}
	
	private void PrintMsgNestingPrefixes(StringWriter LogLine, int TermCnt, int NestCnt) {
		int NestBudget = NestDepthMax;
		// Generate nest prefix
		for (int i = 0; i < NestDepth; i++)
			if (NestBudget-- > 0) {
				LogLine.write(NEST_PREFX);
			} else {
				break;
			}
		// Generate nest start
		for (int i = 0; i < NestCnt; i++)
			if (NestBudget-- > 0) {
				LogLine.write(NEST_START);
			} else {
				break;
			}
		// Generate nest termination
		for (int i = 0; i < TermCnt; i++)
			if (NestBudget-- > 0) {
				LogLine.write(NEXT_TERM);
			} else {
				break;
			}
		if (NestBudget < 0) {
			LogLine.write(DISP_NEXTLIMIT);
		}
		if ((NestDepth | TermCnt | NestCnt) != 0) {
			LogLine.write(' ');
		}
	}
	
	private String[] PrintZabbixLogging(LogRecord record) {
		String[] MessageLines;
		StringBuilder StrBuf = new StringBuilder();
		Object[] Params = record.getParameters();
		if (Params.length > 0) {
			StrBuf.append(DebugLog.ZabbixSupport()? "[Zabbix]" : "[Zabbix (Disabled)]");
			boolean Key = true;
			for (Object Item : Params) {
				if (Key) {
					StrBuf.append(' ').append(Item).append('=');
				} else {
					StrBuf.append(Item).append(';');
				}
				Key = !Key;
			}
			if (!Key) {
				StrBuf.append("???");
			} else {
				StrBuf.deleteCharAt(StrBuf.length() - 1);
			}
		}
		MessageLines = Misc.wrap(StrBuf.toString());
		return MessageLines;
	}
	
	private void PrintMessageHeader(StringWriter LogLine, String MsgTimeStr, String DISPLogGroup,
			String DISPMethodIdent) {
		StringBuilder LineStart = new StringBuilder();
		LineStart.append(MsgTimeStr);
		
		if (DISPLogGroup != null) {
			LineStart.append(String.format("%-" + LogGroupWidth + "s | ", DISPLogGroup));
		}
		
		if (DISPMethodIdent != null) {
			LineStart.append(String.format("%-" + MethodIdentWidth + "s | ", DISPMethodIdent));
		}
		
		LogLine.write(LineStart.toString());
	}
	
	private String PrepareDisplayTimestamp(LogRecord record, String MsgTimeStr) {
		if (MsgTime) {
			String MsgDateTime = Misc.FormatTS(record.getMillis(), TimeSystem.UNIX, TimeUnit.MSEC);
			MsgTimeStr = String.format("%-" + DEF_TS_WIDTH + "s | ", MsgDateTime);
		}
		return MsgTimeStr;
	}
	
	private String PrepareDisplayMethodIdentifier(LogRecord record) {
		String DISPMethodIdent = null;
		if (MethodIdentWidth >= MIN_MethodIdentWidth) {
			if (!record.getSourceMethodName().isEmpty()) {
				DISPMethodIdent = Misc.stripPackageName(record.getSourceMethodName());
				if (DISPMethodIdent.length() > MethodIdentWidth) {
					if (EllipsisMethod == null) {
						EllipsisMethod = new CanonicalCacheMap.Classic<>(
								SlimFormatter.class.getSimpleName() + "-EllipsisMethod");
					}
					DISPMethodIdent = EllipsisMethod.Query(DISPMethodIdent, this::GenerateEllipsisMethod);
				}
			} else {
				DISPMethodIdent = "";
			}
		}
		return DISPMethodIdent;
	}
	
	private String GenerateEllipsisMethod(String Key) {
		StringBuilder StrBuf = new StringBuilder();
		String[] ClassMethodTok = Key.split(DebugLog.LOGMSG_CLASSMETHOD_DELIM, 2);
		int ClassWidth = Math.max(
				MethodIdentWidth - DebugLog.LOGMSG_CLASSMETHOD_DELIM.length() - ClassMethodTok[1].length(),
				DISP_OMITDATA_MINLEN);
		if (ClassMethodTok[0].length() > ClassWidth) {
			StrBuf.append(ClassMethodTok[0].substring(0, DISP_OMITDATA_PREELPS_LEN))
					.append(DISP_OMITDATA_ELPS).append(ClassMethodTok[0]
							.substring(ClassMethodTok[0].length() - (ClassWidth - DISP_OMITDATA_ELPS_HEAD)));
		} else {
			StrBuf.append(ClassMethodTok[0]);
			ClassWidth = ClassMethodTok[0].length();
		}
		StrBuf.append(DebugLog.LOGMSG_CLASSMETHOD_DELIM);
		int MethodWidth = MethodIdentWidth - DebugLog.LOGMSG_CLASSMETHOD_DELIM.length() - ClassWidth;
		if (ClassMethodTok[1].length() > MethodWidth) {
			StrBuf.append(ClassMethodTok[1].substring(0, DISP_OMITDATA_PREELPS_LEN))
					.append(DISP_OMITDATA_ELPS).append(ClassMethodTok[1]
							.substring(ClassMethodTok[1].length() - (MethodWidth - DISP_OMITDATA_PREELPS_LEN)));
		} else {
			StrBuf.append(ClassMethodTok[1]);
		}
		return StrBuf.toString();
	}
	
	private String PrepareDisplayLogGroup(LogRecord record) {
		String DISPLogGroup = null;
		if (LogGroupWidth >= MIN_LogGroupWidth) {
			DISPLogGroup = record.getLoggerName();
			if ((DISPLogGroup == null) || (DISPLogGroup.isEmpty())) {
				DISPLogGroup = DEF_NOGROUP_NAME;
			}
			if (DISPLogGroup.length() > LogGroupWidth) {
				if (EllipsisGroup == null) {
					EllipsisGroup = new CanonicalCacheMap.Classic<>(
							SlimFormatter.class.getSimpleName() + "-EllipsisGroup");
				}
				DISPLogGroup = EllipsisGroup.Query(DISPLogGroup, Key -> {
					StringBuilder StrBuf = new StringBuilder();
					StrBuf.append(Key.substring(0, DISP_OMITDATA_PREELPS_LEN)).append(DISP_OMITDATA_ELPS)
							.append(Key.substring(Key.length() - (LogGroupWidth - DISP_OMITDATA_ELPS_HEAD)));
					return StrBuf.toString();
				});
			}
		}
		return DISPLogGroup;
	}
	
	private static String LeadingSpacePrefix = ":";
	private static String TrailingSpaceSuffix = "|";
	
	/**
	 * Handles the escape of message control prefix and trailing blank spaces
	 *
	 * @param StrIn
	 *          - Source message
	 * @return Message with the escape characters removed
	 */
	protected String trimOverrides(String StrIn) {
		if (StrIn.startsWith(LeadingSpacePrefix)) {
			StrIn = StrIn.substring(LeadingSpacePrefix.length());
		}
		if (StrIn.endsWith(TrailingSpaceSuffix)) {
			StrIn = StrIn.substring(0, StrIn.length() - 1);
		}
		return StrIn;
	}
	
}
