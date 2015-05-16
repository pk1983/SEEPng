package uk.ac.imperial.lsds.seepmaster.query;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.imperial.lsds.seep.api.operator.Operator;
import uk.ac.imperial.lsds.seep.api.operator.SeepLogicalQuery;
import uk.ac.imperial.lsds.seep.comm.Comm;
import uk.ac.imperial.lsds.seep.comm.Connection;
import uk.ac.imperial.lsds.seep.comm.protocol.MasterWorkerCommand;
import uk.ac.imperial.lsds.seep.comm.protocol.ProtocolCommandFactory;
import uk.ac.imperial.lsds.seep.comm.serialization.KryoFactory;
import uk.ac.imperial.lsds.seep.infrastructure.EndPoint;
import uk.ac.imperial.lsds.seep.util.Utils;
import uk.ac.imperial.lsds.seepmaster.LifecycleManager;
import uk.ac.imperial.lsds.seepmaster.MasterConfig;
import uk.ac.imperial.lsds.seepmaster.infrastructure.master.ExecutionUnit;
import uk.ac.imperial.lsds.seepmaster.infrastructure.master.InfrastructureManager;

import com.esotericsoftware.kryo.Kryo;

public class MaterializedQueryManager implements QueryManager {

	final private Logger LOG = LoggerFactory.getLogger(MaterializedQueryManager.class);
	
	private MasterConfig mc;
	private static MaterializedQueryManager qm;
	private LifecycleManager lifeManager;
	private SeepLogicalQuery slq;
	private int executionUnitsRequiredToStart;
	private InfrastructureManager inf;
	private Map<Integer, EndPoint> opToEndpointMapping;
	private final Comm comm;
	private final Kryo k;
	
	// Query information
	private String pathToQueryJar;
	private String definitionClassName;
	private String[] queryArgs;
	private String composeMethodName;
	
	// convenience method for testing
	public static MaterializedQueryManager buildTestMaterializedQueryManager(SeepLogicalQuery lsq, 
			InfrastructureManager inf, Map<Integer, EndPoint> mapOpToEndPoint, Comm comm) {
		return new MaterializedQueryManager(lsq, inf, mapOpToEndPoint, comm);
	}
	
	private MaterializedQueryManager(SeepLogicalQuery lsq, InfrastructureManager inf, 
			Map<Integer, EndPoint> opToEndpointMapping, Comm comm) {
		this.slq = lsq;
		this.executionUnitsRequiredToStart = this.computeRequiredExecutionUnits(lsq);
		this.inf = inf;
		this.opToEndpointMapping = opToEndpointMapping;
		this.comm = comm;
		this.k = KryoFactory.buildKryoForMasterWorkerProtocol();
	}
	
	private MaterializedQueryManager(InfrastructureManager inf, Map<Integer, EndPoint> mapOpToEndPoint, 
			Comm comm, LifecycleManager lifeManager, MasterConfig mc) {
		this.inf = inf;
		this.opToEndpointMapping = mapOpToEndPoint;
		this.comm = comm;
		this.lifeManager = lifeManager;
		this.k = KryoFactory.buildKryoForMasterWorkerProtocol();
		this.mc = mc;
	}
	
	public static MaterializedQueryManager getInstance(InfrastructureManager inf, Map<Integer, EndPoint> mapOpToEndPoint, 
			Comm comm, LifecycleManager lifeManager, MasterConfig mc) {
		if(qm == null){
			return new MaterializedQueryManager(inf, mapOpToEndPoint, comm, lifeManager, mc);
		}
		else{
			return qm;
		}
	}
	
	private boolean canStartExecution() {
		return inf.executionUnitsAvailable() >= executionUnitsRequiredToStart;
	}
	
	@Override
	public boolean loadQueryFromParameter(SeepLogicalQuery slq, String pathToQueryJar, String definitionClass, 
			String[] queryArgs, String composeMethod) {
		boolean allowed = lifeManager.canTransitTo(LifecycleManager.AppStatus.QUERY_SUBMITTED);
		if(!allowed){
			LOG.error("Attempt to violate application lifecycle");
			return false;
		}
		this.slq = slq;
		this.pathToQueryJar = pathToQueryJar;
		this.definitionClassName = definitionClass;
		this.queryArgs = queryArgs;
		this.composeMethodName = composeMethod;
		LOG.debug("Logical query loaded: {}", slq.toString());
		this.executionUnitsRequiredToStart = this.computeRequiredExecutionUnits(slq);
		LOG.info("New query requires: {} units to start execution", this.executionUnitsRequiredToStart);
		lifeManager.tryTransitTo(LifecycleManager.AppStatus.QUERY_SUBMITTED);
		return true;
	}
	
	@Override
	public boolean loadQueryFromFile(String pathToQueryJar, String definitionClass, String[] queryArgs, String composeMethod) {
		boolean allowed = lifeManager.canTransitTo(LifecycleManager.AppStatus.QUERY_SUBMITTED);
		if(!allowed){
			LOG.error("Attempt to violate application lifecycle");
			return false;
		}
		this.pathToQueryJar = pathToQueryJar;
		// get logical query 
		this.slq = Utils.executeComposeFromQuery(pathToQueryJar, definitionClass, queryArgs, "compose");
		LOG.debug("Logical query loaded: {}", slq.toString());
		this.executionUnitsRequiredToStart = this.computeRequiredExecutionUnits(slq);
		LOG.info("New query requires: {} units to start execution", this.executionUnitsRequiredToStart);
		lifeManager.tryTransitTo(LifecycleManager.AppStatus.QUERY_SUBMITTED);
		return true;
	}
	
	@Override
	public boolean deployQueryToNodes() {
		boolean allowed = lifeManager.canTransitTo(LifecycleManager.AppStatus.QUERY_DEPLOYED);
		if(!allowed){
			LOG.error("Attempt to violate application lifecycle");
			return false;
		}
		// Check whether there are sufficient execution units to deploy query
		if(!canStartExecution()){
			LOG.warn("Cannot deploy query, not enough nodes. Required: {}, available: {}"
					, executionUnitsRequiredToStart, inf.executionUnitsAvailable());
			return false;
		}
		// Build mapping for logicalquery
		if(this.opToEndpointMapping != null){
			LOG.info("Using provided mapping for logicalQuery...");
		} 
		else {
			LOG.info("Building mapping for logicalQuery...");
			this.opToEndpointMapping = createMappingOfOperatorWithEndPoint(slq);
		}
		LOG.debug("Mapping for logicalQuery...OK {}", Utils.printMap(opToEndpointMapping));
		Set<Integer> involvedEUId = getInvolvedEuIdIn(opToEndpointMapping.values());
		Set<Connection> connections = inf.getConnectionsTo(involvedEUId);
		sendQueryToNodes(connections, definitionClassName, queryArgs, composeMethodName);
		sendMaterializeTaskToNodes(connections);
		lifeManager.tryTransitTo(LifecycleManager.AppStatus.QUERY_DEPLOYED);
		return true;
	}
	
	private Set<Integer> getInvolvedEuIdIn(Collection<EndPoint> values) {
		Set<Integer> involvedEUs = new HashSet<>();
		for(EndPoint ep : values) {
			involvedEUs.add(ep.getId());
		}
		return involvedEUs;
	}

	@Override
	public boolean startQuery() {
		boolean allowed = lifeManager.canTransitTo(LifecycleManager.AppStatus.QUERY_RUNNING);
		if(!allowed){
			LOG.error("Attempt to violate application lifecycle");
			return false;
		}
		// TODO: take a look at the following two lines. Stateless is good to keep everything lean. Yet consider caching
		Set<Integer> involvedEUId = getInvolvedEuIdIn(opToEndpointMapping.values());
		Set<Connection> connections = inf.getConnectionsTo(involvedEUId);
		// Send start query command
		MasterWorkerCommand start = ProtocolCommandFactory.buildStartQueryCommand();
		comm.send_object_sync(start, connections, k);
		lifeManager.tryTransitTo(LifecycleManager.AppStatus.QUERY_RUNNING);
		return true;
	}
	
	@Override
	public boolean stopQuery() {
		boolean allowed = lifeManager.canTransitTo(LifecycleManager.AppStatus.QUERY_STOPPED);
		if(!allowed){
			LOG.error("Attempt to violate application lifecycle");
			return false;
		}
		// TODO: take a look at the following two lines. Stateless is good to keep everything lean. Yet consider caching
		Set<Integer> involvedEUId = getInvolvedEuIdIn(opToEndpointMapping.values());
		Set<Connection> connections = inf.getConnectionsTo(involvedEUId);
		
		// Send start query command
		MasterWorkerCommand stop = ProtocolCommandFactory.buildStopQueryCommand();
		comm.send_object_sync(stop, connections, k);
		lifeManager.tryTransitTo(LifecycleManager.AppStatus.QUERY_STOPPED);
		return true;
	}
	
	public Map<Integer, EndPoint> createMappingOfOperatorWithEndPoint(SeepLogicalQuery slq) {
		Map<Integer, EndPoint> mapping = new HashMap<>();
		for(Operator lso : slq.getAllOperators()){
			int opId = lso.getOperatorId();
			ExecutionUnit eu = inf.getExecutionUnit();
			EndPoint ep = eu.getEndPoint();
			LOG.debug("LogicalOperator: {} will run on: {} -> ({})", opId, ep.getId(), ep.getIp().toString());
			mapping.put(opId, ep);
		}
		return mapping;
	}

	private int computeRequiredExecutionUnits(SeepLogicalQuery lsq) {
		return lsq.getAllOperators().size();
	}
	
	private void sendQueryToNodes(Set<Connection> connections, String definitionClassName, String[] queryArgs, String composeMethodName) {
		// Send data file to nodes
		byte[] queryFile = Utils.readDataFromFile(pathToQueryJar);
		LOG.info("Sending query file of size: {} bytes", queryFile.length);
		MasterWorkerCommand code = ProtocolCommandFactory.buildCodeCommand(queryFile, definitionClassName, queryArgs, composeMethodName);
		comm.send_object_sync(code, connections, k);
		LOG.info("Sending query file...DONE!");
	}
	
	private void sendMaterializeTaskToNodes(Set<Connection> connections) {
		LOG.info("Sending materialize task command to nodes...");
		MasterWorkerCommand materializeCommand = ProtocolCommandFactory.buildMaterializeTaskCommand(this.opToEndpointMapping);
		comm.send_object_sync(materializeCommand, connections, k);
		LOG.info("Sending materialize task command to nodes...OK");
	}
	
}