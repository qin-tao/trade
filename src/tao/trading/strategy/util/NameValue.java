package tao.trading.strategy.util;

public class NameValue
{
	String name;
	Object value;

	public NameValue(String name, Object value)
	{
		super();
		this.name = name;
		this.value = value;
	}

	public String getName()
	{
		return name;
	}

	public Object getValue()
	{
		return value;
	}
	
	public void setValue( Object v)
	{
		value = v;
	}
}
