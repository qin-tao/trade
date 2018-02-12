package tao.trading.strategy;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Vector;
import java.util.logging.Logger;

import tao.trading.Constants;
import tao.trading.Indicator;
import tao.trading.QuoteData;
import tao.trading.StretchOrderManager2;
import tao.trading.Trade;

import com.ib.client.Contract;
import com.ib.client.ContractDetails;
import com.ib.client.EClientSocket;
import com.ib.client.EWrapper;
import com.ib.client.Execution;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.UnderComp;

public class MovingAverageCrossOver implements EWrapper
{
		private static Logger logger = Logger.getLogger("MovingAverageCrossOver");
		protected Indicator indicator = new Indicator();
		protected Contract contract;
		protected EClientSocket m_client = new EClientSocket(this);
		protected SimpleDateFormat IBDataFormatter = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
		protected StretchOrderManager2[] orderManager;	
		String filename = null;
		Writer output = null;
		double pullback;
		int numOfBars;
		boolean history60m = false;
		boolean history1m = false;
		Contract[] contracts;
		int[] id_1m_history;
		int[] id_realtime;
		int totalSymbol;
		Vector<QuoteData>[] quotes1m; 
		double[] high1min;
		double[] low1min;
		double[] distance;
		
		boolean isOpen = false;
		
		public static void main(String[] args)
		{
			MovingAverageCrossOver maco = new MovingAverageCrossOver();
			maco.run();
			
				try{
					
					Thread.sleep(990000000);
				}
				catch( Exception e)
				{
				}
			}

		public void run() {
			
			// this will kick off the sequence
			m_client.reqCurrentTime();
			
		}

		public MovingAverageCrossOver()
		{
	        totalSymbol = 10;
			
			contracts = new Contract[totalSymbol];
			distance = new double[totalSymbol];
			quotes1m = new Vector[totalSymbol];
			high1min = new double[totalSymbol];
			low1min = new double[totalSymbol];
			orderManager = new StretchOrderManager2[totalSymbol];	

			for ( int i = 0; i < totalSymbol; i++)
			{
				contracts[i] = new Contract();
				quotes1m[i] = new Vector<QuoteData>(200);
				high1min[i] = 0;
				low1min[i] = 999;
			}
			
			//createContract( 0, 100, "USD", "CASH", "IDEALPRO", "JPY", 0.1);//0.004

			
			createContract( 0, 100, "EUR", "CASH", "IDEALPRO", "USD", 0.003);	
			createContract( 1, 101, "EUR", "CASH", "IDEALPRO", "GBP", 0.002);
			createContract( 2, 102, "EUR", "CASH", "IDEALPRO", "CHF", 0.002);
			createContract( 3, 103, "EUR", "CASH", "IDEALPRO", "JPY", 0.7);
			createContract( 4, 104, "EUR", "CASH", "IDEALPRO", "CAD", 0.0025);
			createContract( 5, 105, "EUR", "CASH", "IDEALPRO", "AUD", 0.005);
			createContract( 6, 106, "USD", "CASH", "IDEALPRO", "JPY", 0.5);
			createContract( 7, 107, "USD", "CASH", "IDEALPRO", "CAD", 0.003);
			createContract( 8, 108, "GBP", "CASH", "IDEALPRO", "JPY", 1.0);
			createContract( 9, 109, "USD", "CASH", "IDEALPRO", "CHF", 0.003);
			
			id_1m_history = new int[]{0,1,2,3,4,5,6,7,8,9};
			id_realtime = new int[]{100,101,102,103,104,105,106,107,108,109};

	        m_client.eConnect("127.0.0.1" , 7496, 9);
	        m_client.setServerLogLevel(5);

		}
		
		@Override
		public void error(Exception e) {
			System.out.println("Error: " );
			e.printStackTrace();
		}

		@Override
		public void error(String str) {
			System.out.println("Error: " + str);
		}

		@Override
		public void error(int id, int errorCode, String errorMsg) {
			System.out.println("Error: " + id + " " + errorCode + " " + errorMsg);
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
			String currentTime = IBDataFormatter.format(new Date( time*1000));
			logger.info("CurrnetTime:" + currentTime );

			for ( int i = 0; i < totalSymbol; i++)
			{
		    	m_client.reqHistoricalData( id_1m_history[i], contracts[i],  
		    			currentTime, //"20091015 16:26:44",
		                "72000 S", "1 min", "MIDPOINT", 1, 1);               
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
			//		" WAP:" + WAP + " hasGaps:" + hasGaps);
			
			if (date.indexOf("finished-") == -1)
			{
				for ( int i = 0; i < totalSymbol; i++)
				{
					if (reqId == id_1m_history[i])
					{
						QuoteData data = new QuoteData(date, open, high, low, close, volume, count, WAP, hasGaps);
						quotes1m[i].add(data);
						break;
					}
				}
			}
			else
			{
				for ( int i = 0; i < totalSymbol; i++)
				{
					if (reqId == id_1m_history[i])
					{
						m_client.reqRealTimeBars(id_realtime[i], contracts[i], 60, "MIDPOINT", true);
						System.out.println(contracts[i].m_symbol + "." + contracts[i].m_currency + " started");
					}
				}
			}
			
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
			System.out.println("orderStatus: " + " orderId " + orderId + " status " + status + " filled " + filled 
					+ " remaining " + remaining + " avgFillPrice " + avgFillPrice + " permId " + permId + " parentId " + parentId
					+ " lastFillPrice " + lastFillPrice + " clientId " + clientId + " whyHeld " + whyHeld);
			
			//orderStatus:  orderId 113 status Filled filled 10 remaining 0 avgFillPrice 1.4709 permId 1101316837 parentId 0 lastFillPrice 1.4709 clientId 200 whyHeld null
			
		}

		@Override
		public void realtimeBar(int reqId, long time, double open, double high, double low, double close,
				long volume, double wap, int count)
		{
			String timeStr = IBDataFormatter.format(new Date(time*1000));
			String hr = timeStr.substring(9, 11);
			String min = timeStr.substring(12, 14);
			String sec = timeStr.substring(15,17);
			//logger.info(reqId + " " + " hr:" + hr + " min:"+min+ " sec:" + sec);
			//System.out.println("reqId:" + reqId + " time:" + time + " high:" + high + " low:" + low + " close:" + close);
			// the goal is once the current bar does not make new high
			for ( int i = 0; i < totalSymbol; i++)
			{
				if (reqId == id_realtime[i])
				{
					if (high > high1min[i])
						high1min[i] = high; 
					if ( low < low1min[i] )
						low1min[i] = low;

					// this is for better entry
					Trade trade = orderManager[i].getTrade();
					if ( trade != null )
					{
						// trade already exists
						QuoteData data = new QuoteData(timeStr, 0.0, high, low, close, 0, 0, wap, false);
						if (trade.status.equals(Constants.STATUS_STOPPEDOUT) || trade.status.equals(Constants.STATUS_OPEN) || trade.status.equals(Constants.STATUS_REVERSAL))
						{
							orderManager[i].trackTradeEntry(data);
						}
						else if (trade.status.equals(Constants.STATUS_PLACED))
						{
							// this is to check whether it needs any "stop and reversal"
							orderManager[i].trackTradeStop(data);
						}
					}
					
					if (sec.equals("00"))
					{
						QuoteData data = new QuoteData(timeStr, 0.0, high1min[i], low1min[i], close, 0, 0, wap, false);
						quotes1m[i].add(data);
						
						if ( quotes1m[i].size() > 1000)
							quotes1m[i].removeElementAt(0); // to prevent it running too long
						
						if ( trade != null )
						{
							if (trade.status.equals(Constants.STATUS_STOPPEDOUT) || trade.status.equals(Constants.STATUS_OPEN) || trade.status.equals(Constants.STATUS_REVERSAL))
							{
								// to add, but the trigger is not really here
								orderManager[i].addTradeEntryData(data);
							}
							else if (trade.status.equals(Constants.STATUS_PLACED))
							{
								// this is to check whether it needs any "stop and reversal"
								double[] sma200_1m = indicator.calculateSMA(quotes1m[i], 200);
								orderManager[i].addTradeStopData(data);
								orderManager[i].trackTradeExit(quotes1m[i], sma200_1m);
							}
						}
						else
						{
							// no trade exist
							double[] sma200_1m = indicator.calculateSMA(quotes1m[i], 200);
							double[] sma20_1m = indicator.calculateSMA(quotes1m[i], 20);

							if (((  trade = calculateStretchHigh(i, quotes1m[i], sma200_1m, sma20_1m, distance[i]) ) != null  ) ||
								((  trade = calculateStretchLow(i, quotes1m[i], sma200_1m, sma20_1m, distance[i]) ) != null))
							{
								trade.POSITION_SIZE = calculatePositionSize();
								trade.status = Constants.STATUS_OPEN;
								orderManager[i].setTrade(trade);
								logger.info(contracts[i].m_symbol +"." + contracts[i].m_currency  + " trade placed ");
								logger.info(trade.toString());
							}
							
						}
						// reset counter
						high1min[i]=0;
						low1min[i]=999;
					}
					
				}
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
		public void tickOptionComputation(int tickerId, int field, double impliedVol, double delta,
				double modelPrice, double pvDividend)
		{
			// TODO Auto-generated method stub
			
		}

		@Override
		public void tickPrice(int tickerId, int field, double price, int canAutoExecute)
		{
			// TODO Auto-generated method stub
			
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
			// TODO Auto-generated method stub
			
		}

		@Override
		public void connectionClosed()
		{
			// TODO Auto-generated method stub
			
		}

		private void writeToFile( String str )
		{

			try {
			        BufferedWriter out = new BufferedWriter(new FileWriter("filename", true));
			        out.write(str );
			        out.close();
			    } catch (IOException e) {
			    }
		}

		private void createContract( int i, int id, String symbol, String type, String exchange, String currency, double dist )
		{
			Contract contract = contracts[i];
	    	contract.m_conId = id;
	    	contract.m_symbol = symbol;
	        contract.m_secType = type;
	        contract.m_exchange = exchange;
	        contract.m_currency = currency;
	        
	        distance[i] = dist;
	        
	        orderManager[i] = new StretchOrderManager2(m_client,contract, i );
		}
		/*
		public void strategyStretchFrom200MA(int symbolIndex, Vector<QuoteData> qts, double[] sma200, double[]sma20, double distance)
		{
			logger.info("strategyStretchFrom200MA");
			int size = qts.size();
			Object[] quotes = qts.toArray();

			Trade trade = orderManager[symbolIndex].getTrade();
			if ( trade != null )
			{
				if (trade.status.equals(Constants.STATUS_STOPPEDOUT) || trade.status.equals(Constants.STATUS_OPEN))
				{
					// to add, but the trigger is not really here
					QuoteData data = (QuoteData)qts.lastElement();
					orderManager[symbolIndex].addTradeEntryData(data);
				}
				else if (trade.status.equals(Constants.STATUS_PLACED))
				{
					// this is to check whether it needs any "stop and reversal"
					orderManager[symbolIndex].trackTradeExit(qts, sma200);
				}
			}

			if (((  trade = calculateStretchHigh(qts, sma200, sma20, distance) ) != null  ) ||
			((  trade = calculateStretchLow(qts, sma200, sma20, distance) ) != null))
			{
				trade.positionSize = calculatePositionSize();
				trade.status = Constants.STATUS_OPEN;
				orderManager[symbolIndex].setTrade(trade);
				logger.info(contracts[symbolIndex].m_symbol +"." + contracts[symbolIndex].m_currency  + " trade placed ");
			}
			
		}
		
		*/
		
		/*
		public Trade calculateStretchHigh( Vector<QuoteData> qts, double[] sma200, double[] sma20, double distance)
		{
			int size = qts.size();
			Object[] quotes = qts.toArray();

			// at least high 20MA once and now it is all time high
			int lastbar = size -1;
			if ((((QuoteData)quotes[lastbar]).close - sma200[lastbar] ) < distance )
			{	
				// this is not stretch enough
				return null; // still keep going up
			}

			logger.info("distance" + distance + " reached");

			//logger.info("calculateStretchHigh" + symbol + " distance " + distance + " reached");
				
			//enter trade;
			Trade tt = new Trade();
			tt.action = Constants.ACTION_SELL;
			tt.status = Constants.STATUS_OPEN;
			tt.reEnter = 5;
			tt.addEntryQuotes((QuoteData)quotes[lastbar]);
			return tt;
		}

		public Trade calculateStretchLow( Vector<QuoteData> qts, double[] sma200, double[] sma20, double distance)
		{
			int size = qts.size();
			Object[] quotes = qts.toArray();

			// at least high 20MA once and now it is all time high
			int lastbar = size -1;
			if ((sma200[lastbar] - ((QuoteData)quotes[lastbar]).close) < distance )
			{	
				// this is not stretch enough
				return null; // still keep going up
			}

			logger.info("distance" + distance + " reached");
				
			//enter trade;
			Trade tt = new Trade();
			tt.action = Constants.ACTION_BUY;
			tt.status = Constants.STATUS_OPEN;
			tt.reEnter = 5;
			tt.addEntryQuotes((QuoteData)quotes[lastbar]);
			return tt;
		}*/

		

		private int calculatePositionSize()
		{
			return 1;
		}
		
		public Trade calculateStretchHigh( int index, Vector<QuoteData> qts, double[] sma200, double[] sma20, double distance)
		{
			int size = qts.size();
			Object[] quotes = qts.toArray();

			// at least is distance from 200ma
			int lastbar = size -1;
			if ((((QuoteData)quotes[lastbar]).close - sma200[lastbar] ) < distance )
			{	
				// this is not stretch enough
				return null; // still keep going up
			}

			logger.info(index + " calculateStretchHigh" + " distance " + distance + " reached");

			// need to look for a pull back to 200MA
			int pos = 0;
			for ( int i = lastbar-1; i >=0; i-- )
			{
				if (((QuoteData)quotes[i]).low <= sma200[i])
				{
					pos = i;
					break;
				}
			}
			
			if (pos == 0)
			 return null;
			
			logger.info("200MA start from pos" + pos + " " + ((QuoteData)quotes[pos]).time);
			
			boolean sma20touched = false;
			int lasttouchpos = 0;
			for ( int i = pos; i < lastbar; i++)
			{
				if (((QuoteData)quotes[i]).low <= sma20[i])
				{
					sma20touched = true;
					lasttouchpos = i;
				}
			}
			
			if (!sma20touched)
				return null;
			else
				logger.info("last touch 20MA at " + ((QuoteData)quotes[lasttouchpos]).time);

			double lasthigh = 0;
			int lasthighpos = 0;
			for ( int i = pos; i <= lasttouchpos; i++)
			{
				if (((QuoteData)quotes[i]).high > lasthigh)
				{
					lasthigh = ((QuoteData)quotes[i]).high;
					lasthighpos = i;
				}
			}
			logger.info("last high is " + lasthigh + " " + ((QuoteData)quotes[lasthighpos]).time);

			
			if (( lasthigh - sma200[lasthighpos])< distance*0.7)
			{
				logger.info("last high did not reach 70% distance " + distance);
				return null;
			}
			else
				logger.info("last high is " + lasthigh);

			if (((QuoteData)quotes[lastbar]).high > lasthigh )
			{
				
				//enter trade;
				Trade tt = new Trade();
				tt.action = Constants.ACTION_SELL;
				tt.status = Constants.STATUS_OPEN;
				tt.reEnter = 5;
				tt.addEntryQuotes((QuoteData)quotes[lastbar]);
				System.out.println("Trade placed:" + tt.toString());
				return tt;
				
			}
			else{
				logger.info("not at the all time high");
				return null;
			}
		}

		
		public Trade calculateStretchLow( int index, Vector<QuoteData> qts, double[] sma200, double[] sma20, double distance)
		{
			int size = qts.size();
			Object[] quotes = qts.toArray();

			int lastbar = size -1;
			if ((sma200[lastbar] - ((QuoteData)quotes[lastbar]).close) < distance )
			{	
				// this is not stretch enough
				return null; // still keep going up
			}

			logger.info(index + " calculateStretchLow" + " distance " + distance + " reached");

			// need to look for a pull back to 20MA
			int pos = 0;
			for ( int i = lastbar-1; i >=0; i-- )
			{
				//System.out.println(((QuoteData)quotes[i]).high + " " + sma200[i]);
				if (((QuoteData)quotes[i]).high >= sma200[i])
				{	pos = i;
					break;
				}
			}
			
			if (pos == 0)
			 return null;
			
			logger.info("200MA start from pos" + pos + " " + ((QuoteData)quotes[pos]).time);
			
			boolean sma20touched = false;
			int lasttouchpos = 0;
			for ( int i = pos; i < lastbar; i++)
			{
				if (((QuoteData)quotes[i]).high >= sma20[i])
				{
					sma20touched = true;
					lasttouchpos = i;
				}
			}
			
			if (!sma20touched)
				return null;
			else
				logger.info("last touch 20MA at " + ((QuoteData)quotes[lasttouchpos]).time);

			double lastlow = 999;
			int lastlowpos = 0;
			for ( int i = pos; i <= lasttouchpos; i++)
			{
				if (((QuoteData)quotes[i]).low < lastlow)
				{
					lastlow = ((QuoteData)quotes[i]).low;
					lastlowpos = i;
				}
			}
			logger.info("last low is " + lastlow + " " + ((QuoteData)quotes[lastlowpos]).time);

			if ((sma200[lastlowpos] - lastlow) < distance * 0.7 )
			{
				logger.info("last low did not reach 70% distance " + distance);
				return null;
			}
			else
				logger.info("last low is " + lastlow);

			if (((QuoteData)quotes[lastbar]).low < lastlow )
			{	
				//enter trade;
				Trade tt = new Trade();
				tt.action = Constants.ACTION_BUY;
				tt.status = Constants.STATUS_OPEN;
				tt.reEnter = 5;
				tt.addEntryQuotes((QuoteData)quotes[lastbar]);
				System.out.println("Trade placed:" + tt.toString());
				return tt;
			}
			else{
				logger.info("not at the all time high");
				return null;
			}
		}

		
		void calculateMovingAverages()
		{
			ema5 = indicator.calculateEMA(quotes, 5);
			ema20 = indicator.calculateEMA(quotes, 20);
			ema50 = indicator.calculateEMA(quotes, 50);
			ema200 = indicator.calculateEMA(quotes, 200);
			
			int last = quotes.size() -1;
			
			
			if (( ema5[last] > ema20[last] ) && (ema5[last-1] < ema20[last-1])) 
			{
				// this is a buy signal
				logger.warning( symbol + "5 cross 20 up :" + ((QuoteData)quotes.elementAt(last)).time +  " buy at " + ((QuoteData)quotes.elementAt(last)).close);
				// if position is open, close position
				
				//else add buy position
				Integer existPos = accountManager.getPosition(contract.m_symbol);
				// logic to handle existing position
				int positionsize = 10;
				if (( existPos != null ) && ( existPos < 0))
				{
					// cover the existing position as well
					positionsize -= existPos;
				}
				
				//Order order = orderManager.createOrder("BUY",positionsize,"MKT",0);
				//m_client.placeOrder(order.m_orderId, contract, order);

			}

			if (( ema5[last] < ema20[last] ) && ( ema5[last-1] > ema20[last-1])) 
			{
				// this is a sell signal
				logger.warning( symbol + "5 cross 20 down :" + ((QuoteData)quotes.elementAt(last)).time +  " buy at " + ((QuoteData)quotes.elementAt(last)).close);

				Integer existPos = accountManager.getPosition(contract.m_symbol);
				// logic to handle existing position
				int positionsize = 10;
				if (( existPos != null ) && ( existPos > 0))
				{
					// cover the existing position as well
					positionsize -= existPos;
				}
				
				//OrderManager orderManager = OrderManager.getInstance();	
				//Order order = orderManager.createOrder("SELL",positionsize,"MKT",0);
				//m_client.placeOrder(order.m_orderId, contract, order);

			}
			
		/*
		    if ((ema5[last-1] > ema20[last-1] ) && (ema20[last-1] > ema50[last-1] ) && (ema50[last-1] > ema200[last-1] ))	
		    {
		    	// this is a perfect 5 /20/50 /200 setup
		    	if ( ema5[last] < ema20[last] )
		    	{
		    		// this is a cross over 20
		    		// if there is position, reduce quarter position
		    		// if there is no position, add quarter position, stop = last top 5 
		    	}
		    }
		    
		    // next time it triggers, it should be like this
		    if ((ema5[last-1] < ema20[last-1] ) && (ema20[last-1] > ema50[last-1] ) && (ema50[last-1] > ema200[last-1] ))	
		    {
		    	// this is a perfect 5 /20/50 /200 setup
		    	if ( ema5[last] < ema50[last] )
		    	{
		    		// this is a cross over 5 over 50
		    		// if there is position, reduce quarter position
		    		// if there is no position, add quarter position, stop = last top 5 
		    	}

		    	if ( ema5[last] > ema20[last] )
		    	{
		    		// this is bource back, get out
		    	}
		    }
		    

		    // the third time it triggers, it should be like this
		    if ((ema5[last-1] < ema20[last-1] ) && (ema5[last-1] > ema50[last-1] ) && (ema50[last-1] > ema200[last-1] ))	
		    {
		    	// this is a perfect 5 /20/50 /200 setup
		    	if ( ema5[last] < ema50[last] )
		    	{
		    		// this is a cross over 5 over 50
		    		// if there is position, reduce quarter position
		    		// if there is no position, add quarter position, stop = last top 5 
		    	}

		    	if ( ema5[last] > ema20[last] )
		    	{
		    		// this is bource back, get out
		    	}
		    }
	*/
		    
		}

	}

