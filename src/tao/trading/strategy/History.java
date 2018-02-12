package tao.trading.strategy;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import tao.trading.Constants;
import tao.trading.QuoteData;
import tao.trading.strategy.util.Utility;

import com.ib.client.Contract;
import com.ib.client.ContractDetails;
import com.ib.client.EClientSocket;
import com.ib.client.EWrapper;
import com.ib.client.Execution;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.UnderComp;

// mainly test trend with low attitude
public class History implements EWrapper
{
	private static Logger logger;
	protected EClientSocket m_client = new EClientSocket(this);
	protected SF100TradeManager[][] tradeManager;
	protected SimpleDateFormat IBDataFormatter = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
	Contract[] contracts;
	int[] id_1m_history, id_5m_history, id_15m_history, id_60m_history;
	boolean[] quotes_received_1m,quotes_received_5m,quotes_received_15m,quotes_received_60m; 
	//Vector<QuoteData>[] quotes, quotes5M, quotes15M, quotes60M; 
	int[] id_realtime;
	boolean[] id_realtime_started;
	boolean[] quoteFirstTime;
	int totalSymbolIndex = 0;
	int connectionId = 100;
	String IB_ID;
	boolean timeout= false;
	boolean closeAccount = false;
	String realTimeFile = "C:/trade/Data/realtimeData.csv";
	String historyFile = "C:/trade/Data/historyData.csv";
	int TOTAL_SYMBOLS;
	int TOTAL_TRADES = 4;
	Vector<String> historyData = new Vector<String>();

	
	public static void main(String[] args)
	{
		History open = new History(args[0],args[1]);
		String currentTime = args[2];
		open.historyFile = "C:/trade/Data/history/historyData_" + currentTime + ".csv";
   		open.run(currentTime);
	}

	
	public void run(String currentTime) {
		
		currentTime = currentTime.replace('_',' ');
		for ( int i = 0; i < TOTAL_SYMBOLS; i++)
		{
	    	m_client.reqHistoricalData( id_15m_history[i], contracts[i],  
	    			currentTime, //"20091015 16:26:44",
	                "3 D", "15 mins", "MIDPOINT", 1, 1);               

	    	m_client.reqHistoricalData( id_5m_history[i], contracts[i],  
	    			currentTime, //"20091015 16:26:44",
	                "2 D", /*MINUTE_CHART_5 +*/ "5 mins", "MIDPOINT", 1, 1);               

	    	m_client.reqHistoricalData( id_1m_history[i], contracts[i],  
	    			currentTime, //"20091015 16:26:44",
	                "24000 S", "1 min", "MIDPOINT", 1, 1);	// 300 bars               
		}

		
	}

	
	public History( String ib_id, String trade_market )
	{
	   	IB_ID = ib_id;

	   	if ("JPY".equalsIgnoreCase(trade_market))
		{
			TOTAL_SYMBOLS = 10;
			initialize();
			createContract( "EUR", "CASH", "IDEALPRO", "JPY", true,  200000,   0.01, Constants.PRICE_TYPE_2,20,30,40,50, 7,11,15,19);
			createContract( "EUR", "CASH", "IDEALPRO", "NZD", false, 200000, 0.0001, Constants.PRICE_TYPE_4,20,30,40,50, 7,11,15,19);
			createContract( "EUR", "CASH", "IDEALPRO", "AUD", false, 200000, 0.0001, Constants.PRICE_TYPE_4,20,30,40,50, 7,11,15,19);
			createContract( "GBP", "CASH", "IDEALPRO", "JPY", true,  200000,   0.01, Constants.PRICE_TYPE_2,20,30,40,50, 7,11,15,19);
			createContract( "GBP", "CASH", "IDEALPRO", "AUD", false, 200000, 0.0001, Constants.PRICE_TYPE_4,20,30,40,50, 7,11,15,19);
			createContract( "GBP", "CASH", "IDEALPRO", "NZD", false, 200000, 0.0001, Constants.PRICE_TYPE_4,20,30,40,50, 7,11,15,19);
			createContract( "CHF", "CASH", "IDEALPRO", "JPY", false, 200000,   0.01, Constants.PRICE_TYPE_2,20,30,40,50, 7,11,15,19);
			createContract( "CAD", "CASH", "IDEALPRO", "JPY", true,  200000,   0.01, Constants.PRICE_TYPE_2,20,30,40,50, 7,11,15,19);
			createContract( "USD", "CASH", "IDEALPRO", "JPY", true,  200000,   0.01, Constants.PRICE_TYPE_2,20,30,40,50, 7,11,15,19);
			createContract( "AUD", "CASH", "IDEALPRO", "USD", true,  200000, 0.0001, Constants.PRICE_TYPE_4,20,30,40,50, 7,11,15,19);
		}
		else if ("EUR".equalsIgnoreCase(trade_market))
		{	
			TOTAL_SYMBOLS = 14;
			initialize();
			createContract( "EUR", "CASH", "IDEALPRO", "USD", true,  200000, 0.0001, Constants.PRICE_TYPE_4,20,30,40,50, 7,11,15,19);
			createContract( "EUR", "CASH", "IDEALPRO", "JPY", true,  200000,   0.01, Constants.PRICE_TYPE_2,20,30,40,50, 7,11,15,19);
			createContract( "EUR", "CASH", "IDEALPRO", "CAD", true,  200000, 0.0001, Constants.PRICE_TYPE_3,20,30,40,50, 7,11,15,19);
			createContract( "EUR", "CASH", "IDEALPRO", "GBP", true,  200000, 0.0001, Constants.PRICE_TYPE_4,20,30,40,50, 7,11,15,19);
			createContract( "EUR", "CASH", "IDEALPRO", "CHF", true,  200000, 0.0001, Constants.PRICE_TYPE_4,20,30,40,50, 7,11,15,19);
			createContract( "EUR", "CASH", "IDEALPRO", "NZD", false, 200000, 0.0001, Constants.PRICE_TYPE_4,20,30,40,50, 7,11,15,19);
			createContract( "EUR", "CASH", "IDEALPRO", "AUD", false, 200000, 0.0001, Constants.PRICE_TYPE_4,20,30,40,50, 7,11,15,19);
			createContract( "GBP", "CASH", "IDEALPRO", "AUD", false, 200000, 0.0001, Constants.PRICE_TYPE_4,20,30,40,50, 7,11,15,19);
			createContract( "GBP", "CASH", "IDEALPRO", "NZD", false, 200000, 0.0001, Constants.PRICE_TYPE_4,20,30,40,50, 7,11,15,19);
			createContract( "GBP", "CASH", "IDEALPRO", "USD", true,  200000, 0.0001, Constants.PRICE_TYPE_4,20,30,40,50, 7,11,15,19);
			createContract( "GBP", "CASH", "IDEALPRO", "CAD", true,  200000, 0.0001, Constants.PRICE_TYPE_3,20,30,40,50, 7,11,15,19);
			createContract( "GBP", "CASH", "IDEALPRO", "CHF", true,  200000, 0.0001, Constants.PRICE_TYPE_4,20,30,40,50, 7,11,15,19);
			createContract( "GBP", "CASH", "IDEALPRO", "JPY", true,  200000,   0.01, Constants.PRICE_TYPE_2,20,30,40,50, 7,11,15,19);
			createContract( "CHF", "CASH", "IDEALPRO", "JPY", false, 200000,   0.01, Constants.PRICE_TYPE_2,20,30,40,50, 7,11,15,19);
		}
		else if ("USD".equalsIgnoreCase(trade_market))
		{	
			TOTAL_SYMBOLS = 11;
			initialize();
			createContract( "USD", "CASH", "IDEALPRO", "JPY", true,  200000,   0.01, Constants.PRICE_TYPE_2,20,30,40,50, 7,11,15,19);
			createContract( "USD", "CASH", "IDEALPRO", "CAD", true,  200000, 0.0001, Constants.PRICE_TYPE_3,20,30,40,50, 7,11,15,19);
			createContract( "USD", "CASH", "IDEALPRO", "CHF", true,  200000, 0.0001, Constants.PRICE_TYPE_4,20,30,40,50, 7,11,15,19);
			createContract( "EUR", "CASH", "IDEALPRO", "USD", true,  200000, 0.0001, Constants.PRICE_TYPE_4,20,30,40,50, 7,11,15,19);
			createContract( "EUR", "CASH", "IDEALPRO", "JPY", true,  200000,   0.01, Constants.PRICE_TYPE_2,20,30,40,50, 7,11,15,19);
			createContract( "EUR", "CASH", "IDEALPRO", "CAD", true,  200000, 0.0001, Constants.PRICE_TYPE_3,20,30,40,50, 7,11,15,19);
			createContract( "EUR", "CASH", "IDEALPRO", "GBP", true,  200000, 0.0001, Constants.PRICE_TYPE_4,20,30,40,50, 7,11,15,19);
			createContract( "EUR", "CASH", "IDEALPRO", "CHF", true,  200000, 0.0001, Constants.PRICE_TYPE_4,20,30,40,50, 7,11,15,19);
			createContract( "GBP", "CASH", "IDEALPRO", "USD", true,  200000, 0.0001, Constants.PRICE_TYPE_4,20,30,40,50, 7,11,15,19);
			createContract( "GBP", "CASH", "IDEALPRO", "CAD", true,  200000, 0.0001, Constants.PRICE_TYPE_3,20,30,40,50, 7,11,15,19);
			createContract( "GBP", "CASH", "IDEALPRO", "CHF", true,  200000, 0.0001, Constants.PRICE_TYPE_4,20,30,40,50, 7,11,15,19);
		}


		
		id_1m_history = new int[]{0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23};
		id_5m_history = new int[]{100,101,102,103,104,105,106,107,108,109,110,111,112,113,114,115,116,117,118,119,120,121,122,123};
		id_15m_history = new int[]{150,151,152,153,154,155,156,157,158,159,160,161,162,163,164,165,166,167,168,169,170,171,172,173};
		id_60m_history = new int[]{200,201,202,203,204,205,206,207,208,209,210,211,212,213,214,215,216,217,218,219,220,221,222,223};
		id_realtime = new int[]{500,501,502,503,504,505,506,507,508,509,510,511,512,513,514,515,516,517,518,519,520,521,522,523};

		m_client.eConnect("127.0.0.1" , 7496, new Integer(IB_ID));
		m_client.setServerLogLevel(5);

	}
	
	@Override
	public void error(Exception e) {
		//logger.severe("E1:" + e.getMessage());
		//e.printStackTrace();
	}

	@Override
	public void error(String str) {
		logger.severe("E2:" + str);
	}

	@Override
	public void error(int id, int errorCode, String errorMsg) {
		logger.severe(id + " Error Code:" + errorCode + " " + errorMsg);
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
		//System.out.println("id:" + reqId + " Date:" + date + " Open:" + open + " High" + high + " Low" + low + " Close:" + close + " Volumn:" + volume + " count:" + count + 
		//		" WAP:" + WAP + " hasGaps:" + hasGaps);
		historyData.add(reqId + "," + date + "," + open + "," + high + "," + low + "," + close + "," + 
					volume + "," + count + ","  + WAP + "," + hasGaps );
		
		
		if (date.indexOf("finished-") != -1)
		{
			for ( int i = 0; i < TOTAL_SYMBOLS; i++)
			{
				if (reqId == id_1m_history[i])
			    	quotes_received_1m[i] = true;
				
				if (reqId == id_5m_history[i])
			    	quotes_received_5m[i] = true;
				
				if (reqId == id_15m_history[i])
			    	quotes_received_15m[i] = true;
			}
		}
		
		// only save when everything is in place
		for ( int i = 0; i < TOTAL_SYMBOLS; i++)
			if ( id_realtime_started[i] == false )
				return;
				
		saveHistoryData();
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
	public void tickOptionComputation(int tickerId, int field, double impliedVol, double delta,
			double modelPrice, double pvDividend)
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


	private void createContract(String symbol, String type, String exchange, String currency, boolean minuteSupport, int tradeSize, double pipsize, int pricetype, 
			double min1Target1,double min5Target1,double min15Target1,double min60Target1,double min1StopTarget,double min5StopTarget,double min15StopTarget,double min60StopTarget )
	{
		Contract contract = contracts[totalSymbolIndex];
    	contract.m_conId = connectionId++;
    	contract.m_symbol = symbol;
        contract.m_secType = type;
        contract.m_exchange = exchange;
        contract.m_currency = currency;
       
        //logger.info("currency = " + currency);
       
       	tradeManager[totalSymbolIndex][0] = new SF100TradeManager(m_client,contract, totalSymbolIndex, logger );
       	tradeManager[totalSymbolIndex][0].CHART = Constants.CHART_1_MIN;
       	tradeManager[totalSymbolIndex][0].TARGET_PROFIT_SIZE = 20;
       	tradeManager[totalSymbolIndex][0].PIP_SIZE = pipsize;
       	tradeManager[totalSymbolIndex][0].priceType = pricetype;
       	tradeManager[totalSymbolIndex][0].POSITION_SIZE = 250000;
       	tradeManager[totalSymbolIndex][0].oneMinutChart = minuteSupport;
       	tradeManager[totalSymbolIndex][0].FIXED_STOP = min1StopTarget;
       	tradeManager[totalSymbolIndex][0].currency = currency;
                        	
       	tradeManager[totalSymbolIndex][1] = new SF100TradeManager(m_client,contract, totalSymbolIndex, logger );
       	tradeManager[totalSymbolIndex][1].CHART = Constants.CHART_5_MIN;
       	tradeManager[totalSymbolIndex][1].TARGET_PROFIT_SIZE = 50;
       	tradeManager[totalSymbolIndex][1].PIP_SIZE = pipsize;
       	tradeManager[totalSymbolIndex][1].priceType = pricetype;
       	tradeManager[totalSymbolIndex][1].POSITION_SIZE = 100000;
       	tradeManager[totalSymbolIndex][1].FIXED_STOP = min5StopTarget;
       	tradeManager[totalSymbolIndex][1].currency = currency;
        	
       	tradeManager[totalSymbolIndex][2] = new SF100TradeManager(m_client,contract, totalSymbolIndex, logger );
       	tradeManager[totalSymbolIndex][2].CHART = Constants.CHART_15_MIN;
       	tradeManager[totalSymbolIndex][2].TARGET_PROFIT_SIZE = 40;
       	tradeManager[totalSymbolIndex][2].PIP_SIZE = pipsize;
       	tradeManager[totalSymbolIndex][2].priceType = pricetype;
       	tradeManager[totalSymbolIndex][2].POSITION_SIZE = 50000;
       	tradeManager[totalSymbolIndex][2].FIXED_STOP = min15StopTarget;
       	tradeManager[totalSymbolIndex][2].currency = currency;
        	
       	tradeManager[totalSymbolIndex][3] = new SF100TradeManager(m_client,contract, totalSymbolIndex, logger );
       	tradeManager[totalSymbolIndex][3].CHART = Constants.CHART_60_MIN;
       	tradeManager[totalSymbolIndex][3].TARGET_PROFIT_SIZE = 50;
       	tradeManager[totalSymbolIndex][3].PIP_SIZE = pipsize;
       	tradeManager[totalSymbolIndex][3].priceType = pricetype;
       	tradeManager[totalSymbolIndex][3].POSITION_SIZE = 25000;
       	tradeManager[totalSymbolIndex][3].FIXED_STOP = min60StopTarget;
       	tradeManager[totalSymbolIndex][3].currency = currency;
        
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
			//data.pos = quotes.lastElement().pos + 1;
			data.pos = quotes.size();
			quotes.add(data);
			
			/*if ( quotes.size() == 381 )
			{
				System.out.println("This is it");
			}*/
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
				logger.info( "pipsize is " + pipSize );
				logger.info("invalid quote" + data.high + " " + data.low + " " + data.close);
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
	
	private void initialize()
	{
		contracts = new Contract[TOTAL_SYMBOLS];
		quoteFirstTime = new boolean[TOTAL_SYMBOLS];
		tradeManager = new SF100TradeManager[TOTAL_SYMBOLS][TOTAL_TRADES];	
    	quotes_received_1m = new boolean[TOTAL_SYMBOLS];
    	quotes_received_5m = new boolean[TOTAL_SYMBOLS];
    	quotes_received_15m = new boolean[TOTAL_SYMBOLS];
    	quotes_received_60m = new boolean[TOTAL_SYMBOLS];
    	id_realtime_started = new boolean[TOTAL_SYMBOLS];
		
		for ( int i = 0; i < TOTAL_SYMBOLS; i++)
			contracts[i] = new Contract();
	}
	
	
	private void saveHistoryData()
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
			logger.severe(e.getMessage());
			e.printStackTrace();
		}
	}
	
	private void saveRealTimeData(String realTimeData )
	{
		try {
			FileWriter fw = new FileWriter(realTimeFile, true );
			fw.append(realTimeData + "\n");
			fw.close();
		} 
		catch (IOException e){
			logger.severe(e.getMessage());
			e.printStackTrace();
		}
	}
	
	
	
	private void readHistoryData( String historyFileName )
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
		
		try
		{
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
				
				System.out.println(strLine);
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
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	
	private void readRealTimeData( String realTimeFileName )
	{
		String reqId, time, open, high, low, close,volume,wap, count;
		
		try
		{
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
				//if ( strLine.length() < 20 )
				//	continue;
				//logger.warning("strLine:" + strLine);
				
				//while(st.hasMoreTokens()) 
				try
				{
					reqId = st.nextToken();
					time = st.nextToken();
					open = st.nextToken();
					high = st.nextToken();
					low = st.nextToken();
					close = st.nextToken();
					volume = st.nextToken();
					wap = st.nextToken();
					count = st.nextToken();
				}
				catch( RuntimeException e )
				{
					continue;
				}
				
				realtimeBar(new Integer(reqId), new Long(time), new Double(open), new Double(high), new Double(low), new Double(close),
						new Long(volume), new Double(wap), new Integer(count));

			}
			
			logger.warning(lineNumber + " real time data read");
		
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	
	
}
