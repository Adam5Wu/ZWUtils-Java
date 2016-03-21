
package com.necla.am.zwutils.Workflow;

import com.necla.am.zwutils.Modeling.ITimeStamp;
import com.necla.am.zwutils.Subscriptions.IRegistration;
import com.necla.am.zwutils.Subscriptions.ISubscription;


public interface IDataFlowConnector<IN, OUT> extends ISubscription<IN>, IRegistration<OUT> {
	
	String Name();
	
	void Init();
	
	boolean HeartBeat(ITimeStamp Now);
	
	void FInit();
	
}
