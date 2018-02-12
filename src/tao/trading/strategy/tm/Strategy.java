package tao.trading.strategy.tm;

import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.ObjectInputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Logger;

import tao.trading.AlertOption;
import tao.trading.Command;
import tao.trading.CommandAction;
import tao.trading.Constants;
import tao.trading.EmailSender;
import tao.trading.Env_Setting;
import tao.trading.Instrument;
import tao.trading.QuoteData;
import tao.trading.Trade;
import tao.trading.strategy.SFTest;
import tao.trading.strategy.TradeManager2;
import tao.trading.strategy.util.TimeUtil;

import com.ib.client.EClientSocket;
import com.thoughtworks.xstream.XStream;

public class Strategy
{
	public static SimpleDateFormat IBDataFormatter = new SimpleDateFormat("yyyyMMdd  HH:mm:ss");
	public static SimpleDateFormat InternalDataFormatter = new SimpleDateFormat("yyyyMMdd  HH:mm");
	public static SimpleDateFormat DateFormatter = new SimpleDateFormat("yyyyMMdd");
	public static SimpleDateFormat DateFormatter2 = new SimpleDateFormat("yyyyMMdd-HHmm");

	public int IB_PORT;
	public String STRATEGY_NAME;
	public String[] TRADING_ACCOUNTS;
	//static public Set<Integer> TRADING_SYMBOLS = new HashSet<Integer>(Arrays.asList(0,1,6,12,14,18));
	public Set<Integer> TRADING_SYMBOLS = new HashSet<Integer>();

	int TOTAL_SYMBOLS=0;//22

	public int mode;
	public TradeManager2[] tradeManager;
	//public TradeManager2[] tradeManager;
	SFTest sftest;
    String[] lastSavedTradeData=null;
	public Logger logger;
	Env_Setting env_setting;
	//String triggerEmail, detectEmail;
	//HashSet<String> triggerEmailSent = new HashSet<String>();
	//HashSet<String> detectEmailSent = new HashSet<String>();
	String alertEmail, emailAlerts, pauseTrading,preemptive_reverse;
	HashSet<String> alertEmailSent = new HashSet<String>();
	long lastCmdFileModified = 0;
	public StringBuilder strategyTriggers;
	
	public Strategy( SFTest sf, String StragetyName, Env_Setting setting){
		this.sftest = sf;
		sf.DEFAULT_STRATEGY = StragetyName;
		this.IB_PORT = setting.getPort();
		this.STRATEGY_NAME = StragetyName;
		this.mode = setting.getMode(StragetyName);
		env_setting = setting;
		logger = Logger.getLogger(STRATEGY_NAME);
		strategyTriggers = new StringBuilder(STRATEGY_NAME +"\n\n");
		TOTAL_SYMBOLS = sf.TOTAL_SYMBOLS;
		logger.config("Stragety " + StragetyName + " created at mode " + mode);
	}
	
	public String getStrategyName(){
		return STRATEGY_NAME;
	}
	
	public void initialize(/*String ib_account,*/ EClientSocket m_client, Instrument[] instruments, /*double exp,*/ HashMap<String, Double> exchangeRate, String outPutDir, String parm1, String parm2, String parm3)
	{
		TradeManager2[] tradeManagerNew = null;

		try{
			if (STRATEGY_NAME.equals("RM"))
	    	{		
				RM_ReturnToMA.OUTPUT_DIR = outPutDir;
				//TRADING_SYMBOLS = new HashSet<Integer>(Arrays.asList(0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21));
				//TRADING_SYMBOLS = new HashSet<Integer>(Arrays.asList(1,4,12,16,20));  this is what works
				//TRADING_SYMBOLS = new HashSet<Integer>(Arrays.asList(4,12,16,20));   // this is to work with others
				TRADING_SYMBOLS = new HashSet<Integer>(Arrays.asList(0,1,6,9,12,14,15,19));
				String symbolsInSetting = env_setting.getStrategySymbol(STRATEGY_NAME);
				if (symbolsInSetting != null){
					String[] symbols = symbolsInSetting.split(",");
					TRADING_SYMBOLS = new HashSet<Integer>();
					for ( int i = 0; i < symbols.length; i++ ){
						TRADING_SYMBOLS.add(Integer.parseInt(symbols[i]));
					}	
				}
				logger.info(STRATEGY_NAME + " symbols are: " + TRADING_SYMBOLS.toString());
				
	    		int lot_size[] = new int[]{100000,100000,100000,65000,92000,100000,100000,80000,80000,80000,80000,80000,68000,120000,86000,100000,100000,77160,94000,94000,94000,94000};
	         	int stop_size[] =     new int[]{27,29,30,29,31,30,29,32,32,34,32,32,36,36,25,39,39,37,32,32,32,32};
	         	
				String accounts = env_setting.getStragetyAccounts(STRATEGY_NAME);
				String[] tokens = accounts.split(",");
				int numOfAccounts = tokens.length/2;
				logger.config(STRATEGY_NAME + " number of accounts: " + numOfAccounts);

				//triggerEmail = env_setting.getStrategyTriggerEmail(STRATEGY_NAME);
				//detectEmail = env_setting.getStrategyDetectEmail(STRATEGY_NAME);
				//logger.info(STRATEGY_NAME + " trigger email : " + triggerEmail);
				//logger.info(STRATEGY_NAME + " detect email : " + detectEmail);
				alertEmail = env_setting.getStrategyEmail(STRATEGY_NAME);
				emailAlerts = env_setting.getStrategyEmailAlerts(STRATEGY_NAME);
				logger.info(STRATEGY_NAME + " email: " + alertEmail);
				logger.info(STRATEGY_NAME + " email alerts : " + emailAlerts);

				tradeManagerNew = new RM_ReturnToMA[TOTAL_SYMBOLS * numOfAccounts];
				
				for (int j = 0; j < numOfAccounts; j++){
					String ib_account = tokens[j*2];
					Double exp = new Double(tokens[j*2 + 1]);

					sftest.addAccount(ib_account);
					System.out.println(STRATEGY_NAME + " account:" + ib_account + " exp:" + exp);
				
		    		for ( int i = 0; i < TOTAL_SYMBOLS; i++ ){ 
		    			tradeManagerNew[j*TOTAL_SYMBOLS + i]  = new RM_ReturnToMA(ib_account, m_client, 0,  instruments[i], this, exchangeRate );  
		    			((RM_ReturnToMA)tradeManagerNew[j*TOTAL_SYMBOLS + i]).setPositionSize((int)(lot_size[i]*exp));
		    			((RM_ReturnToMA)tradeManagerNew[j*TOTAL_SYMBOLS + i]).setStopSize(stop_size[i]); 
		    			
	    				if ( parm1 != null ) {
		    	    	    int parm = new Integer(parm1);
			    			((RM_ReturnToMA)tradeManagerNew[j*TOTAL_SYMBOLS + i]).setStopSize(stop_size[i] + parm ); 
	    				}
		    		}
				}
	    	}
			else if (STRATEGY_NAME.equals("RV"))
	    	{		
				RV_Diverage.OUTPUT_DIR = outPutDir;
				//TRADING_SYMBOLS = new HashSet<Integer>(Arrays.asList(0,1,2,3,4,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21));
				TRADING_SYMBOLS = new HashSet<Integer>(Arrays.asList(0,1,6,9,12,14,15,19));
				String symbolsInSetting = env_setting.getStrategySymbol(STRATEGY_NAME);
				if (symbolsInSetting != null){
					String[] symbols = symbolsInSetting.split(",");
					TRADING_SYMBOLS = new HashSet<Integer>();
					for ( int i = 0; i < symbols.length; i++ ){
						TRADING_SYMBOLS.add(Integer.parseInt(symbols[i]));
					}	
				}
				logger.info(STRATEGY_NAME + " symbols are: " + TRADING_SYMBOLS.toString());
				
	    		int lot_size[] = new int[]{100000,100000,100000,65000,92000,100000,100000,80000,80000,80000,80000,80000,68000,120000,86000,100000,100000,77160,94000,94000,94000,94000};
	         	int stop_size[] =     new int[]{27,28,27,29,29,28,29,30,32,34,30,31,34,33,24,37,38,35,32,35,29,33};
                //                              0   1  2  3  4  5  6  7  8  9  0  1  2  3  4  5  6  7  8  9  0 // 514         518 
	         	//                                              ?  ?
				String accounts = env_setting.getStragetyAccounts(STRATEGY_NAME);
				String[] tokens = accounts.split(",");
				int numOfAccounts = tokens.length/2;
				logger.config(STRATEGY_NAME + " number of accounts: " + numOfAccounts);

				alertEmail = env_setting.getStrategyEmail(STRATEGY_NAME);
				emailAlerts = env_setting.getStrategyEmailAlerts(STRATEGY_NAME);
				logger.info(STRATEGY_NAME + " email: " + alertEmail);
				logger.info(STRATEGY_NAME + " email alerts : " + emailAlerts);
				if (env_setting.getStrategySymbol(STRATEGY_NAME) != null){
					String[] symbols = env_setting.getStrategySymbol(STRATEGY_NAME).split(",");
					TRADING_SYMBOLS = new HashSet<Integer>();
					for ( int i = 0; i < symbols.length; i++ ){
						TRADING_SYMBOLS.add(Integer.parseInt(symbols[i]));
					}	
				}

				tradeManagerNew = new RV_Diverage[TOTAL_SYMBOLS * numOfAccounts];
				
				for (int j = 0; j < numOfAccounts; j++){
					String ib_account = tokens[j*2];
					Double exp = new Double(tokens[j*2 + 1]);

					sftest.addAccount(ib_account);
					logger.config(STRATEGY_NAME + " account:" + ib_account + " exp:" + exp);
				
		    		for ( int i = 0; i < TOTAL_SYMBOLS; i++ ){ 
		    			tradeManagerNew[j*TOTAL_SYMBOLS + i]  = new RV_Diverage(ib_account, m_client, 0,  instruments[i], this, exchangeRate );  
		    			((RV_Diverage)tradeManagerNew[j*TOTAL_SYMBOLS + i]).setPositionSize((int)(lot_size[i]*exp));
		    			((RV_Diverage)tradeManagerNew[j*TOTAL_SYMBOLS + i]).setStopSize(stop_size[i]); 

	    				if ( parm1 != null ) {
		    	    	    int parm = new Integer(parm1);
			    			((RV_Diverage)tradeManagerNew[j*TOTAL_SYMBOLS + i]).setStopSize(stop_size[i] + parm ); 
	    				}
		    		}
				}
	    	}
			if (STRATEGY_NAME.equals("PV"))
	    	{		
				RV_Diverage.OUTPUT_DIR = outPutDir;
				TRADING_SYMBOLS = new HashSet<Integer>(Arrays.asList(0,1,2,3,4,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21));
				if (env_setting.getStrategySymbol(STRATEGY_NAME) != null){
					String[] symbols = env_setting.getStrategySymbol(STRATEGY_NAME).split(",;");
					TRADING_SYMBOLS = new HashSet<Integer>();
					for ( int i = 0; i < symbols.length; i++ ){
						TRADING_SYMBOLS.add(Integer.parseInt(symbols[i]));
					}	
				}
				logger.info(STRATEGY_NAME + " symbols are: " + TRADING_SYMBOLS);
				
	    		int lot_size[] = new int[]{100000,100000,100000,65000,92000,100000,100000,80000,80000,80000,80000,80000,68000,120000,86000,100000,100000,77160,94000,94000,94000,94000};
	         	int stop_size[] =     new int[]{27,28,27,29,29,28,29,30,32,34,30,31,34,33,24,37,38,35,32,35,29,33};
                //                              0   1  2  3  4  5  6  7  8  9  0  1  2  3  4  5  6  7  8  9  0 // 514         518 
	         	//                                              ?  ?
				String accounts = env_setting.getStragetyAccounts(STRATEGY_NAME);
				String[] tokens = accounts.split(",");
				int numOfAccounts = tokens.length/2;
				logger.config(STRATEGY_NAME + " number of accounts: " + numOfAccounts);

				alertEmail = env_setting.getStrategyEmail(STRATEGY_NAME);
				emailAlerts = env_setting.getStrategyEmailAlerts(STRATEGY_NAME);
				logger.info(STRATEGY_NAME + " email: " + alertEmail);
				logger.info(STRATEGY_NAME + " email alerts : " + emailAlerts);

				tradeManagerNew = new PV_Pivot[TOTAL_SYMBOLS * numOfAccounts];
				
				for (int j = 0; j < numOfAccounts; j++){
					String ib_account = tokens[j*2];
					Double exp = new Double(tokens[j*2 + 1]);

					sftest.addAccount(ib_account);
					logger.config(STRATEGY_NAME + " account:" + ib_account + " exp:" + exp);
				
		    		for ( int i = 0; i < TOTAL_SYMBOLS; i++ ){ 
		    			tradeManagerNew[j*TOTAL_SYMBOLS + i]  = new PV_Pivot(ib_account, m_client, 0,  instruments[i], this, exchangeRate );  
		    			((PV_Pivot)tradeManagerNew[j*TOTAL_SYMBOLS + i]).setPositionSize((int)(lot_size[i]*exp));
		    			((PV_Pivot)tradeManagerNew[j*TOTAL_SYMBOLS + i]).setStopSize(stop_size[i]); 

	    				if ( parm1 != null ) {
		    	    	    int parm = new Integer(parm1);
			    			((PV_Pivot)tradeManagerNew[j*TOTAL_SYMBOLS + i]).setStopSize(stop_size[i] + parm ); 
	    				}
		    		}
				}
	    	}
	    	if ( STRATEGY_NAME.equals("MC")){
	    		int lot_size[] = new int[]{100000,100000,100000,65000,92000,100000,100000,80000,80000,80000,80000,80000,68000,120000,86000,100000,100000,77160,94000,94000,94000,94000};
                //                              0   1  2  3  4  5  6  7  8  9  0  1  2  3  4  5  6  7  8  9  0 // 514         518 
	         	int stop_size[] =     new int[]{27,31,30,29,31,30,28,34,32,32,32,32,37,36,22,39,38,37,32,32,32,32};
	         	int pullback_size[] = new int[]{50,40,45,47,47,45,47,45,48,46,48,48,52,54,46,45,49,47,45,48,48,48};
 
	         	MC_Momentum.OUTPUT_DIR = outPutDir;
				TRADING_SYMBOLS = new HashSet<Integer>(Arrays.asList(0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21));
				//TRADING_SYMBOLS = new HashSet<Integer>(Arrays.asList(1,5,6));
				// eur.jpy, eur.nzd, eur.aud  only these 3 works well
				//TRADING_SYMBOLS = new HashSet<Integer>(Arrays.asList(5));
				if (env_setting.getStrategySymbol(STRATEGY_NAME) != null){
					String[] symbols = env_setting.getStrategySymbol(STRATEGY_NAME).split(",");
					TRADING_SYMBOLS = new HashSet<Integer>();
					for ( int i = 0; i < symbols.length; i++ ){
						TRADING_SYMBOLS.add(Integer.parseInt(symbols[i]));
					}	
				}
				logger.info(STRATEGY_NAME + " symbol: " + TRADING_SYMBOLS);
				
				String accounts = env_setting.getStragetyAccounts(STRATEGY_NAME);
				String[] tokens = accounts.split(",");
				int numOfAccounts = tokens.length/2;
				logger.config(STRATEGY_NAME + " number of accounts: " + numOfAccounts);

				alertEmail = env_setting.getStrategyEmail(STRATEGY_NAME);
				emailAlerts = env_setting.getStrategyEmailAlerts(STRATEGY_NAME);
				logger.info(STRATEGY_NAME + " email: " + alertEmail);
				logger.info(STRATEGY_NAME + " email alerts : " + emailAlerts);

				tradeManagerNew = new MC_Momentum[TOTAL_SYMBOLS * numOfAccounts];
				
				for (int j = 0; j < numOfAccounts; j++){
					String ib_account = tokens[j*2];
					Double exp = new Double(tokens[j*2 + 1]);

					sftest.addAccount(ib_account);
					logger.config(STRATEGY_NAME + " account:" + ib_account + " exp:" + exp);
				
		    		for ( int i = 0; i < TOTAL_SYMBOLS; i++ ){ 
		    			tradeManagerNew[j*TOTAL_SYMBOLS + i]  = new MC_Momentum(ib_account, m_client, 0,  instruments[i], this, exchangeRate );  
		    			((MC_Momentum)tradeManagerNew[j*TOTAL_SYMBOLS + i]).setPositionSize((int)(lot_size[i]*exp));
		    			((MC_Momentum)tradeManagerNew[j*TOTAL_SYMBOLS + i]).setStopSize(stop_size[i]); 
		    			
	    				if ( parm1 != null ) {
		    	    	    int parm = new Integer(parm1);
			    			((MC_Momentum)tradeManagerNew[j*TOTAL_SYMBOLS + i]).setStopSize(stop_size[i] + parm ); 
	    				}
		    		}
				}
	    	}
	    	else if ( STRATEGY_NAME.equals("MA")){
	         	MA_Manual.OUTPUT_DIR = outPutDir;
				
				// Override stop stop size
				// account info
				String accounts = env_setting.getStragetyAccounts(STRATEGY_NAME);
				String[] tokens = accounts.split(",");
				int numOfAccounts = tokens.length/2;
				logger.info(STRATEGY_NAME + " number of accounts: " + numOfAccounts);

				alertEmail = env_setting.getStrategyEmail(STRATEGY_NAME);
				emailAlerts = env_setting.getStrategyEmailAlerts(STRATEGY_NAME);
				
				logger.info(STRATEGY_NAME + " email:" + alertEmail + " email alerts:" + emailAlerts + " pauseTrading:" + pauseTrading);
				
				tradeManagerNew = new MA_Manual[sftest.ALL_SYMBOLS * numOfAccounts];
				System.out.println("MA initialized: " + sftest.ALL_SYMBOLS * numOfAccounts);
				int lot_size[] = new int[]{100000,100000,100000,65000,92000,100000,100000,80000,80000,80000,80000,80000,68000,120000,86000,100000,100000,77160,94000,94000,94000,94000,1000};
				
				for (int j = 0; j < numOfAccounts; j++){
					String ib_account = tokens[j*2];
					Double exp = new Double(tokens[j*2 + 1]);

					sftest.addAccount(ib_account);
					logger.info(STRATEGY_NAME + " account:" + ib_account + " exp:" + exp);
				
		    		for ( int i = 0; i < sftest.ALL_SYMBOLS; i++ ){ 
						System.out.println("MA222: " + instruments[i].getSymbol());
		    			tradeManagerNew[j*sftest.ALL_SYMBOLS + i]  = new MA_Manual(ib_account, m_client, 0,  instruments[i], this, exchangeRate );
		    			((MA_Manual)tradeManagerNew[j*sftest.ALL_SYMBOLS + i]).setPositionSize((int)(lot_size[i]*exp));
		    		}
				}
	    	}
	    	else if ( STRATEGY_NAME.equals("BO")){

	    		int lot_size[] = new int[]{100000,100000,100000,65000,92000,100000,100000,80000,80000,80000,80000,80000,68000,120000,86000,100000,100000,77160,94000,94000,94000,94000};

	        	TRADING_SYMBOLS = new HashSet<Integer>(Arrays.asList(0,1,6,7,9,12,14));  
	         	int stop_size[] =     new int[]{27,27,27,29,31,30,28,33,32,32,32,35,37,36,22,39,36,37,32,32,34,32};      //current
	         	int pullback_size[] = new int[]{50,36,45,47,47,45,47,44,50,46,51,40,52,54,48,45,49,47,45,45,45,48};
                //                               0  1  2  3  4  5  6  7  8  9  0  1  2  3  4  5  6  7  8  9  0  1// 514         20170411 
	         	int stop_size_offset[] =     new int[]{-10,0, -4, 4,-10, -4, -9, 6,-11,5,-8,-10,-1,-9, 1,-8,-4, -7,-11,-3,-5,-6};      //current
	         	int pullback_size_offset[] = new int[]{-13,2,-10,-6,  5,-11,-10,-5,-11,0,-1,  4,-4, 1,11, 6, 6,-11,-11,9,11,-10};
                        //                              0  1   2  3   4  5   6   7  8  9  0   1  2  3  4  5  6  7   8  9  0  1// 514         8/11/2017

	         	// Override trading symbol
	         	if (env_setting.getStrategySymbol(STRATEGY_NAME) != null){
					String[] symbols = env_setting.getStrategySymbol(STRATEGY_NAME).split(",");
					TRADING_SYMBOLS = new HashSet<Integer>();
					for ( int i = 0; i < symbols.length; i++ ){
						TRADING_SYMBOLS.add(Integer.parseInt(symbols[i]));
					}	
				}
				logger.info(STRATEGY_NAME + " symbol: " + TRADING_SYMBOLS);
				
				// Override stop stop size
	        	if ( env_setting.getStragetyParm1(STRATEGY_NAME) == null ){
	        		logger.warning("Parameter 1 not set, use default");
	        	}else{
	        		logger.warning("Overriding stop size by parameters");
					String[] parm_1 = env_setting.getStragetyParm1(STRATEGY_NAME).trim().split(",");
					if ( parm_1.length != TOTAL_SYMBOLS ){
						logger.severe("BO parameter size incorrect, exit system");
						System.exit(-1);
					}else{
						stop_size = new int[TOTAL_SYMBOLS];
						for ( int i = 0; i < TOTAL_SYMBOLS; i++ ){
							stop_size[i] = Integer.parseInt(parm_1[i]);
						}	
					}
	        	}
				logger.info(STRATEGY_NAME + " stop size: " + Arrays.toString(stop_size));
	        	
				// Override stop pullback size
	        	if ( env_setting.getStragetyParm2(STRATEGY_NAME) == null ){
	        		logger.warning("Parameter 2 not set, use default");
	        	}else{
	        		logger.warning("Overriding pullback size by parameters");
					String[] parm_2 = env_setting.getStragetyParm2(STRATEGY_NAME).trim().split(",");
					if ( parm_2.length != TOTAL_SYMBOLS ){
						logger.severe("BO parameter 2 size incorrect, exit system");
						System.exit(-1);
					}else{
						pullback_size = new int[TOTAL_SYMBOLS];
						for ( int i = 0; i < TOTAL_SYMBOLS; i++ ){
							pullback_size[i] = Integer.parseInt(parm_2[i]);
						}	
					}
	        	}
				logger.info(STRATEGY_NAME + " pullback size: " + Arrays.toString(pullback_size));

				// account info
				String accounts = env_setting.getStragetyAccounts(STRATEGY_NAME);
				String[] tokens = accounts.split(",");
				int numOfAccounts = tokens.length/2;
				logger.info(STRATEGY_NAME + " number of accounts: " + numOfAccounts);

				alertEmail = env_setting.getStrategyEmail(STRATEGY_NAME);
				emailAlerts = env_setting.getStrategyEmailAlerts(STRATEGY_NAME);
				pauseTrading =  env_setting.getStrategyPauseTrading(STRATEGY_NAME);
				preemptive_reverse =  env_setting.getStrategyPreempetiveReversal(STRATEGY_NAME);
				
				logger.info(STRATEGY_NAME + " email:" + alertEmail + " email alerts:" + emailAlerts + " pauseTrading:" + pauseTrading);
				
				tradeManagerNew = new FirstPullBackAfterBreakOut[TOTAL_SYMBOLS * numOfAccounts];
				
				for (int j = 0; j < numOfAccounts; j++){
					String ib_account = tokens[j*2];
					Double exp = new Double(tokens[j*2 + 1]);

					sftest.addAccount(ib_account);
					logger.info(STRATEGY_NAME + " account:" + ib_account + " exp:" + exp);
				
		    		for ( int i = 0; i < TOTAL_SYMBOLS; i++ ){ 
		    			tradeManagerNew[j*TOTAL_SYMBOLS + i]  = new FirstPullBackAfterBreakOut(ib_account, m_client, 0,  instruments[i], this, exchangeRate );  
		    			((FirstPullBackAfterBreakOut)tradeManagerNew[j*TOTAL_SYMBOLS + i]).setPositionSize((int)(lot_size[i]*exp));
		    			((FirstPullBackAfterBreakOut)tradeManagerNew[j*TOTAL_SYMBOLS + i]).setStopSize(stop_size[i]); 
		    			((FirstPullBackAfterBreakOut)tradeManagerNew[j*TOTAL_SYMBOLS + i]).setPullBackSize(pullback_size[i]);

		    			//((FirstPullBackAfterBreakOut)tradeManagerNew[j*TOTAL_SYMBOLS + i]).setParm1(stop_size_offset[i]); 
		    			//((FirstPullBackAfterBreakOut)tradeManagerNew[j*TOTAL_SYMBOLS + i]).setParm2(pullback_size_offset[i]);
		    			
		    			int parm = 0;
	    				if ( parm1 != null ) {
		    	    	    parm = new Integer(parm1);
			    			((FirstPullBackAfterBreakOut)tradeManagerNew[j*TOTAL_SYMBOLS + i]).setStopSize(stop_size[i] + parm ); 
			    			//((FirstPullBackAfterBreakOut)tradeManagerNew[j*TOTAL_SYMBOLS + i]).setParm1((tradeManagerNew[j*TOTAL_SYMBOLS + i]).getParm1() + parm); 
	    				}
	    				if ( parm2 != null ){
		    	    	    parm = new Integer(parm2);
			    			((FirstPullBackAfterBreakOut)tradeManagerNew[j*TOTAL_SYMBOLS + i]).setPullBackSize(pullback_size[i] + parm);
			    			//((FirstPullBackAfterBreakOut)tradeManagerNew[j*TOTAL_SYMBOLS + i]).setParm2((tradeManagerNew[j*TOTAL_SYMBOLS + i]).getParm2() + parm); 
	    				}
		    		}
				}
	         	
				FirstPullBackAfterBreakOut.OUTPUT_DIR = outPutDir;
				if ( pauseTrading != null )
					FirstPullBackAfterBreakOut.PAUSE_TRADING = Boolean.parseBoolean(pauseTrading.trim());
				if (preemptive_reverse != null )
					FirstPullBackAfterBreakOut.PREEMPTIVE_REVERSAL = Boolean.parseBoolean(preemptive_reverse.trim());
				
	    	}
	    	
	    	// here is he common attributes
	    	for ( int i = 0; i < tradeManagerNew.length; i++ ){
	    		tradeManagerNew[i].setStrategy(this);
	    	}

	    	// merge into tradeManager
			if ( tradeManager == null ){
				tradeManager = tradeManagerNew;
			}else{
				List<TradeManager2> list = new ArrayList<TradeManager2>(Arrays.asList(tradeManager));
			    list.addAll(Arrays.asList(tradeManagerNew));
			    tradeManager = (TradeManager2[])list.toArray();
				logger.info("no. of trade managers" + tradeManager.length);
			}

	     	// static members
			logger.info(STRATEGY_NAME + " initialized");

		}
		catch (Exception e){
			e.printStackTrace();
			System.out.println(STRATEGY_NAME + " not initialized");
			System.exit(-1);
		}

	}

	
	public void process( int req_id, QuoteData data ){
		if (STRATEGY_NAME.equals("MA") && ( mode == Constants.TEST_MODE )){
			String hr = data.time.substring(9,12).trim();
			String min = data.time.substring(13,15);
			String sec = data.time.substring(16,18);
			int hour = new Integer(hr);
			int minute = new Integer(min);
			int second = new Integer(sec);
			
			if  (( req_id == 500 ) && ( second == 0 ) && ( hour > 20) ){
				String oppFileName = "./opp/" + Strategy.DateFormatter2.format(new Date(data.timeInMillSec)) + "_Opportunity.csv";
				readExternalInputTrendCSV(oppFileName);
			}
		}
		
		if (( mode == Constants.TEST_MODE ) || ((( mode == Constants.REAL_MODE ) || ( mode == Constants.SIGNAL_MODE )) && (TRADING_SYMBOLS.contains(req_id - 500)))){
			for ( int i = 0; i < tradeManager.length; i++){
				if (  tradeManager[i].getInstrument().getId_realtime() == req_id ){
					tradeManager[i].process(req_id, data);
				}
			}
		}
	}


	public void processTickEvent( int req_id, QuoteData data ){
		if ( ((( mode == Constants.REAL_MODE ) || ( mode == Constants.SIGNAL_MODE )) && (TRADING_SYMBOLS.contains(req_id - 500)))){
			for ( int i = 0; i < tradeManager.length; i++){
				if (  tradeManager[i].getInstrument().getId_realtime() == req_id ){
					tradeManager[i].process(req_id, data);
				}
			}
		}
	}

	
	
	String getTradeDataFileName( String account, String symbol ){
		return "./strategy_data/" + STRATEGY_NAME + "/" + account + "_" + symbol + ".xml";
	}
	
	public void loadAllTradeData( XStream xstream )
	{
	    if (mode == Constants.TEST_MODE)
			return;

		for ( int i = 0; i < tradeManager.length; i++){

			String account = tradeManager[i].getAccount();
			String symbol = tradeManager[i].getSymbol();
			
		    String TRADE_DATA_FILE = getTradeDataFileName(account, symbol);
		    StringBuffer xml = new StringBuffer();
			
		    try{

		    	BufferedReader input =  new BufferedReader(new FileReader(TRADE_DATA_FILE));
	            String line = null; 
		          /*
		          * readLine is a bit quirky :
		          * it returns the content of a line MINUS the newline.
		          * it returns null only for the END of the stream.
		          * it returns an empty String if two newlines appear in a row.
		          */
		        while (( line = input.readLine()) != null){
		            xml.append(line);
		        }
		
		    	Trade t = (Trade)xstream.fromXML(xml.toString());
		    	t.positionUpdated = true;
		    	
		    	tradeManager[i].setTrade(t);
		    	
		        input.close();
		        logger.warning(STRATEGY_NAME + " " + account + " " + symbol + " trade loaded : " + t.toString());

		    }
	        catch ( FileNotFoundException ex ){
	        	//e.printStackTrace();  ignore excetion as file does not exist is normal
	 	        // end of file was reached
		    } 
	        catch ( Exception e ){
	        	e.printStackTrace();
	        }
		}
	}
	
	
	public ArrayList<Trade> getAllTradeData()
	{
	    ArrayList<Trade> trades = new ArrayList<Trade>();
	    for ( int i = 0; i < tradeManager.length; i++)
		{
	    	if ( tradeManager[i].getTrade() != null )
	    		trades.add(tradeManager[i].getTrade());
		}
	    return trades;
	    /*
		Trade [] ret = new Trade[trades.size()];	
    	return (Trade[]) trades.toArray(ret);*/
	}

	public ArrayList<TradeManager2> getAllTraderManagerWithTrade()
	{
	    ArrayList<TradeManager2> tms = new ArrayList<TradeManager2>();
	    for ( int i = 0; i < tradeManager.length; i++)
		{
	    	if ( tradeManager[i].getTrade() != null )
	    		tms.add(tradeManager[i]);
		}
	    return tms;
	}

	
	public void saveAllTradeData( XStream xstream )
	{
	    if (mode == Constants.TEST_MODE)
			return;

    	if ( lastSavedTradeData == null )
	    	lastSavedTradeData = new String[tradeManager.length];
	    
	    for ( int i = 0; i < tradeManager.length; i++){
	    	String account = tradeManager[i].getAccount();
	    	String symbol = tradeManager[i].getSymbol();
		    String TRADE_DATA_FILE = getTradeDataFileName(account, symbol);

		    try{
		    	if ( tradeManager[i].getTrade() != null ){ 
		    		String xml = xstream.toXML(tradeManager[i].getTrade());
				   	if (!xml.equals(lastSavedTradeData[i])){
					   	Writer out = new OutputStreamWriter(new FileOutputStream(TRADE_DATA_FILE));
					   	out.write(xml);
					   	out.close();
					   	lastSavedTradeData[i] = xml;
				   	}
			    }
			    else{
		    	   File f = new File(TRADE_DATA_FILE);
			       if (f.exists())
			        	f.delete();
			    } 
		    }
		    catch ( Exception e ){
		    	e.printStackTrace();
		    	logger.warning("Exception occured during saving " + symbol + " data " + e.getMessage());
		    } 
		}
	}

	
	public void readExternalCommand()
	{
		Command cmd = loadCommand();
		
		if ( cmd != null )
		{	
			logger.info(STRATEGY_NAME + " external command received:" + cmd.toString());
			if ( cmd.getCmd() == CommandAction.remove )
			{
				logger.info(STRATEGY_NAME + " remove trade:" + cmd.getSymbol());
				for ( int i = 0; i < tradeManager.length; i++){
					if ( tradeManager[i].getTrade() != null){
						logger.info("check trade" + tradeManager[i].getTrade().symbol);
						if ((cmd.getSymbol().indexOf(tradeManager[i].getTrade().symbol) >=0 ) || cmd.getSymbol().equalsIgnoreCase("all")){
							System.out.println("Trade " + tradeManager[i].getSymbol() + " found, remove trade");
							tradeManager[i].cancelAllOrders();
							tradeManager[i].removeTrade();
						}
					}
				}
			}
			if ( cmd.getCmd() == CommandAction.target_on_pullback )
			{
				logger.info(STRATEGY_NAME + " target on pullback:" + cmd.getSymbol());
				for ( int i = 0; i < tradeManager.length; i++){
					if (cmd.getSymbol().equalsIgnoreCase(tradeManager[i].getSymbol()) && ( tradeManager[i].getTrade() != null)){
						System.out.println(STRATEGY_NAME + tradeManager[i].getAccount() + " " + tradeManager[i].getSymbol() + " set target on pullback" );
						tradeManager[i].getTrade().targetOnPullback = true;
					}
				}
			}
			else if ( cmd.getCmd() == CommandAction.target )
			{
				logger.info(STRATEGY_NAME + " target trade:" + cmd.getSymbol() + " " + cmd.getPrice());
				for ( int i = 0; i < tradeManager.length; i++){
					if (cmd.getSymbol().equalsIgnoreCase(tradeManager[i].getSymbol()) && ( tradeManager[i].getTrade() != null)){
						logger.info(STRATEGY_NAME + tradeManager[i].getAccount() + " " + tradeManager[i].getSymbol() + " set target " + cmd.getPrice());
		        		tradeManager[i].createTradeTargetOrder((int)(cmd.getQuantity()*tradeManager[i].getPOSITION_SIZE()), cmd.getPrice() );
					}
				}
			}
			else if ( cmd.getCmd() == CommandAction.create ){
				for ( int i = 0; i < tradeManager.length; i++){
					if (cmd.getSymbol().equalsIgnoreCase(tradeManager[i].getSymbol()) && ( tradeManager[i].getTrade() == null)){
						tradeManager[i].createTradePlaceLimit(cmd.getAction(), (int)(cmd.getQuantity()*tradeManager[i].getPOSITION_SIZE()), cmd.getPrice() );
					}
				}
			}
			else if ( cmd.getCmd() == CommandAction.create_other ){
				System.out.println(STRATEGY_NAME + " target trade:" + cmd.getSymbol() + " " + cmd.getPrice());
				for ( int i = 0; i < tradeManager.length; i++){
					if (cmd.getSymbol().equalsIgnoreCase(tradeManager[i].getSymbol()) && ( tradeManager[i].getTrade() == null)){
						System.out.println("symbol " + cmd.getSymbol() + " found ");
						tradeManager[i].createTradePlaceLimit(cmd.getAction(), (int)(cmd.getQuantity()*tradeManager[i].getPOSITION_SIZE()), cmd.getPrice() );
					}
				}
			}
			else if ( cmd.getCmd() == CommandAction.plain_order ){
				for ( int i = 0; i < tradeManager.length; i++){
					if (cmd.getSymbol().equalsIgnoreCase(tradeManager[i].getSymbol())){
						tradeManager[i].placeLmtOrder(cmd.getAction(),  cmd.getPrice(), (int)(tradeManager[i].getPOSITION_SIZE()*cmd.getQuantity()), null);
					}
				}
			}
			else if ( cmd.getCmd() == CommandAction.plain_order_market ){
				for ( int i = 0; i < tradeManager.length; i++){
					if (cmd.getSymbol().equalsIgnoreCase(tradeManager[i].getSymbol())){
						tradeManager[i].placeMktOrder(cmd.getAction(), (int)(tradeManager[i].getPOSITION_SIZE()*cmd.getQuantity()));
					}
				}
			}
			else if ( cmd.getCmd() == CommandAction.plain_stop ){
				for ( int i = 0; i < tradeManager.length; i++){
					if (cmd.getSymbol().equalsIgnoreCase(tradeManager[i].getSymbol())){
					tradeManager[i].placeStopOrder(cmd.getAction(), cmd.getPrice(), (int)(tradeManager[i].getPOSITION_SIZE()*cmd.getQuantity()), null, null); 
					}
				}
			}
			else if ( cmd.getCmd() == CommandAction.update )
			{
				for ( int i = 0; i < tradeManager.length; i++)
				{
					if (cmd.getSymbol().equalsIgnoreCase(tradeManager[i].getSymbol()) && ( tradeManager[i].getTrade() != null) && ( Constants.STATUS_OPEN.equals(tradeManager[i].getTrade().getStatus())))
					{
						tradeManager[i].cancelAllOrders();
						tradeManager[i].removeTrade();
						tradeManager[i].createTradePlaceLimit(cmd.getAction(), (int)(tradeManager[i].getPOSITION_SIZE()*cmd.getQuantity()), cmd.getPrice());
					}
				}
			}
			else if ( cmd.getCmd() == CommandAction.add_position )
			{
				for ( int i = 0; i < tradeManager.length; i++){
					if (cmd.getSymbol().equalsIgnoreCase(tradeManager[i].getSymbol()) && ( tradeManager[i].getTrade() != null)){
						tradeManager[i].enterLimitPositionMulti((int)(tradeManager[i].getPOSITION_SIZE()*cmd.getQuantity()), cmd.getPrice() );
					}
				}
			}
			else if ( cmd.getCmd() == CommandAction.stop )
			{
				if ( cmd.getPrice() < 0 ){
					logger.warning(STRATEGY_NAME  + " " + cmd.getSymbol() + " " + cmd.getPrice() + " stop price < 0, trade ignored");
				}
				else{
					for ( int i = 0; i < tradeManager.length; i++){
						if (cmd.getSymbol().equalsIgnoreCase(tradeManager[i].getSymbol()) && ( tradeManager[i].getTrade() != null)){
							System.out.println(i + " " + tradeManager[i].getSymbol() + tradeManager[i].getInstrument());
							if ( tradeManager[i].getInstrument().getLastQuote() != null ){ // there are other add-hoc trades
								if ((Constants.ACTION_BUY.equals(tradeManager[i].getTrade().action )) && ( cmd.getPrice() > tradeManager[i].getInstrument().getLastQuote().close + tradeManager[i].getPIP_SIZE() * 10)){
									logger.warning(STRATEGY_NAME  + " trade is buy but the stop price is above current close, will cause immediate close, stop ignored");
									return;
								}
								else if ((Constants.ACTION_SELL.equals(tradeManager[i].getTrade().action )) && ( cmd.getPrice() < tradeManager[i].getInstrument().getLastQuote().close - tradeManager[i].getPIP_SIZE() * 10)){
									logger.warning(STRATEGY_NAME  + " trade is sell but the stop price is below current close, will cause immediate close, stop ignored");
									return;
								}
							}
							tradeManager[i].createStopOrder(cmd.getPrice());
						}
					}
				}
			}
			else if ( cmd.getCmd() == CommandAction.cancel_order ){
				for ( int i = 0; i < tradeManager.length; i++){
					if (cmd.getSymbol().equalsIgnoreCase(tradeManager[i].getSymbol()) && ( tradeManager[i].getTrade() != null)){
						tradeManager[i].cancelLimitByPrice(cmd.getPrice());
						tradeManager[i].cancelTargetByPrice(cmd.getPrice());
					}
				}
			}
			else if ( cmd.getCmd() == CommandAction.set_position){
				logger.info(STRATEGY_NAME + " set position size:" + cmd.getSymbol() + " " + cmd.getQuantity());
				for ( int i = 0; i < tradeManager.length; i++){
					if (cmd.getSymbol().equalsIgnoreCase(tradeManager[i].getSymbol()) && ( tradeManager[i].getTrade() != null)){
						logger.info(STRATEGY_NAME + " " + tradeManager[i].getAccount() + " " + tradeManager[i].getSymbol() + " set position size " + cmd.getQuantity() + " " + tradeManager[i].getPOSITION_SIZE());
		        		tradeManager[i].getTrade().remainingPositionSize = (int)(tradeManager[i].getPOSITION_SIZE() * cmd.getQuantity());
					}
				}
			}
		}
	}
	
	
	Command loadCommand(){
	    String COMMAND_FILE = "./"+STRATEGY_NAME + ".cmd";
	    try{
	        File f = new File(COMMAND_FILE);
	        long lastFileModified = f.lastModified();
	        if ( lastFileModified != lastCmdFileModified){
		        FileInputStream file = new FileInputStream(f);
	    		ObjectInputStream input = new ObjectInputStream( file );
	        	Command cmd = ( Command ) input.readObject();
	        	System.out.println("External Command Received:" + cmd.toString());
	        	lastCmdFileModified = lastFileModified;
		        input.close();
		        f.delete(); // remove the command file;
		        return cmd;
	        }
	    }
        catch ( FileNotFoundException ex ){
	    } 
        catch ( Exception e ){
        	e.printStackTrace();
        }

	    return null;
	}

	
	public String getTradeStatus()
	{
		if (STRATEGY_NAME.equalsIgnoreCase("PV"))
			return getTradeStatus_PV();
		
		StringBuffer TradeOpp = new StringBuffer();
    	TradeOpp.append(STRATEGY_NAME + "\n");

		if (STRATEGY_NAME.equalsIgnoreCase("NE"))
			TradeOpp.append(NE_1_NewsBreakOut.getNext3News() + "\n");
    	
		ArrayList<Trade> openlist 	= new ArrayList<Trade>();
		ArrayList<Trade> filledlist = new ArrayList<Trade>();
		ArrayList<Trade> placedlist = new ArrayList<Trade>();
		ArrayList<Trade> detectlist = new ArrayList<Trade>();
		ArrayList<Trade> closedlist = new ArrayList<Trade>();
		for ( int i = 0; i < tradeManager.length; i++){
			Trade t =  tradeManager[i].getTrade();
			if (( t != null ) && t.status.equals(Constants.STATUS_FILLED))
				filledlist.add(t);

			if (( t != null ) && t.status.equals(Constants.STATUS_PLACED))
				placedlist.add(t);

			if (( t != null ) && t.status.equals(Constants.STATUS_DETECTED))
				detectlist.add(t);

			if (( t != null ) && t.status.equals(Constants.STATUS_OPEN))
				openlist.add(t);
			
			if (STRATEGY_NAME.equalsIgnoreCase("MA"))
			{
				Vector v = tradeManager[i].getTradeHistory();
				Iterator it = v.iterator();
				while ( it.hasNext() )
				{
					Trade t1 = (Trade)it.next();
					closedlist.add(t1);
				}
			}
		}

		Trade[] trades = filledlist.toArray(new Trade[filledlist.size()]);
		if (( trades != null ) && ( trades.length > 0 )){
			TradeOpp.append("FILLED TRADES:\n");
			Arrays.sort(trades, Trade.EntryTimeComparator);
			for(Trade t: trades){
				TradeOpp.append((t.marked?"*":" ") + " " + t.symbol + " FILLED " +  t.action + "  Profit:" + getTradeLatestPrice(t) + "  Lived:" + getTradeLastedTime(t) + " Size:"+ getTradeRemainingPosSize(t) +  "\n");
				String events =  t.listTradeEvents();
				if (( events != null ) && ( events.length() > 0 ))
					TradeOpp.append(t.listTradeEvents() );
			}
		}
		
		
		trades = placedlist.toArray(new Trade[placedlist.size()]);
		if (( trades != null ) && ( trades.length > 0 ))
		{
			TradeOpp.append("PLACED TRADES:\n");
			Arrays.sort(trades, Trade.DetectTimeComparator);
			for(Trade t: trades)
			{
				TradeOpp.append((t.marked?"*":" ") + t.symbol + " PLACED " +  t.action + "\n");
				String events =  t.listTradeEvents();
				if (( events != null ) && ( events.length() > 0 ))
					TradeOpp.append(t.listTradeEvents() );
			}
		}

		trades = detectlist.toArray(new Trade[detectlist.size()]);
		if (( trades != null ) && ( trades.length > 0 ))
		{	
			TradeOpp.append("DETECTED TRADES:\n");
			Arrays.sort(trades, Trade.DetectTimeComparator);
			for(Trade t: trades)
			{
				TradeOpp.append((t.marked?"*":" ") + t.symbol + " DETECTED " +  t.action + "\n");
				String events =  t.listTradeEvents();
				if (( events != null ) && ( events.length() > 0 ))
					TradeOpp.append(t.listTradeEvents() );
			}
		}

		trades = openlist.toArray(new Trade[openlist.size()]);
		if (( trades != null ) && ( trades.length > 0 ))
		{	
			TradeOpp.append("OPEN TRADES:\n");
			Arrays.sort(trades, Trade.DetectTimeComparator);
			for(Trade t: trades)
			{
				TradeOpp.append((t.marked?"*":" ") + t.symbol + " OPEN " +  t.action + "\n");
				String events =  t.listTradeEvents();
				if (( events != null ) && ( events.length() > 0 ))
					TradeOpp.append(t.listTradeEvents() );
			}
		}

		
		// display closed_trade status for review
		if (STRATEGY_NAME.equalsIgnoreCase("MA"))
		{
			trades = closedlist.toArray(new Trade[closedlist.size()]);
			if (( trades != null ) && ( trades.length > 0 ))
			{
				TradeOpp.append("\nCLOSED TRADES:\n");

				Arrays.sort(trades, Trade.EntryTimeComparator);
				for(Trade t: trades)
				{
					TradeOpp.append(t.symbol + t.action + "\n");
					String events =  t.listTradeEvents();
					if (( events != null ) && ( events.length() > 0 ))
						TradeOpp.append(t.listTradeEvents() );
				}
			}
		}

		return TradeOpp.toString();
		
	}

	
	public int getTradeLatestPrice( Trade t ){
		int profitInPip = 0;
		for ( int i = 0; i < tradeManager.length; i++){
			if (tradeManager[i].getSymbol().endsWith(t.symbol)){
				QuoteData[] quotes60 = tradeManager[i].getInstrument().getQuoteData(Constants.CHART_60_MIN);
				int lastbar60 = quotes60.length - 1;
				if (Constants.ACTION_SELL.equals(t.action))
					profitInPip = (int)((t.entryPrice - quotes60[lastbar60].close)/tradeManager[i].getPIP_SIZE());
				else if (Constants.ACTION_BUY.equals(t.action))
					profitInPip = (int)((quotes60[lastbar60].close - t.entryPrice )/tradeManager[i].getPIP_SIZE());
			}
		}
		return profitInPip;
	}

	
	public int getTradeLastedTime( Trade t )
	{
		for ( int i = 0; i < tradeManager.length; i++){
			if (tradeManager[i].getSymbol().endsWith(t.symbol)){
				QuoteData[] quotes60 = tradeManager[i].getInstrument().getQuoteData(Constants.CHART_60_MIN);
				int lastbar60 = quotes60.length - 1;
				return (int)TimeUtil.timeDiffInHr(quotes60[lastbar60].time, t.entryTime);	
			}
		}
		return 0;
	}
	
	
	public float getTradeRemainingPosSize( Trade t ){
		for ( int i = 0; i < tradeManager.length; i++){
			if (tradeManager[i].getSymbol().endsWith(t.symbol)){
				return (float)t.remainingPositionSize/(float)tradeManager[i].getPOSITION_SIZE();
			}
		}
		return 0;
	}

	
	public Trade[] getTrades(String stragetyName)
	{
		ArrayList<Trade> filledlist = new ArrayList();
		ArrayList<Trade> placedlist = new ArrayList();
		ArrayList<Trade> detectlist = new ArrayList();
		ArrayList<Trade> closedlist = new ArrayList();

		if (STRATEGY_NAME.equalsIgnoreCase(STRATEGY_NAME))
		{
			for ( int i = 0; i < tradeManager.length; i++)
			{
				Trade t =  tradeManager[i].getTrade();
				if (( t != null ) && t.status.equals(Constants.STATUS_FILLED))
					filledlist.add(t);
	
				if (( t != null ) && t.status.equals(Constants.STATUS_PLACED))
					placedlist.add(t);
	
				if (( t != null ) && t.status.equals(Constants.STATUS_DETECTED))
					detectlist.add(t);
				
			}
		}

		Trade[] ret = new Trade[detectlist.size()];	
		return (Trade[]) detectlist.toArray(ret);
	}

	
	
	
	public String getTradeStatus_PV()
	{
		StringBuffer TradeOpp = new StringBuffer();
    	TradeOpp.append("PV\n");
		
		/*
 		createInstrument(  0, "EUR", "CASH", "IDEALPRO", "USD", 0.0001, Constants.PRICE_TYPE_4);
 		createInstrument(  1, "EUR", "CASH", "IDEALPRO", "JPY",   0.01, Constants.PRICE_TYPE_2);
 		createInstrument(  2, "EUR", "CASH", "IDEALPRO", "CAD", 0.0001, Constants.PRICE_TYPE_3);
 		createInstrument(  3, "EUR", "CASH", "IDEALPRO", "GBP", 0.0001, Constants.PRICE_TYPE_4);
 		createInstrument(  4, "EUR", "CASH", "IDEALPRO", "CHF", 0.0001, Constants.PRICE_TYPE_4);
 		createInstrument(  5, "EUR", "CASH", "IDEALPRO", "NZD", 0.0001, Constants.PRICE_TYPE_3);
 		createInstrument(  6, "EUR", "CASH", "IDEALPRO", "AUD", 0.0001, Constants.PRICE_TYPE_4);
 		createInstrument(  7, "GBP", "CASH", "IDEALPRO", "AUD", 0.0001, Constants.PRICE_TYPE_4);
 		createInstrument(  8, "GBP", "CASH", "IDEALPRO", "NZD", 0.0001, Constants.PRICE_TYPE_3);
 		createInstrument(  9, "GBP", "CASH", "IDEALPRO", "USD", 0.0001, Constants.PRICE_TYPE_4);
 		createInstrument( 10, "GBP", "CASH", "IDEALPRO", "CAD", 0.0001, Constants.PRICE_TYPE_3);
 		createInstrument( 11, "GBP", "CASH", "IDEALPRO", "CHF", 0.0001, Constants.PRICE_TYPE_4);
 		createInstrument( 12, "GBP", "CASH", "IDEALPRO", "JPY",   0.01, Constants.PRICE_TYPE_2);
 		createInstrument( 13, "CHF", "CASH", "IDEALPRO", "JPY",   0.01, Constants.PRICE_TYPE_2);
 		createInstrument( 14, "USD", "CASH", "IDEALPRO", "JPY",   0.01, Constants.PRICE_TYPE_2);
 		createInstrument( 15, "USD", "CASH", "IDEALPRO", "CAD", 0.0001, Constants.PRICE_TYPE_3);
 		createInstrument( 16, "USD", "CASH", "IDEALPRO", "CHF", 0.0001, Constants.PRICE_TYPE_4);
 		createInstrument( 17, "CAD", "CASH", "IDEALPRO", "JPY",   0.01, Constants.PRICE_TYPE_2);
 		createInstrument( 18, "AUD", "CASH", "IDEALPRO", "USD", 0.0001, Constants.PRICE_TYPE_4);
 		createInstrument( 19, "AUD", "CASH", "IDEALPRO", "JPY",   0.01, Constants.PRICE_TYPE_2);
 		createInstrument( 20, "AUD", "CASH", "IDEALPRO", "NZD", 0.0001, Constants.PRICE_TYPE_4);
*/
 		Trade t = null;
		TradeOpp.append("AUD:\n");
		t =  tradeManager[18].getTrade();  // AUD.USD
		if ( t != null ) TradeOpp.append("AUD.USD\n" + t.listTradeEvents() );
		t =  tradeManager[19].getTrade();  // AUD.JPY
		if ( t != null ) TradeOpp.append("AUD.JPY\n" + t.listTradeEvents() );
		t =  tradeManager[20].getTrade();  // AUD.NZD
		if ( t != null ) TradeOpp.append("AUD.NZD\n" + t.listTradeEvents() );

		TradeOpp.append("CAD:\n");
		t =  tradeManager[2].getTrade();  
		if ( t != null ) TradeOpp.append("EUR.CAD\n" + t.listTradeEvents() );
		t =  tradeManager[15].getTrade();  
		if ( t != null ) TradeOpp.append("USD.CAD\n" + t.listTradeEvents() );
		t =  tradeManager[10].getTrade();  
		if ( t != null ) TradeOpp.append("GBP.CAD\n" + t.listTradeEvents() );

		TradeOpp.append("CHF:\n");
		t =  tradeManager[4].getTrade();  
		if ( t != null ) TradeOpp.append("EUR.CHF\n" + t.listTradeEvents() );
		t =  tradeManager[11].getTrade();  
		if ( t != null ) TradeOpp.append("GBP.CHF\n" + t.listTradeEvents() );
		t =  tradeManager[16].getTrade();  
		if ( t != null ) TradeOpp.append("USD.CHF\n" + t.listTradeEvents() );
		
		TradeOpp.append("EUR:\n");
		t =  tradeManager[1].getTrade();  
		if ( t != null ) TradeOpp.append("EUR.JPY\n" + t.listTradeEvents() );
		t =  tradeManager[0].getTrade();  
		if ( t != null ) TradeOpp.append("EUR.USD\n" + t.listTradeEvents() );
		t =  tradeManager[3].getTrade();  
		if ( t != null ) TradeOpp.append("EUR.GBP\n" + t.listTradeEvents() );
		t =  tradeManager[6].getTrade();  
		if ( t != null ) TradeOpp.append("EUR.AUD\n" + t.listTradeEvents() );
		t =  tradeManager[5].getTrade();  
		if ( t != null ) TradeOpp.append("EUR.NZD\n" + t.listTradeEvents() );

		TradeOpp.append("JPY:\n");
		t =  tradeManager[14].getTrade();  
		if ( t != null ) TradeOpp.append("USD.JPY\n" + t.listTradeEvents() );
		t =  tradeManager[12].getTrade();  
		if ( t != null ) TradeOpp.append("GBP.JPY\n" + t.listTradeEvents() );
		
		TradeOpp.append("GBP:\n");
		t =  tradeManager[9].getTrade();  
		if ( t != null ) TradeOpp.append("GBP.USD\n" + t.listTradeEvents() );
		t =  tradeManager[7].getTrade();  
		if ( t != null ) TradeOpp.append("GBP.AUD\n" + t.listTradeEvents() );
		t =  tradeManager[8].getTrade();  
		if ( t != null ) TradeOpp.append("GBP.NZD\n" + t.listTradeEvents() );

		TradeOpp.append("JPY:\n");
		t =  tradeManager[17].getTrade();  
		if ( t != null ) TradeOpp.append("CAD.JPY\n" + t.listTradeEvents() );
		t =  tradeManager[19].getTrade();  
		if ( t != null ) TradeOpp.append("AUD.JPY\n" + t.listTradeEvents() );
		t =  tradeManager[13].getTrade();  
		if ( t != null ) TradeOpp.append("CHF.JPY\n" + t.listTradeEvents() );

		TradeOpp.append("NZD:\n");
		t =  tradeManager[5].getTrade();  
		if ( t != null ) TradeOpp.append("EUR.NZD\n" + t.listTradeEvents() );
		t =  tradeManager[8].getTrade();  
		if ( t != null ) TradeOpp.append("GBP.NZD\n" + t.listTradeEvents() );
		
		
		
		
		return TradeOpp.toString();

		
	}

	
	
	public boolean createTradeReport( String reportFileName )
	{
    	double totalProfit = 0;
    	double totalUnrealizedProfit = 0;
    	int totalTrade = 0;

    	if ( reportFileName == null )
    	  reportFileName = STRATEGY_NAME + "_report.txt";
    	
    	for ( int i = 0; i < tradeManager.length; i++ )
    	{
    		QuoteData[] quotes240 = tradeManager[i].getQuoteData(Constants.CHART_240_MIN);
    		int lastbar240 = quotes240.length - 1;

    		if ( lastbar240 > 0 )
    		{
    			double close = quotes240[lastbar240].close;
    			tradeManager[i].report2(close);
    		}
    	}
    	
    	
    	try
	    {
	    //	if ( writeToFile == true )
	    	{	
		    	FileWriter fstream = new FileWriter(reportFileName);
		    	BufferedWriter out = new BufferedWriter(fstream);
	
		    	out.write("Trade Report " + IBDataFormatter.format( new Date()) + "\n\n");
		    	
		    	for ( int i = 0; i < TOTAL_SYMBOLS; i++ )
		    	{
		    		TradeReport tr = tradeManager[i].getTradeReport();
		    		
	    			if (( tr.getTradeReport() != null ) && (!tr.getTradeReport().equals("")))
	    			{
	    				out.write(tr.getTradeReport() + "\n");
	    				totalProfit += tr.getTotalProfit();
	    				totalUnrealizedProfit += tr.getTotalProfit();
	    				totalTrade += tr.getTotalTrade();
	    			}
		    	}
	
				out.write("\n\nTOTAL PROFIT: " + totalProfit + " USD          TOTAL TRADES: " + totalTrade + "\n" );
				out.write("\nTOTAL UNREALIZED PROFIT: " + totalUnrealizedProfit + " USD\n" );
	
		    	out.close();
	    	}
	  // 	else
	    	{
	    		/*
		    	for ( int i = 0; i < TOTAL_SYMBOLS; i++ )
		    	{
	    			if (( tradeManager[i].portofolioReport != null ) && (!tradeManager[i].portofolioReport.equals("")))
	    			{
	    				totalProfit += tradeManager[i].totalProfit;
	    				totalTrade += tradeManager[i].totalTrade;
	    			}
		    	}*/
	    	}
	    	
	    	if ( totalProfit < -1000 )
	    		return true;
	   }
	   catch (Exception e)
	   {
		   e.printStackTrace();
   	   }

	   return false;
	}


	
	
	
	public String getTradeReport()
	{
    	double totalProfit = 0;
    	double totalUnrealizedProfit = 0;
    	int totalTrade = 0;

    	StringBuffer report = new StringBuffer();
    	
		report.append("Trade Report " + STRATEGY_NAME + " "+ IBDataFormatter.format( new Date()) + "\n\n");
		    	
    	for ( int i = 0; i < tradeManager.length; i++ )
    	{
    		QuoteData[] quotes60 = tradeManager[i].getInstrument().getQuoteData(Constants.CHART_60_MIN);
    		int lastbar60 = quotes60.length - 1;
    		if ( lastbar60 <= 0 )
    			continue;	// some symbol do not have historical data
    		double close = quotes60[lastbar60].close;
			tradeManager[i].report2(close);

    		TradeReport tr = tradeManager[i].getTradeReport();
    		
			if (( tr.getTradeReport() != null ) && (!tr.getTradeReport().equals("")))
			{
				report.append(tr.getTradeReport() + "\n");
				totalProfit += tr.getTotalProfit();
				totalUnrealizedProfit += tr.getTotalProfit();
				totalTrade += tr.getTotalTrade();
			}
    	}

		report.append("\n\nTOTAL PROFIT: " + totalProfit + " USD          TOTAL TRADES: " + totalTrade + "\n" );
		report.append("\nTOTAL UNREALIZED PROFIT: " + totalUnrealizedProfit + " USD\n" );
	
	    return report.toString();
	}

	
	
	
	public void checkOrderFilled(int orderId, String status, int filled, int remaining, double avgFillPrice,
			int permId, int parentId, double lastFillPrice, int clientId, String whyHeld)
	{
		for ( int i = 0; i < tradeManager.length; i++){
			if ( tradeManager[i].getTrade() != null )
				tradeManager[i].checkOrderFilled(orderId, filled);
		}
	}

	
	public void removeTradeNotUpdated()
	{
		for ( int i = 0; i < tradeManager.length; i++){
			Trade trade = tradeManager[i].getTrade();
			String symbol = tradeManager[i].getSymbol();
			if ((trade != null ) && ( trade.status.equals(Constants.STATUS_PLACED )) && (trade.positionUpdated == false))
			{
				logger.warning(symbol + " " +  trade.action + " position did not updated, trade removed");
				tradeManager[i].setTrade(null);
			}
		}
	}
	
	
	public void readExternalInputTrendCSV()
	{
		if ( STRATEGY_NAME.equals("MA") )
		{
			readExternalInputTrendCSV("Opportunity.csv");
		}
	}
	
	public void readExternalInputTrendCSV(String OPPORTUNITY_FILE)
	{
			try 
	        { 
		   		File f = new File(OPPORTUNITY_FILE);
		   		FileReader fr = new FileReader(f);

				BufferedReader br = new BufferedReader( fr ); 
	            String line = ""; 
	            StringTokenizer token = null; 
	            int lineNum = 0; 

	            while((line = br.readLine()) != null)
	            { 
	            	lineNum++;
	            	if ( lineNum == 1 )
	            		continue;
	                   
	                // break comma separated file line by line 
	                token = new StringTokenizer(line, ","); 
	                   
	                int tokenNum = 0;
	                String symbol = null;
	                String action = null;
	                String setupType = null;
	                String parm1=null, parm2=null, parm3=null, parm4=null, parm5=null;
	                /*
	                while(token.hasMoreTokens()) 
	                {
	                    tokenNum++; 
	                	
	                	if ( tokenNum == 1 )
	                		symbol = token.nextToken();
	                	else if ( tokenNum == 2 )
	                		action = token.nextToken().toUpperCase().trim();
	                	else if ( tokenNum == 3 )
	                		setupType = token.nextToken().toUpperCase().trim();
	                	else if ( tokenNum == 4 )
	                		//quantity1 = Utility.readCSVTokenDouble(token.nextToken());
	                		parm1 = token.nextToken();
	                	else if ( tokenNum == 5 )
	                		//price1 = Utility.readCSVTokenDouble(token.nextToken());
	                		parm2 = token.nextToken();
	                	else if ( tokenNum == 6 )
	                		//quantity2 = Utility.readCSVTokenDouble(token.nextToken());
	                		parm3 = token.nextToken();
	                	else if ( tokenNum == 7 )
	                		//price2 = Utility.readCSVTokenDouble(token.nextToken());
	                		parm4 = token.nextToken();
	                	else if ( tokenNum == 8 )
	                		parm5 = token.nextToken();
	                	else if ( tokenNum >=9 )
	                		break;
	                }*/
	                
	                String[] tokens = splitTotokens(line, ",");
	                if (( tokens == null ) || ( tokens.length < 5 ))
	                	continue;

	        		if (( STRATEGY_NAME.equals("MA") && ( tokens != null ) && ( tokens.length >=5 )))
	        		{	
		                symbol = tokens[0];
	               		action = tokens[1];
	               		setupType = tokens[2];
	               		parm1 = tokens[3];
	               		parm2 = tokens[4];
	               		parm3 = tokens[5];

		                // validations: action has to be BUY OR SELL
						if (( action != null ) && (setupType!=null) && (parm1!=null) && (parm2!=null) && (action.equalsIgnoreCase(Constants.ACTION_BUY) || action.equalsIgnoreCase(Constants.ACTION_SELL)))
						{
			                //logger.info("Input CSV2:" + symbol + " " + action + " " + setupType + " " + parm1 + " " + parm2 + " " + parm3 + " " + parm4);
							action = action.toUpperCase();
	
							// validations: setup has to be "L"
							if ((setupType == null ) || ( setupType.length() == 0 ))//(/*(setupType.indexOf(Constants.SETUP_LIMIT)== -1 ) &&*/ (setupType.indexOf(Constants.SETUP_EARLY_PULLBACK)==-1) && (setupType.indexOf(Constants.SETUP_LATE_PULLBACK)==-1) && (setupType.indexOf(Constants.SETUP_REVERSE)==-1)))
							{
								logger.info("Input CSV: invalide setupType: " + setupType);
		  	   					continue;
							}
		                
			        		for ( int i = 0; i < TOTAL_SYMBOLS; i++)
			        		{
			        			if (symbol.equalsIgnoreCase(tradeManager[i].getSymbol()))
			        			{
			  	   					if (( tradeManager[i].getTrade() != null ))
			   	   					{
			  	   						logger.warning("Input CSV:" + tradeManager[i].getSymbol() + " trade already exist, input ignored");
			   	   	   					break;
			   	   					}
			  	   					else
			  	   					{
		  	   							Double quantity1 = new Double(parm1);
		  	   							Double price1 = new Double(parm2);

		  	   							int posSize = (int)(quantity1 * tradeManager[i].getPOSITION_SIZE());
		  	   							tradeManager[i].createOpenTrade(Constants.TRADE_MA, action, null, posSize, price1);
		  	   							tradeManager[i].getTrade().detectTime = IBDataFormatter.format(new Date());
		  	   							tradeManager[i].getTrade().setFirstBreakOutStartTime(parm3);
		  	   							tradeManager[i].enterLimitPositionMulti( posSize, price1 );
			  	   					}

			  	   					/*
		  	   						Trade t = null;
		  	   						
		  	   						try{
		  	   							Double quantity1 = new Double(parm1);
		  	   							Double price1 = new Double(parm2);
	
		  	   							System.out.println(symbol + " " + action + " " + quantity1 + " " + price1 );
		  	   							if (( quantity1 != null ) && ( quantity1 > 0 ) && ( price1 != null ) && ( price1 > 0 ))
			   	   						{	
			   	   							QuoteData[] quotes60 = tradeManager[i].getQuoteData(Constants.CHART_60_MIN);
			   	   							int lastbar60 = quotes60.length - 1;
			   	   							
			   	   							if (( Math.abs(price1 - quotes60[lastbar60].close ) > 100 * tradeManager[i].getPIP_SIZE() )){
			   	   								System.out.println("Input CSV: Incorrect price for LIMIT " + tradeManager[i].getSymbol() + action + " " + quantity1 + " " + price1 );
				   	   							break;
			   	   							}
			   	   							
			   	   							int posSize = (int)(quantity1 * tradeManager[i].getPOSITION_SIZE());
			   	   							t = tradeManager[i].createOpenTrade(Constants.TRADE_MA, action, setupType, posSize, price1);
			   	   						    if (setupType.indexOf(Constants.SETUP_SCALE_IN) != -1 )
			   	   						    	t.scaleIn = true;
			   	   						    
			   	   						    if ( MODE == Constants.TEST_MODE ){
			   	   						    	String detectTime = OPPORTUNITY_FILE.substring(6,19);
			   	   						        Date dt = DateFormatter2.parse(detectTime, new ParsePosition(0));
				   	   						    t.detectTime = IBDataFormatter.format(dt);
			   	   					        }
			   	   						    else 
				   	   						    t.detectTime = IBDataFormatter.format(new Date());
	
			   	   						    tradeManager[i].setTrade(t);
			   	   						    //tradeManager[i].enterLimitPosition1(price1,  (int)(quantity1 * tradeManager[i].getPOSITION_SIZE()));
			   	   						    if (tradeManager[i].createTradeLimitOrder( posSize, price1 ) == Constants.ERROR)
			   	   						    	tradeManager[i].setTrade(null);
	
			   	   						    System.out.println(tradeManager[i].getSymbol() + action + " " +  posSize + " " + price1 + " " + " placed");
			   	   						}
		  	   						}
		   	   						catch ( NumberFormatException e )
		   	   						{
		   	   							System.out.println("Input CSV: invalide quantities for LIMIT " + tradeManager[i].getSymbol() + action + " " + parm1 + " " + parm2);
		   	   							break;
		   							}*/
		  	   					}
			        		}
		        		}
	        		}
	        		
	        		
	        		if (( STRATEGY_NAME.equals("PV") && ( tokens != null ) && ( tokens.length >=6 )))
	        		{	
		                symbol = tokens[0];
	               		action = tokens[1];
	               		setupType = tokens[2];
	               		parm1 = tokens[3];
	               		parm2 = tokens[4];
	               		String monitor = tokens[5];

						if (symbol != null ) 
						{
			        		for ( int i = 0; i < TOTAL_SYMBOLS; i++)
			        		{
			        			if (symbol.equalsIgnoreCase(tradeManager[i].getSymbol()))
			        			{
			        				boolean enabled = "X".equalsIgnoreCase(monitor);
			        				if ( enabled )
			        				{	
			  	   						tradeManager[i].setEnable(true);
			  	   						System.out.println(tradeManager[i].getSymbol() + " enabled");
			        				}
			        			}
			        		}
						}	
	        		}

				}


	            fr.close();	
	            
	            // back up this file,
	            if  ( mode == Constants.REAL_MODE )
	            {	
		            String currentDay = DateFormatter2.format(new Date());
		        	String BACKUP_OPPORTUNITY_FILE = "./opp/" + currentDay + "_Opportunity.csv";
		        	Path source = Paths.get(OPPORTUNITY_FILE);
		        	Path target = Paths.get(BACKUP_OPPORTUNITY_FILE);
		        	Files.copy(source, target,REPLACE_EXISTING, COPY_ATTRIBUTES);
		            
		            f.delete();  // we no longer need this file
	            }
	       
	      } 
	      catch ( FileNotFoundException ex )
		  {
	        	//System.out.println("External Command file not found");
	        	//e.printStackTrace();  ignore excetion as file does not exist is normal
		  } 
	  	  catch(Exception e) 
	  	  {
	    	  e.printStackTrace();
	      }
	     finally
	     {
	    	 /*
	    	 System.out.println();
	    		for ( int i = 0; i < TOTAL_SYMBOLS; i++)
	    		{
		   			if ( tradeManager[i].trade != null ) 
					{
		   				QuoteData[] quotesL = tradeManager[i].getQuoteData(Constants.CHART_60_MIN);;
	   					System.out.println( tradeManager[i].symbol + " " + tradeManager[i].trade.action + " " + tradeManager[i].trade.status + " " + tradeManager[i].trade.triggerPrice +  " PushStart:" + quotesL[tradeManager[i].trade.pushStartL].time);
					}
	    		}*/
	     	}
		}


		private String[] splitTotokens(String line, String delim){
		    String s = line;
		    int i = 0;
		
		    while (s.contains(delim)) {
		        s = s.substring(s.indexOf(delim) + delim.length());
		        i++;
		    }
		    String token = null;
		    String remainder = null;
		    String[] tokens = new String[i];
		
		    for (int j = 0; j < i; j++) {
		        token = line.substring(0, line.indexOf(delim));
		        // System.out.print("#" + token + "#");
		        tokens[j] = token;
		        remainder = line.substring(line.indexOf(delim) + delim.length());
		        //System.out.println("#" + remainder + "#");
		
		        line = remainder;
		    }
		    return tokens;
		}

		
		public void removeTrade(String symbol)
		{
			System.out.println("Remove Trade " + STRATEGY_NAME + " " + symbol);
			if ( symbol != null )
			{	
				for ( int i = 0; i < tradeManager.length; i++)
				{
					if ( tradeManager[i].getTrade() != null)
					{	
						if ((symbol.equalsIgnoreCase(tradeManager[i].getSymbol())))
						{
							System.out.println("Trade " + tradeManager[i].getSymbol() + " found, remove trade");
							tradeManager[i].cancelAllOrders();
							tradeManager[i].removeTrade();
						}
					}
				}
			}
		}

		
		public void removeAllTrades()
		{
			logger.info("Remove all Trade for " + STRATEGY_NAME) ;
			for ( int i = 0; i < tradeManager.length; i++){
				if ( tradeManager[i].getTrade() != null){
					logger.info("Trade " + tradeManager[i].getSymbol() + " found, remove trade");
					tradeManager[i].cancelAllOrders();
					tradeManager[i].removeTrade();
				}
				tradeManager[i].clearTradeReport();
			}
		}

		
		
		public void addTrade(Trade trade)
		{
			System.out.println("Add Trade " + STRATEGY_NAME + " " + trade.symbol);
			if ( trade != null )
			{	
				for ( int i = 0; i < TOTAL_SYMBOLS; i++)
				{
					if ( tradeManager[i].getSymbol().equals(trade.symbol))
					{	
						tradeManager[i].setTrade(trade);
					}
				}
			}
		}

		
		public void closeAllPositionsByLimit() {
			
			for ( int i = 0; i < tradeManager.length; i++)
			{
				if ( tradeManager[i].getTrade() != null )
				{
					tradeManager[i].cancelLimits();
					tradeManager[i].cancelTargets();

					if ( Constants.STATUS_OPEN.equals(tradeManager[i].getTrade().status ) || Constants.STATUS_STOPPEDOUT.equals(tradeManager[i].getTrade().status )) 
					{
						tradeManager[i].cancelStop();
						tradeManager[i].setTrade(null);
					}
					else if ( Constants.STATUS_PLACED.equals(tradeManager[i].getTrade().status ) || Constants.STATUS_FILLED.equals(tradeManager[i].getTrade().status )) 
					{
						if (Constants.ACTION_BUY.equals(tradeManager[i].getTrade().action )){
							
							double lastClosePrice = tradeManager[i].getInstrument().getLastQuote().close;
							double targetClosePrice = tradeManager[i].adjustPrice(lastClosePrice+tradeManager[i].getPIP_SIZE(), Constants.ADJUST_TYPE_UP);
					        logger.info(tradeManager[i].getSymbol() + " place exiting sell target order : " + targetClosePrice + " " + tradeManager[i].getTrade().remainingPositionSize);
			        		tradeManager[i].createTradeTargetOrder(tradeManager[i].getTrade().remainingPositionSize, targetClosePrice );
			        		
						}
						else if (Constants.ACTION_SELL.equals(tradeManager[i].getTrade().action )){

							double lastClosePrice = tradeManager[i].getInstrument().getLastQuote().close;
							double targetClosePrice = tradeManager[i].adjustPrice(lastClosePrice-tradeManager[i].getPIP_SIZE(), Constants.ADJUST_TYPE_DOWN);
					        logger.info(tradeManager[i].getSymbol() + " place exiting buy target order : " + targetClosePrice + " " + tradeManager[i].getTrade().remainingPositionSize);
			        		tradeManager[i].createTradeTargetOrder(tradeManager[i].getTrade().remainingPositionSize, targetClosePrice );
			        		
						}
					}
				}
			}
		}
		
		/*
		public void sentTriggerEmail( String title ){
			if (((mode == Constants.REAL_MODE) || (mode == Constants.SIGNAL_MODE)) && ( triggerEmail != null ) && ( triggerEmail.length() > 3 )){
				if (!triggerEmailSent.contains(title)){
					EmailSender.getInstance().sendEmail(triggerEmail, title, "");
					triggerEmailSent.add(title);
				}
			}
		}

		public void sentDetectEmail(String title){
			if (((mode == Constants.REAL_MODE) || (mode == Constants.SIGNAL_MODE)) &&( detectEmail != null ) && ( detectEmail.length() > 3 )){
				logger.warning(STRATEGY_NAME + " send detect email " + title);
				if (!detectEmailSent.contains(title)){
					EmailSender.getInstance().sendEmail(detectEmail, title, "");
					detectEmailSent.add(title);
				}
			}
		}*/

		public void sentAlertEmailTest( AlertOption o, String title, Trade trade ){
					if (!alertEmailSent.contains(title)){
						System.out.println(STRATEGY_NAME + " send " + title);
						//EmailSender.getInstance().sendEmail(alertEmail, title, body);
						alertEmailSent.add(title);
					}
		}

		
		public void sentAlertEmail( AlertOption o, String title, Trade trade ){
			if (((mode == Constants.REAL_MODE) || (mode == Constants.SIGNAL_MODE)) &&( alertEmail != null ) && ( alertEmail.length() > 3 )){
				if ( emailAlerts.indexOf(o.toString()) != -1 ){
					title = getStrategyName() + " " + IB_PORT + " " + o.toString() + " " + title;
					String body = trade.listTradeEvents();
					logger.warning(STRATEGY_NAME + " send email " + title);
					if (!alertEmailSent.contains(title)){
						EmailSender.getInstance().sendEmail(alertEmail, title, body);
						alertEmailSent.add(title);
					}
				}
			}
		}

		public void addStrategyTrigger( String trigger ){
			strategyTriggers.append(trigger + "\n");
		}
		
}	
	



