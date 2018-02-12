package tao.trading.setup;

import java.util.Vector;

import tao.trading.Constants;
import tao.trading.Indicator;
import tao.trading.Pattern;
import tao.trading.QuoteData;
import tao.trading.dao.PushHighLow;
import tao.trading.dao.PushList;
import tao.trading.dao.PushTrendDAO;
import tao.trading.strategy.util.Utility;

public class PushSetup
{

	/********************************************
     * Main setup algorithm
     *******************************************/
    public static PushList findPastHighsByX(QuoteData[] quotes, int start, int lastbar, int NumbOfBarsInGap )
    {
    	QuoteData lastHigh = Utility.getHigh(quotes, start, lastbar-1);

    	if ( lastHigh == null )
    		return null;
			//return new PushList(Constants.DIRECTION_UP, null, start, lastbar);  // not the highest

    	if (((QuoteData)quotes[lastbar]).high < lastHigh.high )
    		return null;
			//return new PushList(Constants.DIRECTION_UP, null, start, lastbar);  // not the highest
		
    	Vector<PushHighLow> phls = new Vector<PushHighLow>();

    	int start0 = start;
    	while (((start0+1) < lastbar ) && (quotes[start0+1].high < quotes[start0].high ))
    		start0++;
    	if ( start0 == lastbar)
			return null;  															// not the highest
    	
    	PushHighLow phl = null;
   		for ( int i = start; i <= lastbar-2; i++ ){
   			// looking for the next high
    		for ( int j = i+1; j <= lastbar; j++  ){
    			if ( quotes[j].high > quotes[i].high){
    				if ((j - i) > NumbOfBarsInGap){  
    	    			phl = new PushHighLow(i, j);
    	    			phl.pullBack = Utility.getLow( quotes, i, j); 
    	    			phls.add(phl);
        				i = j-1;  // will be added 1 by the for i loop  
    				}
	    			break;
    			}
    		}
    	}
   		
   		if (( phl == null ) || (( phl != null ) && ( phl.curPos != lastbar)))
   			return null;
   		
    	int size = phls.size();
    	PushHighLow [] ret = new PushHighLow[size];
    	phls.toArray(ret);
    	
    	// 0 is the lastbar
    	if ( size > 0 )
    	{
    		ret[0].pushStart = start;
	    	for ( int i = 1; i <= size-1; i++)
	    		ret[i].pushStart = ret[i-1].pullBack.pos;
	    	
	    	for ( int i = 0; i < size; i++ )
	    	{
	    		ret[i].pushSize =  quotes[ret[i].prePos].high - quotes[ret[i].pushStart].low;
	    		ret[i].pullBackSize = quotes[ret[i].prePos].high - ret[i].pullBack.low;
	    		ret[i].pullBackRatio = ret[i].pullBackSize/ret[i].pushSize;
	    	}

    		//ret[0].breakOutSize = ret[0].pushSize;
	    	for ( int i = 0; i < size-1; i++)
	    		ret[i].breakOutSize = quotes[ret[i+1].prePos].high - quotes[ret[i].prePos].high;

    	}
    	
    	return new PushList(Constants.DIRECTION_UP, ret, start, lastbar);

     }
    
    
    
    public static PushList findPastLowsByX(QuoteData[] quotes, int start, int lastbar, int NumbOfBarsInGap )
    {
    	QuoteData lastLow = Utility.getLow(quotes, start, lastbar-1);

    	if ( lastLow == null )
			return null;
    		//return new PushList(Constants.DIRECTION_DOWN, null, start, lastbar);  // not the lowest
    	
		if (((QuoteData)quotes[lastbar]).low > lastLow.low )
			return null;
			//return new PushList(Constants.DIRECTION_DOWN, null, start, lastbar);  // not the lowest
		
    	Vector<PushHighLow> phls = new Vector<PushHighLow>();

    	
    	int start0 = start;
    	while ( ((start0+1) < lastbar ) && (quotes[start0+1].low > quotes[start0].low ))
    			start0++;
    	if ( start0 == lastbar)
			return null; 	 														// not the lowest
		
    	
    	PushHighLow phl = null;
    	for ( int i = start; i <= lastbar-2; i++ ){
   			// looking for the next low
    		for ( int j = i+1; j <= lastbar; j++  ){
    			if ( quotes[j].low < quotes[i].low){
    				if ((j - i) > NumbOfBarsInGap ){  
    	    			phl = new PushHighLow(i, j);
    	    			phl.pullBack = Utility.getHigh( quotes, i, j); 
    	    			phls.add(phl);
        				i = j-1;  // will be added 1 by the for i loop  
    				}
	    			break;
    			}
    		}
    	}

   		if (( phl == null ) || (( phl != null ) && ( phl.curPos != lastbar)))
   			return null;
    	
    	int size = phls.size();
    	PushHighLow [] ret = new PushHighLow[size];
    	phls.toArray(ret);
    	
    	// 0 is the lastbar
    	if ( size > 0 )
    	{
    		ret[0].pushStart = start;
	    	for ( int i = 1; i <= size-1; i++)
	    		ret[i].pushStart = ret[i-1].pullBack.pos;
	    	
	    	for ( int i = 0; i < size; i++ )
	    	{
	    		ret[i].pushSize = quotes[ret[i].pushStart].high - quotes[ret[i].prePos].low;
	    		ret[i].pullBackSize = ret[i].pullBack.high - quotes[ret[i].prePos].low;
	    		ret[i].pullBackRatio = ret[i].pullBackSize/ret[i].pushSize;

	    		ret[i].pushExt = ret[i].curPos;
		    	while (( (ret[i].pushExt+1) <= lastbar) && ( quotes[ret[i].pushExt+1].low < quotes[ret[i].pushExt].low))
		    		ret[i].pushExt++;
	    	}

    		//ret[0].breakOutSize = ret[0].pushSize;
	    	for ( int i = 0; i < size-1; i++)
	    		ret[i].breakOutSize = quotes[ret[i].prePos].low - quotes[ret[i+1].prePos].low;

    	}
    	
    	return new PushList(Constants.DIRECTION_DOWN, ret, start, lastbar);

     }
    
    
    
    

	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	public static PushList getUp2PushList( QuoteData[] quotes, int start, int lastbar )
	{
		for ( int i = lastbar; i > start+1; i--)
		{	
			PushList pushList = Pattern.findPast2Highs2(quotes, start, i);

			if ((pushList != null ) && (pushList.phls != null) && (pushList.phls.length > 0 ))
			{
				return pushList;
			}
		}
		return null;
	}


	
	public static PushList getDown2PushList( QuoteData[] quotes, int start, int lastbar )
	{
		for ( int i = lastbar; i > start+1; i--)
		{	
			PushList pushList = Pattern.findPast2Lows2(quotes, start, i);

			if ((pushList != null ) && (pushList.phls != null) && (pushList.phls.length > 0 ))
			{
				return pushList;
			}
		}
		return null;
	}


	public static PushList getUp1PushList( QuoteData[] quotes, int start, int lastbar )
	{
		for ( int i = lastbar; i > start+1; i--)
		{	
			PushList pushList = Pattern.findPast1Highs1(quotes, start, i);

			if ((pushList != null ) && (pushList.phls != null) && (pushList.phls.length > 0 ))
			{
				return pushList;
			}
		}
			
		
		return null;
	}


	
	public static PushList getDown1PushList( QuoteData[] quotes, int start, int lastbar )
	{
		for ( int i = lastbar; i > start+1; i--)
		{	
			PushList pushList = Pattern.findPast1Lows1(quotes, start, i);

			if ((pushList != null ) && (pushList.phls != null) && (pushList.phls.length > 0 ))
			{
				return pushList;
			}
		}
			
		return null;
	}


	
	


/*    
    
    
	public static PushList findPastHighsByX(QuoteData[] quotes, int start, int lastbar, int NumbOfBarsInGap )
    {
    	QuoteData lastHigh = Utility.getHigh(quotes, start, lastbar-1);

		if (((QuoteData)quotes[lastbar]).high < lastHigh.high )
			return null;  // not the highest
		
    	Vector<PushHighLow> phls = new Vector<PushHighLow>();

    	while( lastbar > start+1 ){
    		for ( int i = lastbar-1; i > start; i-- ){
    			QuoteData afterHigh = Utility.getHigh( quotes, i+1, lastbar);
    			if (( afterHigh == null ) || ( afterHigh.high < ((QuoteData)quotes[i]).high))
    				continue;
    			
    			QuoteData beforeHigh = Utility.getHigh( quotes, start, i-1);
    			if (( beforeHigh == null ) || ( beforeHigh.high < ((QuoteData)quotes[i]).high))
    				continue;
    			
    			if ( afterHigh.pos - beforeHigh.pos < NumbOfBarsInGap + 1 )
    				continue;
    			
    			// now there is a "before" and a "after"
    			PushHighLow phl = new PushHighLow(beforeHigh.pos, afterHigh.pos);
    			phl.pullBack = Utility.getLow( quotes, beforeHigh.pos, afterHigh.pos); 
    			phls.add(phl);
    			
    			lastbar = beforeHigh.pos;
    			break;
    		}
    		lastbar--;
    	}
    	
    	int size = phls.size();
    	
    	if ( size > 0 ){
    		PushHighLow [] ret = new PushHighLow[size];
    		phls.toArray(ret);
    	
    		ret[size-1].pushStart = start;
	    	for ( int i = size-2; i >= 0 ; i--)
	    		ret[i].pushStart = ret[i+1].pullBack.pos;
	    	
	    	for ( int i = 0; i < size; i++ ){
	    		ret[i].pushSize =  quotes[ret[i].prePos].high - quotes[ret[i].pushStart].low;
	    		ret[i].pullBackSize = quotes[ret[i].prePos].high - ret[i].pullBack.low;
	    		ret[i].pullBackRatio = ret[i].pullBackSize/ret[i].pushSize;
	    	}
	    	return new PushList(Constants.DIRECTION_UP, ret, start, lastbar);
    	}
    	return null;
    }
    
    
    public static PushList findPastLowsByX(QuoteData[] quotes, int start, int lastbar, int NumbOfBarsInGap )
    {
    	QuoteData lastLow = Utility.getLow(quotes, start, lastbar-1);

		if (((QuoteData)quotes[lastbar]).low > lastLow.low )
			return null;  // not the lowest
		
    	Vector<PushHighLow> phls = new Vector<PushHighLow>();

    	while( lastbar > start+1){
    		for ( int i = lastbar-1; i > start; i-- ){
    			QuoteData afterLow = Utility.getLow( quotes, i+1, lastbar);
    			if (( afterLow == null ) || ( afterLow.low > ((QuoteData)quotes[i]).low))
    				continue;
    			
    			QuoteData beforeLow = Utility.getLow( quotes, start, i-1);
    			if (( beforeLow == null ) || ( beforeLow.low > ((QuoteData)quotes[i]).low))
    				continue;

    			if ( afterLow.pos - beforeLow.pos < NumbOfBarsInGap + 1 )
    				continue;

    			// now there is a "before" and a "after"
    			PushHighLow phl = new PushHighLow(beforeLow.pos, afterLow.pos);
    			phl.pullBack = Utility.getHigh( quotes, beforeLow.pos, afterLow.pos); 
    			phls.add(phl);
    			
    			lastbar = beforeLow.pos;
    			break;
    		}
    		lastbar--;
    	}
    	
    	int size = phls.size();
    	
    	if ( size > 0 ){
    		PushHighLow [] ret = new PushHighLow[size];
    		phls.toArray(ret);

	    	// 0 is the lastbar
	    	ret[size-1].pushStart = start;
	    	for ( int i = size-2; i >= 0 ; i--)
	    		ret[i].pushStart = ret[i+1].pullBack.pos;
	    	
	    	for ( int i = 0; i < size; i++ ){
	    		ret[i].pushSize = quotes[ret[i].pushStart].high - quotes[ret[i].prePos].low;
	    		ret[i].pullBackSize = ret[i].pullBack.high - quotes[ret[i].prePos].low;
	    		ret[i].pullBackRatio = ret[i].pullBackSize/ret[i].pushSize;
	    	}
	    	return new PushList(Constants.DIRECTION_DOWN, ret, start, lastbar);
    	}
    	return null;
     }

	public static PushList findPastHighsByX2(QuoteData[] quotes, int start, int lastbar, int NumbOfBarsInGap )
    {
    	QuoteData lastHigh = Utility.getHigh(quotes, start, lastbar-1);

		if (((QuoteData)quotes[lastbar]).high < lastHigh.high )
			return null;  // not the highest
		
    	Vector<PushHighLow> phls = new Vector<PushHighLow>();

    	int start0 = start+1;
    	while( start0 < lastbar){
    		for ( int i = start0+1; i < lastbar; i++ ){
    			QuoteData afterHigh = Utility.getHigh( quotes, i+1, lastbar);
    			if (( afterHigh == null ) || ( afterHigh.high < ((QuoteData)quotes[i]).high))
    				continue;
    			
    			QuoteData beforeHigh = Utility.getHigh( quotes, start, i-1);
    			if (( beforeHigh == null ) || ( beforeHigh.high < ((QuoteData)quotes[i]).high))
    				continue;
    			
    			if ( afterHigh.pos - beforeHigh.pos < NumbOfBarsInGap + 1 )
    				continue;
    			
    			// now there is a "before" and a "after"
    			PushHighLow phl = new PushHighLow(beforeHigh.pos, afterHigh.pos);
    			phl.pullBack = Utility.getLow( quotes, beforeHigh.pos, afterHigh.pos); 
    			phls.add(phl);
    			
    			start0 = afterHigh.pos;
    			break;
    		}
    		start0++;
    	}
    	
    	int size = phls.size();
    	
    	if ( size > 0 ){
    		PushHighLow [] ret = new PushHighLow[size];
    		phls.toArray(ret);
    	
    		ret[size-1].pushStart = start;
	    	for ( int i = size-2; i >= 0 ; i--)
	    		ret[i].pushStart = ret[i+1].pullBack.pos;
	    	
	    	for ( int i = 0; i < size; i++ ){
	    		ret[i].pushSize =  quotes[ret[i].prePos].high - quotes[ret[i].pushStart].low;
	    		ret[i].pullBackSize = quotes[ret[i].prePos].high - ret[i].pullBack.low;
	    		ret[i].pullBackRatio = ret[i].pullBackSize/ret[i].pushSize;
	    	}
	    	return new PushList(Constants.DIRECTION_UP, ret, start, lastbar);
    	}
    	return null;
    }
    
    public static PushList findPastLowsByX2(QuoteData[] quotes, int start, int lastbar, int NumbOfBarsInGap )
    {
    	QuoteData lastLow = Utility.getLow(quotes, start, lastbar-1);

		if (((QuoteData)quotes[lastbar]).low > lastLow.low )
			return null;  // not the lowest
		
    	Vector<PushHighLow> phls = new Vector<PushHighLow>();

    	int start0 = start+1;
    	while( start0 < lastbar){
    		for ( int i = start0+1; i < lastbar; i++ ){
    			QuoteData afterLow = Utility.getLow( quotes, i+1, lastbar);
    			if (( afterLow == null ) || ( afterLow.low > ((QuoteData)quotes[i]).low))
    				continue;
    			
    			QuoteData beforeLow = Utility.getLow( quotes, start, i-1);
    			if (( beforeLow == null ) || ( beforeLow.low > ((QuoteData)quotes[i]).low))
    				continue;

    			if ( afterLow.pos - beforeLow.pos < NumbOfBarsInGap + 1 )
    				continue;

    			// now there is a "before" and a "after"
    			PushHighLow phl = new PushHighLow(beforeLow.pos, afterLow.pos);
    			phl.pullBack = Utility.getHigh( quotes, beforeLow.pos, afterLow.pos); 
    			phls.add(phl);
    			
    			start0 = afterLow.pos;
    			break;
    		}
    		start0++;
    	}
    	
    	int size = phls.size();
    	
    	if ( size > 0 ){
    		PushHighLow [] ret = new PushHighLow[size];
    		phls.toArray(ret);

	    	// 0 is the lastbar
	    	ret[size-1].pushStart = start;
	    	for ( int i = size-2; i >= 0 ; i--)
	    		ret[i].pushStart = ret[i+1].pullBack.pos;
	    	
	    	for ( int i = 0; i < size; i++ ){
	    		ret[i].pushSize = quotes[ret[i].pushStart].high - quotes[ret[i].prePos].low;
	    		ret[i].pullBackSize = ret[i].pullBack.high - quotes[ret[i].prePos].low;
	    		ret[i].pullBackRatio = ret[i].pullBackSize/ret[i].pushSize;
	    	}
	    	return new PushList(Constants.DIRECTION_DOWN, ret, start, lastbar);
    	}
    	return null;
     }
  */  
    
    
    
    
    
    
    
    
    
    
    public static PushTrendDAO getPushTrend( QuoteData[] quotes, double PIP_SIZE ){
    	int TOTAL_BARS = 120;
    	int MID_POINT = 60;
    	int lastbar = quotes.length -1;
    	PushList largestUpPush = new PushList(0,null,0,0);
    	PushList largestDownPush = new PushList(0,null,0,0);;
    	PushTrendDAO pushTrendDAO = null;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
    	
    	outer: for ( int i = lastbar; i > lastbar-MID_POINT; i-- ){
    		for ( int j = i-2; j > ((lastbar-TOTAL_BARS>0)?lastbar-TOTAL_BARS:0); j--){
    		//for ( int j = i-2; j > (lastbar-TOTAL_BARS); j--){
    			//System.out.println(i + " " + j );

    			PushList pushList = findPast1Highs1(quotes, j, i);
    			if ((pushList != null ) && (pushList.phls != null) && (pushList.phls.length > 0 )){
    				
    				boolean qualify = true;
    				if ( pushList.phls.length == 1 ){
    					PushHighLow phl = pushList.phls[0];
    					if ((phl.prePos >= 2 ) &&!((quotes[phl.prePos-1].high<quotes[phl.prePos].high) &&  (quotes[phl.prePos-2].high<quotes[phl.prePos].high)))
    						qualify = false;

    					/*if ( phl.pullBack.low < quotes[phl.pushStart].low )
    						qualify = false;

    					QuoteData highest = Utility.getHigh(quotes, phl.curPos, lastbar);
       					if (( highest.high - quotes[phl.prePos].high) < ( 8 * PIP_SIZE ))
       						qualify = false; */
    					
    				}
    				
    				if (qualify){
	    				largestUpPush = pushList;
	    				int start = pushList.begin;
	    				boolean in=false;
	    				while ( --start > 0){
	    	    			pushList = findPast1Highs1(quotes, start, i);
	    	    			if ( pushList == null )
	    	    				break outer;	
	    	    			else if (pushList.getNumOfPushes() > largestUpPush.getNumOfPushes())
	    	    				largestUpPush = pushList;
	    	    			if ( quotes[start].high < ema20[start])
	    	    				in = true;
	    	    			if (( in == true ) && !( quotes[start].high < ema20[start]))
	    	    				break outer;
	    				}
    				}
    			}
    		}
    	}
    			
    	outer: for ( int i = lastbar; i > lastbar-MID_POINT; i-- ){
    		for ( int j = i-2; j > ((lastbar-TOTAL_BARS>0)?lastbar-TOTAL_BARS:0); j--){
    			PushList pushList = findPast1Lows1(quotes, j, i);
    			if ((pushList != null ) && (pushList.phls != null) && (pushList.phls.length > 0 )){
    				
    				boolean qualify = true;
    				if ( pushList.phls.length == 1 ){
    					PushHighLow phl = pushList.phls[0];
    					if ((phl.prePos >= 2 ) && !((quotes[phl.prePos-1].low > quotes[phl.prePos].low) &&  (quotes[phl.prePos-2].low>quotes[phl.prePos].low)))
    						qualify = false;

    					/*if ( phl.pullBack.high > quotes[phl.pushStart].high )
    						qualify = false;
    					
    					QuoteData lowest = Utility.getLow(quotes, phl.curPos, lastbar);
    					if (( quotes[phl.prePos].low - lowest.low ) < ( 8 * PIP_SIZE ))
    						qualify = false; */

    				}
   					
    				if (qualify){
	    				largestDownPush = pushList;
	    				int start = pushList.begin;
	    				boolean in=false;
	    				while ( --start > 0){
	    	    			pushList = findPast1Lows1(quotes, start, i);
	    	    			if ( pushList == null )
	    	    				break outer;
	    	    			else if (pushList.getNumOfPushes() > largestUpPush.getNumOfPushes())
	    	    				largestDownPush = pushList;
	       	    			if ( quotes[start].low > ema20[start])
	    	    				in = true;
	    	    			if (( in == true ) && !( quotes[start].low > ema20[start]))
	    	    				break outer;
	    				}
    				}
    			}
    		}
    	}

    	if ( largestUpPush != null ){
    		
    		if ((largestDownPush == null) || ( largestUpPush.end > largestDownPush.end )){

    			// this largestUpPush.begin is not really the lowest point
    			int lowestPoint = Utility.getLow(quotes, (largestUpPush.begin-2>=0?largestUpPush.begin-2:0), largestUpPush.end).pos;
				PushList largestPrevDownPush = null;
				
			   	for ( int i = lowestPoint; i > lowestPoint - 10; i-- ){
		    		for ( int j = i-2; j > ((lowestPoint-TOTAL_BARS>0)?lowestPoint-TOTAL_BARS:0); j--){
		    			PushList pushList = findPast1Lows1(quotes, j, i);
		    			if ((pushList != null ) && (pushList.phls != null) && (pushList.phls.length > 0 )){
		    				
			    	    	if (( largestPrevDownPush == null ) || (pushList.getNumOfPushes() > largestPrevDownPush.getNumOfPushes()))
			    	    		largestPrevDownPush = pushList;
		   				}
		   			}
			   	}
		  		
			   	return new PushTrendDAO(Constants.DIRECTION_UP, largestUpPush, largestPrevDownPush);
    		}
    	}
    	
    	if ( largestDownPush != null ){

    		if ((largestUpPush == null) || ( largestDownPush.end > largestUpPush.end )){

    			int highestPoint = Utility.getHigh(quotes, (largestDownPush.begin-2>=0?largestDownPush.begin-2:0), largestDownPush.end).pos;
				PushList largestPrevUpPush = null;
				
			   	for ( int i = highestPoint; i > highestPoint-10; i-- ){
		    		for ( int j = i-2; j > ((highestPoint-TOTAL_BARS>0)?highestPoint-TOTAL_BARS:0); j--){
		    			PushList pushList = findPast1Highs1(quotes, j, i);
		    			if ((pushList != null ) && (pushList.phls != null) && (pushList.phls.length > 0 )){
		    				
			    	    	if (( largestPrevUpPush == null ) || (pushList.getNumOfPushes() > largestPrevUpPush.getNumOfPushes()))
			    	    		largestPrevUpPush = pushList;
		   				}
		   			}
			   	}
		  		
			   	return new PushTrendDAO(Constants.DIRECTION_DOWN, largestDownPush, largestPrevUpPush);
    		}
    	}
    				
    	return null;
    }
    

    
    
    public static PushTrendDAO getPushTrend2( QuoteData[] quotes, double PIP_SIZE ){
    	int TOTAL_BARS = 120;
    	int MID_POINT = 60;
    	int lastbar = quotes.length -1;
    	PushList largestUpPush = new PushList(0,null,0,0);
    	PushList largestDownPush = new PushList(0,null,0,0);;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
    	
    	outer: for ( int i = lastbar; i > lastbar-MID_POINT; i-- ){
    		for ( int j = i-2; j > ((lastbar-TOTAL_BARS>0)?lastbar-TOTAL_BARS:0); j--){

    			//PushList pushList = findPast1Highs1(quotes, j, i);
    			PushList pushList = findPastHighsByX(quotes, j, i,1);
    			if ((pushList != null ) && (pushList.phls != null) && (pushList.phls.length > 0 )){
    				
    				boolean qualify = true;
    				if ( pushList.phls.length == 1 ){
    					PushHighLow phl = pushList.phls[0];
    					if ((phl.prePos >= 2 ) &&!((quotes[phl.prePos-1].high<quotes[phl.prePos].high) &&  (quotes[phl.prePos-2].high<quotes[phl.prePos].high)))
    						qualify = false;
    				}
    				
    				if (qualify){
	    				largestUpPush = pushList;
	    				int start = pushList.begin;
	    				boolean in=false;
	    				while ( --start > 0){
	    	    			pushList = findPastHighsByX(quotes, j, i,1);
	    	    			if ( pushList == null )
	    	    				break outer;	
	    	    			else if (pushList.getNumOfPushes() > largestUpPush.getNumOfPushes())
	    	    				largestUpPush = pushList;
	    	    			if ( quotes[start].high < ema20[start])
	    	    				in = true;
	    	    			if (( in == true ) && !( quotes[start].high < ema20[start]))
	    	    				break outer;
	    				}
    				}
    			}
    		}
    	}
    			
    	outer: for ( int i = lastbar; i > lastbar-MID_POINT; i-- ){
    		for ( int j = i-2; j > ((lastbar-TOTAL_BARS>0)?lastbar-TOTAL_BARS:0); j--){
    			//PushList pushList = findPast1Lows1(quotes, j, i);
    			PushList pushList = findPastLowsByX(quotes, j, i,1);
    			if ((pushList != null ) && (pushList.phls != null) && (pushList.phls.length > 0 )){
    				
    				boolean qualify = true;
    				if ( pushList.phls.length == 1 ){
    					PushHighLow phl = pushList.phls[0];
    					if ((phl.prePos >= 2 ) && !((quotes[phl.prePos-1].low > quotes[phl.prePos].low) &&  (quotes[phl.prePos-2].low>quotes[phl.prePos].low)))
    						qualify = false;
    				}
   					
    				if (qualify){
	    				largestDownPush = pushList;
	    				int start = pushList.begin;
	    				boolean in=false;
	    				while ( --start > 0){
	    	    			pushList = findPastLowsByX(quotes, j, i,1);
	    	    			if ( pushList == null )
	    	    				break outer;
	    	    			else if (pushList.getNumOfPushes() > largestUpPush.getNumOfPushes())
	    	    				largestDownPush = pushList;
	       	    			if ( quotes[start].low > ema20[start])
	    	    				in = true;
	    	    			if (( in == true ) && !( quotes[start].low > ema20[start]))
	    	    				break outer;
	    				}
    				}
    			}
    		}
    	}

    	if ( largestUpPush != null ){
    		
    		if ((largestDownPush == null) || ( largestUpPush.end > largestDownPush.end )){

    			// this largestUpPush.begin is not really the lowest point
    			int lowestPoint = Utility.getLow(quotes, (largestUpPush.begin-2>=0?largestUpPush.begin-2:0), largestUpPush.end).pos;
				PushList largestPrevDownPush = null;
				
			   	for ( int i = lowestPoint; i > lowestPoint - 10; i-- ){
		    		for ( int j = i-2; j > ((lowestPoint-TOTAL_BARS>0)?lowestPoint-TOTAL_BARS:0); j--){
		    			//PushList pushList = findPast1Lows1(quotes, j, i);
		    			PushList pushList = findPastLowsByX(quotes, j, i,1);
		    			if ((pushList != null ) && (pushList.phls != null) && (pushList.phls.length > 0 )){
		    				
			    	    	if (( largestPrevDownPush == null ) || (pushList.getNumOfPushes() > largestPrevDownPush.getNumOfPushes()))
			    	    		largestPrevDownPush = pushList;
		   				}
		   			}
			   	}
		  		
			   	return new PushTrendDAO(Constants.DIRECTION_UP, largestUpPush, largestPrevDownPush);
    		}
    	}
    	
    	if ( largestDownPush != null ){

    		if ((largestUpPush == null) || ( largestDownPush.end > largestUpPush.end )){

    			int highestPoint = Utility.getHigh(quotes, (largestDownPush.begin-2>=0?largestDownPush.begin-2:0), largestDownPush.end).pos;
				PushList largestPrevUpPush = null;
				
			   	for ( int i = highestPoint; i > highestPoint-10; i-- ){
		    		for ( int j = i-2; j > ((highestPoint-TOTAL_BARS>0)?highestPoint-TOTAL_BARS:0); j--){
		    			//PushList pushList = findPast1Highs1(quotes, j, i);
		    			PushList pushList = findPastHighsByX(quotes, j, i,1);

		    			if ((pushList != null ) && (pushList.phls != null) && (pushList.phls.length > 0 )){
		    				
			    	    	if (( largestPrevUpPush == null ) || (pushList.getNumOfPushes() > largestPrevUpPush.getNumOfPushes()))
			    	    		largestPrevUpPush = pushList;
		   				}
		   			}
			   	}
		  		
			   	return new PushTrendDAO(Constants.DIRECTION_DOWN, largestDownPush, largestPrevUpPush);
    		}
    	}
    				
    	return null;
    }

    

}
