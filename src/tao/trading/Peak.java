package tao.trading;

import java.util.Vector;

public class Peak {
	public int direction;
	public int startpos;
	public double highlow;
	public int highlowpos;
	public int peakwidth;
	public int prevDist;
	public double pullback;
	public int pullbackpos;
	public Vector<QuoteData> highlowQuoteData;
	public Vector<QuoteData> pullbackQuoteData;
	public int highlowStartPos;
	public int highlowEndPos;
	public int pullbackStartPos;
	public int pullbackEndPos;
	
	
	
	public Peak(double highlow, int highlowpos, double pullback, int pullbackpos) {
		super();
		this.highlow = highlow;
		this.highlowpos = highlowpos;
		this.pullback = pullback;
		this.pullbackpos = pullbackpos;

		highlowQuoteData = new Vector();
		pullbackQuoteData = new Vector();
		
	}

	public Peak() {
		super();
		// TODO Auto-generated constructor stub
		highlowQuoteData = new Vector();
		pullbackQuoteData = new Vector();
	}


}

