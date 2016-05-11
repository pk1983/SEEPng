package ir;

public interface Traceable {
	
	public void composeIdGenerator(IdGen idGen);
	public int getId();
	public void setName(String name);
	public String getName();
	public void addInput(Traceable t);
	public void addOutput(Traceable t);
	public void isInputOf(Traceable t);
	public void isOutputOf(Traceable t);
	public String toString();
	
}
