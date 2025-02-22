package com.wavesplatform.dex.history

import java.time.LocalDateTime

object DBRecords {

  sealed trait Record

  case class OrderRecord(id: String,
                         tpe: Byte,
                         senderAddress: String,
                         senderPublicKey: String,
                         amountAssetId: String,
                         priceAssetId: String,
                         feeAssetId: String,
                         side: Byte,
                         price: Double,
                         amount: Double,
                         timestamp: LocalDateTime,
                         expiration: LocalDateTime,
                         fee: Double,
                         created: LocalDateTime)
      extends Record

  case class EventRecord(orderId: String,
                         eventType: Byte,
                         timestamp: LocalDateTime,
                         price: Double,
                         filled: Double,
                         totalFilled: Double,
                         feeFilled: Double,
                         feeTotalFilled: Double,
                         status: Byte)
      extends Record
}
