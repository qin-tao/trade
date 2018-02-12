package tao.trading;

import java.util.Vector;

public class BreakOut
{
	public int width;			// bar
	public double high;		
	public double below;
	public int highPos;		
	public int belowPos;		
	public int highWidth;		
	public int belowWidth;		
	public Vector<QuoteData> highlowQuotes = new Vector<QuoteData>();
	public Vector<QuoteData> belowQuotes = new Vector<QuoteData>();
	
	public BreakOut(double below, int belowPos, int belowWidth, double high, int highPos, int highWidth)
	{
		super();
		this.below = below;
		this.belowPos = belowPos;
		this.belowWidth = belowWidth;
		this.high = high;
		this.highPos = highPos;
		this.highWidth = highWidth;
		
	}

	public BreakOut(double b, double h, int w)
	{
		super();
		this.below = b;
		this.high = h;
		this.width = w;
	}

	
	public BreakOut()
	{
		super();
	}
	
	
	
}
