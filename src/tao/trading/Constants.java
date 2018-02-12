package tao.trading;

import java.text.SimpleDateFormat;

public class Constants
{
	public static SimpleDateFormat IBDataFormatter = new SimpleDateFormat("yyyyMMdd  HH:mm:ss");
	public static int ERROR = -1;
	public static int STOP = 0;
	public static int CONTINUE = 1;
	
	public static String TRADE_RVS = "RVS";
	public static String TRADE_PULLBACK = "PULLBACK";
	public static String TRADE_RETURN = "RETURN";
	public static String TRADE_123 = "123";
	public static String TRADE_RST = "RST";
	public static String TRADE_RSC = "RSC";
	public static String TRADE_CNT = "CNT";
	public static String TRADE_CCC = "CCC";
	public static String TRADE_R_R = "R_R";
	public static String TRADE_R_C = "R_C";
	public static String TRADE_SLM = "SLM";
	public static String TRADE_TOP = "TOP";
	public static String TRADE_CRS = "CRS";
	public static String TRADE_PBK = "PBK";
	public static String TRADE_EUR = "EUR";
	public static String TRADE_JPY = "JPY";
	public static String TRADE_USD = "USD";
	public static String TRADE_REV = "REV";
	public static String TRADE_MMT = "MMT";
	public static String TRADE_20B = "20B";
	public static String TRADE_SCA = "SCA";
	public static String TRADE_RM = "RM ";
	public static String TRADE_RV = "RV ";
	public static String TRADE_MA = "MA ";
	public static String TRADE_PV = "PV ";

	public static int STATE_UNKNOWN = 0;
	public static int STATE_BREAKOUT_UP = 1;
	public static int STATE_BREAKOUT_DOWN = 2;
	
	public static int TEST_MODE = 0;
	public static int REAL_MODE = 1;
	public static int RECORD_MODE = 2;
	public static int SIGNAL_MODE = 3;
	
	public static int CHART_HOUR = 1;
	public static int CHART_MINUTE = 2;
	public static String HR = "HR";
	public static String MIN = "MIN";
	
	public static String REQUEST_TYPE_SELL_TOP = "selltop";
	public static String REQUEST_TYPE_BUY_BUTTOM = "buybuttom";

    public static String ORDER_TYPE_MKT = "MKT";		//Market Order
    public static String ORDER_TYPE_MKTCLS = "MKTCLS";	//Market On Close Order
    public static String ORDER_TYPE_LMT = "LMT";		//Limit Order
    public static String ORDER_TYPE_LMTCLS = "LMTCLS";	//Limit On Close
    public static String ORDER_TYPE_PEGMKT = "PEGMKT";	//Pegged to Buy on Best Offer/Sell on Best Bid
    public static String ORDER_TYPE_STP = "STP";		//Stop Order
    public static String ORDER_TYPE_STPLMT = "STPLMT";	//Stop Limit Order
    public static String ORDER_TYPE_TRAIL = "TRAIL";	//Trailing Order
    public static String ORDER_TYPE_REL = "REL";		//Relative Order
    public static String ORDER_TYPE_VWAP = "VWAP";		//Volume-Weighted Avg Price Order
	
    public static String ACTION_BUY = "BUY";
    public static String ACTION_SELL = "SELL";		
    public static String ACTION_REVERSAL = "REVERSAL";		

    public static String POSITION_LONG = "Long";		
    public static String POSITION_SHORT = "Short";		
    
    public static String TRIGGER_TOPBUTTOM = "Trigger topbottom";		
    public static String TRIGGER_CROSSOVER = "Trigger crossover";		
    public static String TRIGGER_STRETCH = "Trigger stretch";		
    public static String TRIGGER_NOSTRENGTH = "Trigger no strength";		
    public static String TRIGGER_ADX = "Trigger adx";		
    public static String TRIGGER_DEFAULT = "Trigger default";		
    public static String TRIGGER_S_CROSS = "Trigger S Cross";		

    public static String STATUS_DETECTED = "DETECTED";		
    public static String STATUS_OPEN = "OPEN";		
    public static String STATUS_PLACED = "PLACED";		
    public static String STATUS_STOPPEDOUT = "STOPPEDOUT";		
    public static String STATUS_REVERSAL = "REVERSAL";		
    public static String STATUS_CLOSED = "CLOSED";		
    public static String STATUS_EXITING = "EXITING";	
    public static String STATUS_PARTIAL_FILLED = "PARTIAL_FILLED";	
    public static String STATUS_ONE_MORE = "ONEMORE";	
    public static String STATUS_ONE_MORE_CLEARED = "ONEMORECLEARED";	
    public static String STATUS_EXTENDED = "EXTENDED";	
    public static String STATUS_FILLED = "FILLED";	
    public static String STATUS_INCREMENTAL_COMPLETED = "STATUS_INCREMENTAL_COMPLETED";	
    
    public static int DIRECTION_UP = 1;
    public static int DIRECTION_DOWN = -1;
    public static int DIRECTION_UNKNOWN = 0;

    public static String SMA200 = "SMA200";
    public static String NUMSMA200 = "SMA200";
    
    public static int MAX_RETRY=3;
    public static String ACCOUNT="DU31237";
    
    public static double TARGET_200MA = -1;
    
    public static int NUM_REENTER_STRETCH = 3;
    
    public static int EXIT_STATUS_RUN = 0;
    public static int EXIT_STATUS_PULLBACK=1;
    
    public static int SCALE_IN_BIG_PULLBACK = 1;
    public static int SCALE_IN_LONG_DISTANCE = 2;
    
    public static int PRICE_TYPE_40 = 40;
    public static int PRICE_TYPE_4 = 4;
    public static int PRICE_TYPE_3 = 3;
    public static int PRICE_TYPE_2 = 2;
    
    public static int ADJUST_TYPE_UP = 1;
    public static int ADJUST_TYPE_DOWN = -1;
    
    public static int NOT_FOUND = -1;
	public static Integer touch40 = new Integer(40);
	public static Integer touch20 = new Integer(20);

    public static int CHART_5_SEC = 0;
    public static int CHART_1_MIN = 1;
    public static int CHART_3_MIN = 2;
    public static int CHART_5_MIN = 3;
    public static int CHART_10_MIN = 4;
    public static int CHART_15_MIN = 5;
    public static int CHART_30_MIN = 6;
    public static int CHART_60_MIN = 7;
    public static int CHART_240_MIN = 8;
    public static int CHART_DAILY = 9;
    public static int TOTAL_CHARTS = 10;
    
    /*
    public static int CHART_1 = 0;
    public static int CHART_3 = 1;
    public static int CHART_5 = 2;
    public static int CHART_10 = 3;
    public static int CHART_15 = 4;
    public static int CHART_30 = 5;
    public static int CHART_60 = 6;
    public static int CHART_240 = 7;
    public static int MAX_CHARTS = 8;
*/
    public static int STOPSTATUS_STOP = 0;
    public static int STOPSTATUS_DOUBLEUP = 1;
    public static int STOPSTATUS_EXTEND1 = 2;
    public static int STOPSTATUS_EXTEND2 = 3;
    
    public static int TREND_UNKNOWN = 0;
    public static int TREND_UP = 1;
    public static int TREND_DOWN = -1;
    public static int TREND_UP_BREAK = 2;
    public static int TREND_DOWN_BREAK = -2;

    public static int FRONT_TO_BACK = 1;
    public static int BACK_TO_FRONT = 2;

    public static int AMS = 1;
    public static int EMEA = 2;
    public static int APJ = 3;
    
    public static int TIME_ZONE_12 = 12;
    public static int TIME_ZONE_13 = 13;
    public static int TIME_ZONE_23 = 23;
    public static int TIME_ZONE_3 = 3;
    
    public static String CMD_UPDATE = "update";
    public static String CMD_CLOSE = "close";
    public static String CMD_CLOSE_OUTSTANDING_ORDERS="outstanding_order";
    
    public static int TICK_BID = 1;
    public static int TICK_ASK = 2;
    public static int TICK_LAST = 4;
    
    public static String DIRT_DOWN = "DIRECTION_DOWN";
    public static String DIRT_DOWN_PULLBACK = "DIRECTION_DOWN_PULLBACK";
    public static String DIRT_DOWN_PULLBACK_RESUME = "DIRECTION_DOWN_PULLBACK_RESUME";
    public static String DIRT_DOWN_SEC_2 = "DIRECTION_DOWN_SEC_2";
    public static String DIRT_DOWN_SMALL_2 = "DIRECTION_DOWN_SMALL_2";
    public static String DIRT_DOWN_REVERSAL = "DIRECTION_DOWN_REVERSAL";
    public static String DIRT_DOWN_FADING = "DIRECTION_DOWN_FADING";
    public static String DIRT_UP = "DIRECTION_UP";
    public static String DIRT_UP_PULLBACK = "DIRECTION_UP_PULLBACK";
    public static String DIRT_UP_PULLBACK_RESUME = "DIRECTION_UP_PULLBACK_RESUME";
    public static String DIRT_UP_SEC_2 = "DIRECTION_UP_SEC_2";
    public static String DIRT_UP_SMALL_2 = "DIRECTION_UP_SMALL_2";
    public static String DIRT_UP_REVERSAL = "DIRECTION_UP_REVERSAL";
    public static String DIRT_UP_FADING = "DIRECTION_UP_FADING";
    public static String DIRT_UNKNOWN = "DIRECTION_UNKNOWN";

    public static int ANALYSIS_UNKNOWN = 0;
    public static int ANALYSIS_SKIP = 1;
    public static int ANALYSIS_REVERSE = 2;

    public static String SETUP_EARLY_PULLBACK = "EP";
    public static String SETUP_LATE_PULLBACK = "LP";
    public static String SETUP_TREND_ABOUT_TO_CHANGE = "TC";
    public static String SETUP_LIMIT = "L";
    public static String SETUP_REVERSE = "R";
    public static String SETUP_TRACK = "T";
    public static String SETUP_SCALE_IN = "S";

    public static String ENTRY_TIP = "TIP_ENTRY";
    public static String ENTRY_LAST_PULLBACK = "LAST_PULL_BACK_ENTRY";
    public static String ENTRY_FALL_OFF_BOTTOM = "FALL_OFF_BUTTOM_ENTRY";
    
    public static int ENTRY_MODE_INCREMENTAL = 1;
    
    public static String ENTRY_INCRE_FIXED = "FI";
    
}
