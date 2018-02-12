package tao.trading.strategy;


import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

import tao.trading.Constants;
import tao.trading.QuoteData;

import com.ib.client.Contract;
import com.ib.client.ContractDetails;
import com.ib.client.EClientSocket;
import com.ib.client.EWrapper;
import com.ib.client.Execution;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.UnderComp;

// mainly test trend with low attitude
public class SFHistoryWeekly2 implements EWrapper
{
	String IB_ACCOUNT = "DU31237";
//	Logger logger = Logger.getLogger("SFHistory");
	protected EClientSocket m_client = new EClientSocket(this);
	protected SimpleDateFormat IBDataFormatter = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
	protected static SimpleDateFormat DayOnlyFormatter = new SimpleDateFormat("yyyyMMdd");
	protected SFHistoryTradeManager[][] tradeManager;
	TradeManager closeAccountTradeManager;
	Contract[] contracts;
	int[] id_history;
	boolean[] c; 
	int[] id_realtime;
	boolean[] id_realtime_started;
	boolean[] quoteFirstTime;
	boolean[] quotes_received;
	int totalSymbolIndex = 0;
	int connectionId = 100;
	String IB_ID;
	boolean timeout= false;
	boolean closeAccount = false;
	int TOTAL_SYMBOLS = 11;
	int TOTAL_TRADES = 7;
	int TRADE_1M = 0;
	int TRADE_3M = 1;
	int TRADE_5M = 2;
	int TRADE_10M = 3;
	int TRADE_15M = 4;
	int TRADE_30M = 5;
	int MODE = Constants.REAL_MODE;
	Vector<String> historyData = new Vector<String>();
	HashMap<String, Double> exchangeRate = new HashMap<String, Double>();
	//String realTimeFile = "C:/trade/Data/101/realtimeData.csv";
	//String historyFile = "C:/trade/Data/101/historyData.csv";
	
	boolean historySaved;
	boolean historyCompleted;
	int historyType;
	int numOfDays;
	//  args[0] = TWD port
	//  args[1] = id
	//  args[2] = trade selection

	//String historyPath="C:/gdrive/T_test/data/history_weekly2/";

	public static void main(String[] args)
	{
		int port = new Integer(args[0]);
		String ib_id = args[1];
		String trademarket = args[2];
		String historyReqDate = args[3];
		int numOfDays = new Integer(args[4]);
		//String historyReqDate1 = args[4];
		String historyPath = args[5];

		SFHistoryWeekly2 open = new SFHistoryWeekly2(port, ib_id, trademarket);
       	open.run( trademarket, historyReqDate, numOfDays, historyPath );
	}

	
	public void run( String trade_market, String historyReqDate, int numOfDays,/*String historyReqDate1,*/ String historyPath) {
		
		try
		{
			Date date = DayOnlyFormatter.parse(historyReqDate);
			//Date date1 = DayOnlyFormatter.parse(historyReqDate1);
			//numOfDays = (int)( (date1.getTime() - date.getTime())/ (1000 * 60 * 60 * 24) ) + 1;
			this.numOfDays = numOfDays;
			
			Calendar cal = Calendar.getInstance();
			cal.setTime(date);
			if ( cal.get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY){
				System.out.println("Error: ending data is not a sunday, please correct...");
				System.exit(0);
			}


			// DOWNLOAD HISTORY DATA
			//String currentTime = historyReqDate + " 01:30:00";
			/*String currentTime = historyReqDate + " 17:00:00";
			historyCompleted = false;
			quotes_received = new boolean[TOTAL_SYMBOLS];
			System.out.println("Request 20 D 15 min historical data from "+ currentTime );
			for ( int i = 0; i < TOTAL_SYMBOLS; i++){
				quotes_received[i] = false;
		    	m_client.reqHistoricalData( id_history[i], contracts[i],  
		    			//currentTime, "86400 S", "1 min", "MIDPOINT", 0, 1);	              
    					//currentTime, "10 D", "5 mins", "MIDPOINT", 0, 1);	              
    					currentTime, "20 D", "15 mins", "MIDPOINT", 0, 1);	              
			}
			
			// only save when everything is in place
			while ( historyCompleted == false )
					Thread.sleep(60000);	// this is for trade to exits

			String historyFile = historyPath + "historyData_" +trade_market + "_" +  historyReqDate + ".csv";
			System.out.println("save history file:" + historyFile);
			saveHistoryData(historyFile);
			
			System.out.println("History download completed");
			historyData.clear();
			Thread.sleep( 60000 );   // sleep 4 minute
			*/
			
			
			historyType = 2;
			String currentTime = historyReqDate + " 17:00:00";
			String startDate = DayOnlyFormatter.format( IBDataFormatter.parse(currentTime).getTime() - 60000L * 60 * 24 * numOfDays );
		
			historyCompleted = false;
			//historyData.clear();
			
			for ( int i = 0; i < TOTAL_SYMBOLS; i++)
			{
				quotes_received[i] = false;
				// TOCHANGE 4
		    	m_client.reqHistoricalData( id_realtime[i], contracts[i],  
		    		//	currentTime, "14400 S", "15 secs", "MIDPOINT", 0, 2);	// 4 hours 6 x 60 x 60 =               
		    		//	currentTime, "23400 S", "15 secs", "MIDPOINT", 0, 2);	// 6.5 hours 6.5 x 60 x 60 =               
		    		//  currentTime, "55800 S", "30 secs", "MIDPOINT", 0, 2);	// 7.5 hours 7.5 x 60 x 60 = 27000 sec              
			    	//	currentTime, "41400 S", "30 secs", "MIDPOINT", 0, 2);	// 11.5 hours  1:30-13:00              
		    		//	currentTime, "79200 S", "30 secs", "MIDPOINT", 0, 2);	// 22 hours    18:00 - 16:00  22 * 60 * 60 = 79200 sec             
		    	    //	currentTime, "66600 S", "30 secs", "MIDPOINT", 0, 2);	// 12.5 hours  0:30-13:00  = 45000             
	    			//	currentTime, "14400 S", "15 secs", "MIDPOINT", 0, 2);	// 15.5 hour   7:30 - 23:00 = 55800 sec
		    																	// 18.5 hour   6:30pm - 13:00pm = 66600 sec  	
    					//currentTime, "5 D",     "1 min", "MIDPOINT", 0, 2);
    					currentTime, numOfDays + " D",  "1 min", "MIDPOINT", 0, 2);
				System.out.println("Request " + id_realtime[i] + " Currenttime: " + currentTime + " No.Days:" + numOfDays + " 1 min");
				Thread.sleep( 5000 );   // sleep 4 minute
			}
		
			// only save when everything is in place
			while ( historyCompleted == false )
				Thread.sleep(60000);	// this is for trade to exits

			System.out.println("Real time download finished");

			Thread.sleep( 30000 );   // sleep 30 sec
			
			String realtimeFile = historyPath + "realtimeData_" +trade_market + "_" +  startDate/*historyReqDate*/ + ".csv";
			System.out.println("save realtime file:" + realtimeFile);
			saveHistoryData(realtimeFile);
	
			System.out.println("Realtime download completed");

			//   Making identical historical data requests within 15 seconds;
			//   Making six or more historical data requests for the same Contract, Exchange and Tick Type within two seconds.
			//   history limitation: Do not make more than 60 historical data requests in any ten-minute period.
			// TOCHANGE 5  ( to speed up )
			//Thread.sleep( 60000 * 10);   // sleep 10 minute
			//Thread.sleep( 60000 * 5 );   // sleep 5 minute
			Thread.sleep( 60000 );   // sleep 1 minute

			m_client.eDisconnect();

			System.out.println("disconnected from TradeStation");
			
			System.exit(0);
			
		}
		catch( Exception e)
		{
			e.printStackTrace();
		}
	
	}

	
	
	public SFHistoryWeekly2( int port, String ib_id, String trade_market )
	{
		// create a logger
		/*
		try
		{
			
			// this logger logs everything
			boolean append = true;

			FileHandler handler = new FileHandler("history.log", append);
			handler.setFormatter(new SimpleFormatter());

			// this logger creates trade summary
			/*FileHandler handler2 = new FileHandler(trade_market + "_101_summary.log", append);
			handler2.setLevel(Level.WARNING);
			handler2.setFormatter(new SimpleFormatter());*/

			// Add to the desired logger
/*			logger = Logger.getLogger("History");
			logger.addHandler(handler);
			//logger.addHandler(handler2);
			
		} 
		catch (IOException e)
		{
			//System.out.println(e.getMessage());
			e.printStackTrace();
		}*/

	   	IB_ID = ib_id;
	   	
	   	exchangeRate.put("EUR",0.787373);
	   	exchangeRate.put("GBP",0.643396);
	   	exchangeRate.put("CHF",1.025718);
	   	exchangeRate.put("CAD",1.058030);
	   	exchangeRate.put("AUD",1.127114);
	   	exchangeRate.put("NZD",1.422731);
	   	exchangeRate.put("JPY",84.59720);
	   	exchangeRate.put("USD",1.0);
	   	
		id_history = new int[30];//{0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23};
		id_realtime = new int[30];//]{500,501,502,503,504,505,506,507,508,509,510,511,512,513,514,515,516,517,518,519,520,521,522,523};

		TOTAL_SYMBOLS = 22;
		initialize();

		createContract(  0, 500, "EUR", "CASH", "IDEALPRO", "USD", true,  0, 0.0001, 20, Constants.PRICE_TYPE_4,0);
		createContract(  1, 501, "EUR", "CASH", "IDEALPRO", "JPY", true,  0,   0.01, 20, Constants.PRICE_TYPE_2,0);
		createContract(  2, 502, "EUR", "CASH", "IDEALPRO", "CAD", true,  0, 0.0001, 20, Constants.PRICE_TYPE_3,0);
		createContract(  3, 503, "EUR", "CASH", "IDEALPRO", "GBP", true,  0, 0.0001, 20, Constants.PRICE_TYPE_4,0);
		createContract(  4, 504, "EUR", "CASH", "IDEALPRO", "CHF", true,  0, 0.0001, 20, Constants.PRICE_TYPE_4,0);
		createContract(  5, 505, "EUR", "CASH", "IDEALPRO", "NZD", false, 0, 0.0001, 20, Constants.PRICE_TYPE_3,0);
		createContract(  6, 506, "EUR", "CASH", "IDEALPRO", "AUD", false, 0, 0.0001, 20, Constants.PRICE_TYPE_4,0);
		createContract(  7, 507, "GBP", "CASH", "IDEALPRO", "AUD", false, 0, 0.0001, 22, Constants.PRICE_TYPE_4,0);
		createContract(  8, 508, "GBP", "CASH", "IDEALPRO", "NZD", false, 0, 0.0001, 22, Constants.PRICE_TYPE_3,0);
		createContract(  9, 509, "GBP", "CASH", "IDEALPRO", "USD", true,  0, 0.0001, 22, Constants.PRICE_TYPE_4,0);
		createContract( 10, 510, "GBP", "CASH", "IDEALPRO", "CAD", true,  0, 0.0001, 22, Constants.PRICE_TYPE_3,0);
		createContract( 11, 511, "GBP", "CASH", "IDEALPRO", "CHF", true,  0, 0.0001, 22, Constants.PRICE_TYPE_4,0);
		createContract( 12, 512, "GBP", "CASH", "IDEALPRO", "JPY", true,  0,   0.01, 22, Constants.PRICE_TYPE_2,0);
		createContract( 13, 513, "CHF", "CASH", "IDEALPRO", "JPY", false, 0,   0.01, 18, Constants.PRICE_TYPE_2,0);
		createContract( 14, 514, "USD", "CASH", "IDEALPRO", "JPY", false, 0,   0.01, 20, Constants.PRICE_TYPE_2,0);
		createContract( 15, 515, "USD", "CASH", "IDEALPRO", "CAD", true,  0, 0.0001, 20, Constants.PRICE_TYPE_3,0);
		createContract( 16, 516, "USD", "CASH", "IDEALPRO", "CHF", true,  0, 0.0001, 20, Constants.PRICE_TYPE_4,0);
		createContract( 17, 517, "CAD", "CASH", "IDEALPRO", "JPY", true,  0,   0.01, 20, Constants.PRICE_TYPE_2,0);
		createContract( 18, 518, "AUD", "CASH", "IDEALPRO", "USD", true,  0, 0.0001, 20, Constants.PRICE_TYPE_4,0);
		createContract( 19, 519, "AUD", "CASH", "IDEALPRO", "JPY", true,  0,   0.01, 20, Constants.PRICE_TYPE_2,0);
		createContract( 20, 520, "AUD", "CASH", "IDEALPRO", "NZD", true,  0, 0.0001, 20, Constants.PRICE_TYPE_4,0);
		createContract( 21, 521, "NZD", "CASH", "IDEALPRO", "USD", true,  0, 0.0001, 20, Constants.PRICE_TYPE_4,0);

	
	
/*		AD.FOREX	AUSUSD	Australian Dollar/US Dollar	FOREX
		AR.FOREX	AUSNZD	Australian/New Zealand Dollar	FOREX
		AS.FOREX	AUSCAD	Australian/Canadian	FOREX
		BP.FOREX	GBPUSD	British Pound/US Dollar	FOREX
		CD.FOREX	CADUSD	Canadian Dollar/US Dollar	FOREX
		EC.FOREX	EURUSD	Euro FX/US Dollar	FOREX
		EJ.FOREX	EURJPY	Euro/Japanese Yen	FOREX
		EP.FOREX	EURCAD	Euro/Canadian Dollar	FOREX
		EQ.FOREX	EURAUS	Euro/Australian Dollar	FOREX
		GB.FOREX	GBPEUR	British Pound/Euro	FOREX
		HY.FOREX	CADJPY	Canadian/Japanese Yen	FOREX
		JY.FOREX	JPYUSD	Japanese Yen/US Dollar	FOREX
		KU.FOREX	USDSEK	US Dollar/Krona	FOREX
		MQ.FOREX	MXPUSD	Mexican Peso/Dollar	FOREX
		NE.FOREX	NZDUSD	New Zealand Dollar/US Dollar	FOREX
		NS.FOREX	USDNOK	US Dollar/Krone	FOREX
		OL.FOREX	EURNOK	Euro/Krone	FOREX
		OR.FOREX	USDZAR	US Dollar/African Rand	FOREX
		RK.FOREX	EURSEK	Euro/Krona	FOREX
		RZ.FOREX	EURCHF	Euro/Swiss	FOREX
		SF.FOREX	CHFUSD	Swiss Franc/US Dollar	FOREX
		SS.FOREX	GBPCHF	British Pound/Swiss Franc	FOREX
		SY.FOREX	GBPJPY	British Pound/Japanese Yen	FOREX
		YA.FOREX	AUSJPY	Aussie/Japanese Yen	FOREX
		YD.FOREX	USDCAD	Dollar/Canadian	FOREX
		YF.FOREX	USDCHF	US Dollar/Swiss Franc	FOREX
		YP.FOREX	GBPUSD	British Pound/US Dollar	FOREX
		YY.FOREX	USDJPY	US Dollar/Japanese Yen	FOREX
		ZY.FOREX	CHFJPY	Swiss Franc/Japanese Yen	FOREX
*/
		
		
		
		if (( MODE == Constants.REAL_MODE ) || ( MODE == Constants.RECORD_MODE )) 
        {
			m_client.eConnect("127.0.0.1" , port, new Integer(IB_ID));
			m_client.setServerLogLevel(5);
        }

	}
	
	@Override
	public void error(Exception e) {
		System.out.println(e.getMessage());
		e.printStackTrace();
	}

	@Override
	public void error(String str) {
		System.out.println(str);
	}

	@Override
	public void error(int id, int errorCode, String errorMsg) {
		System.out.println("Error Code:" + errorCode + " " + errorMsg);
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
	public void currentTime(long time)
	{
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
		if (date.indexOf("finished-")== -1){
			System.out.println("id:" + reqId + " Date:" + IBDataFormatter.format(Long.parseLong(date)*1000));
			historyData.add(reqId + "," + date + "," + open + "," + high + "," + low + "," + close + "," + 
					volume + "," + count + ","  + WAP + "," + hasGaps );
		}else{
			System.out.println("id:" + reqId + " Date:" + date + " Open:" + open + " High" + high + " Low" + low + " Close:" + close + " Volumn:" + volume + " count:" + count + 
					" WAP:" + WAP + " hasGaps:" + hasGaps);
		}
		
		//int minute = new Integer(date.substring(13,15));

		for ( int i = 0; i < TOTAL_SYMBOLS; i++){
			if (((historyType == 1 ) && ( reqId == id_history[i])) || (( historyType == 2 ) && ( reqId == id_realtime[i]))){
				if (date.indexOf("finished-") != -1){
					quotes_received[i] = true;
				}
				break;
			}
		}
		
		for ( int i = 0; i < TOTAL_SYMBOLS; i++)
			if ( quotes_received[i] == false )
				return;

		historyCompleted = true;
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
		// TODO Auto-generated method stub
		
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
	}

	@Override
	public void realtimeBar(int reqId, long time, double open, double high, double low, double close,
			long volume, double wap, int count)
	{
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
		if ("NetLiquidation".equals(key))
		{
			System.out.println("AccountName:" + accountName + " begin " + key + " " + value + " " + currency);
			m_client.reqAccountUpdates(false,Constants.ACCOUNT);		
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
	}

	@Override
	public void connectionClosed()
	{
		// TODO Auto-generated method stub
		
	}


	private void createContract(int idHist, int idReal, String symbol, String type, String exchange, String currency, boolean minuteSupport, int tradeSize, double pipsize, int stopSize, int pricetype, 
			int timeZone )
	{
		Contract contract = contracts[totalSymbolIndex];
    	contract.m_conId = connectionId++;
    	contract.m_symbol = symbol;
        contract.m_secType = type;
        contract.m_exchange = exchange;
        contract.m_currency = currency;
       
        double exchangerate = exchangeRate.get(currency);
        
        id_history[totalSymbolIndex] = idHist;
        id_realtime[totalSymbolIndex] = idReal;
        
       	tradeManager[totalSymbolIndex][0] = new SFHistoryTradeManager(IB_ACCOUNT, m_client,contract, totalSymbolIndex );
       	tradeManager[totalSymbolIndex][0].MODE = this.MODE;
       	tradeManager[totalSymbolIndex][0].CHART = Constants.CHART_1_MIN;
       	tradeManager[totalSymbolIndex][0].TARGET_PROFIT_SIZE = 20;
       	tradeManager[totalSymbolIndex][0].PIP_SIZE = pipsize;
       	tradeManager[totalSymbolIndex][0].priceType = pricetype;
       	tradeManager[totalSymbolIndex][0].POSITION_SIZE = 250000;
       	tradeManager[totalSymbolIndex][0].oneMinutChart = minuteSupport;
       	tradeManager[totalSymbolIndex][0].FIXED_STOP = 7;
       	tradeManager[totalSymbolIndex][0].TIME_ZONE = timeZone;
       	tradeManager[totalSymbolIndex][0].currency = currency;
       	tradeManager[totalSymbolIndex][0].exchangeRate = exchangerate;
                        	
       	tradeManager[totalSymbolIndex][1] = new SFHistoryTradeManager(IB_ACCOUNT, m_client,contract, totalSymbolIndex );
       	tradeManager[totalSymbolIndex][1].MODE = this.MODE;
       	tradeManager[totalSymbolIndex][1].CHART = Constants.CHART_3_MIN;
       	tradeManager[totalSymbolIndex][1].TARGET_PROFIT_SIZE = 35;
       	tradeManager[totalSymbolIndex][1].PIP_SIZE = pipsize;
       	tradeManager[totalSymbolIndex][1].priceType = pricetype;
       	tradeManager[totalSymbolIndex][1].POSITION_SIZE = 125000;
       	tradeManager[totalSymbolIndex][1].FIXED_STOP = 10;
       	tradeManager[totalSymbolIndex][1].TIME_ZONE = timeZone;
       	tradeManager[totalSymbolIndex][1].currency = currency;
       	tradeManager[totalSymbolIndex][1].exchangeRate = exchangerate;
        	
       	tradeManager[totalSymbolIndex][2] = new SFHistoryTradeManager(IB_ACCOUNT, m_client,contract, totalSymbolIndex );
       	tradeManager[totalSymbolIndex][2].MODE = this.MODE;
       	tradeManager[totalSymbolIndex][2].CHART = Constants.CHART_5_MIN;
       	tradeManager[totalSymbolIndex][2].TARGET_PROFIT_SIZE = 50;
       	tradeManager[totalSymbolIndex][2].PIP_SIZE = pipsize;
       	tradeManager[totalSymbolIndex][2].priceType = pricetype;
       	tradeManager[totalSymbolIndex][2].POSITION_SIZE = tradeSize;//200000;//tradeSize;//50000;//200000;
       	tradeManager[totalSymbolIndex][2].FIXED_STOP = stopSize;
       	tradeManager[totalSymbolIndex][2].TIME_ZONE = timeZone;
       	tradeManager[totalSymbolIndex][2].currency = currency;
       	tradeManager[totalSymbolIndex][2].exchangeRate = exchangerate;
       	tradeManager[totalSymbolIndex][2].tickId = idReal + 100;
        	
       	tradeManager[totalSymbolIndex][3] = new SFHistoryTradeManager(IB_ACCOUNT, m_client,contract, totalSymbolIndex );
       	tradeManager[totalSymbolIndex][3].MODE = this.MODE;
       	tradeManager[totalSymbolIndex][3].CHART = Constants.CHART_10_MIN;
       	tradeManager[totalSymbolIndex][3].TARGET_PROFIT_SIZE = 35;
       	tradeManager[totalSymbolIndex][3].PIP_SIZE = pipsize;
       	tradeManager[totalSymbolIndex][3].priceType = pricetype;
       	tradeManager[totalSymbolIndex][3].POSITION_SIZE = 50000;
       	tradeManager[totalSymbolIndex][3].FIXED_STOP = 21;
       	tradeManager[totalSymbolIndex][3].TIME_ZONE = timeZone;
       	tradeManager[totalSymbolIndex][3].currency = currency;
       	tradeManager[totalSymbolIndex][3].exchangeRate = exchangerate;
        
       	tradeManager[totalSymbolIndex][4] = new SFHistoryTradeManager(IB_ACCOUNT, m_client,contract, totalSymbolIndex );
       	tradeManager[totalSymbolIndex][4].MODE = this.MODE;
       	tradeManager[totalSymbolIndex][4].CHART = Constants.CHART_15_MIN;
       	tradeManager[totalSymbolIndex][4].TARGET_PROFIT_SIZE = 40;
       	tradeManager[totalSymbolIndex][4].PIP_SIZE = pipsize;
       	tradeManager[totalSymbolIndex][4].priceType = pricetype;
       	tradeManager[totalSymbolIndex][4].POSITION_SIZE = tradeSize;
       	tradeManager[totalSymbolIndex][4].FIXED_STOP = 28;
       	tradeManager[totalSymbolIndex][4].TIME_ZONE = timeZone;
       	tradeManager[totalSymbolIndex][4].currency = currency;
       	tradeManager[totalSymbolIndex][4].exchangeRate = exchangerate;

       	tradeManager[totalSymbolIndex][5] = new SFHistoryTradeManager(IB_ACCOUNT, m_client,contract, totalSymbolIndex );
       	tradeManager[totalSymbolIndex][6] = new SFHistoryTradeManager(IB_ACCOUNT, m_client,contract, totalSymbolIndex );
       
       	//tradeManager[totalSymbolIndex][2].largerTimeFrameTraderManager = tradeManager[totalSymbolIndex][6];

       	// increase one for next
        totalSymbolIndex++;
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
			/*
			if ((( data.close - data.low ) > 5 * pipSize) || (( data.high - data.close ) > 5 * pipSize) )
			{
				System.out.println( "pipsize is " + pipSize );
				System.out.println("invalid quote" + data.high + " " + data.low + " " + data.close);
				return;
			}*/

			
			if (data.high > lastElem.high)
				lastElem.high = data.high;
			if (data.low < lastElem.low )
				lastElem.low = data.low;
			lastElem.close = data.close;
			//lastElem.time = data.time; do not over ride the time
		}
	}
	
	
	public void createOrUpdateQuotes( HashMap<String, QuoteData> quotes, int CHART, final QuoteData data )
	{
		int minute = new Integer(data.time.substring(13,15));

		if ( CHART == Constants.CHART_3_MIN )
			minute = minute - minute%3;
		else if ( CHART == Constants.CHART_5_MIN )
			minute = minute - minute%5;
		else if ( CHART == Constants.CHART_10_MIN )
			minute = minute - minute%10;
		else if ( CHART == Constants.CHART_15_MIN )
			minute = minute - minute%15;
		
		String minStr = new Integer(minute).toString();
		if ( minute < 10 )
			minStr = "0" + new Integer(minute).toString();
		else
			minStr = new Integer(minute).toString();
		
		String indexStr = data.time.substring(0,13) + minStr;
		
		QuoteData qdata = quotes.get(indexStr);
		if ( qdata == null )
		{
			qdata = new QuoteData();
			qdata.open = data.open;
			qdata.high = data.high;
			qdata.low = data.low;
			qdata.close = data.close;
			qdata.time = indexStr;
		}
		else
		{
			/*
			if (data.time.compareTo(qdata.time)< 0 )
			{
				qdata.open = data.open;
				qdata.time = data.time;
			}
			if (data.time.compareTo(qdata.time)> 0 )*/
				qdata.close = data.close;
				
				//qdata.time = data.time;

			if (data.high > qdata.high)
				qdata.high = data.high;
			if (data.low < qdata.low )
				qdata.low = data.low;

		}
		
		quotes.put( indexStr, qdata);
	}

	
	public void replaceQuotes( Hashtable<String, QuoteData> quotes, int CHART, QuoteData data )
	{
		int minute = new Integer(data.time.substring(13,15));

		if ( CHART == Constants.CHART_3_MIN )
			minute = minute - minute%3;
		else if ( CHART == Constants.CHART_5_MIN )
			minute = minute - minute%5;
		else if ( CHART == Constants.CHART_10_MIN )
			minute = minute - minute%10;
		else if ( CHART == Constants.CHART_15_MIN )
			minute = minute - minute%15;
		
		String minStr = new Integer(minute).toString();
		if ( minute < 10 )
			minStr = "0" + new Integer(minute).toString();
		else
			minStr = new Integer(minute).toString();
		
		String indexStr = data.time.substring(0,12) + minStr;
		
		quotes.put( indexStr, data);
	}

	
	
	
	private void initialize()
	{
		contracts = new Contract[TOTAL_SYMBOLS];
		quoteFirstTime = new boolean[TOTAL_SYMBOLS];
		tradeManager = new SFHistoryTradeManager[TOTAL_SYMBOLS][TOTAL_TRADES];	
    	quotes_received = new boolean[TOTAL_SYMBOLS];
 		
		for ( int i = 0; i < TOTAL_SYMBOLS; i++)
			contracts[i] = new Contract();
	}
	
	
	private void saveHistoryData(String historyFile)
	{
		try {
			FileWriter fw = new FileWriter(historyFile);
			
			Iterator<String> it = historyData.iterator();
			
			while ( it.hasNext())
			{
				String h = (String)it.next();
				fw.append(h + "\n");
			}
			
			fw.close();
		} 
		catch (IOException e){
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
	}
	
	


	@Override
	public void tickOptionComputation(int tickerId, int field,
			double impliedVol, double delta, double modelPrice,
			double pvDividend) {
		// TODO Auto-generated method stub
		
	}
	
	public String getNextDate(String historyReqDate)
	{
		String year = historyReqDate.substring(0,4);
		String monthDay = historyReqDate.substring(4);
		int y = new Integer( year);
		int nextDay = new Integer( monthDay );
		
		switch (nextDay)
		{
			case 131:   return year+"0201";
			case 227:   if ( y % 4 == 0 )
				           return year+"0228";
						else
						   return year + "0301";
			case 228:   return year+"0301";
			case 331:   return year+"0401";
			case 430:   return year+"0501";
			case 531:   return year+"0601";
			case 630:   return year+"0701";
			case 731:   return year+"0801";
			case 831:   return year+"0901";
			case 930:   return year+"1001";
			case 1031:  return year+"1101";
			case 1130:  return year+"1201";
			case 1231:  return (new Integer(y+1))+"0101";
			
			default: if ( nextDay >= 1000 )
						return year + new Integer(nextDay + 1);
				   	else
						return year + "0" + new Integer(nextDay + 1);
				
						
		}
		
	}

/*
	@Override
	public void tickOptionComputation(int tickerId, int field,
			double impliedVol, double delta, double optPrice,
			double pvDividend, double gamma, double vega, double theta,
			double undPrice) {
		// TODO Auto-generated method stub
		
	}*/

	
	
	/*	public void run( String trade_market, /*String historyReqDate,int numOfDays, String historyReqDate1, String historyPath) {
		
		Date date = new Date();
		Date date1 = new Date();
		try
		{
			try{
				//Date date = DayOnlyFormatter.parse(historyReqDate);
				historyReqDate1 += " 17:00:00";
				date1 = DayOnlyFormatter.parse(historyReqDate1);
				//numOfDays = (int)( (date1.getTime() - date.getTime())/ (1000 * 60 * 60 * 24) ) + 1;
				
				Calendar cal = Calendar.getInstance();
				cal.setTime(date1);
				if ( cal.get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY){
					System.out.println("Error: ending data is not a friday, please correct...");
					System.exit(0);
				}
				date = new Date(date1.getTime() - 5 * 24 * 60 * 60000L);  // 5 days

			}catch ( Exception e){
				e.printStackTrace();
				System.exit(0);
			}
			


			// TOCHANGE 2
			//String currentTime = historyReqDate + " 01:30:00";
			//String currentTime = historyReqDate + " 17:00:00";
			String currentTime = IBDataFormatter.format(date);
			historyCompleted = false;
			quotes_received = new boolean[TOTAL_SYMBOLS];
			System.out.println("Request 20 D 15 min historical data from "+ currentTime );
			for ( int i = 0; i < TOTAL_SYMBOLS; i++){
				quotes_received[i] = false;
		    	m_client.reqHistoricalData( id_history[i], contracts[i],  
		    			//currentTime, "86400 S", "1 min", "MIDPOINT", 0, 1);	              
    					//currentTime, "10 D", "5 mins", "MIDPOINT", 0, 1);	              
    					currentTime, "20 D", "15 mins", "MIDPOINT", 0, 1);	              
			}
			
			// only save when everything is in place
			while ( historyCompleted == false )
					Thread.sleep(60000);	// this is for trade to exits

			//String historyFile = historyPath + "historyData_" +trade_market + "_" +  historyReqDate + ".csv";
			String historyFile = historyPath + "historyData_" +trade_market + "_" +  DayOnlyFormatter.format(date) + ".csv";
			System.out.println("save history file:" + historyFile);
			saveHistoryData(historyFile);
			
			System.out.println("History download completed");
			historyData.clear();
			Thread.sleep( 60000 );   // sleep 4 minute
			
			historyType = 2;
			//currentTime = historyReqDate1 + " 17:00:00";
			currentTime = IBDataFormatter.format(date1);
		
			historyCompleted = false;
			//historyData.clear();
			
			for ( int i = 0; i < TOTAL_SYMBOLS; i++)
			{
				quotes_received[i] = false;
				// TOCHANGE 4
		    	m_client.reqHistoricalData( id_realtime[i], contracts[i],  
		    		//	currentTime, "14400 S", "15 secs", "MIDPOINT", 0, 2);	// 4 hours 6 x 60 x 60 =               
		    		//	currentTime, "23400 S", "15 secs", "MIDPOINT", 0, 2);	// 6.5 hours 6.5 x 60 x 60 =               
		    		//  currentTime, "55800 S", "30 secs", "MIDPOINT", 0, 2);	// 7.5 hours 7.5 x 60 x 60 = 27000 sec              
			    	//	currentTime, "41400 S", "30 secs", "MIDPOINT", 0, 2);	// 11.5 hours  1:30-13:00              
		    		//	currentTime, "79200 S", "30 secs", "MIDPOINT", 0, 2);	// 22 hours    18:00 - 16:00  22 * 60 * 60 = 79200 sec             
		    	    //	currentTime, "66600 S", "30 secs", "MIDPOINT", 0, 2);	// 12.5 hours  0:30-13:00  = 45000             
	    			//	currentTime, "14400 S", "15 secs", "MIDPOINT", 0, 2);	// 15.5 hour   7:30 - 23:00 = 55800 sec
		    																	// 18.5 hour   6:30pm - 13:00pm = 66600 sec  	
    					//currentTime, "5 D",     "1 min", "MIDPOINT", 0, 2);
    					currentTime, numOfDays + " D",  "1 min", "MIDPOINT", 0, 2);
				System.out.println("Request " + id_realtime[i] + " Currenttime: " + currentTime + " No.Days:" + numOfDays + " 1 min");
			}
		
			// only save when everything is in place
			while ( historyCompleted == false )
				Thread.sleep(60000);	// this is for trade to exits

			System.out.println("Real time download finished");

			Thread.sleep( 60000 );   // sleep 4 minute
			
			String realtimeFile = historyPath + "realtimeData_" +trade_market + "_" +  DayOnlyFormatter.format(date1) + ".csv";
			System.out.println("save realtime file:" + realtimeFile);
			saveHistoryData(realtimeFile);
	
			System.out.println("Realtime download completed");

			//   Making identical historical data requests within 15 seconds;
			//   Making six or more historical data requests for the same Contract, Exchange and Tick Type within two seconds.
			//   history limitation: Do not make more than 60 historical data requests in any ten-minute period.
			// TOCHANGE 5  ( to speed up )
			//Thread.sleep( 60000 * 10);   // sleep 10 minute
			//Thread.sleep( 60000 * 5 );   // sleep 5 minute
			
			m_client.eDisconnect();

			System.out.println("disconnected from TradeStation");
			
			System.exit(0);
			
		}
		catch( Exception e)
		{
			e.printStackTrace();
		}
	
	}*/

	

	
}
