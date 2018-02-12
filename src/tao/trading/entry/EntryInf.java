package tao.trading.entry;

import tao.trading.QuoteData;
import tao.trading.strategy.tm.TradeManagerInf;

public interface EntryInf {

	public void entry_manage( QuoteData data, TradeManagerInf tradeManager );
}
