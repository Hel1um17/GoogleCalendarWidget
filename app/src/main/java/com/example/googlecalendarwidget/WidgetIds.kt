package com.example.googlecalendarwidget

/**
 * widget_calendar.xml 中 6x7 日期格和 4 个任务槽的 View ID。
 * 由同一份布局命名规则生成，Provider 用这些 ID 更新已有 View。
 */
internal object WidgetIds {
    val WEEK_ROWS = intArrayOf(
        R.id.week_row_1,
        R.id.week_row_2,
        R.id.week_row_3,
        R.id.week_row_4,
        R.id.week_row_5,
        R.id.week_row_6
    )

    val DAY_CELL = arrayOf(
        intArrayOf(R.id.w1d1_cell, R.id.w1d2_cell, R.id.w1d3_cell, R.id.w1d4_cell, R.id.w1d5_cell, R.id.w1d6_cell, R.id.w1d7_cell),
        intArrayOf(R.id.w2d1_cell, R.id.w2d2_cell, R.id.w2d3_cell, R.id.w2d4_cell, R.id.w2d5_cell, R.id.w2d6_cell, R.id.w2d7_cell),
        intArrayOf(R.id.w3d1_cell, R.id.w3d2_cell, R.id.w3d3_cell, R.id.w3d4_cell, R.id.w3d5_cell, R.id.w3d6_cell, R.id.w3d7_cell),
        intArrayOf(R.id.w4d1_cell, R.id.w4d2_cell, R.id.w4d3_cell, R.id.w4d4_cell, R.id.w4d5_cell, R.id.w4d6_cell, R.id.w4d7_cell),
        intArrayOf(R.id.w5d1_cell, R.id.w5d2_cell, R.id.w5d3_cell, R.id.w5d4_cell, R.id.w5d5_cell, R.id.w5d6_cell, R.id.w5d7_cell),
        intArrayOf(R.id.w6d1_cell, R.id.w6d2_cell, R.id.w6d3_cell, R.id.w6d4_cell, R.id.w6d5_cell, R.id.w6d6_cell, R.id.w6d7_cell)
    )

    val DAY_NUMBER = arrayOf(
        intArrayOf(R.id.w1d1_number, R.id.w1d2_number, R.id.w1d3_number, R.id.w1d4_number, R.id.w1d5_number, R.id.w1d6_number, R.id.w1d7_number),
        intArrayOf(R.id.w2d1_number, R.id.w2d2_number, R.id.w2d3_number, R.id.w2d4_number, R.id.w2d5_number, R.id.w2d6_number, R.id.w2d7_number),
        intArrayOf(R.id.w3d1_number, R.id.w3d2_number, R.id.w3d3_number, R.id.w3d4_number, R.id.w3d5_number, R.id.w3d6_number, R.id.w3d7_number),
        intArrayOf(R.id.w4d1_number, R.id.w4d2_number, R.id.w4d3_number, R.id.w4d4_number, R.id.w4d5_number, R.id.w4d6_number, R.id.w4d7_number),
        intArrayOf(R.id.w5d1_number, R.id.w5d2_number, R.id.w5d3_number, R.id.w5d4_number, R.id.w5d5_number, R.id.w5d6_number, R.id.w5d7_number),
        intArrayOf(R.id.w6d1_number, R.id.w6d2_number, R.id.w6d3_number, R.id.w6d4_number, R.id.w6d5_number, R.id.w6d6_number, R.id.w6d7_number)
    )

    val DAY_HIGHLIGHT = arrayOf(
        intArrayOf(R.id.w1d1_highlight, R.id.w1d2_highlight, R.id.w1d3_highlight, R.id.w1d4_highlight, R.id.w1d5_highlight, R.id.w1d6_highlight, R.id.w1d7_highlight),
        intArrayOf(R.id.w2d1_highlight, R.id.w2d2_highlight, R.id.w2d3_highlight, R.id.w2d4_highlight, R.id.w2d5_highlight, R.id.w2d6_highlight, R.id.w2d7_highlight),
        intArrayOf(R.id.w3d1_highlight, R.id.w3d2_highlight, R.id.w3d3_highlight, R.id.w3d4_highlight, R.id.w3d5_highlight, R.id.w3d6_highlight, R.id.w3d7_highlight),
        intArrayOf(R.id.w4d1_highlight, R.id.w4d2_highlight, R.id.w4d3_highlight, R.id.w4d4_highlight, R.id.w4d5_highlight, R.id.w4d6_highlight, R.id.w4d7_highlight),
        intArrayOf(R.id.w5d1_highlight, R.id.w5d2_highlight, R.id.w5d3_highlight, R.id.w5d4_highlight, R.id.w5d5_highlight, R.id.w5d6_highlight, R.id.w5d7_highlight),
        intArrayOf(R.id.w6d1_highlight, R.id.w6d2_highlight, R.id.w6d3_highlight, R.id.w6d4_highlight, R.id.w6d5_highlight, R.id.w6d6_highlight, R.id.w6d7_highlight)
    )

    val DAY_MORE = arrayOf(
        intArrayOf(R.id.w1d1_more, R.id.w1d2_more, R.id.w1d3_more, R.id.w1d4_more, R.id.w1d5_more, R.id.w1d6_more, R.id.w1d7_more),
        intArrayOf(R.id.w2d1_more, R.id.w2d2_more, R.id.w2d3_more, R.id.w2d4_more, R.id.w2d5_more, R.id.w2d6_more, R.id.w2d7_more),
        intArrayOf(R.id.w3d1_more, R.id.w3d2_more, R.id.w3d3_more, R.id.w3d4_more, R.id.w3d5_more, R.id.w3d6_more, R.id.w3d7_more),
        intArrayOf(R.id.w4d1_more, R.id.w4d2_more, R.id.w4d3_more, R.id.w4d4_more, R.id.w4d5_more, R.id.w4d6_more, R.id.w4d7_more),
        intArrayOf(R.id.w5d1_more, R.id.w5d2_more, R.id.w5d3_more, R.id.w5d4_more, R.id.w5d5_more, R.id.w5d6_more, R.id.w5d7_more),
        intArrayOf(R.id.w6d1_more, R.id.w6d2_more, R.id.w6d3_more, R.id.w6d4_more, R.id.w6d5_more, R.id.w6d6_more, R.id.w6d7_more)
    )

    val DAY_EVENT = arrayOf(
        arrayOf(
            intArrayOf(R.id.w1d1_event1, R.id.w1d1_event2, R.id.w1d1_event3, R.id.w1d1_event4),
            intArrayOf(R.id.w1d2_event1, R.id.w1d2_event2, R.id.w1d2_event3, R.id.w1d2_event4),
            intArrayOf(R.id.w1d3_event1, R.id.w1d3_event2, R.id.w1d3_event3, R.id.w1d3_event4),
            intArrayOf(R.id.w1d4_event1, R.id.w1d4_event2, R.id.w1d4_event3, R.id.w1d4_event4),
            intArrayOf(R.id.w1d5_event1, R.id.w1d5_event2, R.id.w1d5_event3, R.id.w1d5_event4),
            intArrayOf(R.id.w1d6_event1, R.id.w1d6_event2, R.id.w1d6_event3, R.id.w1d6_event4),
            intArrayOf(R.id.w1d7_event1, R.id.w1d7_event2, R.id.w1d7_event3, R.id.w1d7_event4)
        ),
        arrayOf(
            intArrayOf(R.id.w2d1_event1, R.id.w2d1_event2, R.id.w2d1_event3, R.id.w2d1_event4),
            intArrayOf(R.id.w2d2_event1, R.id.w2d2_event2, R.id.w2d2_event3, R.id.w2d2_event4),
            intArrayOf(R.id.w2d3_event1, R.id.w2d3_event2, R.id.w2d3_event3, R.id.w2d3_event4),
            intArrayOf(R.id.w2d4_event1, R.id.w2d4_event2, R.id.w2d4_event3, R.id.w2d4_event4),
            intArrayOf(R.id.w2d5_event1, R.id.w2d5_event2, R.id.w2d5_event3, R.id.w2d5_event4),
            intArrayOf(R.id.w2d6_event1, R.id.w2d6_event2, R.id.w2d6_event3, R.id.w2d6_event4),
            intArrayOf(R.id.w2d7_event1, R.id.w2d7_event2, R.id.w2d7_event3, R.id.w2d7_event4)
        ),
        arrayOf(
            intArrayOf(R.id.w3d1_event1, R.id.w3d1_event2, R.id.w3d1_event3, R.id.w3d1_event4),
            intArrayOf(R.id.w3d2_event1, R.id.w3d2_event2, R.id.w3d2_event3, R.id.w3d2_event4),
            intArrayOf(R.id.w3d3_event1, R.id.w3d3_event2, R.id.w3d3_event3, R.id.w3d3_event4),
            intArrayOf(R.id.w3d4_event1, R.id.w3d4_event2, R.id.w3d4_event3, R.id.w3d4_event4),
            intArrayOf(R.id.w3d5_event1, R.id.w3d5_event2, R.id.w3d5_event3, R.id.w3d5_event4),
            intArrayOf(R.id.w3d6_event1, R.id.w3d6_event2, R.id.w3d6_event3, R.id.w3d6_event4),
            intArrayOf(R.id.w3d7_event1, R.id.w3d7_event2, R.id.w3d7_event3, R.id.w3d7_event4)
        ),
        arrayOf(
            intArrayOf(R.id.w4d1_event1, R.id.w4d1_event2, R.id.w4d1_event3, R.id.w4d1_event4),
            intArrayOf(R.id.w4d2_event1, R.id.w4d2_event2, R.id.w4d2_event3, R.id.w4d2_event4),
            intArrayOf(R.id.w4d3_event1, R.id.w4d3_event2, R.id.w4d3_event3, R.id.w4d3_event4),
            intArrayOf(R.id.w4d4_event1, R.id.w4d4_event2, R.id.w4d4_event3, R.id.w4d4_event4),
            intArrayOf(R.id.w4d5_event1, R.id.w4d5_event2, R.id.w4d5_event3, R.id.w4d5_event4),
            intArrayOf(R.id.w4d6_event1, R.id.w4d6_event2, R.id.w4d6_event3, R.id.w4d6_event4),
            intArrayOf(R.id.w4d7_event1, R.id.w4d7_event2, R.id.w4d7_event3, R.id.w4d7_event4)
        ),
        arrayOf(
            intArrayOf(R.id.w5d1_event1, R.id.w5d1_event2, R.id.w5d1_event3, R.id.w5d1_event4),
            intArrayOf(R.id.w5d2_event1, R.id.w5d2_event2, R.id.w5d2_event3, R.id.w5d2_event4),
            intArrayOf(R.id.w5d3_event1, R.id.w5d3_event2, R.id.w5d3_event3, R.id.w5d3_event4),
            intArrayOf(R.id.w5d4_event1, R.id.w5d4_event2, R.id.w5d4_event3, R.id.w5d4_event4),
            intArrayOf(R.id.w5d5_event1, R.id.w5d5_event2, R.id.w5d5_event3, R.id.w5d5_event4),
            intArrayOf(R.id.w5d6_event1, R.id.w5d6_event2, R.id.w5d6_event3, R.id.w5d6_event4),
            intArrayOf(R.id.w5d7_event1, R.id.w5d7_event2, R.id.w5d7_event3, R.id.w5d7_event4)
        ),
        arrayOf(
            intArrayOf(R.id.w6d1_event1, R.id.w6d1_event2, R.id.w6d1_event3, R.id.w6d1_event4),
            intArrayOf(R.id.w6d2_event1, R.id.w6d2_event2, R.id.w6d2_event3, R.id.w6d2_event4),
            intArrayOf(R.id.w6d3_event1, R.id.w6d3_event2, R.id.w6d3_event3, R.id.w6d3_event4),
            intArrayOf(R.id.w6d4_event1, R.id.w6d4_event2, R.id.w6d4_event3, R.id.w6d4_event4),
            intArrayOf(R.id.w6d5_event1, R.id.w6d5_event2, R.id.w6d5_event3, R.id.w6d5_event4),
            intArrayOf(R.id.w6d6_event1, R.id.w6d6_event2, R.id.w6d6_event3, R.id.w6d6_event4),
            intArrayOf(R.id.w6d7_event1, R.id.w6d7_event2, R.id.w6d7_event3, R.id.w6d7_event4)
        )
    )
}
