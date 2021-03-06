package uk.ac.imperial.lsds.seepmaster.comm;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.imperial.lsds.seep.comm.protocol.BootstrapCommand;
import uk.ac.imperial.lsds.seep.comm.protocol.Command;
import uk.ac.imperial.lsds.seep.comm.protocol.CommandFamilyType;
import uk.ac.imperial.lsds.seep.comm.protocol.DeadWorkerCommand;
import uk.ac.imperial.lsds.seep.comm.protocol.MasterWorkerCommand;
import uk.ac.imperial.lsds.seep.comm.protocol.MasterWorkerProtocolAPI;
import uk.ac.imperial.lsds.seep.comm.protocol.StageStatusCommand;
import uk.ac.imperial.lsds.seep.comm.serialization.KryoFactory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;

public class MasterWorkerCommManager {

	final private Logger LOG = LoggerFactory.getLogger(MasterWorkerCommManager.class.getName());
	
	private ServerSocket serverSocket;
	private Kryo k;
	private Thread listener;
	private boolean working = false;
	private MasterWorkerAPIImplementation api;
	
	public MasterWorkerCommManager(int port, MasterWorkerAPIImplementation api){
		this.api = api;
		this.k = KryoFactory.buildKryoForProtocolCommands(this.getClass().getClassLoader());
		try {
			serverSocket = new ServerSocket(port);
			LOG.info(" Listening on {}:{}", InetAddress.getLocalHost(), port);
		} 
		catch (IOException e) {
			e.printStackTrace();
		}
		listener = new Thread(new CommMasterWorker());
		listener.setName(CommMasterWorker.class.getSimpleName());
		// TODO: set uncaughtexceptionhandler
	}
	
	public void start(){
		this.working = true;
		LOG.info("Start MasterWorkerCommManager");
		this.listener.start();
	}
	
	public void stop(){
		//TODO: do some other cleaning work here
		this.working = false;
	}
	
	class CommMasterWorker implements Runnable{

		@Override
		public void run() {
			while(working){
				Input i = null;
				Socket incomingSocket = null;
				try{
					// Blocking call
					incomingSocket = serverSocket.accept();
					
					InputStream is = incomingSocket.getInputStream();
					i = new Input(is);
					
					Command c = k.readObject(i, Command.class);
					if(c.familyType() != CommandFamilyType.MASTERCOMMAND.ofType()) {
						LOG.error("Received an unrecognized (non-Master Command) here");
					}
					MasterWorkerCommand command = (MasterWorkerCommand) c.getCommand();
					short type = command.type();
					// BOOTSTRAP command
					if(type == MasterWorkerProtocolAPI.BOOTSTRAP.type()){
						LOG.info("RX-> Bootstrap command");
						BootstrapCommand bc = command.getBootstrapCommand();
						api.bootstrapCommand(bc);
					}
					// STAGE_STATUS command
					else if(type == MasterWorkerProtocolAPI.STAGE_STATUS.type()) {
						LOG.info("RX-> Stage Status update command");
						StageStatusCommand ssc = command.getStageStatusCommand();
						api.stageStatusCommand(ssc);
					}
					// CRASH command
					else if(type == MasterWorkerProtocolAPI.CRASH.type()){
						LOG.info("RX-> Crash command");
					}
					// DEADWORKER command
					else if(type == MasterWorkerProtocolAPI.DEADWORKER.type()){
						LOG.info("RX-> DeadWorker command");
						DeadWorkerCommand dwc = command.getDeadWorkerCommand();
						api.handleDeadWorker(dwc);
					}
				}
				catch(IOException io){
					io.printStackTrace();
				}
				finally {
					if (incomingSocket != null){
						try {
							i.close();
							incomingSocket.close();
						}
						catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			}	
		}
	}
}
