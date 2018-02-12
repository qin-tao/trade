package tao.trading.strategy.tm;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

public class StrategyParm {

	public String strategyName;
	public String fileName;
    public Properties props = new Properties();
	
	public StrategyParm(String fileName) {
		super();
		this.fileName = fileName;
	}
	
    public void loadParmeters() throws FileNotFoundException, IOException{
    	//String fileName = strategyName + "_Parm.properties";
    	FileInputStream fis = new FileInputStream(fileName);
    	props.load(fis);

    }
    
	int getParm0(int index){
		String parm = props.getProperty("Parm"+index);
	    String delims = ",";
	    String[] tokens = parm.split(delims);
	    return new Integer(tokens[0]);
	}

	int getParm1(int index){
		String parm = props.getProperty("Parm"+index);
	    String delims = ",";
	    String[] tokens = parm.split(delims);
	    return new Integer(tokens[1]);
	}
}
