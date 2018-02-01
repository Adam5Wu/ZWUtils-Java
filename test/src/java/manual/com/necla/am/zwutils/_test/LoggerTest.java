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

package com.necla.am.zwutils._test;

import java.util.logging.Logger;

import com.necla.am.zwutils.Logging.DebugLog;
import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Logging.Utils.Formatters.LogFormatter;
import com.necla.am.zwutils.Logging.Utils.Formatters.SlimFormatter;


public class LoggerTest {
	
	protected static final IGroupLogger CLog = new GroupLogger("Main");
	
	void ExceptStackTraceTest(int i) {
		CLog.Entry("+ExceptStackTraceTest");
		if (i > 1) {
			CLog.logExcept(new Exception("Test Exception"));
			CLog.logExcept(new Exception(""));
			CLog.logExcept(new Exception((String) null));
			CLog.StackTrace();
		} else {
			ExceptStackTraceTest(i + 1);
		}
		CLog.Exit("*ExceptStackTraceTest");
	}
	
	public void Go(String[] args) throws Exception {
		
		CLog.Info("---------- Test NoInterp");
		CLog.Info("!ABC");
		CLog.Info("!  ABC");
		CLog.Info("!>  ABC");
		CLog.Info("!+  ABC");
		CLog.Info("!!  ABC");
		CLog.Info("---------- Test Entry/Exit");
		CLog.Entry();
		CLog.Info("ABC");
		CLog.Entry();
		CLog.Info("测试");
		CLog.Exit();
		CLog.Info("ABC");
		CLog.Exit();
		CLog.Info("---------- Modifier Basic Test");
		CLog.Info(">(Add1NewLine)Logger Test");
		CLog.Info("Logger Test(NoNewLine)<");
		CLog.Info("@(NoHeader)Logger Test");
		CLog.Info("Logger Test(NoNewLine)<");
		CLog.Info("~(ProbeNewLine)Logger Test");
		CLog.Info("~(ProbeNewLine)Logger Test");
		CLog.Info("Logger Test(Add1NewLine)~");
		CLog.Info("Logger Test");
		CLog.Info(">>(Add2NewLine)Logger Test(Add2NewLine)~~");
		CLog.Info("---------- Test ''(HeaderOnlyLine)");
		CLog.Info("");
		CLog.Info("---------- Test '@'(EmptyLine)");
		CLog.Info("@");
		CLog.Info("---------- Test '@@@'(EmptyLine)");
		CLog.Info("@@@");
		CLog.Info("---------- Test '>'(Add1NewLine before HeaderOnlyLine)");
		CLog.Info(">");
		CLog.Info("---------- Test '>>>'(Add3NewLine before HeaderOnlyLine)");
		CLog.Info(">>>");
		CLog.Info("---------- Test '<'(NoNewLine)");
		CLog.Info("(NoNewLine)<");
		CLog.Info("@Test");
		CLog.Info("---------- Test '<<<'(NoNewLine)");
		CLog.Info("(NoNewLine)<<<");
		CLog.Info("@Test");
		CLog.Info("---------- Test '~'(ProbeNewLine)");
		CLog.Info("a(NoNewLine)<");
		CLog.Info("~(NewLine)");
		CLog.Info("b");
		CLog.Info("---------- Test '~~~'(ProbeNewLine)");
		CLog.Info("a(NoNewLine)<");
		CLog.Info("~~~(NewLine)");
		CLog.Info("b");
		CLog.Info("---------- Test '|~'(Add1NewLine after HeaderOnlyLine)");
		CLog.Info("|~");
		CLog.Info("---------- Test '|~~~'(Add3NewLine after HeaderOnlyLine)");
		CLog.Info("|~~~");
		CLog.Info("---------- Test ':~'(= '|~')");
		CLog.Info(":~");
		CLog.Info("---------- Test ':~~~'(= '|~~~')");
		CLog.Info(":~~~");
		CLog.Info("---------- Test '@<'(Do Nothing)");
		CLog.Info("a(NoNewLine)<");
		CLog.Info("@<");
		CLog.Info("@(NoHeader)b");
		CLog.Info("a(DefNewLine)");
		CLog.Info("@<");
		CLog.Info("~(NoNewLine)b");
		CLog.Info("---------- Test '@>'(Add2NewLine)");
		CLog.Info("a");
		CLog.Info("@>");
		CLog.Info("(Add2NewLine)b");
		CLog.Info("---------- Test '@~'(== Ensure EmptyLine)");
		CLog.Info("a(NoNewLine)<");
		CLog.Info("@~");
		CLog.Info("(NewLine)b");
		CLog.Info("a");
		CLog.Info("@~");
		CLog.Info("(NewLine)b");
		CLog.Info("---------- Test '<>'(As is)");
		CLog.Info("a");
		CLog.Info("<>");
		CLog.Info("b");
		CLog.Info("---------- Test '@><'(== '@')");
		CLog.Info("a");
		CLog.Info("@><");
		CLog.Info("(Add1NewLine)b");
		CLog.Info("---------- Test '@~<'(== Ensure NewLine)");
		CLog.Info("a(DefNewLine)");
		CLog.Info("@~<");
		CLog.Info("(NewLine)b");
		CLog.Info("a(NoNewLine)<");
		CLog.Info("@~<");
		CLog.Info("(NewLine)b");
		CLog.Info("---------- Test Warn");
		CLog.Warn("(Prefix)Blah");
		CLog.Info("a(NoNewLine)<");
		CLog.Warn("@(NoHeader+NewLine+Prefix)Blah");
		CLog.Info("(NewLine)b");
		CLog.Info("a(NoNewLine)<");
		CLog.Error("@$(NoHeader+Time+NewLine+Prefix)Blah\n(NewLine+Prefix)Blah");
		CLog.Info("(NewLine)b");
		CLog.Info("---------- Toggle AutoWarn OFF");
		LogFormatter.ConfigData.Mutable LogFormatterConfig = LogFormatter.Config.mirror();
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_AUTOWARN, "False");
		LogFormatter.Config.set(LogFormatterConfig);
		CLog.Warn("Blah");
		CLog.Info("a(NoNewLine)<");
		CLog.Warn("@Blah");
		CLog.Info("(NewLine)b");
		CLog.Info("a(NoNewLine)<");
		CLog.Error("@Blah\n(NewLine)Blah");
		CLog.Info("(NewLine)b");
		CLog.Info("---------- Toggle AutoWarn ON");
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_AUTOWARN, "True");
		LogFormatter.Config.set(LogFormatterConfig);
		CLog.Info("---------- Test Exception/StackTrace");
		ExceptStackTraceTest(0);
		CLog.Info("---------- Toggle Exception Stack Trace Printing OFF");
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_EXCEPTSTACK,
				"False");
		LogFormatter.Config.set(LogFormatterConfig);
		CLog.Info("---------- Test Exception/StackTrace");
		ExceptStackTraceTest(0);
		CLog.Info("---------- Test StdErr / StdOut");
		System.err.print("Test");
		System.err.print("(NoNewLine)Test");
		System.err.print("(NewLine)\n");
		System.err.print("Test\n(NewLine)Test");
		System.err.print("(NoNewLine)Test(NewLine)\n");
		System.err.print("Test");
		System.err.print("(NoNewLine)Test");
		System.err.print("(NewLine)\n");
		System.err.print("Test\n(NewLine)Test");
		System.err.print("(NoNewLine)Test(NewLine)\n");
		System.err.print(" 1 Test");
		System.err.flush();
		CLog.Info(" 2 Test");
		System.out.print(" 3 Test");
		System.out.print("(NoNewLine) 4 Test(NewLine)\n");
		System.out.print("(NoNewLine) 5 Test(NewLine)\n");
		System.out.flush();
		CLog.Info("---------- Test Levels");
		CLog.Info("+Test1");
		CLog.Info("+Test2");
		CLog.Info("+Test3");
		CLog.Info("+Test4");
		CLog.Info("*Test3");
		CLog.Info("*Test2");
		CLog.Info("*Test1");
		CLog.Info("+++Test4");
		CLog.Info("**Test2");
		CLog.Info("*+++Test4");
		CLog.Info("***+Test2");
		CLog.Info("***++++Test4");
		CLog.Info("++++++Test10");
		CLog.Info("++++++Test16");
		CLog.Info("+Test17");
		CLog.Info("+Test18");
		CLog.Info("+Test19");
		CLog.Info("**Test17");
		CLog.Info("#Test0");
		CLog.Info("---------- Setting Level Nest Depth to 6");
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_NESTDEPTH, "6");
		LogFormatter.Config.set(LogFormatterConfig);
		CLog.Info("---------- Test Levels");
		CLog.Info("+Test1");
		CLog.Info("+Test2");
		CLog.Info("+Test3");
		CLog.Info("+Test4");
		CLog.Info("*Test3");
		CLog.Info("*Test2");
		CLog.Info("*Test1");
		CLog.Info("+++Test4");
		CLog.Info("**Test2");
		CLog.Info("*+++Test4");
		CLog.Info("***+Test2");
		CLog.Info("***++++Test4");
		CLog.Info("++++++Test10");
		CLog.Info("++++++Test16");
		CLog.Info("+Test17");
		CLog.Info("+Test18");
		CLog.Info("+Test19");
		CLog.Info("**Test17");
		CLog.Info("#Test0");
		CLog.Info("---------- Setting Group Width to 15");
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_GROUPWIDTH, "15");
		LogFormatter.Config.set(LogFormatterConfig);
		CLog.Info("Test Message");
		CLog.Info("---------- Setting Group Width to 4");
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_GROUPWIDTH, "4");
		LogFormatter.Config.set(LogFormatterConfig);
		CLog.Info("Test Message");
		CLog.Info("---------- Restore default Group Width");
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_GROUPWIDTH, "-1");
		LogFormatter.Config.set(LogFormatterConfig);
		CLog.Info("Test Message");
		CLog.Info("---------- Setting Method Width to 32");
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_METHODWIDTH, "32");
		LogFormatter.Config.set(LogFormatterConfig);
		CLog.Info("Test Message");
		CLog.Info("---------- Setting Method Width to 6");
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_METHODWIDTH, "6");
		LogFormatter.Config.set(LogFormatterConfig);
		CLog.Info("Test Message");
		CLog.Info("---------- Restore default Method Width");
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_METHODWIDTH, "-1");
		LogFormatter.Config.set(LogFormatterConfig);
		CLog.Info("Test Message");
		CLog.Info("---------- Setting Group Width to 4");
		CLog.Info("---------- Setting Method Width to 6");
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_GROUPWIDTH, "4");
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_METHODWIDTH, "6");
		LogFormatter.Config.set(LogFormatterConfig);
		CLog.Info("Test Message");
		CLog.Info("---------- Restore default Group Width");
		CLog.Info("---------- Restore default Method Width");
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_GROUPWIDTH, "-1");
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_METHODWIDTH, "-1");
		LogFormatter.Config.set(LogFormatterConfig);
		CLog.Info("---------- Message Time OFF");
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_MSGTIME, "False");
		LogFormatter.Config.set(LogFormatterConfig);
		CLog.Info("(Header)Test Message");
		CLog.Info("@(NoHeader)Test Message");
		CLog.Info("@$(NoHeader)Test Message");
		CLog.StackTrace("(NoTime)");
		CLog.Info("---------- Message Time ON");
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_MSGTIME, "True");
		LogFormatter.Config.set(LogFormatterConfig);
		CLog.Info("(Header)Test Message");
		CLog.Info("@(NoHeader)Test Message");
		CLog.Info("@$(NoHeader+Time)Test Message");
		CLog.StackTrace("(Time)");
		CLog.Info("---------- Message Header Off");
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_MSGHDR, "False");
		LogFormatter.Config.set(LogFormatterConfig);
		CLog.Info("(NoHeader)Test Message");
		CLog.Info("@(NoHeader)Test Message");
		CLog.Info("@$(NoHeader)Test Message");
		CLog.StackTrace("(NoTime)");
		CLog.Info("---------- Message Header On");
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_MSGHDR, "True");
		LogFormatter.Config.set(LogFormatterConfig);
		CLog.Info("---------- Test external logging");
		Logger TestLogger = Logger.getLogger("");
		TestLogger.info("Test Root Logger");
		TestLogger = Logger.getLogger("Test");
		TestLogger.info("Test Sub Logger");
	}
	
	public static void main(String[] args) {
		CLog.Info("========== Logger Test");
		try {
			LoggerTest Main = new LoggerTest();
			Main.Go(args);
		} catch (Exception e) {
			DebugLog.Logger.logExcept(e);
		}
		CLog.Info("#@~<");
		CLog.Info("========== Done");
	}
	
}
