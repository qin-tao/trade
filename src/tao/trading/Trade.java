package tao.trading;

import java.io.Serializable;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Logger;

import tao.trading.dao.PullBackEntriesDAO;
import tao.trading.dao.PushHighLow;
import tao.trading.entry.EntryInf;
import tao.trading.strategy.SFTest;
import tao.trading.strategy.util.NameValue;
import tao.trading.strategy.util.Utility;
import tao.trading.dao.PullBackEntriesDAO;

public class Trade implements Serializable {
	static final long serialVersionUID = 4240866601949723291L;

	// Key properties
	public int id;
	public String symbol;
	public String account;
	public String type;
	public String action;
	public double entryPrice;

	public String entryTime;
	public String entryTime1;
	public String entryTime5;
	public String entryTime15;
	public String entryTime60;
	public String entryTime240;
	public String entryTimeD;
	
	public String entrySetup;
	public int remainingPositionSize;
	public int incrementSize;
	public String status;
	public double closePrice;
	private double program_trailing_stop_size;
	private double program_trailing_start;
	private double program_trailing_end;
	private double program_trailing_stop;
	public String options;
	public boolean marked;
	public boolean enabled;

	public double PIP_SIZE;
	public int FIXED_STOP;
	public int POSITION_SIZE;

	public boolean averageUp;
	public int averageUpSize;
	public boolean reverse;
	public boolean scaleIn;

	public String closeTime;

	// Key positions
	private String firstBreakOutTime;
	private String firstBreakOutStartTime;
	private String touch20MATime;
	public String pullBackStartTime;
	public double pullBackStartPrice;

	public double potentialExitPrice;
	public String potentialExitTime;
	public int potentialExitSize;
	public double potentialPriceAdvance;

	public int slimJimStart, slimJimEnd;

	// order ids
	public int orderId;
	public int stopId;
	public int closeId;

	public boolean filled;
	public double stop;
	public int stopPositionSize;
	public int adjustStop;
	public double triggerPrice;
	public String detectTime;
	public int stoppedOutPos;
	public int detectPos;
	public double detectPrice;
	public int pullBackPos;
	public int reEnter;
	public boolean reach1FixedStop, reach2FixedStop;
	public boolean breakEvenStopPlaced;
	public boolean stopAdjusted;
	public int pullBackSize;
	public int pullBackWidth;
	public boolean initProfitTaken = false;
	public boolean orderFilled;
	public boolean orderPlaced;
	public boolean recordOpened;
	public boolean positionUpdated;
	public boolean limitOrderPlaced;
	public int sequence;
	public int wave2PtL;
	public double detectTimeInMill;
	public long openOrderPlacedTimeInMill, openOrderExpireInMill;
	public long openOrderDurationInHour;
	public int prevDownStart, prevUpStart;
	public String trendStartTime;

	public int pushStartL, pushStart;
	public int triggerPosL, triggerPos;
	public int detectPosStart, detectPosEnd;
	public PushHighLow phl;
	public int pullBackStartL;
	public String eventTime;

	public boolean sort;
	public boolean sendNotification;

	public Object setupDAO;
	public double lastClosePrice;
	// Vector<TradeRecord> tradeRecords;

	// stopmarket order entry
	public boolean stopMarketPlaced;
	public int stopMarketId;
	public int stopMarketSize;
	public double stopMarketStopPrice;
	public double stopMarketLimitPrice;
	public int stopMarketPosFilled;

	// limit is for entry
	public boolean limit1Placed;
	public boolean limit2Placed;
	public int limitId1;
	public int limitId2;
	public double limitPrice1;
	public double limitPrice2;
	public int limitPos1;
	public int limitPos2;
	public int limitPos1Filled;
	public int limitPos2Filled;
	public double limit1Stop;
	public double limit2Stop;
	public String limit1PlacedTime;
	public String limit2PlacedTime;
	// public boolean reachExitBench;

	// target is for exit
	public boolean profitTake1;
	public boolean profitTake2;
	public boolean profitTake3;
	public boolean profitTake4;
	public int takeProfit1Id;
	public int takeProfit2Id;
	public int takeProfitPartialId;
	public int takeProfit1PosSize;
	public int takeProfit2PosSize;
	public int takeProfitPartialPosSize;
	public double takeProfit1Price;
	public double takeProfit2Price;
	public double takeProfitPartialPrice;

	// target for exit
	public boolean targetPlaced;
	public boolean targetPlaced1;
	public boolean targetPlaced2;
	public int targetId;
	public int targetId1;
	public int targetId2;
	public double targetPrice;
	public double targetPrice1;
	public double targetPrice2;
	public int targetPos;
	public int targetPos1;
	public int targetPos2;
	public boolean targetReached;
	public boolean target1Reached;
	public boolean target2Reached;
	
	public boolean startMonitoring;
	public boolean finalPeakExisting;
	public boolean finalExisting;
	public double peakPrice;

	public static int TOTAL_TARGETS = 34;
	public TradePosition[] targets = new TradePosition[TOTAL_TARGETS];
	public int exitTargetPlaced;
	public static int TOTAL_LIMITS = 20;
	public TradePosition[] limits = new TradePosition[TOTAL_LIMITS];

	public ArrayList<TradePosition> filledPositions = new ArrayList();
	public boolean incrementalComplete;
	
	public ArrayBlockingQueue<TradePosition> takeProfitQueue = new ArrayBlockingQueue<TradePosition>(10);
	public ArrayList<TradePosition> takeProfitHistory = new ArrayList<TradePosition>(10);
	public ArrayBlockingQueue<TradePosition> enterPositionQueue = new ArrayBlockingQueue<TradePosition>(10);
	public ArrayList<TradePosition> entryPositionHistory = new ArrayList<TradePosition>(10);
	public ArrayList<TradeEvent> events = new ArrayList<TradeEvent>(5);
	public String lastEvents;

	public int entryMode;
	public Object entryDAO;
	public boolean newLimitEntered;
	public boolean weakBreakOut;
	public boolean targetOnPullback;

	public int monitorHrAfterEntry = 0;
	public int monitorCurrentHour = 0;
	public int monitorProfitInPips = 0;
	
	public QuoteData trendBar;
	
	public EntryInf entryInf;
	public PullBackEntriesDAO pullBackSetup = new PullBackEntriesDAO();

	// public int lastLargeMoveDirection;
	// public QuoteData lastLargeMoveEnd;
	public static Comparator<Trade> DetectTimeComparator = new Comparator<Trade>() {
		public int compare(Trade t1, Trade t2) {
			// ascending order
			return t1.detectTime.compareTo(t2.detectTime);
			// descending order
			// return fruitName2.compareTo(fruitName1);
		}
	};

	public static Comparator<Trade> EntryTimeComparator = new Comparator<Trade>() {
		public int compare(Trade t1, Trade t2) {
			return t1.entryTime.compareTo(t2.entryTime);
		}
	};

	public String toString() {
		StringBuffer strBuf = new StringBuffer();
		strBuf.append("id " + id + " ");
		strBuf.append("type " + type + " ");
		strBuf.append("action " + action + " ");
		strBuf.append("positionSize " + POSITION_SIZE + " ");
		strBuf.append("filled " + filled + " ");
		strBuf.append("target " + targetPrice + " ");
		strBuf.append("stop " + stop + " ");
		strBuf.append("orderId " + orderId + " ");
		strBuf.append("stopId " + stopId + " ");
		strBuf.append("status " + status + " ");
		strBuf.append("reEnter " + reEnter + " ");

		return strBuf.toString();
	}

	public String getTradeStatus() {
		SimpleDateFormat IBDataFormatter = new SimpleDateFormat("yyyyMMdd  HH:mm:ss");
		Date dt = IBDataFormatter.parse(detectTime, new ParsePosition(0));

		return (symbol + " " + action + " " + POSITION_SIZE + " " + status + " " + detectTime + " " + (new Date().getTime() - dt.getTime()) / (60 * 60000L));

	}

	public Trade() {
	}

	public Trade(String symbol) {
		this.symbol = symbol;
		// tradeRecords = new Vector<TradeRecord>();
	}

	public int getReEnter() {
		return --reEnter;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getPullBackStartTime() {
		return pullBackStartTime;
	}

	public void setPullBackStartTime(String pullBackStartTime) {
		this.pullBackStartTime = pullBackStartTime;
	}

	public void setFirstBreakOutTime(String time) {
		firstBreakOutTime = time;
	}

	public String getFirstBreakOutTime() {
		return firstBreakOutTime;
	}

	public int getFirstBreakOutPos(QuoteData[] quotesL) {
		return Utility.findPositionByHour(quotesL, firstBreakOutTime, 1);
	}

	public void setFirstBreakOutStartTime(String time) {
		firstBreakOutStartTime = time;
	}

	public String getFirstBreakOutStartTime() {
		return firstBreakOutStartTime;
	}

	public int getFirstBreakOutStartPosL(QuoteData[] quotesL) {
		return Utility.findPositionByHour(quotesL, firstBreakOutStartTime, 1);
	}

	public void setTouch20MATime(String time) {
		touch20MATime = time;
	}

	public String getTouch20MATime() {
		return touch20MATime;
	}

	public int getTouch20MAPosL(QuoteData[] quotesL) {
		return Utility.findPositionByHour(quotesL, touch20MATime, 1);
	}

	public void setEntryTime(String time) {
		entryTime = time;
	}

	public String getEntryTime() {
		return entryTime;
	}

	public int getEntryTimePosL(QuoteData[] quotesL) {
		return Utility.findPositionByHour(quotesL, entryTime, 1);
	}

	
	
	/*
	 * public void setStopQuotes(QuoteData quote) { if (
	 * Constants.ACTION_SELL.equals(action)) { if ( stopQuote == null )
	 * stopQuote = quote; else if ( quote.high > stopQuote.high) stopQuote =
	 * quote; } else if ( Constants.ACTION_BUY.equals(action)) { if ( stopQuote
	 * == null ) stopQuote = quote; else if ( quote.low < stopQuote.low)
	 * stopQuote = quote; }
	 * 
	 * } /* public void addEntryQuote(QuoteData quote) { entryQuotes.add(quote);
	 * }
	 * 
	 * public void clearEntryQuote() { entryQuotes.clear(); }
	 */
	/*
	 * 
	 * public void recordTrade(String time, String action, int size, double
	 * price) { tradeRecords.add( new TradeRecord( time, action, price, size));
	 * }
	 * 
	 * 
	 * public String getReport(double currentPrice ) { StringBuffer sb = new
	 * StringBuffer();
	 * 
	 * int size = tradeRecords.size();
	 * 
	 * if ( size == 0 ) return null;
	 * 
	 * 
	 * for ( int i = 0; i < size; i++ ) { TradeRecord t =
	 * (TradeRecord)tradeRecords.elementAt(i);
	 * 
	 * if ( t.action.equals(action)) { // this is the entry order if ( i == size
	 * -1) { // appear at last, not covered yet if (
	 * Constants.ACTION_BUY.equals( action )) sb.append( t.time + " " + t.action
	 * + " " + t.size + " " + t.price + "  " + "  unrealized profit: " + (
	 * currentPrice - t.price ) * t.size + "\n"); else if (
	 * Constants.ACTION_SELL.equals( action )) sb.append( t.time + " " +
	 * t.action + " " + t.size + " " + t.price + "  " + "  unrealized profit: "
	 * + ( t.price - currentPrice ) * t.size + "\n"); } else { sb.append( t.time
	 * + " " + t.action + " " + t.size + " " + t.price + "  " + "\n" ); } } else
	 * { // this is the closing orders if ( Constants.ACTION_BUY.equals( action
	 * )) { for ( int j = i-1; j >=0; j-- ) { TradeRecord t2 =
	 * (TradeRecord)tradeRecords.elementAt(j); if ( Constants.ACTION_BUY.equals(
	 * t2.action )) { sb.append( t.time + " " + t.action + " " + t.size + " " +
	 * t.price + "  " + "  realized profit: " + ( t.price - t2.price ) * t.size
	 * + "\n"); break; } } } else if ( Constants.ACTION_SELL.equals( action )) {
	 * for ( int j = i-1; j >=0; j-- ) { TradeRecord t2 =
	 * (TradeRecord)tradeRecords.elementAt(j); if (
	 * Constants.ACTION_SELL.equals( t2.action )) { sb.append( t.time + " " +
	 * t.action + " " + t.size + " " + t.price + "  " + "  realized profit: " +
	 * ( t2.price - t.price ) * t.size + "\n"); break; } } } }
	 * 
	 * 
	 * }
	 * 
	 * /* Iterator<TradeRecord> it = tradeRecords.iterator(); while
	 * (it.hasNext()) { TradeRecord t = (TradeRecord)it.next(); sb.append(
	 * t.action + " " + t.size + " " + t.price + "  " + "\n" ); }
	 */

	/*
	 * return sb.toString();
	 * 
	 * } /* private String calculateTradeRecord(TradeRecord entryTradeRecord,
	 * Vector<TradeRecord> closeTradeRecords, double currentPrice) { String rtn
	 * = "";
	 * 
	 * if ( entryTradeRecord != null ) { int entrySize = entryTradeRecord.size;
	 * if ( Constants.ACTION_BUY.equals( entryTradeRecord.action)) { // long rtn
	 * += entryTradeRecord.size + " @ " + entryTradeRecord.price + "\n" ;
	 * Iterator<TradeRecord> it = closeTradeRecords.iterator(); while
	 * (it.hasNext()) { TradeRecord t = (TradeRecord)it.next(); rtn += (t.size +
	 * " " + t.price + " Realize Profit:" + ( t.price - entryTradeRecord.price )
	 * * t.size + "\n" ); entrySize -= t.size; }
	 * 
	 * // remaining if ( entrySize != 0 ) rtn += "Unrealize Profit:" + (
	 * currentPrice - entryTradeRecord.price ) * entrySize + "\n";
	 * 
	 * } else if ( Constants.ACTION_SELL.equals( entryTradeRecord.action)) { rtn
	 * +=(entryTradeRecord.size + " @ " + entryTradeRecord.price + "\n");
	 * Iterator<TradeRecord> it = closeTradeRecords.iterator(); while
	 * (it.hasNext()) { TradeRecord t = (TradeRecord)it.next(); rtn += t.size +
	 * " " + t.price + " Realize Profit:" + ( entryTradeRecord.price - t.price )
	 * * t.size + "\n"; entrySize -= t.size; }
	 * 
	 * // remaining if ( entrySize != 0 ) rtn += "Unrealize Profit:" + (
	 * entryTradeRecord.price - currentPrice ) * entrySize + "\n";
	 * 
	 * } }
	 * 
	 * return rtn;
	 * 
	 * }
	 */

	public EntryInf getEntryInf() {
		return entryInf;
	}

	public void setEntryInf(EntryInf entryInf) {
		this.entryInf = entryInf;
	}

	public void createProgramTrailingRange(double trailing_start, double trailing_end, double stop_size) {
		if (trailing_start != program_trailing_start) {
			program_trailing_start = trailing_start;
			program_trailing_end = trailing_end;
			program_trailing_stop_size = stop_size;
		}
	}

	public void adjustProgramTrailingStop(QuoteData data) {
		if (program_trailing_stop_size != 0) {
			if (Constants.ACTION_SELL.equals(action)) {
				if (data.low > program_trailing_end) {
					double newStop = data.low + program_trailing_stop_size;
					if (program_trailing_stop == 0)
						program_trailing_stop = newStop;
					else if (newStop < program_trailing_stop)
						program_trailing_stop = newStop;
				}
			} else if (Constants.ACTION_BUY.equals(action)) {
				if (data.high < program_trailing_end) {
					double newStop = data.high - program_trailing_stop_size;
					if (program_trailing_stop == 0)
						program_trailing_stop = newStop;
					else if (newStop > program_trailing_stop)
						program_trailing_stop = newStop;
				}
			}
		}
	}

	public double getProgramTrailingStop() {
		return program_trailing_stop;
	}

	public void resetProgramTrailingStop() {
		program_trailing_stop_size = 0;
		;
	}

	public void setProtentialExit(double potPrice, String potTime, int potSize) {
		if (potentialExitTime != null)
			return;

		potentialExitPrice = potPrice;
		potentialExitTime = potTime;
		potentialExitSize = potSize;
	}

	public void reSetPotentialExit() {
		potentialExitPrice = 0;
		potentialExitTime = null;
		potentialExitSize = 0;
	}

	public boolean findTradeEvent( TradeEvent event ){
		
		int size = events.size();
		if (size > 0) {
			Iterator<TradeEvent> it = events.iterator();
			while (it.hasNext()) {
				TradeEvent last = it.next();
				// should not set the same event within 8 minutes
				if (last.getEventName().equals(event.getEventName())) {
					boolean match = true;
					int nameValueSize = event.nameValues.size();
					if ((nameValueSize > 0) && (nameValueSize == last.nameValues.size())) {
						for (int i = 0; i < nameValueSize; i++) {
							NameValue nv1 = event.nameValues.get(i);
							NameValue nv2 = last.nameValues.get(i);

							if (!nv1.getName().equals(nv2.getName()))
								match = false;

							if (!nv1.getValue().equals(nv2.getValue()))
								match = false;
						}
					}

					if (match == true) // already exist
						return true;
				}
			}
		}
		
		return false;
	}
	
	public void addTradeEvent(TradeEvent event, Logger logger) {
		if (!findTradeEvent(event)){
			if (logger != null)
				logger.info("add trade event:" + event.toString());
			events.add(event);
	
			// send trade notification
		/*	Env_Setting env = SFTest.setting;
			if ("Signal".equalsIgnoreCase(env.props.getProperty(type.trim())) || "Real".equalsIgnoreCase(env.props.getProperty(type.trim()))) {
				if ((env.notification_1 != null) & (env.notification_1_email != null)) {
					StringTokenizer st = new StringTokenizer(env.notification_1, ";");
	
					while (st.hasMoreElements()) {
						String notif = (String) st.nextElement();
	
						if ((notif.indexOf(type.trim()) != -1) && (notif.indexOf(event.eventName) != -1)) {
							if (logger != null)
								logger.info("Send Criticatial Notification for Trade:" + type + " Event:" + event.eventName);
	
							EmailSender es = EmailSender.getInstance();
							es.sendEmail(env.notification_1_email, (event.getHeader() != null) ? event.getHeader() : "" + symbol + " " + type + " " + action + " "
									+ event.toString(), listTradeEvents());
						}
					}
				}
	
				if ((env.notification_2 != null) & (env.notification_2_email != null)) {
					StringTokenizer st = new StringTokenizer(env.notification_2, ";");
	
					while (st.hasMoreElements()) {
						String notif = (String) st.nextElement();
	
						if ((notif.indexOf(type.trim()) != -1) && (notif.indexOf(event.eventName) != -1)) {
							if (logger != null)
								logger.info("Send Notification for Trade:" + type + " Event:" + event.eventName);
	
							EmailSender es = EmailSender.getInstance();
							es.sendEmail(env.notification_2_email, (event.getHeader() != null) ? event.getHeader() : "" + symbol + " " + type + " " + action + " "
									+ event.toString(), listTradeEvents());
						}
					}
				}
			}*/
		}
	}

	public void addTradeEvent(TradeEvent event) {
		addTradeEvent(event, null);
	}

	public void addTradeEvent_update(TradeEvent event) {
		int size = events.size();
		boolean match = false;
		if (size > 0) {
			Iterator<TradeEvent> it = events.iterator();
			while (it.hasNext()) {
				TradeEvent last = it.next();
				// should not set the same event within 8 minutes
				if (last.getEventName().equals(event.getEventName()) && last.getTime().equals(event.getTime())) {
					match = true;
					// update the value
					Iterator<NameValue> nameValueIT2 = event.nameValues.iterator();
					while (nameValueIT2.hasNext()) {
						NameValue nv2 = nameValueIT2.next();
						boolean found = false;
						Iterator<NameValue> nameValueIT1 = last.nameValues.iterator();
						while (nameValueIT1.hasNext()) {
							NameValue nv1 = nameValueIT1.next();
							if (nv1.getName().equals(nv2.getName())) {
								if (nv2.getValue().toString().compareTo(nv1.getValue().toString()) > 0)
									nv1.setValue(nv2.getValue());
								found = true;
								break;
							}
						}

						if (found == false)
							last.nameValues.add(nv2);
					}

				}

			}
		}

		if (!match) // already exist
			events.add(event);
	}

	public ArrayList<TradeEvent> getTradeEvents() {
		return events;
	}

	public String listTradeEvents() {
		StringBuffer sb = new StringBuffer();
		if ((events != null) && (events.size() > 0)) {
			Iterator<TradeEvent> it = events.iterator();
			while (it.hasNext()) {
				TradeEvent ev1 = it.next();
				sb.append("  " + ev1.toString() + "\n");
			}
		}

		return sb.toString();

	}

	public int getProfitLoss() {
		if (Constants.ACTION_BUY.equals(action) && (entryPrice != 0) && (lastClosePrice != 0))
			return (int) ((lastClosePrice - entryPrice) / PIP_SIZE);
		else if (Constants.ACTION_SELL.equals(action) && (entryPrice != 0) && (lastClosePrice != 0))
			return (int) ((entryPrice - lastClosePrice) / PIP_SIZE);
		else
			return 0;

	}

	public void addFilledPosition(double price, int position_size, String position_time) {
		filledPositions.add( new TradePosition(price, position_size, position_time));
	}

	public boolean findFilledPosition(double price) {
		Iterator<TradePosition> it = filledPositions.iterator();
		while ( it.hasNext()){
			TradePosition tp = it.next();
			if ( tp.price == price )
				return true;
		}
		return false;
	}

	
	public boolean findLimitPosition(double price) {
		for ( int i = 0; i < Trade.TOTAL_LIMITS; i++ ){
			if (( limits[i] != null ) && (limits[i].price == price))
			return true;
		}
		return false;
	}

	public boolean findTargetPosition(double price) {
		for ( int i = 0; i < Trade.TOTAL_TARGETS; i++ ){
			if (( targets[i] != null ) && (targets[i].price == price))
			return true;
		}
		return false;
	}
	
	
	public TradePosition[] getFilledPositions() {
		TradePosition [] ret = new TradePosition[filledPositions.size()];	
    	return  filledPositions.toArray(ret);
	}

	public double getStopPrice() {
		
		TradePosition[] filledPositions = getFilledPositions();
		int numOfFilledPosition = filledPositions.length;
		double basePrice = filledPositions[0].price;

		if (Constants.ACTION_BUY.equals(action)) {
			for (double stopPrice = basePrice; stopPrice > stopPrice - 4 * FIXED_STOP * PIP_SIZE; stopPrice -= PIP_SIZE) {
				double stopSize = 0;
				for (int i = 0; i < numOfFilledPosition; i++) {
					stopSize += (filledPositions[i].price - stopPrice) * filledPositions[i].position_size;
				}

				if (stopSize >= FIXED_STOP * PIP_SIZE * POSITION_SIZE)
					return stopPrice;

			}

		} else if (Constants.ACTION_SELL.equals(action)) {
			for (double stopPrice = basePrice; stopPrice < stopPrice + 4 * FIXED_STOP * PIP_SIZE; stopPrice += PIP_SIZE) {
				double stopSize = 0;
				for (int i = 0; i < numOfFilledPosition; i++) {
					stopSize += (stopPrice - filledPositions[i].price) * filledPositions[i].position_size;
				}

				if (stopSize >= FIXED_STOP * PIP_SIZE * POSITION_SIZE)
					return stopPrice;

			}

		}

		return 0;

	}

	public int getCurrentPositionSize() {
		TradePosition[] filledPositions = getFilledPositions();
		int numOfFilledPosition = filledPositions.length;
		int currentPosSize = 0;
		for (int i = 0; i < numOfFilledPosition; i++) {
			currentPosSize += filledPositions[i].position_size;
		}
		return currentPosSize;
	}
	
    public boolean isLimitAlreadyExist( double price )
    {
	    for ( int i = 0; i < Trade.TOTAL_LIMITS; i++ )
	    {
		    if (( limits[i] != null ) && ( limits[i].price == price ))
			    return true;  // order already placed
	    }
	    
	    return false;
    }


}
