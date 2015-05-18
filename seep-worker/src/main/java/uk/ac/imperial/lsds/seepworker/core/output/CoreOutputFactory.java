package uk.ac.imperial.lsds.seepworker.core.output;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.imperial.lsds.seep.api.DataReference;
import uk.ac.imperial.lsds.seep.api.DataStoreType;
import uk.ac.imperial.lsds.seep.api.operator.DownstreamConnection;
import uk.ac.imperial.lsds.seep.api.operator.LogicalOperator;
import uk.ac.imperial.lsds.seep.core.OutputAdapter;
import uk.ac.imperial.lsds.seep.infrastructure.EndPoint;
import uk.ac.imperial.lsds.seepcontrib.kafka.config.KafkaConfig;
import uk.ac.imperial.lsds.seepworker.WorkerConfig;

public class CoreOutputFactory {

	final private static Logger LOG = LoggerFactory.getLogger(CoreOutputFactory.class);
	
	public static CoreOutput buildCoreOutputForOperator(WorkerConfig wc, LogicalOperator o, Map<Integer, EndPoint> mapping){
		LOG.info("Building coreOutput...");
		List<OutputAdapter> outputAdapters = new ArrayList<>();
		// Create an InputAdapter per upstream connection -> know with the streamId
		Map<Integer, List<DownstreamConnection>> streamToOpConn = new HashMap<>();
		for(DownstreamConnection dc : o.downstreamConnections()){
			int streamId = dc.getStreamId();
			if(streamToOpConn.containsKey(streamId)){
				streamToOpConn.get(streamId).add(dc);
			}
			else{
				List<DownstreamConnection> l = new ArrayList<>();
				l.add(dc);
				streamToOpConn.put(streamId, l);
			}
		}
		// Perform sanity check. All ops for a given streamId should have same schema
		// TODO:
		
		// Build an output adapter per streamId
		for(Integer streamId : streamToOpConn.keySet()){
			
			List<DownstreamConnection> doCon = streamToOpConn.get(streamId);
			DataStoreType dOriginType = doCon.get(0).getExpectedDataOriginTypeOfDownstream();
			
			OutputAdapter oa = null;
			if(dOriginType == DataStoreType.NETWORK){
				// Create outputAdapter
				LOG.info("Building outputAdapter for downstream streamId: {} of type: {}", streamId, "NETWORK");
				oa = OutputAdapterFactory.buildOutputAdapterOfTypeNetworkForOps(wc, streamId, doCon, mapping);
			}
			else if(dOriginType == DataStoreType.KAFKA){
				// Create outputAdapter to send data to Kafka, and *not* to the downstream operator
				KafkaConfig kc = new KafkaConfig( doCon.get(0).getExpectedDataOriginOfDownstream().getConfig() );
				LOG.info("Building outputAdapter for downstream streamId: {} of type: {}", streamId, "KAFKA");
				oa = OutputAdapterFactory.buildOutputAdapterOfTypeKafkaForOps(kc, streamId, doCon);
			}
			outputAdapters.add(oa);
		}
		CoreOutput cOutput = new CoreOutput(outputAdapters);
		LOG.info("Building coreOutput...OK");
		return cOutput;
	}

	public static CoreOutput buildCoreOutputForStage(WorkerConfig wc, Map<Integer, Set<DataReference>> output) {
		return null;
	}
}
