package tao.trading;

import java.util.logging.Filter;
import java.util.logging.LogRecord;

public class SymbolFilter implements Filter {

	public String symbol;
	
	public SymbolFilter(String symbol) {
		super();
		this.symbol = symbol;
	}

	public boolean isLoggable(LogRecord lr) {
		String msg = lr.getMessage();
		if (msg.startsWith(symbol)){
			return true;
		}
		return false;
	}

}
