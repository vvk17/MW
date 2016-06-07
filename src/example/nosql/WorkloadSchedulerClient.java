/*********************************************************************
 *
 * Licensed Materials - Property of IBM
 * Product ID = 5698-WSH
 *
 * Copyright IBM Corp. 2015. All Rights Reserved.
 *
 ********************************************************************/
package example.nosql;

import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ibm.twa.applab.client.WorkloadService;
import com.ibm.twa.applab.client.helpers.Step;
import com.ibm.twa.applab.client.helpers.TriggerFactory;
import com.ibm.twa.applab.client.helpers.WAProcess;
import com.ibm.twa.applab.client.helpers.steps.RestfulStep;
import com.ibm.tws.simpleui.bus.Task;
import com.ibm.tws.simpleui.bus.TaskLibrary;
import com.ibm.tws.simpleui.bus.Trigger;

public class WorkloadSchedulerClient {
	private static final String WORKLOAD_SCHEDULER = "WorkloadScheduler";	 
	private static final String AGENT_SUFFIX = "_CLOUD";

	// Once connected and authenticated correctly, this boolean is true
	private boolean connected = false;
	// Hold the instance of the WorkloadService
	private WorkloadService ws;
	
	private String cloudAgentName = "_CLOUD";
	
	//Enable debug mode if needed
	private boolean debugMode = false;
	
	// Default empty constructor.
	public WorkloadSchedulerClient(){};
		
	
	/**
	 *  Connects and authenticates to the server, exploring the content of 
	 *  VCAP_SERVICES content.
	 *  
	 *  @param o: Output Stream to write useful info
	 */
	public void connect() {
		 String vcapJSONString = System.getenv("VCAP_SERVICES");

    	if (vcapJSONString != null) {
    		// parse the VCAP JSON structure
    		JsonObject obj =  new JsonParser().parse(vcapJSONString).getAsJsonObject();
        	System.out.println("Looking for Workload Automation Service...");
    		Entry<String, JsonElement> wsEntry = null;
    		Set<Entry<String, JsonElement>> entries = obj.entrySet();
    		// Look for the VCAP key that holds the Workload Scheduler service information
    		for (Entry<String, JsonElement> eachEntry : entries) {				
    			if (eachEntry.getKey().startsWith(WORKLOAD_SCHEDULER)) {
    				wsEntry = eachEntry;
    				break;
    			}
    		}

			if (wsEntry == null) {
				System.out.println("Could not connect: Workload Scheduler service information not found in VCAP_SERVICES!");
				throw new RuntimeException("Could not find " + WORKLOAD_SCHEDULER + " key in VCAP_SERVICES env variable");
			}

			JsonObject twaService = wsEntry.getValue().getAsJsonArray().get(0).getAsJsonObject();
			JsonObject credentials = twaService.get("credentials").getAsJsonObject();

			System.out.println("Starting Workload Automation connection..");
			String url = credentials.get("url").getAsString();
			int index = url.indexOf("tenantId=") + 9;
			String prefix = url.substring(index, index + 2);
			System.out.println("prefix=" + prefix);
			cloudAgentName = prefix + AGENT_SUFFIX;
			try {
//				WorkloadService.disableCertificateValidation();
				ws = new WorkloadService(url);
				ws.setDebugMode(debugMode);
			} catch (Exception e) {
				System.out.println("Could not connect to the service: " + e.getClass().getName() + " " + e.getMessage());
				return;
			}
			connected = true;
			System.out.println("Connection obtained.");
    	}
		else {
			throw new RuntimeException("VCAP_SERVICES not found");
		}

	}


	public synchronized Task setupProcess(String libName, String processName, String processDescription, String callbackURL) throws Exception {
		if (!connected) connect();
		
		Task process = null;
		
		// check if process exists
		List<Task> processes = ws.getTasks(ws.getDefaultLibrary());
		for (Task p : processes) {
			if (p.getName().equalsIgnoreCase(processName)) {
				process = p;
			}
		}
		
		// If not found, create the process
		if (process==null) {
			System.out.println("Creating Workload Scheduler Process...");
			// Use default xx_CLOUD agent to run the step 
			String agentName = cloudAgentName;

			// Crate a new Process
			WAProcess p = new WAProcess(processName, processDescription); // Process name and description
			
			// Create a REST Step
//			RESTAction action = new RESTAction(callbackURL,"application/json","application/json",RestfulStep.POST_METHOD,null); 
//			RESTAuthenticationData auth = RESTAuthenticationData.fromUserPwd("userName", "password"); 
//			RESTInput input = RESTInput.fromText("Your text body"); 
//			Step s1 = new RestfulStep(agentName, action, auth, input);
			
			Step s1 = new RestfulStep(agentName, callbackURL, "*/*", "text/plain", RestfulStep.GET_METHOD);
			p.addStep(s1); 
		
			// Define trigger to run it every day at 10
			Trigger trigger = TriggerFactory.everyDayAt(10, 0);
			
			p.addTrigger(trigger);
			
			// Finally create and enable the process
		    process = ws.createAndEnableTask(p);
		}
		
		System.out.println("Workload Scheduler Process id: "+process.getId());
		return process;
	}

}
