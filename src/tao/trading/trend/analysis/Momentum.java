package tao.trading.trend.analysis;

import java.util.Vector;

import tao.trading.Constants;
import tao.trading.QuoteData;
import tao.trading.dao.PushHighLow;
import tao.trading.dao.PushList;
import tao.trading.strategy.util.Utility;

public class Momentum
{

	
	public static PushList findMomentumPush2Lows(QuoteData[] quotes, int start, int lastbar )
    {
    	QuoteData lastLow = Utility.getLow(quotes, start, lastbar-1);

		//if (((QuoteData)quotes[lastbar]).low > lastLow.low )
			//return new PushList(Constants.DIRECTION_DOWN, null, start, lastbar);  // not the lowest
		
    	Vector<PushHighLow> phls = new Vector<PushHighLow>();
    	int end = lastbar;

    	while( lastbar > start+1)
    	{
    		for ( int i = lastbar-1; i > start; i-- )
    		{
    			QuoteData afterLow = Utility.getLow( quotes, i+1, lastbar);
    			if (( afterLow == null ) || ( afterLow.low > ((QuoteData)quotes[i]).low) || ( afterLow.low > ((QuoteData)quotes[i-1]).low))
    				continue;
    			
    			QuoteData beforeLow = Utility.getLow( quotes, start, i-2);
    			if (( beforeLow == null ) || ( beforeLow.low > ((QuoteData)quotes[i]).low)|| ( beforeLow.low > ((QuoteData)quotes[i-1]).low))
    				continue;

    			if ( afterLow.low > beforeLow.low )
    				continue;
    			
    			int breakOutPos = afterLow.pos;
    			for ( int j = i+1; j <= afterLow.pos; j++)
    				if ( quotes[j].low < beforeLow.low )
    				{	
    					breakOutPos = j;
    					break;
    				}
    			
    			// now there is a "before" and a "after"
    			//int extAfterLowPos = afterLow.pos;
    			//while (( extAfterLowPos +1 <= lastbar) && ( quotes[extAfterLowPos+1].low < quotes[extAfterLowPos].low ))
    				//extAfterLowPos++;
    			PushHighLow phl = new PushHighLow(beforeLow.pos, afterLow.pos);
    			phl.breakOutPos = breakOutPos;
    			//phl.pushExt = extAfterLowPos;
    			phl.pullBack = Utility.getHigh( quotes, beforeLow.pos+1, afterLow.pos);
    			phls.add(phl);
    			
    			lastbar = beforeLow.pos+1;
    			break;
    		}
    		lastbar--;
    	}
    	
    	//return (PushHighLow[]) phls.toArray(ret);
    	int size = phls.size();
    	PushHighLow [] ret = new PushHighLow[size];
    	phls.toArray(ret);
    	
    	if ( size > 0 )
    	{	
	    	// 0 is the lastbar
	    	ret[size-1].pushStart = start;
	    	for ( int i = size-2; i >= 0 ; i--)
	    		ret[i].pushStart = ret[i+1].pullBack.pos;
	    	
	    	for ( int i = 0; i < size; i++ )
	    	{
	    		ret[i].pushSize = quotes[ret[i].pushStart].high - quotes[ret[i].prePos].low;
	    		ret[i].pullBackSize = ret[i].pullBack.high - quotes[ret[i].prePos].low;
	    		ret[i].pullBackRatio = ret[i].pullBackSize/ret[i].pushSize;
	    	}
    	}
    	return new PushList(Constants.DIRECTION_DOWN, ret, start, end);
     }


	
	
	
	
	

}
