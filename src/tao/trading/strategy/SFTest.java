package tao.trading.strategy;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Logger;

import tao.trading.Constants;
import tao.trading.Env_Setting;
import tao.trading.Instrument;
import tao.trading.QuoteData;
import tao.trading.Trade;
import tao.trading.strategy.gui.SFGUI;
import tao.trading.strategy.tm.Strategy;
import tao.trading.strategy.tm.TradeManagerBasic;
import tao.trading.strategy.util.Utility;

import com.ib.client.Contract;
import com.ib.client.ContractDetails;
import com.ib.client.EClientSocket;
import com.ib.client.EWrapper;
import com.ib.client.Execution;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.UnderComp;
import com.thoughtworks.xstream.XStream;


// Command Parameter
//Test 0 0 0 0 1 sftest.properties C:/OneDrive/T_test/data/history_weekly2/realtimeData_EUR_20171203.csv report.txt
//Test2 0 0 0 0 1 sftest.properties C:/OneDrive/T_test/data/history_weekly2/ 20171203 20171224 ./
public class SFTest extends SF implements EWrapper
{
	//public String ib_account_master;
	public List<String> ib_accounts = new ArrayList<String>();
	protected static Logger logger=Logger.getLogger("SFTest");
	protected int[] id_realtime;
	protected int[] id_history;
	protected EClientSocket m_client = new EClientSocket(this);
	public static SimpleDateFormat IBDataFormatter = new SimpleDateFormat("yyyyMMdd  HH:mm:ss");
	public static SimpleDateFormat DateFormatter = new SimpleDateFormat("yyyyMMdd");
	public static SimpleDateFormat InternalDataFormatter = new SimpleDateFormat("yyyyMMdd  HH:mm");
	//protected TradeManagerInf[] tradeManager;
	TradeManager2[] defaultAccountTradeManager;
	public Instrument[] instruments;
	boolean init_history = false;
	boolean[] quotes_received; 
	boolean[] id_realtime_started;
	long lastRealTimeRequest=0;
	boolean[] firstQuote;
	int totalSymbolIndex = 0;
	int connectionId = 100;
	public boolean friday_10 = false, friday_11=false, friday_12=false, friday_14=false,friday_15=false,friday_16=false;
	Vector<String> historyData = new Vector<String>();
	HashMap<String, QuoteData>[][] qts;
	HashMap<String, Double> exchangeRate = new HashMap<String, Double>();
 	boolean save_realtime_data = false;
	String saveRealTimeFile = "realtimeData.csv";
	String saveHistoryFile = "historyData.csv";
	String reportFileName = "report.txt";
	String statusFileName = "trade_status.txt";
	String blockSymbols = null;
	String noReverseSymbols = null;
	boolean dataBroken,linkLost;
	boolean historySaved;
	String updatePortfolioCmd, updatePortfolioAccount, updateOpenOrderCmd;
	boolean init_quote_update = false;
	int last_hour = -1;
	int last_errorcode = 0;
	int not_received_realtime = 0;
	long systemStartTime, lastTickUpdateTime;
	XStream xstream;
	String market;
	boolean stop_loss = false;
	QuoteData prevData[];
	long lastRealTimeUpdate;
	long lastRealTimeRequestTime[];
	long lastRealTimeUpdateTime[]=null;
	long lastRealTimeUpdateNotificationTime[];
	int realtimeBarCount=0;
	boolean restart = false;
	public Env_Setting setting;
	public String outputdir;
	int SYSTEM_RUNNING_MODE;
	//static public String tradeEventNotification;
	//String shutdown = "WEEKLY";
	//SFGUI newContentPane;
	SFGUI sfgui;
	String STATUS_FILE = "TRADE_STATUS.txt";
	boolean TICK_ENABLED = false;
	// Strageties
	public int NUM_STRATEGY = 0;
	public int NUM_ACCOUNTS = 0;
	
	public int TOTAL_SYMBOLS = 22;
	public int ALL_SYMBOLS = 23;
	
	public Strategy[] strageties;
	public String DEFAULT_STRATEGY;
	public Set<Integer> REAL_TIME_TRADING_PAIRS;

	
	
	
	/*  News Hour
	US 			8:30 - 10:00
	Japan		18:50 - 23:30
	Canada		7:00 - 8:30
	UK			2:00 - 4:30
	Germany		2:00 - 6:00
	France		2:45 - 4:00
	Switzerland	1:45 - 5:30
	Australia	17:30 - 19:30
	*/
	
	
	public static void main(String[] args)
	{
		SFTest open;
		String account;
		int port;
		String conn_id;
		String market;
		float exp;
		String outputdir = "";
		String propFile = null; 
		
		//Test2 0 0 0 0 1 sftest.properties C:/OneDrive/T_test/data/history_weekly2/ 20171203 20171224 ./
		if ( "Test2".equalsIgnoreCase(args[0]))
    	{
    		account = "DU31237";
    		port = 8496;
    		conn_id = "81";
    		
    		String parmId = args[1];
    		String parm1 = args[2];
    		String parm2 = args[3];
    		String parm3 = args[4];
    		
    		exp = new Float(args[5]);
    		propFile = args[6];
    		String data_dir = args[7];
    		String dateBegin = args[8];
    		String dateEnd = args[9];
    		String report_dir = args[10];
    		
    		try{
    			open = new SFTest(parm1, parm2, parm3, propFile, Constants.TEST_MODE);

         	    //open.getRealTimeTradingPairs();
        		
        		// 1 history 5
    			//open.readHistoryData( histFileName );

    			Date realTimeDate = InternalDataFormatter.parse(dateBegin +"   08:00"); //extra 8 hour in case for day light saving time
    			Date realTimeDateEnd = InternalDataFormatter.parse(dateEnd +"   08:00"); //extra 8 hour in case for day light saving time
 
     			Date history1 = new Date(realTimeDate.getTime() - 60000L * 60 * 24 * 7 * 4 );   
        		Date history2 = new Date(realTimeDate.getTime() - 60000L * 60 * 24 * 7 * 3 ); 
        		Date history3 = new Date(realTimeDate.getTime() - 60000L * 60 * 24 * 7 * 2 ); 
        		Date history4 = new Date(realTimeDate.getTime() - 60000L * 60 * 24 * 7  );
        		
        		String history1FileName = data_dir + "realtimeData_EUR_" + DateFormatter.format(history1) + ".csv"; 
        		String history2FileName = data_dir + "realtimeData_EUR_" + DateFormatter.format(history2) + ".csv"; 
        		String history3FileName = data_dir + "realtimeData_EUR_" + DateFormatter.format(history3) + ".csv"; 
        		String history4FileName = data_dir + "realtimeData_EUR_" + DateFormatter.format(history4) + ".csv"; 
        		
        		open.readHistoryData2( history1FileName );
        		open.readHistoryData2( history2FileName );
        		open.readHistoryData2( history3FileName );
        		open.readHistoryData2( history4FileName );
        		
        		String realFileName;
        		while ( realTimeDate.getTime() <= realTimeDateEnd.getTime()){
        			realFileName = data_dir + "realtimeData_EUR_" + DateFormatter.format(realTimeDate) + ".csv"; 
        			System.out.println("Realtime filename:" + realFileName); 
        			
	        		open.readRealTimeData( parmId, realFileName, realTimeDate );
	
	        		open.setExchangeRate();
	    			//StringBuffer tradeStatus = new StringBuffer("Trade Status\n" );
	    			StringBuffer tradeReport = new StringBuffer("Trade Report\n" );
	    			for ( int i = 0; i < open.NUM_STRATEGY; i++ ){
	        			tradeReport.append(open.strageties[i].getTradeReport() + "\n\n");
	        		}
	    			
	    			String reportFileName2 = report_dir + "Report_" + DateFormatter.format(realTimeDate) + ".txt";
        			System.out.println("Report file:" + reportFileName2); 
	    			Utility.saveFile(reportFileName2, tradeReport.toString());
	    			
	    			realTimeDate = new Date(realTimeDate.getTime() + 60000L * 60 * 24 * 7  );
	    			open.resetParam();
        		}
    			/*StringBuffer tradeTriggers = new StringBuffer(); 
    			for ( int i = 0; i < open.NUM_STRATEGY; i++ ){
    				tradeTriggers.append(open.strageties[i].strategyTriggers.toString() + "\n");
	    		}
				Utility.saveFile(open.STATUS_FILE, tradeTriggers.toString());*/
    			return;
    		}
    		catch ( Exception e){
    			e.printStackTrace();
    			//System.exit(0);
    			// continue so it can produce an empty report
    		}
    	}
		//Test 0 0 0 0 1 sftest.properties C:/OneDrive/T_test/data/history_weekly2/historyData_EUR_20101128.csv C:/OneDrive/T_test/data/history_weekly2/realtimeData_EUR_20101128.csv report.txt		
		//Test 0 0 0 0 1 sftest.properties C:/OneDrive/T_test/data/history_weekly2/realtimeData_EUR_20171203.csv report.txt
		else if ( "Test".equalsIgnoreCase(args[0]))
    	{
    		account = "DU31237";
    		port = 8496;
    		conn_id = "81";
    		market = "EUR";
    		
    		String parmId = args[1];
    		String parm1 = args[2];
    		String parm2 = args[3];
    		String parm3 = args[4];
    		
    		exp = new Float(args[5]);
    		propFile = args[6];    		
    		String realFileName = args[7];
    		String reportFileName = args[8];
    		//int mode = Constants.TEST_MODE;
    		

    		//for ( int i = 0; i < open.NUM_STRATEGY; i++ )
        	//	open.strageties[i].readExternalInputTrendCSV();
    		
    		
    		int ind = realFileName.indexOf("EUR_");
    		String realTimeFileDate = realFileName.substring(ind+4, ind+12);
    		try{
    			open = new SFTest(parm1, parm2, parm3, propFile, Constants.TEST_MODE);

        		// add hold flag or exit flat, 
         	    while(true){ 
	         	    open.setting = new Env_Setting(propFile);
	         	    if ( open.setting.getBooleanProperty("EXIT")){ // this is needed for some mac/linux system
	         	    	System.out.println("System exit on exit flag");
	         	    	System.exit(0);
	         	    }
	         	    if ( !open.setting.getBooleanProperty("PAUSE")){
	         	    	break;
	         	    }
	         	    else{
	         	    	System.out.println("pause is on, sleep 1 minutes");
						open.sleep(1);
	         	    }
         	    }
         	    
         	    open.getRealTimeTradingPairs();
        		
        		// 1 history 5
    			//open.readHistoryData( histFileName );

    			Date realTimeDate = InternalDataFormatter.parse(realTimeFileDate +"   08:00"); //extra 8 hour in case for day light saving time
 
     			Date history1 = new Date(realTimeDate.getTime() - 60000L * 60 * 24 * 7 * 4 );   
        		Date history2 = new Date(realTimeDate.getTime() - 60000L * 60 * 24 * 7 * 3 ); 
        		Date history3 = new Date(realTimeDate.getTime() - 60000L * 60 * 24 * 7 * 2 ); 
        		Date history4 = new Date(realTimeDate.getTime() - 60000L * 60 * 24 * 7  );
        		
        		String history1FileName = realFileName.substring(0, ind+4) + DateFormatter.format(history1) + ".csv"; 
        		String history2FileName = realFileName.substring(0, ind+4) + DateFormatter.format(history2) + ".csv"; 
        		String history3FileName = realFileName.substring(0, ind+4) + DateFormatter.format(history3) + ".csv"; 
        		String history4FileName = realFileName.substring(0, ind+4) + DateFormatter.format(history4) + ".csv"; 
        		
        		open.readHistoryData2( history1FileName );
        		open.readHistoryData2( history2FileName );
        		open.readHistoryData2( history3FileName );
        		open.readHistoryData2( history4FileName );
        		
        		open.readRealTimeData( parmId, realFileName, realTimeDate);

        		open.setExchangeRate();
    			//StringBuffer tradeStatus = new StringBuffer("Trade Status\n" );
    			StringBuffer tradeReport = new StringBuffer("Trade Report\n" );
    			for ( int i = 0; i < open.NUM_STRATEGY; i++ ){
        			tradeReport.append(open.strageties[i].getTradeReport() + "\n\n");
        		}
    			
    			int reportFileNameInd1 = realFileName.indexOf("_2");
    			//reportFileName = "Report" + realFileName.substring(reportFileNameInd1,reportFileNameInd1+9) +".txt";
    			Utility.saveFile(reportFileName, tradeReport.toString());

    			StringBuffer tradeTriggers = new StringBuffer(); 
    			for ( int i = 0; i < open.NUM_STRATEGY; i++ ){
    				tradeTriggers.append(open.strageties[i].strategyTriggers.toString() + "\n");
	    		}
				Utility.saveFile(open.STATUS_FILE, tradeTriggers.toString());
    			return;
    		}
    		catch ( Exception e){
    			e.printStackTrace();
    			//System.exit(0);
    			// continue so it can produce an empty report
    		}
    	}
		else{	
    		try{
        		propFile = args[0];
        		open = new SFTest( null, null, null, propFile, Constants.REAL_MODE);
	    		open.run();


	    		/*
	            javax.swing.SwingUtilities.invokeLater(new Runnable() {
	                public void run() {
	                	SFGUI.createAndShowGUI();
	                	//Disable boldface controls.
	                }
	            });*/

	            //  temporilary disable GUI
			/*	System.out.println("starting GUI...");
	           	SFGUI sfgui = new SFGUI(open);
	        	sfgui.run();
				
	           	for ( int i = 0; i < open.NUM_STRATEGY; i++ )
	    		{
					if ("RM".equals(open.strageties[i].STRATEGY_NAME))
						sfgui.setTable1Content(open.strageties[i].getAllTraderManagerWithTrade());
					else if ("PV".equals(open.strageties[i].STRATEGY_NAME))
						sfgui.setTable3Content(open.strageties[i].getAllTraderManagerWithTrade());
	    		}*/

	    		int init_history_wait = 0;
				while ( open.init_history == false ){
					open.sleep(0.5);
					if ( ++init_history_wait > 30 ){
						logger.warning("History data not received, restart application..");
						open.restartApplication();
					}
				}
				logger.warning("History received, trading starts...");

				String lastTradeStatus= "";
				//int loop = 0;
		    	while ( true  ){
		    		//loop++;
	   	    		//open.sleep(0.05);  // can not remove, will cause command file to be read multiple timess
	   	    		open.sleep(0.01);  // can not remove, will cause command file to be read multiple timess
	         	    open.setting = new Env_Setting(propFile);  // read the pause command
		    		
		    		/**************************************
	    			 * Read External Commands
	    			 *************************************/
		    		for ( int i = 0; i < open.NUM_STRATEGY; i++ ){
		    			open.strageties[i].readExternalCommand();
	    	    		open.strageties[i].readExternalInputTrendCSV();
		    		}
		    		
		    		/**************************************
	    			 * Save Status
	    			 *************************************/
	    			Date now = new Date();
					Calendar calendar = Calendar.getInstance();
					calendar.setTime( now );
					int weekday = calendar.get(Calendar.DAY_OF_WEEK);
					int hour_of_day=calendar.get(Calendar.HOUR_OF_DAY);
					int minute=calendar.get(Calendar.MINUTE);
					long nowl = now.getTime()/1000;

					if (minute % 5 == 0 ){
						int notReceiveMin = (int)((nowl - open.lastRealTimeUpdate)/60);
						StringBuffer tradeStatus = new StringBuffer("Trade Status     "  + IBDataFormatter.format(now) + "\n          Quote Delay:" + notReceiveMin + " " + "\n\n" );
						String status_summary = open.loadStatusHeadline();
						if (( status_summary != null ) && ( status_summary.length() > 0 ))
							tradeStatus.append(open.loadStatusHeadline() + "\n\n");
						
						for ( int i = 0; i < open.NUM_STRATEGY; i++ ){
							// Set GUI
							/*
							if ("MA".equals(open.strageties[i].STRATEGY_NAME))
								sfgui.setTable1Content(open.strageties[i].getAllTraderManagerWithTrade());
							else if ("RM".equals(open.strageties[i].STRATEGY_NAME))
								sfgui.setTable3Content(open.strageties[i].getAllTraderManagerWithTrade());*/
		
							open.strageties[i].saveAllTradeData(open.xstream);
			    			//open.strageties[i].createTradeReport(null);  temporilary disable report file
		        			tradeStatus.append(open.strageties[i].getTradeStatus() + "\n");
			    		}

						if ( tradeStatus.toString().compareTo(lastTradeStatus) != 0 ){
							Utility.saveFile(open.setting.getOutputDir(), open.STATUS_FILE, lastTradeStatus );
							lastTradeStatus = tradeStatus.toString();
						}
					}
					
					
					int notReceivedInMin = 0;
					for ( int j = 0; j < open.TOTAL_SYMBOLS; j++){
						if ( open.getRealTimeTradingPairs().contains(j)){ 
							if ( open.lastRealTimeUpdateTime != null )
								notReceivedInMin = (int)((nowl - open.lastRealTimeUpdateTime[j] )/60);
							if ( notReceivedInMin > 1 )
								logger.fine(open.instruments[j].getSymbol() + " not detected real time data for " + notReceivedInMin + " minutes");
							if (( notReceivedInMin > 10 ) && ( notReceivedInMin < 120 )){
								if (( hour_of_day == 0 ) && ( notReceivedInMin > 15 )){
									logger.warning(open.instruments[j].getSymbol() + " not detected real time data for " + notReceivedInMin + " minutes, Restart application...");
									open.restartApplication();
								}
								else{
									logger.warning(open.instruments[j].getSymbol() + " not detected real time data for " + notReceivedInMin + " minutes, Restart application...");
									open.restartApplication();
								}
							}
		
							if ((( open.lastRealTimeUpdateTime == null ) || (open.lastRealTimeUpdateTime[j] == 0 )) && (((nowl - open.systemStartTime/1000)/60) > 15 )){
								logger.warning("Have not received any real time data for 10 minutes, Restart application...");
								open.restartApplication();
							}
						}
					}

					// Every turn of the hour
					if (( open.last_hour != -1 ) && ( hour_of_day != 0 ) && ( hour_of_day != open.last_hour ))   // 0 is 12am
					{
						// garbage collection
						System.gc(); 			
						
						// check open orders
						open.updateOpenOrderCmd = Constants.CMD_UPDATE;
						open.m_client.reqOpenOrders();
						
						logger.info(lastTradeStatus);

						//if ((open.last_errorcode != 0 ) || ( open.not_received_realtime > 0 )) 
						//	open.checkDataBroken();  //last exist
					}

					open.last_hour = hour_of_day;

					// stop trading
					if (( weekday == Calendar.FRIDAY ) && ( hour_of_day >= 16 ))
						break;
					if ("daily".equalsIgnoreCase(open.setting.getShutdown())){
						// stop every day at 5pm 17
						if (( hour_of_day == 17 ) && ( minute >= 0 ) && ( minute < 15 )){
							open.restart = true;
							break;
						}

					}
				}

		    	
				logger.info("trading time is up");
	    		if (open.restart != true){
					logger.info("close all on weekend close all positions by limit....");
					for ( int i = 0; i < open.NUM_STRATEGY; i++ ){
						logger.warning("close positions for "  + open.strageties[i].STRATEGY_NAME);
		    			open.strageties[i].closeAllPositionsByLimit();
			    	}
					
					open.sleep(30);  // wait 30 minutes for all positions to close 
					logger.info("Close all positions by market....");
					open.closeAllPositionsByMarket();
					
					logger.info("Remove all trades....");
		    		for ( int i = 0; i < open.NUM_STRATEGY; i++ )
		    			open.strageties[i].removeAllTrades();
					
					// last, cancel any outstanding orders, this has to be absolute last one as it will cancel any (new) orders too
					logger.info("Clean remaining open orders....");
					open.cleanOpenOrder();
				}
				
				logger.info("Trading system shut down");
				System.exit(0);
    		
    		}catch ( Exception e){
    			e.printStackTrace();
    			System.exit(0);
    		}
    	}
    		
	}

	

	void setExchangeRate(){
		int last = instruments[0].getQuoteData(Constants.CHART_60_MIN).length-1;
	   	double EUR_rate = 1/instruments[0].getQuoteData(Constants.CHART_60_MIN)[last].close;
		exchangeRate.put("EUR",EUR_rate);
		last = instruments[9].getQuoteData(Constants.CHART_60_MIN).length-1;
	   	double GBP_rate = 1/instruments[9].getQuoteData(Constants.CHART_60_MIN)[last].close;
	   	exchangeRate.put("GBP",GBP_rate);
		last = instruments[16].getQuoteData(Constants.CHART_60_MIN).length-1;
	   	double CHF_rate = instruments[16].getQuoteData(Constants.CHART_60_MIN)[last].close;
	   	exchangeRate.put("CHF",CHF_rate);
		last = instruments[15].getQuoteData(Constants.CHART_60_MIN).length-1;
	   	double CAD_rate = instruments[15].getQuoteData(Constants.CHART_60_MIN)[last].close;
	   	exchangeRate.put("CAD",CAD_rate);
		last = instruments[18].getQuoteData(Constants.CHART_60_MIN).length-1;
	   	double AUD_rate = 1/instruments[18].getQuoteData(Constants.CHART_60_MIN)[last].close;
	   	exchangeRate.put("AUD",AUD_rate);
		last = instruments[21].getQuoteData(Constants.CHART_60_MIN).length-1;
	   	exchangeRate.put("NZD",1.325334);
	   	//if ( instruments[21] != null ){
	   	//	double NZD_rate = 1/instruments[21].getQuoteData(Constants.CHART_60_MIN)[last].close;
	   	//	exchangeRate.put("NZD",NZD_rate);
	   	//}
	   	//else
		   	exchangeRate.put("NZD",1.325334);
		last = instruments[14].getQuoteData(Constants.CHART_60_MIN).length-1;
	   	double JPY_rate = instruments[14].getQuoteData(Constants.CHART_60_MIN)[last].close;
	   	exchangeRate.put("JPY",JPY_rate);
	   	exchangeRate.put("USD",1.0);
	}
	
	
	public void run() {
		// this starts real time bar
		updateAllAccounts(Constants.CMD_UPDATE);
		m_client.reqCurrentTime();
	}

	public void cleanOpenOrder()
	{
		updateOpenOrderCmd = Constants.CMD_CLOSE;
		m_client.reqOpenOrders();
		sleep(1);
		// in clean the orders in openOrder();
	}
	
	
	private void updateAllAccounts(String updateCommand){
		// this requests account update so we can calculate the portfolio
		updatePortfolioCmd = updateCommand;
		for ( String ib_account: ib_accounts ){
			logger.info("Request account update: "+ ib_account);
			m_client.reqAccountUpdates(true,ib_account);	
			sleep(0.1);  // sleep 6 seconds
			m_client.reqAccountUpdates(false,ib_account);
			sleep(0.1);  // sleep 6 seconds
		}
	}
	

	public void closeAllPositionsByMarket() {
		logger.warning("Close all positions by market orders");
		updateAllAccounts(Constants.CMD_CLOSE);
	}
	
	

/*	
	public SFTest( String account, int port, String ib_id, String trade_market, int mode, float exp, String propFile) throws Exception
	{
		xstream = new XStream();
        xstream.alias("trade", Trade.class);
        
        this.market = trade_market;
		
		/*
		logger = Logger.getLogger("SFTest_" + account);

		
		try
		{
			boolean append = true;
			logger = Logger.getLogger("SFTest_" + account);

			FileHandler handler = new FileHandler(detailLogFile, append);
			handler.setLevel(Level.FINER);
			handler.setFormatter(new SimpleFormatter());
			logger.addHandler(handler);

			// this logger creates trade summary
			
			if (( mode == Constants.REAL_MODE ) || ( mode == Constants.SIGNAL_MODE ))
			{
				FileHandler handler2 = new FileHandler(summaryLogFile, append);
				handler2.setLevel(Level.WARNING);
				handler2.setFormatter(new SimpleFormatter());
				logger.addHandler(handler2);
			}

			// Add to the desired logger
			
		} 
		catch (IOException e)
		{
			logger.severe(e.getMessage());
			e.printStackTrace();
		}*/

		
/*		IB_ACCOUNT = account;
	   	IB_ID = ib_id;
	   	SYSTEM_RUNNING_MODE = mode;

	   	exchangeRate.put("EUR",0.9104);
	   	exchangeRate.put("GBP",0.6544);
	   	exchangeRate.put("CHF",0.9490);
	   	exchangeRate.put("CAD",1.2446);
	   	exchangeRate.put("AUD",1.3009);
	   	exchangeRate.put("NZD",1.325334);
	   	exchangeRate.put("JPY",124.14);
	   	exchangeRate.put("USD",1.0);

/*	   	exchangeRate.put("EUR",0.6940);
	   	exchangeRate.put("GBP",0.6150);
	   	exchangeRate.put("CHF",0.7954);
	   	exchangeRate.put("CAD",0.9891);
	   	exchangeRate.put("AUD",1.001532344487);
	   	exchangeRate.put("NZD",1.325334);
	   	exchangeRate.put("JPY",76.6525);
	   	exchangeRate.put("USD",1.0);*/
	   	
/*		id_history = new int[50];//{0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23};
		id_realtime = new int[50];//]{500,501,502,503,504,505,506,507,508,509,510,511,512,513,514,515,516,517,518,519,520,521,522,523};

		TOTAL_SYMBOLS = 21;
		initialize();
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


 	    setting = new Env_Setting(propFile);

 	    if ( propFile != null )
			propertyFileName = propFile;
    	setting = new Env_Setting(propertyFileName);

 	    strageties = new Strategy[5]; // max strageies should be less than 5
 	    if (setting.getStragety("MA")){
    		strageties[NUM_STRATEGY++] = new Strategy(this, "MA", setting.getStragetyMode("MA"));
 	    }
 	    if (setting.getStragety("RM")){
    		strageties[NUM_STRATEGY++] = new Strategy(this, "RM", setting.getStragetyMode("RM"));
 	    }
 	    if (setting.getStragety("RV")){
    		strageties[NUM_STRATEGY++] = new Strategy(this, "RV", setting.getStragetyMode("RV"));
 	    }
 	    if (setting.getStragety("PV")){
    		strageties[NUM_STRATEGY++] = new Strategy(this, "PV", setting.getStragetyMode("RV"));
 	    }
 	    if (setting.getStragety("MC")){
    		strageties[NUM_STRATEGY++] = new Strategy(this, "MC", setting.getStragetyMode("RV"));
 	    }
 	    if (setting.getStragety("BO")){
    		strageties[NUM_STRATEGY++] = new Strategy(this, "BO", setting.getStragetyMode("RV"));
 	    }
 	    
 	    String outputdir = setting.getOutputDir();
 	    logger.config("Environment: outputdir=" + setting.getOutputDir());
		if ( outputdir != null ){
    		//summaryLogFile = outputDir + summaryLogFile;
			//detailLogFile = outputDir + detailLogFile;
    		saveRealTimeFile = outputdir + saveRealTimeFile;
    		saveHistoryFile = outputdir + saveHistoryFile;
    		reportFileName = outputdir + reportFileName;
    		statusFileName = outputdir + statusFileName;
     	    logger.config("Environment: saveRealTimeFile=" + saveRealTimeFile);
     	    logger.config("Environment: saveHistoryFile=" + saveHistoryFile);
		}
		
 	    String save_rt_data = setting.props.getProperty("SAVE_REALTIME_DATA");
 	    if ("true".equalsIgnoreCase(save_rt_data))
 	    	save_realtime_data = true;
 	    
        // other properties
        logger.warning("Number of Stragety loaded: " + NUM_STRATEGY);
		for ( int i = 0; i < NUM_STRATEGY; i++ )
		{
			logger.warning("Stragety " + strageties[i].STRATEGY_NAME + " mode:" + strageties[i].mode + " loaded");
/*			strageties[i].initialize(account, m_client, instruments, exp, exchangeRate, /*logger, outputdir);
/*			strageties[i].loadAllTradeData(xstream);
		}

		
		prevData = new QuoteData[TOTAL_SYMBOLS];
        closeAccountTradeManager = new TradeManager2(account, m_client, null, 499, logger );

        if (( SYSTEM_RUNNING_MODE == Constants.REAL_MODE ) || ( SYSTEM_RUNNING_MODE == Constants.SIGNAL_MODE )) 
        {
			m_client.eConnect("127.0.0.1" , port, new Integer(IB_ID));
			m_client.setServerLogLevel(5);
			logger.warning("SFTest started in " + SYSTEM_RUNNING_MODE + " mode at port " + port);
			
		/*	for ( int i = 0; i < NUM_STRATEGY; i++ )
			{
				strageties[i].loadAllTradeData(xstream);
			}*/
			//SG_240_MakingNewHHLL.loadAllTradeData(xstream);

/*        }

        
	}*/

	
	
	public SFTest(String parm1, String parm2, String parm3, String propFile, int mode) throws Exception
	{
		systemStartTime = new Date().getTime();
		xstream = new XStream();
        xstream.alias("trade", Trade.class);

 	    setting = new Env_Setting(propFile);
		logger.config(setting.toString());
 	    
		int ib_id=setting.getIb_id();
		int port = setting.getPort();

	   	exchangeRate.put("EUR",0.9104);
	   	exchangeRate.put("GBP",0.6544);
	   	exchangeRate.put("CHF",0.9490);
	   	exchangeRate.put("CAD",1.2446);
	   	exchangeRate.put("AUD",1.3009);
	   	exchangeRate.put("NZD",1.325334);
	   	exchangeRate.put("JPY",124.14);
	   	exchangeRate.put("USD",1.0);

/*	   	exchangeRate.put("EUR",0.6940);
	   	exchangeRate.put("GBP",0.6150);
	   	exchangeRate.put("CHF",0.7954);
	   	exchangeRate.put("CAD",0.9891);
	   	exchangeRate.put("AUD",1.001532344487);
	   	exchangeRate.put("NZD",1.325334);
	   	exchangeRate.put("JPY",76.6525);
	   	exchangeRate.put("USD",1.0);*/
	   	
		id_history = new int[50];//{0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23};
		id_realtime = new int[50];//]{500,501,502,503,504,505,506,507,508,509,510,511,512,513,514,515,516,517,518,519,520,521,522,523};

		initialize();
 		createInstrument(  0, "EUR", "CASH", "IDEALPRO", "USD", 0.0001, Constants.PRICE_TYPE_4, Constants.EMEA, Constants.AMS);
 		createInstrument(  1, "EUR", "CASH", "IDEALPRO", "JPY",   0.01, Constants.PRICE_TYPE_2, Constants.EMEA, Constants.APJ);
 		createInstrument(  2, "EUR", "CASH", "IDEALPRO", "CAD", 0.0001, Constants.PRICE_TYPE_3, Constants.EMEA, Constants.AMS);
 		createInstrument(  3, "EUR", "CASH", "IDEALPRO", "GBP", 0.0001, Constants.PRICE_TYPE_4, Constants.EMEA, Constants.EMEA);
 		createInstrument(  4, "EUR", "CASH", "IDEALPRO", "CHF", 0.0001, Constants.PRICE_TYPE_4, Constants.EMEA, Constants.EMEA);
 		createInstrument(  5, "EUR", "CASH", "IDEALPRO", "NZD", 0.0001, Constants.PRICE_TYPE_3, Constants.EMEA, Constants.APJ);
 		createInstrument(  6, "EUR", "CASH", "IDEALPRO", "AUD", 0.0001, Constants.PRICE_TYPE_4, Constants.EMEA, Constants.APJ);
 		createInstrument(  7, "GBP", "CASH", "IDEALPRO", "AUD", 0.0001, Constants.PRICE_TYPE_4, Constants.EMEA, Constants.APJ);
 		createInstrument(  8, "GBP", "CASH", "IDEALPRO", "NZD", 0.0001, Constants.PRICE_TYPE_3, Constants.EMEA, Constants.APJ);
 		createInstrument(  9, "GBP", "CASH", "IDEALPRO", "USD", 0.0001, Constants.PRICE_TYPE_4, Constants.EMEA, Constants.AMS);
 		createInstrument( 10, "GBP", "CASH", "IDEALPRO", "CAD", 0.0001, Constants.PRICE_TYPE_3, Constants.EMEA, Constants.AMS);
 		createInstrument( 11, "GBP", "CASH", "IDEALPRO", "CHF", 0.0001, Constants.PRICE_TYPE_4, Constants.EMEA, Constants.EMEA);
 		createInstrument( 12, "GBP", "CASH", "IDEALPRO", "JPY",   0.01, Constants.PRICE_TYPE_2, Constants.EMEA, Constants.APJ);
 		createInstrument( 13, "CHF", "CASH", "IDEALPRO", "JPY",   0.01, Constants.PRICE_TYPE_2, Constants.EMEA, Constants.APJ);
 		createInstrument( 14, "USD", "CASH", "IDEALPRO", "JPY",   0.01, Constants.PRICE_TYPE_2, Constants.AMS,  Constants.APJ);
 		createInstrument( 15, "USD", "CASH", "IDEALPRO", "CAD", 0.0001, Constants.PRICE_TYPE_3, Constants.AMS,  Constants.AMS);
 		createInstrument( 16, "USD", "CASH", "IDEALPRO", "CHF", 0.0001, Constants.PRICE_TYPE_4, Constants.AMS,  Constants.EMEA);
 		createInstrument( 17, "CAD", "CASH", "IDEALPRO", "JPY",   0.01, Constants.PRICE_TYPE_2, Constants.AMS,  Constants.APJ);
 		createInstrument( 18, "AUD", "CASH", "IDEALPRO", "USD", 0.0001, Constants.PRICE_TYPE_4, Constants.APJ,  Constants.AMS);
 		createInstrument( 19, "AUD", "CASH", "IDEALPRO", "JPY",   0.01, Constants.PRICE_TYPE_2, Constants.APJ,  Constants.APJ);
 		createInstrument( 20, "AUD", "CASH", "IDEALPRO", "NZD", 0.0001, Constants.PRICE_TYPE_4, Constants.APJ,  Constants.APJ);
 		createInstrument( 21, "NZD", "CASH", "IDEALPRO", "USD", 0.0001, Constants.PRICE_TYPE_4, Constants.APJ,  Constants.AMS);

 		createInstrument( 22, "SPY", "STK", "SMART", "USD", 0.01, Constants.PRICE_TYPE_2, Constants.AMS,  Constants.AMS);  //ETF ?

 		
 		
 	    strageties = new Strategy[5]; // max strageies should be less than 5
 	    if (setting.getStragety("MA")){
    		strageties[NUM_STRATEGY++] = new Strategy(this, "MA", setting);
 	    }
 	    if (setting.getStragety("RM")){
    		strageties[NUM_STRATEGY++] = new Strategy(this, "RM", setting );
 	    }
 	    if (setting.getStragety("RV")){
    		strageties[NUM_STRATEGY++] = new Strategy(this, "RV", setting );
 	    }
 	    if (setting.getStragety("PV")){
    		strageties[NUM_STRATEGY++] = new Strategy(this, "PV", setting );
 	    }
 	    if (setting.getStragety("MC")){
    		strageties[NUM_STRATEGY++] = new Strategy(this, "MC", setting );
 	    }
 	    if (setting.getStragety("BO")){
    		strageties[NUM_STRATEGY++] = new Strategy(this, "BO", setting );
 	    }

 	    outputdir = setting.getOutputDir();
 	    logger.config("Environment: outputdir=" + setting.getOutputDir());
		if ( outputdir != null ){
    		//summaryLogFile = outputDir + summaryLogFile;
			//detailLogFile = outputDir + detailLogFile;
    		saveRealTimeFile = outputdir + saveRealTimeFile;  // need to be T_loca to avoid upload all the time
    		saveHistoryFile = outputdir + saveHistoryFile;
    		reportFileName = outputdir + reportFileName;
    		statusFileName = outputdir + statusFileName;
     	    logger.config("Environment: saveRealTimeFile=" + saveRealTimeFile);
     	    logger.config("Environment: saveHistoryFile=" + saveHistoryFile);
		}

		save_realtime_data = setting.getSaveRealTimeData();

        // other properties
        logger.warning("Number of Stragety loaded: " + NUM_STRATEGY);
		for ( int i = 0; i < NUM_STRATEGY; i++ ){
			logger.warning("Stragety " + strageties[i].STRATEGY_NAME + " mode:" + strageties[i].mode + " loaded");
			//strageties[i].initialize(ib_account, m_client, instruments, exp, exchangeRate, /*logger,*/ outputdir, parm1, parm2, parm3);
			strageties[i].initialize(m_client, instruments, exchangeRate, outputdir, parm1, parm2, parm3);
			strageties[i].loadAllTradeData(xstream);
		}
 
		prevData = new QuoteData[TOTAL_SYMBOLS];
		defaultAccountTradeManager = new TradeManager2[ib_accounts.size()];
		int ind = 0;
		for ( String account: ib_accounts){
			defaultAccountTradeManager[ind++] = new TradeManagerBasic(account, m_client, null, ib_id );
		}
		
		SYSTEM_RUNNING_MODE = mode;
        if (( SYSTEM_RUNNING_MODE == Constants.REAL_MODE ) || ( SYSTEM_RUNNING_MODE == Constants.SIGNAL_MODE )){ 
        	m_client.eConnect("127.0.0.1" , port, ib_id);
			m_client.setServerLogLevel(5);
			logger.warning("SFTest started in " + SYSTEM_RUNNING_MODE + " mode at port " + port);
        }
     }

	
	
	public TradeManager2 getDefaultAccount( String accountName ){
		for ( int i = 0; i < defaultAccountTradeManager.length; i++){
			if (defaultAccountTradeManager[i].IB_ACCOUNT.equals(accountName))
				return defaultAccountTradeManager[i];
		}
		return null;
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	@Override
	public void error(Exception e) {
		logger.severe("E1: " + e.getMessage());
		e.printStackTrace();
	}

	@Override
	public void error(String str) {
		logger.severe("E2: " + str);
	}

	@Override
	public void error(int id, int errorCode, String errorMsg) {
		
		last_errorcode = errorCode;

		switch (errorCode) {
		
		case 165:
		//case 1100:	
		//case 2105:
		//case 2106:
		//case 2107:	
					logger.info("E3: " + id + " Error Code:" + errorCode + " " + errorMsg);
					return;
		default:			
					logger.severe("E3: " + id + " Error Code:" + errorCode + " " + errorMsg);
					return;
	}

		//2105/2106
		//if ( errorCode == 2105 )
			//dataBroken = true; 
		/*
		if (( errorCode == 2106 ) && ( dataBroken == true))  // data farm disconnect, request history data
		{
			logger.warning("request history data for missing period");
			String currentTime = IBDataFormatter.format(new Date());
			for ( int i = 0; i < TOTAL_SYMBOLS; i++)
			{
				id_history[i] += TOTAL_SYMBOLS;
				m_client.reqHistoricalData( id_history[i], contracts[i],  
		    			currentTime, "6000 S", "1 min", "MIDPOINT", 1, 1);	          
			}
			dataBroken = false;
		}

	
		if ( errorCode == 1100 )
			linkLost = true; 
		if (( errorCode == 1102 ) && ( linkLost == true))  // data farm disconnect, request history data
		{
			logger.warning("request history data for missing period");
			String currentTime = IBDataFormatter.format(new Date());
			for ( int i = 0; i < TOTAL_SYMBOLS; i++)
			{
				id_history[i] += TOTAL_SYMBOLS;
				m_client.reqHistoricalData( id_history[i], contracts[i],  
		    			currentTime, "6000 S", "1 min", "MIDPOINT", 1, 1);	          
			}
			linkLost = false;
		}*/

	
	
	}

	@Override
	public void accountDownloadEnd(String accountName)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void bondContractDetails(int reqId, ContractDetails contractDetails)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void contractDetails(int reqId, ContractDetails contractDetails)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void contractDetailsEnd(int reqId)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void currentTime(long time){
		String currentTime = IBDataFormatter.format(new Date( time*1000));
		logger.info("CurrnetTime:" + currentTime + " current trading currency:" + getRealTimeTradingPairs().toString() );

		for ( int i = 0; i < TOTAL_SYMBOLS; i++){
			if ( getRealTimeTradingPairs().contains(i)){
				logger.info("Request historical data " + instruments[i].getSymbol());
				//m_client.reqHistoricalData( instruments[i].getId_history(), instruments[i].getContract(),  
			    //	currentTime, "10 D", "5 mins", "MIDPOINT", 0, 1);	// 300 bars
				m_client.reqHistoricalData( instruments[i].getId_history(), instruments[i].getContract(),  
			    	currentTime, "25 D", "5 mins", "MIDPOINT", 0, 1);	// 300 bars
			}
		}
	}
	
	
	@Override
	public void deltaNeutralValidation(int reqId, UnderComp underComp)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void execDetails(int reqId, Contract contract, Execution execution)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void execDetailsEnd(int reqId)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void fundamentalData(int reqId, String data)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void historicalData(int reqId, String date, double open, double high, double low, double close,
			int volume, int count, double WAP, boolean hasGaps)
	{
		//System.out.println("id:" + reqId + " Date:" + date + " Open:" + open + " High" + high + " Low" + low + " Close:" + close + " Volumn:" + volume + " count:" + count + 
			//	" WAP:" + WAP + " hasGaps:" + hasGaps);
		//if (( SYSTEM_RUNNING_MODE == Constants.REAL_MODE ) && (save_realtime_data == true) && ( historySaved == false ))
		//	historyData.add(reqId + "," + date + "," + open + "," + high + "," + low + "," + close + "," + 	volume + "," + count + ","  + WAP + "," + hasGaps );

		for ( int i = 0; i < TOTAL_SYMBOLS; i++){
			if (reqId == instruments[i].getId_history()){
				if ( quotes_received[i] == false )  // this is to allow history data to be pulled again later
				{	
					if (date.indexOf("finished-") == -1){
						QuoteData data = new QuoteData(date, open, high, low, close, volume, count, WAP, hasGaps);
						instruments[i].createOrUpdateQuotes(data, Constants.CHART_5_MIN,Constants.CHART_15_MIN,Constants.CHART_60_MIN,Constants.CHART_240_MIN, Constants.CHART_DAILY,-1,-1,-1,-1);
					}
					else{	// last bar
						if (( SYSTEM_RUNNING_MODE == Constants.REAL_MODE ) || ( SYSTEM_RUNNING_MODE == Constants.SIGNAL_MODE )){ 
							quotes_received[i] = true;
							m_client.reqRealTimeBars(instruments[i].getId_realtime(), instruments[i].getContract(), 60, "MIDPOINT", true);
							if ( TICK_ENABLED == true )
								m_client.reqMktData(instruments[i].getId_ticker(), instruments[i].getContract(), null, false);
							logger.warning(instruments[i].getSymbol() + " started, " + instruments[i].getQuoteData(Constants.CHART_60_MIN).length + " data received");
						}
					}
				}
				break;
			}
		}
		
		if ( init_history == false ){
			boolean allReceived = true;
			for ( int j = 0; j < TOTAL_SYMBOLS; j++){
				if ( getRealTimeTradingPairs().contains(j)){
					if ( quotes_received[j] == false ){
						allReceived = false;
					}
				}
			}
			init_history = allReceived; 
		}
		
		// after all contract created, call update portfolio
		//if (( init_history == true ) && (save_realtime_data == true) && ( historySaved == false) && (( SYSTEM_RUNNING_MODE == Constants.REAL_MODE ) || ( SYSTEM_RUNNING_MODE == Constants.SIGNAL_MODE ))){
		//	saveHistoryData();
		//	historySaved = true;
		//	historyData = null;  // free memory
		//}		
		
		// end account update and get the result
		/*
		m_client.reqAccountUpdates(false,Constants.ACCOUNT);  		
		
		for ( int i = 0; i < NUM_STRATEGY; i++ ){
			strageties[i].removeTradeNotUpdated();
		}*/
	}

	
	@Override
	public void managedAccounts(String accountsList)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void nextValidId(int orderId)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void openOrder(int orderId, Contract contract, Order order, OrderState orderState)
	{
		// the only time to use this is to close all open orders
		// use any trade manager to cancel order
		//logger.info("Open Order:" + orderId + " currency:" + contract.m_currency + " symbol:" + contract.m_symbol + " OrderState:" + orderState);
		if (Constants.CMD_CLOSE.equals(updateOpenOrderCmd)){
			logger.info("final closing: cancel order " + orderId );
			if ( defaultAccountTradeManager != null ){
				for ( TradeManager2 tm :  defaultAccountTradeManager ){
					tm.cancelOrder(orderId);
				}
			}
		}
	}

	@Override
	public void openOrderEnd()
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void orderStatus(int orderId, String status, int filled, int remaining, double avgFillPrice,
			int permId, int parentId, double lastFillPrice, int clientId, String whyHeld)
	{
		if ("Filled".equals(status)) //&& (orderId > Constants.ORDER_ID_STOP_OFFSET))
		{
			logger.info("Order filled- orderId:" + orderId + " status:" + status + " filled:" + filled + " remaining:" + remaining + " avgFillPrice:" + avgFillPrice);
    		for ( int i = 0; i < NUM_STRATEGY; i++ ){
    			strageties[i].checkOrderFilled(orderId, status, filled, remaining, avgFillPrice, permId, parentId, lastFillPrice, clientId, whyHeld);
    		}
		}
		else{
			logger.info("Order Status:" + orderId + " status:" + status + " filled:" + filled + " remaining:" + remaining + " avgFillPrice:" + avgFillPrice);
		}
			
	}

	@Override
	public void realtimeBar(int reqId, long time, double open, double high, double low, double close,long volume, double wap, int count){

		realtimeBarCount++;
		if ( lastRealTimeUpdateTime == null ){
			lastRealTimeUpdateTime = new long[TOTAL_SYMBOLS];
			lastRealTimeRequestTime = new long[TOTAL_SYMBOLS];
			lastRealTimeUpdateNotificationTime = new long[TOTAL_SYMBOLS];
		}
		
		lastRealTimeUpdateTime[reqId-500]=time;
		lastRealTimeUpdate = time;
		
		String timeStr = IBDataFormatter.format(new Date(time*1000));
		//String hr = timeStr.substring(9,12).trim();
		//String min = timeStr.substring(13,15);
		//String sec = timeStr.substring(16,18);
		//int hour = new Integer(hr);
		//int second = new Integer(sec);
		//int minute = new Integer(min);

			
		Calendar calendar = Calendar.getInstance();
		calendar.setTime( new Date(time*1000) );
		int weekday = calendar.get(Calendar.DAY_OF_WEEK);
		int hour_of_day=calendar.get(Calendar.HOUR_OF_DAY);

		if( ( weekday == Calendar.FRIDAY ) && ( hour_of_day >= 10 ))
			friday_10 = true;
		if( ( weekday == Calendar.FRIDAY ) && ( hour_of_day >= 11 ))
			friday_11 = true;
		if( ( weekday == Calendar.FRIDAY ) && ( hour_of_day >= 12 ))
			friday_12 = true;
		if( ( weekday == Calendar.FRIDAY ) && ( hour_of_day >= 14 ))
			friday_14 = true;
		if( ( weekday == Calendar.FRIDAY ) && ( hour_of_day >= 15 ))
			friday_15 = true;
		if( ( weekday == Calendar.FRIDAY ) && ( hour_of_day >= 16 ))
			friday_16 = true;

		
		if ((( SYSTEM_RUNNING_MODE == Constants.REAL_MODE ) || ( SYSTEM_RUNNING_MODE == Constants.SIGNAL_MODE ) ) && ( save_realtime_data == true )){ 
			saveRealTimeData(reqId + "," + time + "," + open + "," + high + "," + low + "," + close + "," + volume + "," + wap + "," + count);
		}
		
		QuoteData data = new QuoteData(timeStr, time*1000, open, high, low, close, 0, 0, wap, false);
		
		for ( int i = 0; i < TOTAL_SYMBOLS; i++){
			if (reqId == instruments[i].getId_realtime()){
				instruments[i].createOrUpdateQuotes(data, Constants.CHART_5_SEC, Constants.CHART_1_MIN, Constants.CHART_5_MIN, Constants.CHART_15_MIN,Constants.CHART_60_MIN,Constants.CHART_240_MIN, Constants.CHART_DAILY,-1,-1 );
				break;
			}
		}
		
		for ( int i = 0; i < NUM_STRATEGY; i++ ){
			strageties[i].process(reqId, data);
		}
	}

	
	@Override
	public void receiveFA(int faDataType, String xml)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void scannerData(int reqId, int rank, ContractDetails contractDetails, String distance,
			String benchmark, String projection, String legsStr)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void scannerDataEnd(int reqId)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void scannerParameters(String xml)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void tickEFP(int tickerId, int tickType, double basisPoints, String formattedBasisPoints,
			double impliedFuture, int holdDays, String futureExpiry, double dividendImpact,
			double dividendsToExpiry)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void tickGeneric(int tickerId, int tickType, double value)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void tickPrice(int tickerId, int field, double price, int canAutoExecute)
	{
		//1 = bid
		//2 = ask
		//4 = last
		//6 = high 
		//7 = low
		//9 = close
		//System.out.println("Ticket id:" + tickerId + " field:" + field + " price:" + price);
		
		for ( int i = 0; i < TOTAL_SYMBOLS; i++){
			if (tickerId == instruments[i].getId_ticker()){
				if ( field == 1 ){
					instruments[i].createOrUpdateQuotesByTicker( Constants.CHART_5_MIN, price, -1);
					instruments[i].createOrUpdateQuotesByTicker( Constants.CHART_15_MIN, price, -1);
					instruments[i].createOrUpdateQuotesByTicker( Constants.CHART_60_MIN, price, -1);
					instruments[i].createOrUpdateQuotesByTicker( Constants.CHART_240_MIN, price, -1);
					//instruments[i].createOrUpdateQuotesByTicker( Constants.CHART_DAILY, price, -1);
				}
				else if ( field == 2 ){
					instruments[i].createOrUpdateQuotesByTicker( Constants.CHART_5_MIN, -1, price);
					instruments[i].createOrUpdateQuotesByTicker( Constants.CHART_15_MIN, -1, price);
					instruments[i].createOrUpdateQuotesByTicker( Constants.CHART_60_MIN, -1, price);
					instruments[i].createOrUpdateQuotesByTicker( Constants.CHART_240_MIN, -1, price);
				}
			}
		}

		if ((( field == 1 ) ||  (field == 2) || (field == 4)) && ( price > 0 )) // 1 bid, 2 ask  sometime I see price is -1.0
		{
			/*
			for (  int i = 0; i < TOTAL_SYMBOLS; i++ )
			{
				if ( tickerId == tradeManager[i].tickId ) 
				{
					if ( tradeManager[i].trade != null )
					{	
						if (!timeout && (tradeManager[i].trade.status.equals(Constants.STATUS_DETECTED)))
						{
							switch ( field )
							{
								case 1: tradeManager[i].lastTick_bid = price;
										tradeManager[i].lastTick = Constants.TICK_BID;
										if (Constants.ACTION_BUY.equals(tradeManager[i].trade.action))
										{
											//tradeManager[i].trackPullBackTradeBuy( null, price);
										}
										break;
								case 2: tradeManager[i].lastTick_ask = price;
										tradeManager[i].lastTick = Constants.TICK_ASK;
										if (Constants.ACTION_SELL.equals(tradeManager[i].trade.action))
										{
											//tradeManager[i].trackPullBackTradeSell( null, price);
										}
										break;
							}
						}
						// we do not use ticker data to trigger close
					}
				}
			}*/
		}

		lastTickUpdateTime = new Date().getTime();

	}

	
	@Override
	public void tickSize(int tickerId, int field, int size)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void tickSnapshotEnd(int reqId)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void tickString(int tickerId, int tickType, String value)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateAccountTime(String timeStamp)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateAccountValue(String key, String value, String currency, String accountName)
	{
		// TODO Auto-generated method stub
		if ("NetLiquidation".equals(key)){
			logger.warning("AccountName:" + accountName + " open balance is " + value + " " + currency);
		}
		
	}

	@Override
	public void updateMktDepth(int tickerId, int position, int operation, int side, double price, int size)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateMktDepthL2(int tickerId, int position, String marketMaker, int operation, int side,
			double price, int size)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateNewsBulletin(int msgId, int msgType, String message, String origExchange)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updatePortfolio(Contract contract, int position, double marketPrice, double marketValue,
			double averageCost, double unrealizedPNL, double realizedPNL, String accountName)
	{
		logger.info("Portfolio update:" + accountName + " " + contract.m_conId + " " + contract.m_symbol + "." + contract.m_currency + " position size: " + position );
		
		if ( updatePortfolioCmd == Constants.CMD_UPDATE )
		{
			/*
			for ( int i = 0; i < TOTAL_SYMBOLS; i++)
			{
		    	//contract.m_conId = connectionId++;
		    	//contract.m_symbol = symbol;
		        //contract.m_secType = type;
		        //contract.m_exchange = exchange;
		        //contract.m_currency = currency;
				if ( contract.m_symbol.equals(tradeManager[i].m_contract.m_symbol) && contract.m_currency.equals(tradeManager[i].m_contract.m_currency)) 
				{
					if ( tradeManager[i].trade != null )
					{
						if (( position > 0 ) && tradeManager[i].trade.action.equals(Constants.ACTION_BUY))
						{
							tradeManager[i].trade.positionUpdated = true;
							if (tradeManager[i].trade.status.equals( Constants.STATUS_PLACED))
							{	
								if ( position == tradeManager[i].trade.remainingPositionSize )
								{
									logger.warning(tradeManager[i].symbol + " " + tradeManager[i].CHART + " " + tradeManager[i].trade.action + " position updated " + position );
								}
								else
								{
									logger.warning(tradeManager[i].symbol + " " + tradeManager[i].CHART + " " + tradeManager[i].trade.action + " position sized adjusted to " + Math.abs(position) );
									tradeManager[i].trade.remainingPositionSize = position;
								}
							}
							else if (tradeManager[i].trade.status.equals( Constants.STATUS_OPEN))
							{
								logger.warning(tradeManager[i].symbol + " " + tradeManager[i].CHART + " " + tradeManager[i].trade.action + " position filled " + Math.abs(position) );
								tradeManager[i].trade.remainingPositionSize = position;
								tradeManager[i].trade.status = Constants.STATUS_PLACED;
							}
						}
						else if (( position < 0 ) && tradeManager[i].trade.action.equals(Constants.ACTION_SELL))
						{
							tradeManager[i].trade.positionUpdated = true;
							if (tradeManager[i].trade.status.equals( Constants.STATUS_PLACED))
							{	
								if ( Math.abs(position) == tradeManager[i].trade.remainingPositionSize )
								{
									logger.warning(tradeManager[i].symbol + " " + tradeManager[i].CHART + " " + tradeManager[i].trade.action + " position updated " + Math.abs(position) );
								}
								else
								{
									logger.warning(tradeManager[i].symbol + " " + tradeManager[i].CHART + " " + tradeManager[i].trade.action + " position sized adjusted to " + Math.abs(position) );
									tradeManager[i].trade.remainingPositionSize = Math.abs(position);
								}
							}
							else if (tradeManager[i].trade.status.equals( Constants.STATUS_OPEN))
							{
								logger.warning(tradeManager[i].symbol + " " + tradeManager[i].CHART + " " + tradeManager[i].trade.action + " position filled " + Math.abs(position) );
								tradeManager[i].trade.remainingPositionSize = Math.abs(position);
								tradeManager[i].trade.status = Constants.STATUS_PLACED;
							}
						}
						else if ( position == 0 )
						{
							logger.warning(tradeManager[i].symbol + " " + tradeManager[i].CHART + " " + tradeManager[i].trade.action + " position no longer exist, remove trade");
							tradeManager[i].cancelAllOrders();
							tradeManager[i].trade = null;
						}
						else
						{
							logger.severe(tradeManager[i].symbol + " " + tradeManager[i].CHART + " position update does not match, remove trade"  );
							tradeManager[i].cancelAllOrders();
							tradeManager[i].trade = null;
						}
					}
					else
					{
						logger.warning(tradeManager[i].symbol + " " + tradeManager[i].CHART + "  trade DID NOT FOUND");
					}
				}

			}*/
		}
		else if (Constants.CMD_CLOSE.equals(updatePortfolioCmd)){
			// close each position
			if ( position != 0 ){ 
				logger.warning("Close this position: " + accountName + " " + "Contract:" + contract.m_symbol + "." + contract.m_currency + " position size: " + position );
				contract.m_exchange = "IDEALPRO";
				
				TradeManager2 closePositionTradeMgr = getDefaultAccount( accountName );
				if ( closePositionTradeMgr != null ){
					logger.warning("Send close order: " + Math.abs(position) );
					closePositionTradeMgr.closePosition( contract, position>0?Constants.ACTION_SELL:Constants.ACTION_BUY, Math.abs(position));
				}

				/*
				for ( TradeManager2 tradeManager: defaultAccountTradeManager){
					if (tradeManager.IB_ACCOUNT.equals(accountName)){
						logger.warning("Send close order: " + Math.abs(position) );
						tradeManager.closePosition( contract, position>0?Constants.ACTION_SELL:Constants.ACTION_BUY, Math.abs(position));
						//this.getDefaultAccount(accountName).closePosition( contract, position>0?Constants.ACTION_SELL:Constants.ACTION_BUY, Math.abs(position));
					}
				}*/
			}
		}
	}

	@Override
	public void connectionClosed()
	{
		// TODO Auto-generated method stub
		
	}


	private void createInstrument(int id, String symbol, String type, String exchange, String currency, double pip_size, int priceType, int region1, int region2 ){
		int idHist = id;
		int idReal = id + 500;
		
		Contract contract = new Contract();
		contract.m_conId = id;//conId;
		contract.m_symbol = symbol;
		contract.m_secType = type;
		contract.m_exchange = exchange;
		contract.m_currency = currency;
       
   		instruments[id] = new Instrument(id, contract, idHist, idReal, pip_size, priceType, region1, region2);
	}

	
	public void createNewQuoteEntry( Vector<QuoteData> quotes, QuoteData data )
	{
		if ( quotes.size() == 0 )
		{
			data.pos = 0;
			quotes.add( data );
		}
		else
		{
			data.pos = quotes.lastElement().pos + 1;
			quotes.add(data);
		}
		
	}
	

	private void updateLastQuoteEntry( Vector<QuoteData> quotes, QuoteData data )
	{
		if ( quotes.size() > 0 )
		{
			QuoteData lastElem = (QuoteData) quotes.lastElement();
			
			if (data.high > lastElem.high)
				lastElem.high = data.high;
			if (data.low < lastElem.low )
				lastElem.low = data.low;
			lastElem.close = data.close;
			//lastElem.time = data.time; do not over ride the time
		}
	}
	
	


	public void ReplaceHourlyQuotes( HashMap<String, QuoteData> quotes, int CHART, final QuoteData data, boolean replaceOnly )
	{
		String indexStr = data.time.substring(0,13) + "00";
		
		QuoteData qdata = quotes.get(indexStr);
		
		if (( qdata == null ) || ( replaceOnly == true ))
		{
			qdata = new QuoteData();
			qdata.time = indexStr;
		}

		qdata.open = data.open;
		qdata.high = data.high;
		qdata.low = data.low;
		qdata.close = data.close;
		
		quotes.put( indexStr, qdata);
	}

	
	private void initialize(){
		firstQuote = new boolean[TOTAL_SYMBOLS];
    	quotes_received = new boolean[TOTAL_SYMBOLS];
		//instruments = new Instrument[TOTAL_SYMBOLS];
		instruments = new Instrument[ALL_SYMBOLS];
	}
	
	
	private void saveHistoryData()
	{
		try {
			//logger.info("Save History Data:" + saveHistoryFile);

			FileWriter fw = new FileWriter(saveHistoryFile);
			Iterator<String> it = historyData.iterator();
			
			while ( it.hasNext()){
				String h = (String)it.next();
				fw.append(h + "\n");
			}
			
			fw.close();
		} 
		catch (IOException e){
			logger.severe(e.getMessage());
			e.printStackTrace();
		}
	}
	
	private void saveRealTimeData(String realTimeData )
	{
		try {
			//logger.info("Save Realtime Data:" + saveRealTimeFile);

			FileWriter fw = new FileWriter(saveRealTimeFile, true );
			fw.append(realTimeData + "\n");
			fw.close();
		} 
		catch (IOException e){
			logger.severe(e.getMessage());
			e.printStackTrace();
		}
	}
	
	
	
	private void readHistoryData( String historyFileName )  throws FileNotFoundException, IOException
	{
		String reqId;
		String date;
		String open;
		String high;
		String low;
		String close;
		String volume;
		String count;
		String WAP;
		String hasGaps;
		
		//csv file containing data
		String strFile = historyFileName;
		BufferedReader br = new BufferedReader( new FileReader(strFile));

		String strLine = "";
		StringTokenizer st = null;
		int lineNumber = 0;
	
		//read comma separated file line by line
		while( (strLine = br.readLine()) != null)
		{
			lineNumber++;
			//break comma separated line using ","
			st = new StringTokenizer(strLine, ",");
			
			//System.out.println(strLine);
			//while(st.hasMoreTokens()) 
			{
				reqId = st.nextToken();
				date = st.nextToken();
				if (date.indexOf("finished-") != -1)
					//date = date.substring(9);
					continue;
				open = st.nextToken();
				high = st.nextToken();
				low = st.nextToken();
				close = st.nextToken();
				//close = close.substring(0, close.length()-2);
				volume = st.nextToken();
				count = st.nextToken();
				WAP = st.nextToken();
				hasGaps = st.nextToken();
			}
			
		historicalData(new Integer(reqId), date, new Double(open), new Double(high), new Double(low), new Double(close),
					new Integer(volume), new Integer(count), new Double(WAP), new Boolean(hasGaps));

		}
		
		logger.warning(lineNumber + " history read");
	}

	
	
	private void readHistoryData2( String historyFileName ) throws FileNotFoundException, IOException
	{
		String reqId;
		String time, date;
		String open;
		String high;
		String low;
		String close;
		String volume;
		String count;
		String WAP;
		String hasGaps;
		
		
		//csv file containing data
		String strFile = historyFileName;
		BufferedReader br = new BufferedReader( new FileReader(strFile));

		String strLine = "";
		StringTokenizer st = null;
		int lineNumber = 0;
	
		//read comma separated file line by line
		while( (strLine = br.readLine()) != null)
		{
			lineNumber++;
			//break comma separated line using ","
			st = new StringTokenizer(strLine, ",");
			
			//System.out.println(strLine);
			//while(st.hasMoreTokens()) 
			{
				reqId = st.nextToken();
				time = st.nextToken();
				if (time.indexOf("finished-") != -1)
					continue;
				date = IBDataFormatter.format( 1000L * new Integer(time));
				open = st.nextToken();
				high = st.nextToken();
				low = st.nextToken();
				close = st.nextToken();
				volume = st.nextToken();
				count = st.nextToken();
				WAP = st.nextToken();
				hasGaps = st.nextToken();
			
			}
			
		historicalData(new Integer(reqId)-500, date, new Double(open), new Double(high), new Double(low), new Double(close),
					new Integer(volume), new Integer(count), new Double(WAP), new Boolean(hasGaps));

		}
		
		logger.warning("History file:"+historyFileName + " " + lineNumber + " history read");
			
	}
	
	private void readRealTimeData( String parmId, String realTimeFileName, Date realTimeStartDate  ) throws FileNotFoundException, IOException
	{
		String reqId, time, open, high, low, close,volume,wap, count;
		Vector<QuoteData> rtDatas = new Vector<QuoteData>();
		
		//csv file containing data
		String strFile = realTimeFileName;
		BufferedReader br = new BufferedReader( new FileReader(strFile));

		String strLine = "";
		StringTokenizer st = null;
		int lineNumber = 0;
	
		//read comma separated file line by line
		while( (strLine = br.readLine()) != null)
		{
			lineNumber++;
			//break comma separated line using ","
			st = new StringTokenizer(strLine, ",");
			
			//while(st.hasMoreTokens()) 
			reqId = st.nextToken();
			time = st.nextToken();
			open = st.nextToken();
			high = st.nextToken();
			low = st.nextToken();
			close = st.nextToken();
			//volume = st.nextToken();
			//wap = st.nextToken();
			//count = st.nextToken();
			
			// filter by parmSymbol
			
			if (( parmId != null ) && (parmId.length() == 3)){
				if (!parmId.equals(reqId))
				continue;
			}else{
				boolean tradingSymbol = false;
				
				for ( Integer reqid : getRealTimeTradingPairs() ){
					if ( ( reqid.intValue() + 500 ) == new Integer ( reqId).intValue()){
						tradingSymbol = true;
						break;
					}
				}
				if ( ! tradingSymbol )
					continue;
			}
				
			if ( time.indexOf("finished-") == -1 ){
				//realtimeBar(new Integer(reqId), new Long(time), new Double(open), new Double(high), new Double(low), new Double(close),
				//	new Long(0), new Double(0), new Integer(0));
				QuoteData data = new QuoteData(new Integer(reqId), new Long(time),new Double(open),new Double(high), new Double(low), new Double(close) );
				rtDatas.add( data);
			}
		}
		
		logger.warning("Realtime:" + realTimeFileName + " " + lineNumber + " real time data read");
		logger.warning("parmId = " + parmId);
		
		Object[] qts = rtDatas.toArray();
	    Arrays.sort(qts);
		QuoteData[] quotes = Arrays.copyOf(qts, qts.length, QuoteData[].class);
		
		int length = quotes.length - 1;
		//logger.warning("length = " + length);
		long realTimeStartTime = realTimeStartDate.getTime()/1000;
		logger.info("real time start date: "+ realTimeStartDate + " " + realTimeStartDate.getTime() );
		for ( int i = 0; i <= length; i++){
			
			/*if ((i > 0) && (( quotes[i].timeInMillSec - quotes[i-1].timeInMillSec ) > 5 * 60000 )){
				System.out.println("Data disconnect detected " + new Date(quotes[i-1].timeInMillSec) + " " + new Date(quotes[i].timeInMillSec));
			}
			else*/
			if ( quotes[i].timeInMillSec > realTimeStartTime){
				realtimeBar(quotes[i].req_id, quotes[i].timeInMillSec, quotes[i].open, quotes[i].high, quotes[i].low, quotes[i].close, 
					new Long(0), new Double(0), new Integer(0));
			}
		}

		logger.warning(lineNumber + " real time data processed");
		
	}


	@Override
	public void tickOptionComputation(int tickerId, int field,
			double impliedVol, double delta, double modelPrice,
			double pvDividend) {
		// TODO Auto-generated method stub
		
	}

	
	public void checkDataBroken()
	{
		String currentTime = IBDataFormatter.format(new Date());
		//logger.warning("request history data update - currentTime:" + currentTime);

		/*
		if ( init_quote_update == false )
		{
			/*
			for ( int i = 0; i < TOTAL_SYMBOLS; i++)
			{
				id_history[i] += TOTAL_SYMBOLS;
				m_client.reqHistoricalData( id_history[i], contracts[i],  
						currentTime, "20 D", "1 hour", "MIDPOINT", 0, 1);
			}*/
			
		/*	for ( int i = 0; i < TOTAL_SYMBOLS; i++)
			{
				id_history[i] += TOTAL_SYMBOLS;
				QuoteData[] qts5 = instruments[i].getQuoteData(Constants.CHART_5_MIN);
				String earliestTime = qts5[0].time + ":00";
				m_client.reqHistoricalData( id_history[i]++, contracts[i],  
						earliestTime, "10 D", "5 mins", "MIDPOINT", 0, 1); // this gives 20 day history data
			}
			
			
			init_quote_update = true;
		}
		else*/
		{	
			Set<Integer> totalRealTimeTradingPairs = getRealTimeTradingPairs();
			for ( int i = 0; i < TOTAL_SYMBOLS; i++){
				if ( totalRealTimeTradingPairs.contains(i)){ 
				//id_history[i] += TOTAL_SYMBOLS;
	
				//System.out.println(tradeManager[i][TIME_PERIOD].symbol + " request history data update id:" + id_history[i] + " - currentTime:" + currentTime);

				instruments[i].setId_history(instruments[i].getId_history() + TOTAL_SYMBOLS);
				
				m_client.reqHistoricalData( instruments[i].getId_history(),  instruments[i].getContract(), 
						//currentTime, "7200 S", "1 hour", "MIDPOINT", 0, 1);	// 2 hour
						currentTime, "21600 S", "1 min", "MIDPOINT", 0, 1);	// 6 hour
						//currentTime, "20 D", "1 hour", "MIDPOINT", 0, 1);
						//currentTime, "10800 S", "1 hour", "MIDPOINT", 0, 1);	// 3 hour
				
				firstQuote[i] = true;   // to avoid first quote
				}
			}
		}
	}

	
	public void sleep ( double min )
	{
		try
		{
			//Thread.sleep(min*60000);
			Thread.sleep((int)(60000 * min));
		}
		catch ( Exception e)
		{
			e.printStackTrace();
		}
	}

	
	protected void finalize() throws Throwable
	{
	    m_client.eDisconnect();
	} 	



	public String loadStatusHeadline()
	{
	    String headline_fineName = "./Status_Headline.txt";

	    StringBuffer sb = new StringBuffer();
		try 
        { 
	   		File f = new File(headline_fineName);
	   		FileReader fr = new FileReader(f);
			BufferedReader br = new BufferedReader( fr ); 

			String line = null; 
             
            while((line = br.readLine()) != null){
            	sb.append(line + "\n");
			}
            fr.close();	
            
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
		
		return sb.toString();
	}


	public boolean  isNoReverse(String symbol )
	{
		if (( noReverseSymbols != null ) && (( noReverseSymbols.toLowerCase().indexOf(symbol.toLowerCase())!= -1) || noReverseSymbols.equalsIgnoreCase("all")))
			return true;
		
		return false;
	}

	/*
	private String getTradeList()
	{
    	StringBuffer TradeOpp = new StringBuffer();
		for ( int i = 0; i < TOTAL_SYMBOLS; i++)
		{
			Trade trade = tradeManager[i].getTrade();
			if (( trade != null ) && trade.status.equals(Constants.STATUS_DETECTED))
			{
				//TradeOpp.append(tradeManager[i].symbol + " detected " + tradeManager[i].trade.detectTime + " " + tradeManager[i].trade.action + "\n" );
				TradeOpp.append(getTradeEvents(trade));
			}
		}
			
		TradeOpp.append("\n");
			
		for ( int i = 0; i < TOTAL_SYMBOLS; i++)
		{
			Trade trade = tradeManager[i].getTrade();
			if (( trade != null ) && trade.status.equals(Constants.STATUS_PLACED))
			{
				//TradeOpp.append(tradeManager[i].symbol + " detected " + tradeManager[i].trade.detectTime + " " + tradeManager[i].trade.action + " placed " + tradeManager[i].trade.entryTime + "\n");
				TradeOpp.append(getTradeEvents(trade));
			}
		}
		
		return TradeOpp.toString();
	}*/
	
	/*

	public void removeTrade(String strategy, String symbol )
	{
		System.out.println("Remove Trade from GUI received " + strategy + " " + symbol);

		for ( int i = 0; i < NUM_STRATEGY; i++ )
		{
			if (strageties[i].STRATEGY_NAME.equals(strategy))
			{
				strageties[i].removeTrade(symbol);
				break;
			}
		}
		
	}*/
	
	public void addTrade(String strategy, Trade trade )
	{
		System.out.println("Adde Trade from GUI received " + strategy + " " + trade.symbol);

		for ( int i = 0; i < NUM_STRATEGY; i++ )
		{
			if (strageties[i].STRATEGY_NAME.equals(strategy))
			{
				strageties[i].addTrade(trade);
				break;
			}
		}
		
	}
	
	
	public void printOutQuote(){
		
		//for ( int i = 0; i < TOTAL_SYMBOLS; i++)
		{
			int i = 12;
			QuoteData[] quotes60 = instruments[i].getQuoteData(Constants.CHART_60_MIN);
			int lastbar = quotes60.length -1;
			System.out.println(instruments[i].getSymbol());
			for ( int j = 0; j <= lastbar; j++)
				System.out.println(quotes60[j]);
		}
	
	}
	
	
	public void restartApplication()
	{
		try {
			
			String restartProgram = "cmd.exe /c start " + setting.props.getProperty("Program");
			logger.info("restart program:" + restartProgram);
			Process p =  Runtime.getRuntime().exec(restartProgram) ;
			
			//Process p =  Runtime.getRuntime().exec("cmd.exe /c start C:/gdrive/T_beta/LOAD_SFTEST_REAL_QTEST452_7496.bat") ;
	  	  	System.exit(0);
	    } 
		catch (IOException ex) {
			logger.warning(ex.toString());
	    }
		
	}
	
	private Set<Integer> getRealTimeTradingPairs(){
		if ( REAL_TIME_TRADING_PAIRS == null ){
			REAL_TIME_TRADING_PAIRS = new HashSet<Integer>();
			for ( int i = 0; i < NUM_STRATEGY; i++ ){
				REAL_TIME_TRADING_PAIRS.addAll(strageties[i].TRADING_SYMBOLS);
			}
		}
		return REAL_TIME_TRADING_PAIRS;
	}
	
	public void addAccount( String account ){
		ib_accounts.add(account);
	}

	public void resetParam(){
		for ( int i = 0; i < NUM_STRATEGY; i++ ){
			strageties[i].removeAllTrades();
		}

		friday_10 = friday_11= friday_12= friday_14 = friday_15 = friday_16=false;

	}
}

