package com.wavesplatform.dex.model

import java.nio.ByteBuffer

import com.wavesplatform.common.state.ByteStr
import com.wavesplatform.dex.MatcherTestData
import com.wavesplatform.dex.model.MatcherModel.Price
import com.wavesplatform.dex.model.OrderBook.{LastTrade, Level, SideSnapshot, Snapshot}
import com.wavesplatform.dex.settings.MatchingRules
import com.wavesplatform.settings.Constants
import com.wavesplatform.transaction.Asset.{IssuedAsset, Waves}
import com.wavesplatform.transaction.assets.exchange.{AssetPair, Order}
import com.wavesplatform.{NTPTime, NoShrink}
import org.scalacheck.Gen
import org.scalatest.{FreeSpec, Matchers}
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}

import scala.collection.{SortedSet, mutable}

class OrderBookSpec extends FreeSpec with PropertyChecks with Matchers with MatcherTestData with NTPTime with NoShrink {
  val pair: AssetPair = AssetPair(Waves, mkAssetId("BTC"))

  "place buy orders with different prices" in {
    val ord1 = LimitOrder(buy(pair, 1583290045643L, 34118))
    val ord2 = LimitOrder(buy(pair, 170484969L, 34120))
    val ord3 = LimitOrder(buy(pair, 44521418496L, 34000))

    val ob = OrderBook.empty
    ob.add(ord1, ntpNow)
    ob.add(ord2, ntpNow)
    ob.add(ord3, ntpNow)

    ob.allOrders.toSeq.map(_._2) shouldEqual Seq(ord2, ord1, ord3)
  }

  "place several buy orders at the same price" in {}

  "place buy and sell orders with prices different from each other less than tick size in one level" in {
    val ob                 = OrderBook.empty
    val normalizedTickSize = toNormalized(100L)

    val buyOrd1 = LimitOrder(buy(pair, 1583290045643L, 34100))
    val buyOrd2 = LimitOrder(buy(pair, 170484969L, 34120))
    val buyOrd3 = LimitOrder(buy(pair, 44521418496L, 34210))
    val buyOrd4 = LimitOrder(buy(pair, 44521418495L, 34303))
    val buyOrd5 = LimitOrder(buy(pair, 44521418494L, 34357))
    val buyOrd6 = LimitOrder(buy(pair, 44521418493L, 34389))

    val sellOrd1 = LimitOrder(sell(pair, 2583290045643L, 44100))
    val sellOrd2 = LimitOrder(sell(pair, 270484969L, 44120))
    val sellOrd3 = LimitOrder(sell(pair, 54521418496L, 44210))
    val sellOrd4 = LimitOrder(sell(pair, 54521418495L, 44303))
    val sellOrd5 = LimitOrder(sell(pair, 54521418494L, 44357))
    val sellOrd6 = LimitOrder(sell(pair, 54521418493L, 44389))

    ob.add(buyOrd1, ntpNow, normalizedTickSize)
    ob.add(buyOrd2, ntpNow, normalizedTickSize)
    ob.add(buyOrd3, ntpNow, normalizedTickSize)
    ob.add(buyOrd4, ntpNow, normalizedTickSize)
    ob.add(buyOrd5, ntpNow, normalizedTickSize)
    ob.add(buyOrd6, ntpNow, normalizedTickSize)

    ob.add(sellOrd1, ntpNow, normalizedTickSize)
    ob.add(sellOrd2, ntpNow, normalizedTickSize)
    ob.add(sellOrd3, ntpNow, normalizedTickSize)
    ob.add(sellOrd4, ntpNow, normalizedTickSize)
    ob.add(sellOrd5, ntpNow, normalizedTickSize)
    ob.add(sellOrd6, ntpNow, normalizedTickSize)

    ob.getBids.keySet shouldBe SortedSet[Long](34300, 34200, 34100).map(toNormalized)
    ob.getAsks.keySet shouldBe SortedSet[Long](44100, 44200, 44300, 44400).map(toNormalized)

    ob.getBids shouldBe mutable.TreeMap[Price, Level](
      Seq(
        34300 -> Vector(buyOrd4, buyOrd5, buyOrd6),
        34200 -> Vector(buyOrd3),
        34100 -> Vector(buyOrd1, buyOrd2),
      ).map { case (price, orders) => toNormalized(price) -> orders }: _*
    )

    ob.getAsks shouldBe mutable.TreeMap[Price, Level](
      Seq(
        44100 -> Vector(sellOrd1),
        44200 -> Vector(sellOrd2),
        44300 -> Vector(sellOrd3),
        44400 -> Vector(sellOrd4, sellOrd5, sellOrd6),
      ).map { case (price, orders) => toNormalized(price) -> orders }: _*
    )
  }

  "place sell orders" in {
    val ord1 = sell(pair, 1583290045643L, 34110)
    val ord2 = sell(pair, 170484969L, 34220)
    val ord3 = sell(pair, 44521418496L, 34000)

    Seq(ord3, ord1, ord2).map(LimitOrder(_))
  }

  "place several sell orders at the same price" - {
    "with same level" - {
      "TickSize.Enabled" in {
        val normalizedTickSize = toNormalized(100L)
        val ob                 = OrderBook.empty

        val sellOrder = LimitOrder(sell(pair, 54521418493L, 44389))
        ob.add(sellOrder, ntpNow, normalizedTickSize)
        ob.add(sellOrder, ntpNow, normalizedTickSize)

        ob.getAsks.size shouldBe 1
        ob.getAsks.head._2.toList shouldBe List(sellOrder, sellOrder)
      }

      "MatchingRules.Default.normalizedTickSize" in {
        val ob = OrderBook.empty

        val sellOrder = LimitOrder(sell(pair, 54521418493L, 44389))
        ob.add(sellOrder, ntpNow, MatchingRules.Default.normalizedTickSize)
        ob.add(sellOrder, ntpNow, MatchingRules.Default.normalizedTickSize)

        ob.getAsks.size shouldBe 1
        ob.getAsks.head._2.toList shouldBe List(sellOrder, sellOrder)
      }
    }

    "with different levels" in {
      val ob = OrderBook.empty

      val sellOrder = LimitOrder(sell(pair, 54521418493L, 44389))
      ob.add(sellOrder, ntpNow, toNormalized(100L))
      ob.add(sellOrder, ntpNow, MatchingRules.Default.normalizedTickSize)

      ob.getAsks.size shouldBe 2

      ob.getAsks.head._2.head shouldBe sellOrder
      ob.getAsks.last._2.head shouldBe sellOrder
    }
  }

  "sell market" in {
    val ord1 = buy(pair, 10 * Order.PriceConstant, 100)
    val ord2 = buy(pair, 10 * Order.PriceConstant, 105)

    val ob = OrderBook.empty
    ob.add(LimitOrder(ord1), ntpNow)
    ob.add(LimitOrder(ord2), ntpNow)

    ob.allOrders.map(_._2) shouldEqual Seq(BuyLimitOrder(ord2.amount, ord2.matcherFee, ord2), BuyLimitOrder(ord1.amount, ord1.matcherFee, ord1))

    val ord3 = sell(pair, 10 * Order.PriceConstant, 100)
    ob.add(LimitOrder(ord3), ntpNow)

    ob.allOrders.map(_._2) shouldEqual Seq(BuyLimitOrder(ord1.amount, ord1.matcherFee, ord1))
  }

  "execute orders at different price levels" in {
    val ord1 = sell(pair, 10 * Order.PriceConstant, 100)
    val ord2 = sell(pair, 5 * Order.PriceConstant, 110)
    val ord3 = sell(pair, 10 * Order.PriceConstant, 110)
    val ord4 = buy(pair, 22 * Order.PriceConstant, 115)

    val ob = OrderBook.empty
    ob.add(LimitOrder(ord1), ntpNow)
    ob.add(LimitOrder(ord2), ntpNow)
    ob.add(LimitOrder(ord3), ntpNow)
    ob.add(LimitOrder(ord4), ntpNow)

    val restAmount = ord1.amount + ord2.amount + ord3.amount - ord4.amount

    ob.allOrders.map(_._2) shouldEqual Seq(
      SellLimitOrder(
        restAmount,
        ord3.matcherFee - AcceptedOrder.partialFee(ord3.matcherFee, ord3.amount, ord3.amount - restAmount),
        ord3
      ))
  }

  "partially execute order with small remaining part" in {
    val ord1 = sell(pair, 200000000, 0.00041)
    val ord2 = sell(pair, 100000000, 0.0004)
    val ord3 = buy(pair, 100000001, 0.00045)

    val ob = OrderBook.empty

    ob.add(LimitOrder(ord1), ntpNow)
    ob.add(LimitOrder(ord2), ntpNow)
    ob.add(LimitOrder(ord3), ntpNow)

    ob.allOrders.map(_._2) shouldEqual Seq(SellLimitOrder(ord1.amount, ord1.matcherFee, ord1))
  }

  "partially execute order with zero fee remaining part" in {
    val ord1 = sell(pair, 1500 * Constants.UnitsInWave, 0.0006999)
    val ord2 = sell(pair, 3075248828L, 0.00067634)
    val ord3 = buy(pair, 3075363900L, 0.00073697)

    val ob = OrderBook.empty

    ob.add(LimitOrder(ord1), ntpNow)
    ob.add(LimitOrder(ord2), ntpNow)
    ob.add(LimitOrder(ord3), ntpNow)

    val corrected1 = Order.correctAmount(ord2.amount, ord2.price)
    val leftovers1 = ord3.amount - corrected1
    val corrected2 = Order.correctAmount(leftovers1, ord1.price)
    val restAmount = ord1.amount - corrected2
    // See OrderExecuted.submittedRemainingFee
    val restFee = ord1.matcherFee - AcceptedOrder.partialFee(ord1.matcherFee, ord1.amount, corrected2)
    ob.allOrders.toSeq.map(_._2) shouldEqual Seq(SellLimitOrder(restAmount, restFee, ord1))
  }

  "partially execute order with price > 1 and zero fee remaining part " in {
    val pair = AssetPair(IssuedAsset(ByteStr("BTC".getBytes)), IssuedAsset(ByteStr("USD".getBytes)))
    val ord1 = sell(pair, (0.1 * Constants.UnitsInWave).toLong, 1850)
    val ord2 = sell(pair, (0.01 * Constants.UnitsInWave).toLong, 1840)
    val ord3 = buy(pair, (0.0100001 * Constants.UnitsInWave).toLong, 2000)

    val ob = OrderBook.empty

    ob.add(LimitOrder(ord1), ntpNow)
    ob.add(LimitOrder(ord2), ntpNow)
    ob.add(LimitOrder(ord3), ntpNow)

    val restAmount = ord1.amount - (ord3.amount - ord2.amount)
    val restFee    = ord1.matcherFee - AcceptedOrder.partialFee(ord1.matcherFee, ord1.amount, ord3.amount - ord2.amount)
    ob.allOrders.toSeq.map(_._2) shouldEqual Seq(SellLimitOrder(restAmount, restFee, ord1))
  }

  "buy small amount of pricey asset" in {
    val p = AssetPair(IssuedAsset(ByteStr("WAVES".getBytes)), IssuedAsset(ByteStr("USD".getBytes)))
    val b = rawBuy(p, 700000L, 280)
    val s = rawSell(p, 30000000000L, 280)

    val ob = OrderBook.empty
    ob.add(LimitOrder(s), ntpNow)
    ob.add(LimitOrder(b), ntpNow)

    val restSAmount = Order.correctAmount(700000L, 280)
    val restAmount  = 30000000000L - restSAmount
    val restFee     = s.matcherFee - AcceptedOrder.partialFee(s.matcherFee, s.amount, restSAmount)
    ob.allOrders.map(_._2) shouldEqual Seq(SellLimitOrder(restAmount, restFee, s))
  }

  "cleanup expired buy orders" in {
    pending
  }

  "cleanup expired sell orders" in {
    pending
  }

  "aggregate levels for snapshot, preserving order" in {
    pending
  }

  "LimitOrder serialization" in forAll(limitOrderGenerator) { x =>
    val dest = new mutable.ArrayBuilder.ofByte
    SideSnapshot.serialize(dest, x)
    val bb = ByteBuffer.wrap(dest.result())
    SideSnapshot.loFromBytes(bb) shouldBe x
  }

  "SideSnapshot serialization" in forAll(sideSnapshotSerGen) { x =>
    val dest = new mutable.ArrayBuilder.ofByte
    SideSnapshot.serialize(dest, x)
    val bb = ByteBuffer.wrap(dest.result())
    SideSnapshot.fromBytes(bb) shouldBe x
  }

  "LastTrade serialization" in forAll(lastTradeGen) { x =>
    val dest = new mutable.ArrayBuilder.ofByte
    LastTrade.serialize(dest, x)
    val bb = ByteBuffer.wrap(dest.result())
    LastTrade.fromBytes(bb) shouldBe x
  }

  "Snapshot serialization" in forAll(snapshotGen) { x =>
    val dest = new mutable.ArrayBuilder.ofByte
    Snapshot.serialize(dest, x)
    val bb       = ByteBuffer.wrap(dest.result())
    val restored = Snapshot.fromBytes(bb)
    restored.asks shouldBe x.asks
    restored.bids shouldBe x.bids
    restored.lastTrade shouldBe x.lastTrade
  }

  private val sellLevelGen: Gen[Vector[SellLimitOrder]] =
    Gen.containerOf[Vector, SellLimitOrder](sellLimitOrderGenerator)

  private val asksGen: Gen[SideSnapshot] = for {
    n      <- Gen.choose(0, 10)
    levels <- Gen.containerOfN[Vector, Vector[SellLimitOrder]](n, sellLevelGen)
    prices <- Gen.containerOfN[Vector, Long](n, Gen.choose(1, 1000L))
  } yield prices.zip(levels).toMap

  private val buyLevelGen: Gen[Vector[BuyLimitOrder]] =
    Gen.containerOf[Vector, BuyLimitOrder](buyLimitOrderGenerator)

  private val bidsGen: Gen[SideSnapshot] = for {
    n      <- Gen.choose(0, 10)
    levels <- Gen.containerOfN[Vector, Vector[BuyLimitOrder]](n, buyLevelGen)
    prices <- Gen.containerOfN[Vector, Long](n, Gen.choose(1, 1000L))
  } yield prices.zip(levels).toMap

  private val lastTradeGen: Gen[LastTrade] = for {
    price     <- Gen.choose(1, Long.MaxValue)
    amount    <- Gen.choose(1, Long.MaxValue)
    orderType <- orderTypeGenerator
  } yield LastTrade(price, amount, orderType)

  private val snapshotGen: Gen[Snapshot] = for {
    asks      <- asksGen
    bids      <- bidsGen
    lastTrade <- Gen.option(lastTradeGen)
  } yield Snapshot(bids, asks, lastTrade)

  private val sideSnapshotSerGen: Gen[SideSnapshot] = Gen.oneOf(asksGen, bidsGen)
}
