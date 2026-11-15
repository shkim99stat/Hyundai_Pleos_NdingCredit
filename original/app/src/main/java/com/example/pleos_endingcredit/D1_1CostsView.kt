package com.example.pleos_endingcredit

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import java.time.format.DateTimeFormatter
import java.util.Locale

/** D1-1's travel cost calculation, displayed in the existing in-car cost dialog. */
internal class D1_1CostsView(
    context: Context,
    private val content: TripResultContent,
    dismiss: () -> Unit,
    home: () -> Unit,
    private val peopleChanged: (Int) -> Unit,
) : ResultDesignSurface(context, 1100f, HEIGHT) {
    private val regular = AppTypography.font(context)
    private val bold = AppTypography.font(context, 700)
    private val primary = 0xFF131417.toInt()
    private val secondary = 0xFF44464E.toInt()
    private val dates = if (content.demo) parseTripDate(content.trip.createdOn)?.let { start ->
        "${start.format(DATE)} - ${start.plusDays(1).format(DATE)}"
    } ?: content.trip.createdOn else content.dates
    private val distance = if (content.demo) TripSample.distanceKm.toLong() else content.record?.distanceMeters
        ?.takeIf { it.isFinite() && it >= 0 }?.let { (it / 1000).toLong() }
    private val efficiency = if (content.demo) "5.0km/kWh" else MISSING
    private val usage = if (content.demo) "62.4kWh" else MISSING
    private val energy = if (content.demo) TripSample.fuel else content.energy
    private val toll = if (content.demo) TripSample.toll else content.toll
    private val parking = if (content.demo) 0 else content.parking
    private val total = if (energy != null && toll != null && parking != null) energy + toll + parking else null
    private var people = content.splitPeople
    private val peopleValue = label("d1.costs.people.value", "", 20f, 700, primary, centered = true, typeface = bold)
    private val qr = ImageView(context).apply {
        tag = "d1.costs.qr"
        scaleType = ImageView.ScaleType.FIT_CENTER
        contentDescription = "이동비 내역 QR 코드"
    }

    init {
        tag = "d1.costs.dialog"
        setTag(R.id.ending_credit_screen_id, Sheet.D1_1_COSTS.screenId)
        accessibilityPaneTitle = "이동비 내역"
        place(label("d1.costs.trip", content.trip.name, 28f, 700, primary, typeface = bold),
            38f, 31f, 515f, 36f)
        place(label("d1.costs.dates", dates, 20f, color = secondary, typeface = regular),
            38f, 75f, 515f, 27f)
        place(label("d1.costs.title", "이동비 계산", 23f, 700, primary, typeface = bold),
            38f, 130f, 515f, 30f)
        divider("top", 179f)
        row("distance", "주행 거리", distance?.let { "${it}km" } ?: MISSING, 194f)
        row("efficiency", "차가 잰 전비", efficiency, 241f)
        row("usage", if (content.demo) "전기 사용량 (${TripSample.distanceKm} ÷ 5.0)" else "전기 사용량",
            usage, 288f)
        row("energy", if (content.demo) "전기료 (× 300원)" else "전기료", money(energy), 335f)
        row("toll", "예상 통행료 (내비게이션)", money(toll), 382f)
        row("parking", "주차비", money(parking), 429f)
        divider("total", 477f)
        row("total", "합계", money(total), 494f, emphasized = true)
        place(label("d1.costs.people.label", "나누는 인원", 20f, color = secondary, typeface = regular),
            38f, 547f, 245f, 28f)
        val minus = button("d1.costs.minus", "−", { changePeople(-1) }, size = 23f, astaSans = true).apply {
            contentDescription = "나누는 인원 줄이기"
        }
        val plus = button("d1.costs.plus", "+", { changePeople(1) }, size = 23f, astaSans = true).apply {
            contentDescription = "나누는 인원 늘리기"
        }
        place(minus, 360f, 538f, 50f, 46f)
        place(peopleValue, 411f, 538f, 69f, 46f)
        place(plus, 481f, 538f, 50f, 46f)
        place(label("d1.costs.note", "돈을 내지 않는 사람이 있으면 줄여 주세요.", 17f,
            color = 0xFF686A72.toInt(), typeface = regular), 38f, 597f, 515f, 24f)
        place(label("d1.costs.note.qr", "변경한 인원은 QR에도 반영돼요.", 17f,
            color = 0xFF686A72.toInt(), typeface = regular), 38f, 621f, 515f, 24f)
        place(box("d1.costs.qr.rule", 0xFFEDEEF2.toInt()), 573f, 128f, 2f, 467f)
        place(label("d1.costs.qr.title", "이동비 QR", 26f, 700, primary, centered = true, typeface = bold),
            610f, 153f, 450f, 36f)
        place(qr, 684f, 207f, 300f, 300f)
        place(button("d1.costs.home", "홈 화면으로", home, solid = true, size = 25f, astaSans = true).apply {
            background = shape(0xFF0064FF.toInt(), 16f)
        }, 684f, 540f, 300f, 72f)
        place(button("d1.costs.close", "×", dismiss, size = 38f, astaSans = true).apply {
            contentDescription = "이동비 내역 닫기"
        }, 1008f, 26f, 60f, 60f)
        refreshPeople()
    }

    private fun money(value: Int?) = value?.let(TripSample::won) ?: MISSING

    private fun row(id: String, caption: String, value: String, y: Float, emphasized: Boolean = false) {
        val size = if (emphasized) 25f else 20f
        place(label("d1.costs.$id.label", caption, size, if (emphasized) 700 else 400,
            if (emphasized) primary else secondary, typeface = if (emphasized) bold else regular),
            38f, y, 323f, 30f)
        place(label("d1.costs.$id.value", value, size, 700, primary, typeface = bold).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }, 363f, y, 172f, 30f)
    }

    private fun divider(id: String, y: Float) {
        place(asset("costs-divider.svg", "d1.costs.divider.$id", "f4_834"), 38f, y, 497f, 1.55f)
    }

    private fun changePeople(delta: Int) {
        val next = (people + delta).coerceIn(content.minPeople, content.maxPeople)
        if (next == people) return
        people = next
        peopleChanged(people)
        refreshPeople()
    }

    private fun refreshPeople() {
        peopleValue.text = "${people}명"
        findViewWithTag<View>("d1.costs.minus").isEnabled = people > content.minPeople
        findViewWithTag<View>("d1.costs.plus").isEnabled = people < content.maxPeople
        qr.setImageBitmap(TripShare.qrText(buildString {
            appendLine(content.trip.name)
            appendLine(dates)
            appendLine("주행 거리: ${distance?.let { "${it}km" } ?: MISSING}")
            appendLine("차가 잰 전비: $efficiency")
            appendLine("전기 사용량: $usage")
            appendLine("전기료: ${money(energy)}")
            appendLine("예상 통행료: ${money(toll)}")
            appendLine("주차비: ${money(parking)}")
            appendLine("합계: ${money(total)}")
            appendLine("나누는 인원: ${people}명")
            total?.let { append("1인 이동비: ${TripSample.won((it + people - 1) / people)}") }
        }))
        requestLayout()
    }

    companion object {
        const val HEIGHT = 702.3f
        private const val MISSING = "기록 없음"
        private val DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd(E)", Locale.KOREAN)
    }
}
