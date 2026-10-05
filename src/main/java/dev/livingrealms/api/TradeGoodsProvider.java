package dev.livingrealms.api;
import java.util.List;
@FunctionalInterface
public interface TradeGoodsProvider {
    List<String> goodsIds();
}
