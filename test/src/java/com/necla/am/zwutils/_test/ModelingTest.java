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

import com.necla.am.zwutils.Logging.DebugLog;
import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.Misc.Misc.TimeSystem;
import com.necla.am.zwutils.Misc.Misc.TimeUnit;
import com.necla.am.zwutils.Modeling.IDecoratable;
import com.necla.am.zwutils.Modeling.IIdentifier;
import com.necla.am.zwutils.Modeling.IIdentifier.Nested;
import com.necla.am.zwutils.Modeling.ITimeStamp;


public class ModelingTest {
	
	protected static final IGroupLogger CLog = new GroupLogger("Main");
	
	public void Go(String[] args) {
		
		try {
			CLog.Info("#---------- Identifiers");
			IIdentifier.Nested<?> ROOT = IIdentifier.Nested.ROOT;
			CLog.Info("* Root Identifier = %s", ROOT);
			
			IIdentifier A = new IIdentifier.Named.Impl("A");
			CLog.Info("* Named Identifier A = %s", A);
			
			CLog.Info("* Identifier A == ROOT ? %s", A.equals(ROOT));
			CLog.Info("* Identifier ROOT == A ? %s", ROOT.equals(A));
			
			IIdentifier B = new IIdentifier.Named.Impl("B");
			CLog.Info("* Named Identifier B = %s", B);
			
			CLog.Info("* Identifier A == B ? %s", A.equals(B));
			CLog.Info("* Identifier B == A ? %s", B.equals(A));
			
			IIdentifier A2 = new IIdentifier.Named.Impl("A");
			CLog.Info("* Named Identifier A2 = %s", A2);
			
			CLog.Info("* Identifier A == A2 ? %s", A.equals(A2));
			CLog.Info("* Identifier A2 == A ? %s", A2.equals(A));
			
			IIdentifier RA = new IIdentifier.Nested.Named.Impl<Nested<?>>(ROOT, "A");
			CLog.Info("* Nested Named Identifier Root->A = %s", RA);
			
			CLog.Info("* Identifier RA == ROOT ? %s", RA.equals(ROOT));
			CLog.Info("* Identifier ROOT == RA ? %s", ROOT.equals(RA));
			CLog.Info("* Identifier RA == A ? %s", RA.equals(A));
			CLog.Info("* Identifier A == RA ? %s", A.equals(RA));
			
			IIdentifier RB = new IIdentifier.Nested.Named.Impl<Nested<?>>(ROOT, "B");
			CLog.Info("* Nested Named Identifier Root->B = %s", RB);
			
			CLog.Info("* Identifier RA == RB ? %s", RA.equals(RB));
			CLog.Info("* Identifier RB == RA ? %s", RB.equals(RA));
			
			IIdentifier RA2 = new IIdentifier.Nested.Named.Impl<Nested<?>>(ROOT, "A");
			CLog.Info("* Nested Named Identifier Root->A2 = %s", RA2);
			
			CLog.Info("* Identifier RA == RA2 ? %s", RA.equals(RA2));
			CLog.Info("* Identifier RA2 == RA ? %s", RA2.equals(RA));
			
			CLog.Info("#---------- Decoratable");
			IDecoratable.Type<IIdentifier> DA = new IDecoratable.Value<IIdentifier>(A);
			CLog.Info("* Decoratable Identifier A (Not Decorated) = %s", DA);
			IDecoratable.Type<IIdentifier> DA2 = new IDecoratable.Value<IIdentifier>(A, "Test");
			CLog.Info("* Decoratable Identifier A3 (Decorated) = %s", DA2);
			
			CLog.Info("* Decoratable DA == DA ? %s", DA.equals(DA));
			CLog.Info("* Decoratable DA == DA2 ? %s", DA.equals(DA2));
			
			CLog.Info("* Decoratable DA2 == DA ? %s", DA2.equals(DA));
			CLog.Info("* Decoratable DA2 == DA2 ? %s", DA2.equals(DA2));
			
			IDecoratable.Type<IIdentifier> DRA = new IDecoratable.Value<IIdentifier>(RA);
			CLog.Info("* Decoratable Nested Identifier Root->A (Not Decorated) = %s", DRA);
			
			CLog.Info("* Decoratable DA == DRA ? %s", DA.equals(DRA));
			CLog.Info("* Decoratable DRA == DA ? %s", DRA.equals(DA));
			
			IDecoratable.Type<IIdentifier> DRA2 = new IDecoratable.Value<IIdentifier>(RA2, "Test1");
			CLog.Info("* Decoratable Nested Identifier Root->(A2) (Decorated, non-unique) = %s", DRA2);
			
			CLog.Info("* Decoratable DRA == DRA2 ? %s", DRA.equals(DRA2));
			CLog.Info("* Decoratable DRA2 == DRA ? %s", DRA2.equals(DRA));
			
			CLog.Info("#---------- TimeStamp");
			ITimeStamp TS = ITimeStamp.Impl.Now();
			CLog.Info("* TimeStamp TS = %s", TS);
			
			ITimeStamp TS2 = new ITimeStamp.Impl(TS.VALUE(TimeSystem.UNIX, TimeUnit.DAY), TimeSystem.UNIX,
					TimeUnit.DAY);
			CLog.Info("* TimeStamp TS2 = %s", TS2);
			
			ITimeStamp TS3 = new ITimeStamp.Impl(TS.VALUE(TimeSystem.GREGORIAN, TimeUnit.DAY),
					TimeSystem.GREGORIAN, TimeUnit.DAY);
			CLog.Info("* TimeStamp TS3 = %s", TS3);
			
			CLog.Info("* TimeStamp TS = TS2 ? %s", TS.equals(TS2));
			CLog.Info("* TimeStamp TS2 = TS3 ? %s", TS2.equals(TS3));
			CLog.Info("* TimeStamp TS < TS2 ? %s", TS.Before(TS2));
			CLog.Info("* TimeStamp TS2 < TS ? %s", TS2.Before(TS));
			CLog.Info("* TimeStamp TS > TS2 ? %s", TS.After(TS2));
			CLog.Info("* TimeStamp TS2 > TS ? %s", TS2.After(TS));
			CLog.Info("* TimeStamp TS <- TS2 = %s",
					Misc.FormatDeltaTime(TS.MillisecondsFrom(TS2), false));
			CLog.Info("* TimeStamp TS -> TS2 = %s", Misc.FormatDeltaTime(TS.MillisecondsTo(TS2), false));
			CLog.Info("* TimeStamp TS2 <- TS = %s",
					Misc.FormatDeltaTime(TS2.MillisecondsFrom(TS), false));
			CLog.Info("* TimeStamp TS2 -> TS = %s", Misc.FormatDeltaTime(TS2.MillisecondsTo(TS), false));
			
		} catch (Exception e) {
			CLog.logExcept(e);
		}
	}
	
	public static void main(String[] args) {
		CLog.Info("========== Modeling Test");
		try {
			ModelingTest Main = new ModelingTest();
			Main.Go(args);
		} catch (Exception e) {
			DebugLog.Logger.logExcept(e);
		}
		CLog.Info("#@~<");
		CLog.Info("========== Done");
	}
	
}
