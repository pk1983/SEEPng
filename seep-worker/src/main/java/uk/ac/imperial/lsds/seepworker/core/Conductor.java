package uk.ac.imperial.lsds.seepworker.core;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.imperial.lsds.seep.api.DataReference;
import uk.ac.imperial.lsds.seep.api.SeepTask;
import uk.ac.imperial.lsds.seep.api.StatefulSeepTask;
import uk.ac.imperial.lsds.seep.api.operator.LogicalOperator;
import uk.ac.imperial.lsds.seep.api.operator.SeepLogicalQuery;
import uk.ac.imperial.lsds.seep.api.state.SeepState;
import uk.ac.imperial.lsds.seep.comm.Connection;
import uk.ac.imperial.lsds.seep.comm.protocol.StageStatusCommand;
import uk.ac.imperial.lsds.seep.core.DataStoreSelector;
import uk.ac.imperial.lsds.seep.core.EventAPI;
import uk.ac.imperial.lsds.seep.infrastructure.EndPoint;
import uk.ac.imperial.lsds.seep.scheduler.ScheduleDescription;
import uk.ac.imperial.lsds.seep.scheduler.Stage;
import uk.ac.imperial.lsds.seepworker.WorkerConfig;
import uk.ac.imperial.lsds.seepworker.comm.WorkerMasterAPIImplementation;
import uk.ac.imperial.lsds.seepworker.core.input.CoreInput;
import uk.ac.imperial.lsds.seepworker.core.input.CoreInputFactory;
import uk.ac.imperial.lsds.seepworker.core.output.CoreOutput;
import uk.ac.imperial.lsds.seepworker.core.output.CoreOutputFactory;

public class Conductor {

	final private Logger LOG = LoggerFactory.getLogger(Conductor.class.getName());
	
	private WorkerConfig wc;
	private InetAddress myIp;
	private WorkerMasterAPIImplementation masterApi;
	private Connection masterConn;
	private int id;
	private SeepLogicalQuery query;
	private Map<Integer, EndPoint> mapping;
	
	private List<DataStoreSelector> dataStoreSelectors;
	
	private CoreInput coreInput;
	private CoreOutput coreOutput;
	private ProcessingEngine engine;
	
	// Keep stageId - scheduleTask
	private Map<Integer, ScheduleTask> scheduleTasks;
	
	public Conductor(InetAddress myIp, WorkerMasterAPIImplementation masterApi, Connection masterConn, WorkerConfig wc){
		this.myIp = myIp;
		this.masterApi = masterApi;
		this.masterConn = masterConn;
		this.wc = wc;
		this.engine = ProcessingEngineFactory.buildProcessingEngine(wc);
		this.scheduleTasks = new HashMap<>();
	}
	
	public void setQuery(int id, SeepLogicalQuery query, Map<Integer, EndPoint> mapping) {
		this.id = id;
		this.query = query;
		this.mapping = mapping;
	}
	
	public void materializeAndConfigureTask() {
		int opId = getOpIdLivingInThisEU(id);
		LogicalOperator o = query.getOperatorWithId(opId);
		LOG.info("Found LogicalOperator: {} mapped to this executionUnit: {} stateful: {}", o.getOperatorName(), 
				id, o.isStateful());
		
		SeepTask task = o.getSeepTask();
		LOG.info("Configuring local task: {}", task.toString());
		// set up state if any
		SeepState state = o.getState();
		if(o.isStateful()){
			LOG.info("Configuring state of local task: {}", state.toString());
			((StatefulSeepTask)task).setState(state);
		}
		// This creates one inputAdapter per upstream stream Id
		coreInput = CoreInputFactory.buildCoreInputForOperator(wc, o);
		// This creates one outputAdapter per downstream stream Id
		coreOutput = CoreOutputFactory.buildCoreOutputForOperator(wc, o, mapping);
		
		dataStoreSelectors = DataStoreSelectorFactory.buildDataStoreSelector(coreInput, 
				coreOutput, wc, o, myIp, wc.getInt(WorkerConfig.DATA_PORT));

		// FIXME: this is ugly, why ns is special?
		for(DataStoreSelector dss : dataStoreSelectors) {
			if(dss instanceof EventAPI) coreOutput.setEventAPI((EventAPI)dss);
		}
		
		engine.setId(o.getOperatorId());
		engine.setTask(task);
		engine.setSeepState(state);
		engine.setCoreInput(coreInput);
		engine.setCoreOutput(coreOutput);
		
		// Initialize system
		LOG.info("Setting up task...");
		task.setUp(); // setup method of task
		LOG.info("Setting up task...OK");
		for(DataStoreSelector dss : dataStoreSelectors) {
			dss.initSelector();
		}
	}
	
	public void configureScheduleTasks(int id, ScheduleDescription sd, SeepLogicalQuery slq) {
		this.id = id;
		this.query = slq;
		// Create ScheduleTask for every stage
		for(Stage s : sd.getStages()) {
			ScheduleTask st = ScheduleTask.buildTaskFor(id, s, slq);
			scheduleTasks.put(s.getStageId(), st);
		}
	}
	
	public void scheduleTask(int stageId, Set<DataReference> input, Set<DataReference> output) {
		ScheduleTask task = this.scheduleTasks.get(stageId);
		// TODO: configure input and output data and then run this task
		// TODO: we may need a datareferencemanager that can keep track of those even when tasks are not alive here
		
		// TODO: do the processing while storing the data in output data references
		
		// TODO: notify processing is done, so scheduleTask is done, and point out to the datareferences
		masterApi.scheduleTaskStatus(masterConn, stageId, id, StageStatusCommand.Status.OK, output);
	}
	
	private int getOpIdLivingInThisEU(int id) {
		for(Entry<Integer, EndPoint> entry : mapping.entrySet()) {
			if(entry.getValue().getId() == id) return entry.getKey();
		}
		return -1;
	}
	
	public void startProcessing(){
		LOG.info("Starting processing engine...");
		for(DataStoreSelector dss : dataStoreSelectors) {
			dss.startSelector();
		}
		engine.start();
	}
	
	public void stopProcessing(){
		LOG.info("Stopping processing engine...");
		engine.stop();
		for(DataStoreSelector dss : dataStoreSelectors) {
			dss.stopSelector();
		}
		LOG.info("Stopping processing engine...OK");
	}
		
}
