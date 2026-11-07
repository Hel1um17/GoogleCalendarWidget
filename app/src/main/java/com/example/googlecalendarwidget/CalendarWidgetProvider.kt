package com.example.googlecalendarwidget

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * 4x6 月历小部件。
 *
 * 数据：
 * - 通过 CalendarContract.Instances 查询当前显示月份覆盖的日期范围，重复日程会展开。
 * - Instances.query 默认只返回 Calendars.VISIBLE=1 的日历，已勾选显示的共享 Google 日历
 *   也会包含进来。
 *
 * 展示：
 * - 顶部：月份标题 + 左右翻月箭头。
 * - 每个日期格：上方日期，下方最多 3~4 条蓝色圆角任务条，放不下时显示 +N。
 * - 深色主题：颜色位于 values-night/colors.xml。
 */
class CalendarWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "CalendarWidget"

        private const val ACTION_REFRESH = "com.example.googlecalendarwidget.action.REFRESH"
        private const val ACTION_PREV_MONTH =
            "com.example.googlecalendarwidget.action.PREV_MONTH"
        private const val ACTION_NEXT_MONTH =
            "com.example.googlecalendarwidget.action.NEXT_MONTH"

        private const val MIDNIGHT_REQUEST_CODE = 1001
        private const val DAY_CLICK_REQUEST_BASE = 10_000
        private const val GOOGLE_CALENDAR_PACKAGE = "com.google.android.calendar"

        private const val STATE_PREFS = "calendar_widget_state"
        private const val KEY_SELECTED_MONTH_PREFIX = "selected_month_"
        private const val KEY_PAGED_PREFIX = "paged_"

        /**
         * Instances 查询是阻塞操作，使用单线程串行执行，避免主线程 I/O，
         * 也避免同一个 widget 被并发更新时出现旧数据覆盖新数据。
         */
        private val updateExecutor = Executors.newSingleThreadExecutor()

        /**
         * Instances 查询列。START_DAY/END_DAY 是 Julian Day，END_DAY 为 inclusive，
         * 用来把全天日程正确映射到每一个显示日。
         */
        private val INSTANCE_PROJECTION = arrayOf(
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.STATUS,
            CalendarContract.Instances.START_DAY,
            CalendarContract.Instances.END_DAY,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME
        )

        /**
         * 布局 widget_calendar.xml 已经静态包含 6 行 x 7 天、每天 4 个任务槽。
         * 具体 View ID 在 WidgetIds 中集中维护；刷新时只改已有 View，
         * 不再 addView / removeAllViews 嵌套 RemoteViews。
         */

        /**
         * 和布局对应的尺寸常量（dp）：
         * 75 = 根 padding 16 + 顶部月份/箭头 40 + 星期表头 18 + 网格 margin 1
         * 16 = 日期格上下 padding 2 + 日期行 13 + 任务区 margin 1
         * 13 = 任务条最小高度 12 + 任务间距 1
         */
        private const val WIDGET_FIXED_CHROME_DP = 75
        private const val CELL_FIXED_HEIGHT_DP = 16
        private const val EVENT_SLOT_HEIGHT_DP = 13
        private const val MAX_EVENT_SLOTS = 4

        /** LocalDate.toEpochDay() 与 Julian Day 的固定偏移。 */
        private const val JULIAN_DAY_OFFSET = 2440588L

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, CalendarWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return

            val intent = Intent(context, CalendarWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }

        private fun statePrefs(context: Context) =
            context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)

        private fun monthKey(appWidgetId: Int) =
            "$KEY_SELECTED_MONTH_PREFIX$appWidgetId"

        private fun pagedKey(appWidgetId: Int) =
            "$KEY_PAGED_PREFIX$appWidgetId"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        scheduleNextMidnight(context)
        requestUpdate(context, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        scheduleNextMidnight(context)
        requestUpdate(context, intArrayOf(appWidgetId))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val editor = statePrefs(context).edit()
        appWidgetIds.forEach { appWidgetId ->
            editor.remove(monthKey(appWidgetId))
            editor.remove(pagedKey(appWidgetId))
        }
        editor.apply()
        super.onDeleted(context, appWidgetIds)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleNextMidnight(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelMidnightRefresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PREV_MONTH -> {
                handleMonthNavigation(context, intent, -1)
            }

            ACTION_NEXT_MONTH -> {
                handleMonthNavigation(context, intent, 1)
            }

            ACTION_REFRESH,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_CONFIGURATION_CHANGED -> {
                val ids = getWidgetIds(context)
                if (ids.isNotEmpty()) {
                    requestUpdate(context, ids)
                    scheduleNextMidnight(context)
                }
            }

            else -> super.onReceive(context, intent)
        }
    }

    private fun handleMonthNavigation(context: Context, intent: Intent, deltaMonths: Long) {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val currentMonth = resolveSelectedMonth(context, appWidgetId, today)
        val targetMonth = currentMonth.plusMonths(deltaMonths)
        val prefs = statePrefs(context)
        val editor = prefs.edit()

        if (targetMonth == YearMonth.from(today)) {
            // 翻回本月后恢复“跟随今天”的行为。
            editor.remove(monthKey(appWidgetId))
            editor.putBoolean(pagedKey(appWidgetId), false)
        } else {
            editor.putString(monthKey(appWidgetId), targetMonth.toString())
            editor.putBoolean(pagedKey(appWidgetId), true)
        }
        editor.apply()

        requestUpdate(context, intArrayOf(appWidgetId))
    }

    private fun requestUpdate(context: Context, ids: IntArray) {
        if (ids.isEmpty()) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        updateExecutor.execute {
            try {
                val manager = AppWidgetManager.getInstance(appContext)

                ids.forEach { appWidgetId ->
                    val snapshot = loadSnapshot(appContext, appWidgetId)
                    val views = buildRemoteViews(appContext, manager, appWidgetId, snapshot)
                    manager.updateAppWidget(appWidgetId, views)
                }
            } catch (e: Exception) {
                Log.e(TAG, "更新日历小部件失败", e)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    private fun loadSnapshot(context: Context, appWidgetId: Int): WidgetSnapshot {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val selectedMonth = resolveSelectedMonth(context, appWidgetId, today)
        val baseCells = buildMonthCells(selectedMonth, today)

        val hasPermission = context.checkSelfPermission(
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return WidgetSnapshot(
                month = selectedMonth,
                cells = baseCells,
                permissionGranted = false,
                loadFailed = false
            )
        }

        val firstDate = baseCells.first().date
        val lastDate = baseCells.last().date

        val eventsByDay = try {
            queryEventsForRange(context, firstDate, lastDate, zone)
        } catch (e: Exception) {
            Log.e(TAG, "读取月历日程失败", e)
            null
        }

        if (eventsByDay == null) {
            return WidgetSnapshot(
                month = selectedMonth,
                cells = baseCells,
                permissionGranted = true,
                loadFailed = true
            )
        }

        val cells = baseCells.map { cell ->
            cell.copy(events = eventsByDay[cell.date].orEmpty())
        }

        return WidgetSnapshot(
            month = selectedMonth,
            cells = cells,
            permissionGranted = true,
            loadFailed = false
        )
    }

    /**
     * 查询整个月历网格覆盖的日期范围，并把每条 instance 映射到它发生的每一个本地日期。
     * 跨午夜日程会出现在两天；全天日程按 START_DAY/END_DAY（inclusive）映射。
     */
    private fun queryEventsForRange(
        context: Context,
        firstDate: LocalDate,
        lastDate: LocalDate,
        zone: ZoneId
    ): Map<LocalDate, List<DayEvent>> {
        val rangeStart = firstDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val rangeEnd = lastDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val cursor = CalendarContract.Instances.query(
            context.contentResolver,
            INSTANCE_PROJECTION,
            rangeStart,
            rangeEnd
        ) ?: return emptyMap()

        return cursor.use { c ->
            val beginIndex = c.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val endIndex = c.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val titleIndex = c.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val allDayIndex = c.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
            val statusIndex = c.getColumnIndexOrThrow(CalendarContract.Instances.STATUS)
            val startDayIndex = c.getColumnIndexOrThrow(CalendarContract.Instances.START_DAY)
            val endDayIndex = c.getColumnIndexOrThrow(CalendarContract.Instances.END_DAY)
            val calendarNameIndex = c.getColumnIndexOrThrow(
                CalendarContract.Instances.CALENDAR_DISPLAY_NAME
            )

            val result = HashMap<LocalDate, MutableList<DayEvent>>()

            fun addEvent(date: LocalDate, event: DayEvent) {
                if (date.isBefore(firstDate) || date.isAfter(lastDate)) return
                result.getOrPut(date) { ArrayList() }.add(event)
            }

            while (c.moveToNext()) {
                if (c.getInt(statusIndex) == CalendarContract.Events.STATUS_CANCELED) {
                    continue
                }

                val begin = c.getLong(beginIndex)
                val end = c.getLong(endIndex)
                val allDay = c.getInt(allDayIndex) != 0

                val rawTitle = c.getString(titleIndex)?.trim()
                val title = if (rawTitle.isNullOrEmpty()) {
                    context.getString(R.string.untitled_event)
                } else {
                    rawTitle
                }

                val calendarName = c.getString(calendarNameIndex).orEmpty()
                val kind = detectEventKind(calendarName, title)

                val event = DayEvent(
                    begin = begin,
                    title = title,
                    allDay = allDay,
                    kind = kind
                )

                if (allDay) {
                    val startDay = c.getInt(startDayIndex).toLong()
                    val endDay = c.getInt(endDayIndex).toLong()

                    val startDate: LocalDate
                    val endDateInclusive: LocalDate
                    if (startDay > 0L && endDay >= startDay) {
                        startDate = LocalDate.ofEpochDay(startDay - JULIAN_DAY_OFFSET)
                        endDateInclusive = LocalDate.ofEpochDay(endDay - JULIAN_DAY_OFFSET)
                    } else {
                        // 个别 ROM 的兜底：全天日程按 UTC 日期理解，END 是开区间。
                        startDate = Instant.ofEpochMilli(begin)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                        endDateInclusive = Instant.ofEpochMilli(end)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                            .minusDays(1)
                    }

                    var date = maxOf(startDate, firstDate)
                    val last = minOf(endDateInclusive, lastDate)
                    while (!date.isAfter(last)) {
                        addEvent(date, event)
                        date = date.plusDays(1)
                    }
                } else {
                    val startDate = Instant.ofEpochMilli(begin).atZone(zone).toLocalDate()
                    val endDate = Instant.ofEpochMilli(end).atZone(zone).toLocalDate()

                    var date = maxOf(startDate, firstDate)
                    val last = minOf(endDate, lastDate)
                    while (!date.isAfter(last)) {
                        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
                        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                        if (begin < dayEnd && end > dayStart) {
                            addEvent(date, event)
                        }
                        date = date.plusDays(1)
                    }
                }
            }

            result.mapValues { (_, events) ->
                events.sortedWith(
                    compareByDescending<DayEvent> { it.allDay }
                        .thenBy { it.begin }
                )
            }
        }
    }

    /**
     * 通过日历名称识别生日/节假日：
     * - Google 日历的生日日历通常名为“Birthdays / 联系人生日”；
     * - 节假日日历通常名为“Holidays in China / 中国节假日 / 法定节假日”等。
     * 同时兜底识别标题中的“生日 / birthday”。
     */
    private fun detectEventKind(calendarName: String, title: String): DayEventKind {
        val calendarLower = calendarName.lowercase(Locale.ROOT)
        val titleLower = title.lowercase(Locale.ROOT)

        val isBirthday = calendarLower.contains("birthday") ||
            calendarName.contains("生日") ||
            titleLower.contains("birthday") ||
            title.contains("生日")
        if (isBirthday) return DayEventKind.BIRTHDAY

        val isHoliday = calendarLower.contains("holiday") ||
            calendarName.contains("节假日") ||
            calendarName.contains("节日") ||
            calendarName.contains("法定假日")
        return if (isHoliday) DayEventKind.HOLIDAY else DayEventKind.NORMAL
    }

    private fun resolveSelectedMonth(
        context: Context,
        appWidgetId: Int,
        today: LocalDate
    ): YearMonth {
        val prefs = statePrefs(context)
        if (!prefs.getBoolean(pagedKey(appWidgetId), false)) {
            return YearMonth.from(today)
        }

        val saved = prefs.getString(monthKey(appWidgetId), null)
        if (saved.isNullOrEmpty()) return YearMonth.from(today)

        return try {
            YearMonth.parse(saved)
        } catch (_: Exception) {
            YearMonth.from(today)
        }
    }

    /**
     * 计算当月网格。周一为 1，所以 1 号前面的空位数 = dayOfWeek.value - 1。
     * 网格覆盖 4~6 周，前后补齐日期也会显示，也会展示对应日程。
     */
    private fun buildMonthCells(month: YearMonth, today: LocalDate): List<CalendarDay> {
        val firstDay = month.atDay(1)
        val leadingEmptyCells = firstDay.dayOfWeek.value - 1
        val weekCount = (leadingEmptyCells + month.lengthOfMonth() + 6) / 7
        val gridStart = firstDay.minusDays(leadingEmptyCells.toLong())

        return List(weekCount * 7) { index ->
            val date = gridStart.plusDays(index.toLong())
            CalendarDay(
                date = date,
                inCurrentMonth = YearMonth.from(date) == month,
                isToday = date == today
            )
        }
    }

    private fun buildRemoteViews(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        snapshot: WidgetSnapshot
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_calendar)

        bindCalendarGrid(
            context = context,
            views = views,
            appWidgetId = appWidgetId,
            snapshot = snapshot,
            maxEventsPerDay = resolveMaxEventsPerDay(
                appWidgetManager = appWidgetManager,
                appWidgetId = appWidgetId,
                weekCount = snapshot.cells.size / 7
            )
        )
        bindStatus(context, views, snapshot)
        bindMonthNavigation(context, views, appWidgetId)
        bindRootClick(context, views, appWidgetId)

        // 月份标题最后设置：如果网格构建/应用失败，至少不会出现“标题变了但网格没变”的假象。
        views.setTextViewText(
            R.id.month_title,
            context.getString(
                R.string.month_title_format,
                snapshot.month.year,
                snapshot.month.monthValue
            )
        )

        return views
    }

    /**
     * widget_calendar.xml 已经静态包含 6 个周行、每行 7 个日期格、每格 4 个任务槽。
     * 这里只隐藏多余周行并更新已有 View，完全不执行 addView / removeAllViews。
     */
    private fun bindCalendarGrid(
        context: Context,
        views: RemoteViews,
        appWidgetId: Int,
        snapshot: WidgetSnapshot,
        maxEventsPerDay: Int
    ) {
        val primaryColor = context.getColor(R.color.text_primary)
        val mutedColor = context.getColor(R.color.text_muted)
        val todayTextColor = context.getColor(R.color.today_text)
        val zone = ZoneId.systemDefault()
        val googleCalendarInstalled = isGoogleCalendarInstalled(context)
        val weekCount = snapshot.cells.size / 7

        for (row in 0 until WidgetIds.WEEK_ROWS.size) {
            val visible = row < weekCount
            views.setViewVisibility(
                WidgetIds.WEEK_ROWS[row],
                if (visible) View.VISIBLE else View.GONE
            )
            if (!visible) continue

            repeat(7) { column ->
                val cell = snapshot.cells[row * 7 + column]
                bindDayCell(
                    context = context,
                    views = views,
                    appWidgetId = appWidgetId,
                    row = row,
                    column = column,
                    cell = cell,
                    zone = zone,
                    googleCalendarInstalled = googleCalendarInstalled,
                    maxEventsPerDay = maxEventsPerDay,
                    primaryColor = primaryColor,
                    mutedColor = mutedColor,
                    todayTextColor = todayTextColor
                )
            }
        }
    }

    private fun bindDayCell(
        context: Context,
        views: RemoteViews,
        appWidgetId: Int,
        row: Int,
        column: Int,
        cell: CalendarDay,
        zone: ZoneId,
        googleCalendarInstalled: Boolean,
        maxEventsPerDay: Int,
        primaryColor: Int,
        mutedColor: Int,
        todayTextColor: Int
    ) {
        val numberId = WidgetIds.DAY_NUMBER[row][column]
        views.setTextViewText(numberId, cell.date.dayOfMonth.toString())
        views.setTextColor(
            numberId,
            when {
                cell.isToday -> todayTextColor
                cell.inCurrentMonth -> primaryColor
                else -> mutedColor
            }
        )
        views.setViewVisibility(
            WidgetIds.DAY_HIGHLIGHT[row][column],
            if (cell.isToday) View.VISIBLE else View.GONE
        )

        val shownEvents = cell.events.take(maxEventsPerDay)
        val hiddenCount = cell.events.size - shownEvents.size
        val moreId = WidgetIds.DAY_MORE[row][column]
        if (hiddenCount > 0) {
            views.setViewVisibility(moreId, View.VISIBLE)
            views.setTextViewText(moreId, "+$hiddenCount")
        } else {
            views.setViewVisibility(moreId, View.GONE)
        }

        val eventIds = WidgetIds.DAY_EVENT[row][column]
        val normalTextColor = context.getColor(R.color.event_chip_text)
        val holidayTextColor = context.getColor(R.color.holiday_chip_text)
        for (slot in 0 until MAX_EVENT_SLOTS) {
            val eventId = eventIds[slot]
            val event = shownEvents.getOrNull(slot)
            if (event == null) {
                views.setViewVisibility(eventId, View.GONE)
                views.setTextViewText(eventId, "")
            } else {
                val isHoliday = event.kind != DayEventKind.NORMAL
                views.setTextViewText(eventId, event.title)
                views.setTextColor(
                    eventId,
                    if (isHoliday) holidayTextColor else normalTextColor
                )
                views.setInt(
                    eventId,
                    "setBackgroundResource",
                    if (isHoliday) {
                        R.drawable.bg_event_chip_holiday
                    } else {
                        R.drawable.bg_event_chip
                    }
                )
                views.setTextViewCompoundDrawables(
                    eventId,
                    if (event.kind == DayEventKind.BIRTHDAY) {
                        R.drawable.ic_birthday_cake
                    } else {
                        0
                    },
                    0,
                    0,
                    0
                )
                views.setViewVisibility(eventId, View.VISIBLE)
            }
        }

        // 点击整个日期格，打开该日期在 Google 日历中的位置。
        views.setOnClickPendingIntent(
            WidgetIds.DAY_CELL[row][column],
            dayClickPendingIntent(
                context = context,
                appWidgetId = appWidgetId,
                date = cell.date,
                zone = zone,
                googleCalendarInstalled = googleCalendarInstalled,
                cellIndex = row * 7 + column
            )
        )
    }

    private fun bindStatus(
        context: Context,
        views: RemoteViews,
        snapshot: WidgetSnapshot
    ) {
        val messageRes = when {
            !snapshot.permissionGranted -> R.string.widget_permission_short
            snapshot.loadFailed -> R.string.widget_read_error
            else -> null
        }

        if (messageRes == null) {
            views.setViewVisibility(R.id.widget_status, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_status, View.VISIBLE)
            views.setTextViewText(R.id.widget_status, context.getString(messageRes))
        }
    }

    private fun bindMonthNavigation(
        context: Context,
        views: RemoteViews,
        appWidgetId: Int
    ) {
        views.setOnClickPendingIntent(
            R.id.prev_month,
            monthPendingIntent(context, appWidgetId, -1)
        )
        views.setOnClickPendingIntent(
            R.id.next_month,
            monthPendingIntent(context, appWidgetId, 1)
        )
    }

    /**
     * 上下月按钮使用显式广播 + FLAG_IMMUTABLE；每个 widget、每个方向使用不同 requestCode，
     * 避免两个按钮的 PendingIntent 互相覆盖。
     */
    private fun monthPendingIntent(
        context: Context,
        appWidgetId: Int,
        deltaMonths: Int
    ): PendingIntent {
        val action = if (deltaMonths < 0) ACTION_PREV_MONTH else ACTION_NEXT_MONTH
        val intent = Intent(context, CalendarWidgetProvider::class.java).apply {
            this.action = action
            // data 让每个 widget、每个方向的 PendingIntent 都有唯一身份，避免被复用覆盖。
            data = Uri.parse(
                "calendarwidget://month/$appWidgetId/${if (deltaMonths < 0) "prev" else "next"}"
            )
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val requestCode = appWidgetId * 2 + if (deltaMonths < 0) 0 else 1

        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * 点击日期格时直接打开 Google 日历的对应日期。
     *
     * Google 日历接受 content://com.android.calendar/time/<millis> 这种 URI，
     * 其中 millis 是该日期本地 00:00 的时间戳。
     */
    private fun dayClickPendingIntent(
        context: Context,
        appWidgetId: Int,
        date: LocalDate,
        zone: ZoneId,
        googleCalendarInstalled: Boolean,
        cellIndex: Int
    ): PendingIntent {
        val dayStartMillis = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.CONTENT_URI.buildUpon()
            .appendPath("time")
            .appendPath(dayStartMillis.toString())
            .build()

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (googleCalendarInstalled) {
                setPackage(GOOGLE_CALENDAR_PACKAGE)
            }
        }

        val requestCode = DAY_CLICK_REQUEST_BASE + appWidgetId * 100 + cellIndex
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    @Suppress("DEPRECATION")
    private fun isGoogleCalendarInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(GOOGLE_CALENDAR_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * 根据 widget 实际最小高度和当月周数动态计算每格能放几条任务。
     * 4x6（390dp）6 周时约 2 条，5 周时约 3 条；4x4（250dp）6 周时约 1 条。
     * 放不下的用 +N 表示。
     */
    private fun resolveMaxEventsPerDay(
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        weekCount: Int
    ): Int {
        if (weekCount <= 0) return 1

        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minHeight = max(
            250,
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 250)
        )
        val cellHeight = (minHeight - WIDGET_FIXED_CHROME_DP) / weekCount
        val eventArea = cellHeight - CELL_FIXED_HEIGHT_DP
        return (eventArea / EVENT_SLOT_HEIGHT_DP).coerceIn(1, MAX_EVENT_SLOTS)
    }

    private fun bindRootClick(context: Context, views: RemoteViews, appWidgetId: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
    }

    private fun getWidgetIds(context: Context): IntArray {
        return AppWidgetManager.getInstance(context).getAppWidgetIds(
            ComponentName(context, CalendarWidgetProvider::class.java)
        )
    }

    private fun scheduleNextMidnight(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val zone = ZoneId.systemDefault()
        val nextMidnight = LocalDate.now(zone)
            .plusDays(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextMidnight + 30_000L,
            midnightPendingIntent(context)
        )
    }

    private fun cancelMidnightRefresh(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = midnightPendingIntent(context)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun midnightPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, CalendarWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
        }
        return PendingIntent.getBroadcast(
            context,
            MIDNIGHT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private data class CalendarDay(
        val date: LocalDate,
        val inCurrentMonth: Boolean,
        val isToday: Boolean,
        val events: List<DayEvent> = emptyList()
    )

    private data class DayEvent(
        val begin: Long,
        val title: String,
        val allDay: Boolean,
        val kind: DayEventKind
    )

    private enum class DayEventKind {
        NORMAL,
        HOLIDAY,
        BIRTHDAY
    }

    private data class WidgetSnapshot(
        val month: YearMonth,
        val cells: List<CalendarDay>,
        val permissionGranted: Boolean,
        val loadFailed: Boolean
    )
}
