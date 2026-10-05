package com.neko7ina.wallet.assistant.core.parser

import com.neko7ina.wallet.assistant.core.model.Location
import com.neko7ina.wallet.assistant.core.model.Money
import com.neko7ina.wallet.assistant.core.model.ProviderInfo
import com.neko7ina.wallet.assistant.core.model.Reservation
import com.neko7ina.wallet.assistant.core.model.SeatAssignment
import com.neko7ina.wallet.assistant.core.model.TravelDocument
import com.neko7ina.wallet.assistant.core.model.TravelDocumentStatus
import com.neko7ina.wallet.assistant.core.model.TravelDocumentType
import com.neko7ina.wallet.assistant.core.model.TravelSegment
import com.neko7ina.wallet.assistant.core.model.Traveler
import java.math.BigDecimal
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ChinaRailwayEmailParser : TravelDocumentParser {
    override val parserId = "china-railway-email"

    /**
     * 改动 [TICKET_REGEX] 的座席抓取规则时必须递增此版本号：它是 IMAP 同步断点 key
     * 的一部分（见 AppPreferences.imapSyncCheckpoint），只有变了才会让已经扫过的
     * 邮件重新解析一遍。v3 放开了「X车NN号」里「号」的必需性，用来兜住无座和卧铺。
     */
    override val version = 3

    override fun detect(document: RawDocument): DetectionResult {
        val body = normalize(document.body)
        val supported = "12306.cn" in body && EVENT_MARKERS.any { it in body }
        return DetectionResult(
            supported = supported,
            confidence = if (supported) 1f else 0f,
        )
    }

    override fun parse(document: RawDocument): ParseResult = parseAll(listOf(document))

    fun parseAll(
        documents: List<RawDocument>,
        baselineDocuments: List<TravelDocument> = emptyList(),
    ): ParseResult {
        val failures = mutableListOf<String>()
        val events = documents.mapNotNull { document ->
            when (val result = parseEvent(document)) {
                is EventParseResult.Success -> result.event
                is EventParseResult.Failure -> {
                    failures += result.message
                    null
                }
            }
        }.sortedBy(RailwayEmailEvent::sortEpochMillis)

        if (events.isEmpty()) {
            return ParseResult.Failure(
                failures.firstOrNull()
                    ?: "没有找到可识别的铁路购票、改签、候补兑现或退票通知。",
            )
        }

        val warnings = mutableListOf<String>()
        val affectedReservations = events.mapTo(mutableSetOf(), RailwayEmailEvent::reservationReference)
        val orders = baselineDocuments
            .filter { it.reservation.reference in affectedReservations }
            .groupBy { it.reservation.reference }
            .mapValuesTo(linkedMapOf()) { (reservationReference, existingDocuments) ->
                MutableOrder.fromDocuments(reservationReference, existingDocuments)
            }
        events.forEach { event ->
            val order = orders.getOrPut(event.reservationReference) {
                MutableOrder(
                    reservationReference = event.reservationReference,
                    purchasedOn = null,
                    totalPrice = event.totalPrice,
                )
            }
            when (event.type) {
                RailwayEmailEventType.PURCHASED -> {
                    order.purchasedOn = event.occurredOn
                    order.totalPrice = event.totalPrice
                    event.tickets.forEach { order.addConfirmed(it) }
                }

                RailwayEmailEventType.WAITLIST_FULFILLED -> {
                    if (order.totalPrice.amount.compareTo(BigDecimal.ZERO) == 0) {
                        order.totalPrice = event.totalPrice
                    }
                    event.tickets.forEach { order.addConfirmed(it) }
                }

                RailwayEmailEventType.RESCHEDULED -> event.tickets.forEach { newTicket ->
                    val travelerCandidates = order.ticketStates().filter { state ->
                        state.status == TravelDocumentStatus.CONFIRMED &&
                            state.ticket.travelerName == newTicket.travelerName &&
                            state.ticket.identity != newTicket.identity
                    }
                    val sameRouteCandidates = travelerCandidates.filter { state ->
                        state.ticket.origin == newTicket.origin &&
                            state.ticket.destination == newTicket.destination
                    }
                    val candidates = when {
                        sameRouteCandidates.size == 1 -> sameRouteCandidates
                        travelerCandidates.size == 1 -> travelerCandidates
                        else -> sameRouteCandidates
                    }
                    if (candidates.size == 1) {
                        candidates.single().status = TravelDocumentStatus.RESCHEDULED
                    } else if (candidates.size > 1) {
                        warnings += "订单 ${event.reservationReference} 中有多张可能被改签的车票，请核对原行程。"
                    } else {
                        warnings += "改签订单 ${event.reservationReference} 未找到对应的原车票，请核对原行程。"
                    }
                    order.addConfirmed(newTicket)
                }

                RailwayEmailEventType.REFUNDED -> event.tickets.forEach { refundedTicket ->
                    val exact = order.ticketStates().filter { state ->
                        state.status == TravelDocumentStatus.CONFIRMED &&
                            state.ticket.identity == refundedTicket.identity
                    }
                    val candidates = if (exact.isNotEmpty()) {
                        exact
                    } else {
                        order.ticketStates().filter { state ->
                            state.status == TravelDocumentStatus.CONFIRMED &&
                                state.ticket.journeyKey == refundedTicket.journeyKey &&
                                state.ticket.travelerName == refundedTicket.travelerName
                        }
                    }
                    if (candidates.size == 1) {
                        candidates.single().status = TravelDocumentStatus.REFUNDED
                    } else {
                        warnings += "退票订单 ${event.reservationReference} 未找到唯一对应的原车票，未自动更改行程。"
                    }
                }
            }
            if (event.expectedTicketCount != event.tickets.size) {
                warnings += "订单 ${event.reservationReference} 显示有 ${event.expectedTicketCount} 张车票，" +
                    "当前识别出 ${event.tickets.size} 张，请核对行程信息。"
            }
        }

        val parsedDocuments = orders.values.flatMap(MutableOrder::toDocuments)
        if (parsedDocuments.isEmpty()) {
            return ParseResult.Failure("铁路通知中没有找到可以形成行程的完整车票信息。")
        }
        if (failures.isNotEmpty()) {
            warnings += "另有 ${failures.size} 封铁路邮件未形成行程。"
        }
        return ParseResult.Success(
            documents = parsedDocuments.sortedBy {
                it.segments.first().departureTime.toInstant()
            },
            warnings = warnings.distinct(),
        )
    }

    private fun parseEvent(document: RawDocument): EventParseResult {
        val body = normalize(document.body)
        if (!detect(document.copy(body = body)).supported) {
            return EventParseResult.Failure("这封邮件不是支持的铁路订单通知。")
        }

        return try {
            val type = when {
                PURCHASE_MARKER in body -> RailwayEmailEventType.PURCHASED
                RESCHEDULE_MARKER in body -> RailwayEmailEventType.RESCHEDULED
                WAITLIST_MARKER in body -> RailwayEmailEventType.WAITLIST_FULFILLED
                REFUND_MARKER in body -> RailwayEmailEventType.REFUNDED
                else -> return EventParseResult.Failure("无法识别铁路邮件的业务类型。")
            }
            val headerMatch = when (type) {
                RailwayEmailEventType.PURCHASED -> PURCHASE_HEADER_REGEX.find(body)
                RailwayEmailEventType.RESCHEDULED -> RESCHEDULE_HEADER_REGEX.find(body)
                RailwayEmailEventType.WAITLIST_FULFILLED -> WAITLIST_HEADER_REGEX.find(body)
                RailwayEmailEventType.REFUNDED -> REFUND_HEADER_REGEX.find(body)
            } ?: return EventParseResult.Failure("铁路通知中缺少业务日期或车票数量。")
            val order = ORDER_REGEX.find(body)?.groupValues?.get(1)
                ?: return EventParseResult.Failure("铁路通知中缺少订单号码。")
            val totalPrice = eventPrice(type, body)
                ?: return EventParseResult.Failure("铁路通知中缺少业务金额。")
            val ticketSection = body
                .substringAfter(sectionMarker(type), missingDelimiterValue = "")
                .trimStart('：', ':', ' ')
            val tickets = TICKET_REGEX.findAll(ticketSection).map { it.toTicket() }.toList()
            if (tickets.isEmpty()) {
                return EventParseResult.Failure("铁路通知中没有找到完整的车票信息。")
            }

            EventParseResult.Success(
                RailwayEmailEvent(
                    type = type,
                    reservationReference = order,
                    occurredOn = eventDate(type, headerMatch),
                    receivedAtEpochMillis = document.receivedAtEpochMillis,
                    expectedTicketCount = eventTicketCount(type, headerMatch, tickets.size),
                    totalPrice = Money(totalPrice, "CNY"),
                    tickets = tickets,
                ),
            )
        } catch (_: DateTimeException) {
            EventParseResult.Failure("铁路通知中的乘车日期或时间无效。")
        } catch (_: NumberFormatException) {
            EventParseResult.Failure("铁路通知中的车票数量或金额无法识别。")
        }
    }

    private fun eventDate(type: RailwayEmailEventType, match: MatchResult): LocalDate? = when (type) {
        RailwayEmailEventType.WAITLIST_FULFILLED -> null
        else -> LocalDate.of(
            match.groupValues[1].toInt(),
            match.groupValues[2].toInt(),
            match.groupValues[3].toInt(),
        )
    }

    private fun eventTicketCount(
        type: RailwayEmailEventType,
        match: MatchResult,
        parsedTicketCount: Int,
    ): Int = when (type) {
        RailwayEmailEventType.PURCHASED,
        RailwayEmailEventType.RESCHEDULED,
        -> match.groupValues[4].toInt()

        RailwayEmailEventType.WAITLIST_FULFILLED -> match.groupValues[1].toInt()
        RailwayEmailEventType.REFUNDED -> parsedTicketCount
    }

    private fun eventPrice(type: RailwayEmailEventType, body: String): BigDecimal? {
        val regex = if (type == RailwayEmailEventType.REFUNDED) REFUND_TOTAL_REGEX else TOTAL_PRICE_REGEX
        return regex.find(body)?.groupValues?.get(1)?.let(::BigDecimal)
    }

    private fun sectionMarker(type: RailwayEmailEventType): String = when (type) {
        RailwayEmailEventType.PURCHASED -> "所购车票信息如下"
        RailwayEmailEventType.RESCHEDULED -> "改签后的车票信息如下"
        RailwayEmailEventType.WAITLIST_FULFILLED -> "车票信息如下"
        RailwayEmailEventType.REFUNDED -> "所退车票信息如下"
    }

    private fun MatchResult.toTicket(): RailwayTicket {
        val departure = LocalDate.of(
            groupValues[2].toInt(),
            groupValues[3].toInt(),
            groupValues[4].toInt(),
        ).atTime(
            LocalTime.of(groupValues[5].toInt(), groupValues[6].toInt()),
        ).atZone(CHINA_TIME_ZONE)
        return RailwayTicket(
            travelerName = groupValues[1].trim(),
            departureTimeEpochMillis = departure.toInstant().toEpochMilli(),
            departureTime = departure,
            origin = groupValues[7].trim(),
            destination = groupValues[8].trim(),
            serviceNumber = groupValues[9].trim(),
            carriage = groupValues[10].trim(),
            seat = groupValues[11].trim(),
            category = groupValues[12].trim(),
            ticketType = groupValues[13].trim().ifEmpty { null },
            price = groupValues[14],
        )
    }

    private fun normalize(text: String): String = text
        .replace('\u00A0', ' ')
        .replace(WHITESPACE_REGEX, " ")
        .trim()

    private companion object {
        val CHINA_TIME_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
        val WHITESPACE_REGEX = Regex("\\s+")
        const val PURCHASE_MARKER = "成功购买了"
        const val RESCHEDULE_MARKER = "成功改签车票"
        const val WAITLIST_MARKER = "成功兑现了"
        const val REFUND_MARKER = "成功办理了退票业务"
        val EVENT_MARKERS = listOf(PURCHASE_MARKER, RESCHEDULE_MARKER, WAITLIST_MARKER, REFUND_MARKER)
        val PURCHASE_HEADER_REGEX = Regex(
            "于(\\d{4})年(\\d{1,2})月(\\d{1,2})日.*?成功购买了(\\d+)张车票",
        )
        val RESCHEDULE_HEADER_REGEX = Regex(
            "于(\\d{4})年(\\d{1,2})月(\\d{1,2})日.*?成功改签车票(\\d+)张",
        )
        val WAITLIST_HEADER_REGEX = Regex("成功兑现了(\\d+)张车票")
        val REFUND_HEADER_REGEX = Regex(
            "于(\\d{4})年(\\d{1,2})月(\\d{1,2})日.*?成功办理了退票业务",
        )
        val TOTAL_PRICE_REGEX = Regex("(?:新车票)?票款共计([\\d.]+)元")
        val REFUND_TOTAL_REGEX = Regex("应退票款([\\d.]+)元")
        val ORDER_REGEX = Regex("订单号码\\s*([A-Za-z0-9]+)")
        /**
         * 座席段写成「X车<座位>」，「号」是**可选**后缀。
         *
         * 有座票是「2车17C号」，但 12306 对无座票只写「11车无座」，
         * 卧铺则是「3车12号下铺」——这两种都没有紧跟在座位值后面的「号」。
         * 早先的写法把「号」写成了必需字符，于是这两类车票整条正则都匹配不上，
         * 邮件会被判成「没有找到完整的车票信息」，改签/购票记录直接丢掉。
         *
         * 改成 ([^，,]+?)(?:号)? 之后：有座仍取到「17C」，无座取到「无座」，
         * 卧铺取到「12号下铺」，三类写法都能落地。
         */
        val TICKET_REGEX = Regex(
            """(?:^|。\s*)(?:\d+[.．]\s*)?([^，,。]+)[，,]\s*(\d{4})年(\d{1,2})月(\d{1,2})日(\d{1,2}):(\d{2})开[，,]\s*([^－—–,，-]+)\s*[-－—–]\s*([^，,]+)[，,]\s*([A-Za-z0-9]+)次列车[，,]\s*(\d+)车([^，,]+?)(?:号)?[，,]\s*([^，,]+)[，,]\s*(?:((?!票价)[^，,]+)[，,]\s*)?票价([\d.]+)元""",
        )
    }
}

private enum class RailwayEmailEventType {
    PURCHASED,
    RESCHEDULED,
    WAITLIST_FULFILLED,
    REFUNDED,
}

private sealed interface EventParseResult {
    data class Success(val event: RailwayEmailEvent) : EventParseResult
    data class Failure(val message: String) : EventParseResult
}

private data class RailwayEmailEvent(
    val type: RailwayEmailEventType,
    val reservationReference: String,
    val occurredOn: LocalDate?,
    val receivedAtEpochMillis: Long?,
    val expectedTicketCount: Int,
    val totalPrice: Money,
    val tickets: List<RailwayTicket>,
) {
    fun sortEpochMillis(): Long = receivedAtEpochMillis
        ?: occurredOn?.atStartOfDay(CHINA_TIME_ZONE)?.toInstant()?.toEpochMilli()
        ?: Long.MAX_VALUE

    private companion object {
        val CHINA_TIME_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
    }
}

private data class RailwayTicket(
    val travelerName: String,
    val departureTimeEpochMillis: Long,
    val departureTime: java.time.ZonedDateTime,
    val origin: String,
    val destination: String,
    val serviceNumber: String,
    val carriage: String,
    val seat: String,
    val category: String,
    val ticketType: String?,
    val price: String,
) {
    val journeyKey: String = listOf(
        departureTimeEpochMillis.toString(),
        origin,
        destination,
        serviceNumber,
    ).joinToString("|")

    val identity: String = listOf(journeyKey, travelerName, carriage, seat).joinToString("|")
}

private data class MutableTicketState(
    val ticket: RailwayTicket,
    var status: TravelDocumentStatus,
)

private data class MutableOrder(
    val reservationReference: String,
    var purchasedOn: LocalDate?,
    var totalPrice: Money,
    val journeys: LinkedHashMap<String, MutableList<MutableTicketState>> = linkedMapOf(),
) {
    fun ticketStates(): List<MutableTicketState> = journeys.values.flatten()

    fun addConfirmed(ticket: RailwayTicket) {
        val states = journeys.getOrPut(ticket.journeyKey) { mutableListOf() }
        if (states.none { it.ticket.identity == ticket.identity }) {
            states += MutableTicketState(ticket, TravelDocumentStatus.CONFIRMED)
        }
    }

    fun toDocuments(): List<TravelDocument> = journeys.mapNotNull { (journeyKey, states) ->
        if (states.isEmpty()) return@mapNotNull null
        val documentStatus = when {
            states.any { it.status == TravelDocumentStatus.CONFIRMED } -> TravelDocumentStatus.CONFIRMED
            states.any { it.status == TravelDocumentStatus.RESCHEDULED } -> TravelDocumentStatus.RESCHEDULED
            else -> TravelDocumentStatus.REFUNDED
        }
        val travelers = states.mapIndexed { index, state ->
            Traveler(id = "traveler-${index + 1}", name = state.ticket.travelerName)
        }
        val first = states.first().ticket
        val attributes = buildMap {
            first.ticketType?.let { put("ticketType", it) }
            put("price", first.price)
        }
        TravelDocument(
            type = TravelDocumentType.RAIL,
            provider = ProviderInfo(code = "china-railway", name = "中国铁路 12306"),
            reservation = Reservation(
                reference = reservationReference,
                purchasedOn = purchasedOn,
                totalPrice = totalPrice,
            ),
            travelers = travelers,
            segments = listOf(
                TravelSegment(
                    origin = Location(first.origin),
                    destination = Location(first.destination),
                    departureTime = first.departureTime,
                    arrivalTime = null,
                    serviceNumber = first.serviceNumber,
                    seatAssignments = states.mapIndexed { index, state ->
                        SeatAssignment(
                            travelerId = travelers[index].id,
                            section = state.ticket.carriage,
                            seat = state.ticket.seat,
                            category = state.ticket.category,
                            status = state.status,
                        )
                    },
                    attributes = attributes,
                ),
            ),
            journeyKey = journeyKey,
            status = documentStatus,
        )
    }

    companion object {
        fun fromDocuments(
            reservationReference: String,
            documents: List<TravelDocument>,
        ): MutableOrder {
            val firstDocument = documents.first()
            val order = MutableOrder(
                reservationReference = reservationReference,
                purchasedOn = documents.firstNotNullOfOrNull { it.reservation.purchasedOn },
                totalPrice = firstDocument.reservation.totalPrice,
            )
            documents.forEach { document ->
                val travelersById = document.travelers.associateBy(Traveler::id)
                document.segments.forEach { segment ->
                    segment.seatAssignments.forEach { assignment ->
                        val traveler = travelersById[assignment.travelerId]
                        if (traveler != null) {
                            val ticket = RailwayTicket(
                                travelerName = traveler.name,
                                departureTimeEpochMillis =
                                    segment.departureTime.toInstant().toEpochMilli(),
                                departureTime = segment.departureTime,
                                // 必须用存储的原文，不能换成 railStationName。
                                // journeyKey 由此重算，而它是 stableId() 的组成部分；
                                // 归一化会改变已有行程的主键。详见 RailStationNames 的说明。
                                origin = segment.origin.name,
                                destination = segment.destination.name,
                                serviceNumber = segment.serviceNumber,
                                carriage = assignment.section,
                                seat = assignment.seat,
                                category = assignment.category,
                                ticketType = segment.attributes["ticketType"],
                                price = segment.attributes["price"].orEmpty(),
                            )
                            val status = if (
                                document.status != TravelDocumentStatus.CONFIRMED &&
                                assignment.status == TravelDocumentStatus.CONFIRMED
                            ) {
                                document.status
                            } else {
                                assignment.status
                            }
                            order.journeys.getOrPut(ticket.journeyKey) { mutableListOf() }
                                .add(MutableTicketState(ticket, status))
                        }
                    }
                }
            }
            return order
        }
    }
}
