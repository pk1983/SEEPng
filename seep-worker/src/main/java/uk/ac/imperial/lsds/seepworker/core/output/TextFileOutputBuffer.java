package uk.ac.imperial.lsds.seepworker.core.output;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.WritableByteChannel;
import java.nio.file.FileSystems;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.imperial.lsds.seep.api.DataReference;
import uk.ac.imperial.lsds.seep.api.operator.sources.FileConfig;
import uk.ac.imperial.lsds.seep.core.OBuffer;

public class TextFileOutputBuffer implements OBuffer {

	final private static Logger LOG = LoggerFactory.getLogger(FileOutputBuffer.class);
	
	private DataReference dr;
	private int id;
	private BufferedWriter fstream;
	
	public TextFileOutputBuffer(DataReference dr, int batchSize) {
		this.dr = dr;
		this.id = dr.getId();
		// Create output file attaching id for unique naming and output stream
		this.fstream = createOutputFile();
	}
		
	private BufferedWriter createOutputFile() {
		String path = dr.getDataStore().getConfig().getProperty(FileConfig.FILE_PATH);
		String pathAndFilename = path + id;
		Path p = FileSystems.getDefault().getPath(pathAndFilename);
		FileWriter outputFile;
		BufferedWriter bws = null;
		try {
			outputFile = new FileWriter (p.toString(), true);
			bws = new BufferedWriter(outputFile);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return bws;
	}

	@Override
	public int id() {
		return id;
	}

	@Override
	public DataReference getDataReference() {
		return dr;
	}

	@Override
	public boolean drainTo(WritableByteChannel channel) {
		LOG.error("Not implemented for TextFileOutputBuffer");
		return false;
	}

	@Override
	public boolean write(byte[] data) {
		// write that data to the output stream
		try {
			fstream.write(dr.getDataStore().getSchema().getSchemaParser().stringFromBytes(data));
			fstream.newLine();
			// FIXME The workers do not currently do all the necessary clean up 
			// (like flushing buffers) when they finish. Long term it will be
			// more efficient to let the writer flush as the buffer fills up, 
			// then flush it one last time. For now we flush after every write
			// to make sure everything makes it to disk.
			fstream.flush();
		} 
		catch (IOException e) {
			e.printStackTrace();
		}
		return true;
	}

	@Override
	public boolean readyToWrite() {
		LOG.error("Not implemented for FileOutputBuffer");
		return false;
	}

}
