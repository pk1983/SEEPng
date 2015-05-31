package uk.ac.imperial.lsds.seepworker.core.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.imperial.lsds.seep.api.ConnectionType;
import uk.ac.imperial.lsds.seep.api.DataReference;
import uk.ac.imperial.lsds.seep.api.DataStoreType;
import uk.ac.imperial.lsds.seep.api.data.Schema;
import uk.ac.imperial.lsds.seep.comm.IOComm;
import uk.ac.imperial.lsds.seep.core.InputAdapter;
import uk.ac.imperial.lsds.seepcontrib.kafka.comm.KafkaDataStream;
import uk.ac.imperial.lsds.seepworker.WorkerConfig;

public class InputAdapterFactory {

	// FIXME: refactor -> all inputadapters that are per buffer do the same. code reuse
	
	final static private Logger LOG = LoggerFactory.getLogger(IOComm.class.getName());
	
	public static List<InputAdapter> buildInputAdapterForStreamId(WorkerConfig wc, int streamId, List<InputBuffer> buffers, Set<DataReference> drefs, ConnectionType connType) {
		List<InputAdapter> ias = null;
		DataStoreType type = drefs.iterator().next().getDataStore().type();
		if(type.equals(DataStoreType.NETWORK)){
			ias = buildInputAdapterOfTypeNetworkForOps(wc, streamId, drefs, buffers, connType);
		}
		else if(type.equals(DataStoreType.FILE)){
			ias = buildInputAdapterOfTypeFileForOps(wc, streamId, drefs, buffers, connType);
		}
		else if(type.equals(DataStoreType.KAFKA)){
			ias = buildInputAdapterOfTypeKafkaForOps(wc, streamId, drefs, buffers, connType);
		}
		return ias;
	}

	private static List<InputAdapter> buildInputAdapterOfTypeNetworkForOps(
			WorkerConfig wc, int streamId, Set<DataReference> drefs, List<InputBuffer> buffers, ConnectionType connType) {
		List<InputAdapter> ias = new ArrayList<>();
		short cType = connType.ofType();
		Schema expectedSchema = drefs.iterator().next().getDataStore().getSchema();
		if(cType == ConnectionType.ONE_AT_A_TIME.ofType()) {
			// one-queue-per-conn, one-single-queue, etc.
			LOG.info("Creating NETWORK inputAdapter for upstream streamId: {} of type {}", streamId, "ONE_AT_A_TIME");
			for(InputBuffer buffer : buffers) {
				InputAdapter ia = new NetworkDataStream(wc, streamId, buffer, expectedSchema);
				ias.add(ia);
			}
		}
		else if(cType == ConnectionType.UPSTREAM_SYNC_BARRIER.ofType()) {
			LOG.info("Creating NETWORK inputAdapter for upstream streamId: {} of type {}", streamId, "UPSTREAM_SYNC_BARRIER");
			InputAdapter ia = new NetworkBarrier(wc, streamId, buffers, expectedSchema);
			ias.add(ia);
		}
		return ias;
	}
	
	private static List<InputAdapter> buildInputAdapterOfTypeFileForOps(
			WorkerConfig wc, int streamId, Set<DataReference> drefs, List<InputBuffer> buffers, ConnectionType connType) {
		List<InputAdapter> ias = new ArrayList<>();
		short cType = connType.ofType();
		Schema expectedSchema = drefs.iterator().next().getDataStore().getSchema();
		if(cType == ConnectionType.ONE_AT_A_TIME.ofType()) {
			// one-queue-per-conn, one-single-queue, etc.
			LOG.info("Creating FILE inputAdapter for upstream streamId: {} of type {}", streamId, "ONE_AT_A_TIME");
			for(InputBuffer buffer : buffers) {
				InputAdapter ia = new FileDataStream(wc, streamId, buffer, expectedSchema);
				ias.add(ia);
			}
		}
		return ias;
	}
	
	private static List<InputAdapter> buildInputAdapterOfTypeKafkaForOps(
			WorkerConfig wc, int streamId, Set<DataReference> drefs, List<InputBuffer> buffers, ConnectionType connType) {
		List<InputAdapter> ias = new ArrayList<>();
		short cType = connType.ofType();
		Schema expectedSchema = drefs.iterator().next().getDataStore().getSchema();
		if(cType == ConnectionType.ONE_AT_A_TIME.ofType()) {
			// one-queue-per-conn, one-single-queue, etc.
			LOG.info("Creating KAFKA inputAdapter for upstream streamId: {} of type {}", streamId, "ONE_AT_A_TIME");
			for(InputBuffer buffer : buffers) {
				InputAdapter ia = new KafkaDataStream(streamId, buffer, expectedSchema);
				ias.add(ia);
			}
		}
		return ias;
	}
}
