package tao.trading;

import java.io.FileInputStream;
import java.util.Properties;

public class Env_Setting
{
    public Properties props = new Properties();

	private int ib_id;
	private String mode;
	private String env;
	private int port;
	private String propFileName;
	
	private String shutdown;


	public Env_Setting(String propFileName) throws Exception {
		super();
		this.propFileName = propFileName;
	
		FileInputStream fis = new FileInputStream(propFileName);
	    props.load(fis);

	    mode = props.getProperty("MODE");
	    ib_id = Integer.parseInt(props.getProperty("IB_ID"));
	    port = Integer.parseInt(props.getProperty("PORT"));
    	shutdown = getStringProperty("SHUTDOWN");
	}

	private String getStringProperty( String propName ){
    	String p = props.getProperty(propName);
    	if ( p != null )
    		return p.trim();
    	else
    		return null;
	}

	
	public boolean getBooleanProperty( String propName ){
    	String p = props.getProperty(propName);
    	if ( p != null ){
    		if ("True".equalsIgnoreCase(p.trim()))
    			return true;
    	}
    	return false;
	}
	

	public int getIb_id() {
		return ib_id;
	}

	public int getMode( String strategyName) {
	    String mode = props.getProperty(strategyName.toUpperCase()+ ".mode");
	    if ("Real".equalsIgnoreCase(mode))
	    	return Constants.REAL_MODE;
	    else if ("Signal".equalsIgnoreCase(mode))
	    	return Constants.SIGNAL_MODE;
	    else 
	    	return Constants.TEST_MODE;
	}

	public String getEnv() {
		return env;
	}

	public String getShutdown() {
		return shutdown;
	}

	public Properties getProps() {
		return props;
	}

	public int getPort() {
		return port;
	}

	public int loadProperty( String propertyFileName){
 	    try{
 	    	FileInputStream fis = new FileInputStream(propertyFileName);
 	    	props.load(fis);
 
 	    	mode = props.getProperty("MODE");
 	    	port = Integer.parseInt(props.getProperty("PORT"));
	    	shutdown = props.getProperty("SHUTDOWN");
 	    }
 	    catch( Exception e ){
 	    	e.printStackTrace();
 	    	return -1;
 	    }
 	    return 0;
	}

	

	public boolean getStragety(String st_name){
    	if (( props.getProperty("strategy.list") != null ) && ( props.getProperty("strategy.list").indexOf(st_name) !=-1 ))
    		return true;
    	else
    		return false;
	}

	public int getStragetyMode(String st_name){
		String proName = st_name + ".mode";
		String mode = props.getProperty(proName).trim();
		
   		if ("Real".equalsIgnoreCase(mode))
   			return Constants.REAL_MODE;
		return Constants.TEST_MODE;
	}

	public String getStragetyAccounts(String st_name){
		String proName = st_name + ".accounts";
		return props.getProperty(proName).trim();
	}

	public String getStragetyParm1(String st_name){
		String proName = st_name + ".parm1";
		return props.getProperty(proName);
	}

	public String getStragetyParm2(String st_name){
		String proName = st_name + ".parm2";
		return props.getProperty(proName);
	}
	
	public String getStrategyEmail(String st_name){
		String proName = st_name + ".email";
		String prop = props.getProperty(proName);
		if ( prop != null )
			prop = prop.trim();
		return prop;
	}

	public String getStrategySymbol(String st_name){
		String proName = st_name + ".symbols";
		String prop = props.getProperty(proName);
		if ( prop != null )
			prop = prop.trim();
		return prop;
	}

	public String getStrategyPauseTrading(String st_name){
		String proName = st_name + ".pause";
		String prop = props.getProperty(proName);
		if ( prop != null )
			prop = prop.trim();
		return prop;
	}

	public String getStrategyPreempetiveReversal(String st_name){
		String proName = st_name + ".preemptive_reverse";
		String prop = props.getProperty(proName);
		if ( prop != null )
			prop = prop.trim();
		return prop;
	}

	public String getStrategyEmailAlerts(String st_name){
		String proName = st_name + ".email.alert";
		String prop = props.getProperty(proName);
		if ( prop != null )
			prop = prop.trim();
		return prop;
	}

	
	public String getStrategyTriggerEmail(String st_name){
		String proName = st_name + ".email.trigger";
		String prop = props.getProperty(proName);
		if ( prop != null )
			prop = prop.trim();
		return prop;
	}
	
	public String getStrategyDetectEmail(String st_name){
		String proName = st_name + ".email.detect";
		String prop = props.getProperty(proName);
		if ( prop != null )
			prop = prop.trim();
		return prop;
	}
	
	public String getStragetyExps(String st_name){
		String proName = st_name + ".exps";
		return props.getProperty(proName).trim();
	}

	
	public String getOutputDir(){
		return getStringProperty("OUTPUT_DIR");
	}
	
	public boolean getSaveRealTimeData(){
		return getBooleanProperty( "SAVE_REALTIME_DATA" );
		
	}

	public int getStragetySymbolTrend(String st_name, String symbol){
		String proName = st_name + "." + symbol;
		String prop = props.getProperty(proName);
		if ( prop != null ){
			prop = prop.trim();
			return Integer.parseInt(prop);
		}
		else
			return 0;
	}

	
	@Override
	public String toString() {
		return "Env_Setting [props=" + props + ", ib_id=" + ib_id + ", mode="
				+ mode + ", env=" + env + ", port=" + port + ", propFileName="
				+ propFileName + ", shutdown=" + shutdown + "]";
	}

}
