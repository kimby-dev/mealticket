package com.kimby.bycalendar.view

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.kimby.bycalendar.utils.ImagePagerAdapter
import java.time.LocalDate
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.kimby.bycalendar.R
import com.kimby.bycalendar.database.PhotoDatabase
import com.kimby.bycalendar.model.CalendarViewModel
import com.kimby.bycalendar.model.PhotoEntity
import com.kimby.bycalendar.utils.CalendarAdapter
import com.kimby.bycalendar.utils.GridImageAdapter
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : AppCompatActivity(), ImagePagerAdapter.OnPhotoLongClickListener {

    private lateinit var viewModel: MainViewModel

    private lateinit var dividerHandle: View
    private lateinit var customDateBar: LinearLayout
    private lateinit var customDateText: TextView
    private lateinit var btnDateLeft: ImageButton
    private lateinit var btnDateRight: ImageButton
    private lateinit var viewPager: ViewPager2
    private lateinit var addButton: ImageButton
    private lateinit var defaultViewBtn: ImageButton
    private lateinit var gridViewBtn: ImageButton
    private lateinit var tabLayout: TabLayout

    private lateinit var adapter: ImagePagerAdapter
    private lateinit var gridView: RecyclerView
    private lateinit var gridAdapter: GridImageAdapter

    private val minCalendarHeightPercent = 0.2f
    private var isCustomDateMode = false
    private var currentSelectedDate: LocalDate = LocalDate.now()
    private var currentCategory: String = "식권"
    private var currentPage: Int = 0
    private var startX = 0f

    private lateinit var btnMonthPrev: ImageButton
    private lateinit var btnMonthNext: ImageButton
    private lateinit var textCurrentMonth: TextView
    private var currentYearMonth: YearMonth = YearMonth.now()

    private lateinit var calendarRecyclerView: RecyclerView
    private lateinit var calendarAdapter: CalendarAdapter
    private lateinit var calendarViewModel: CalendarViewModel

    val database: PhotoDatabase by lazy {
        PhotoDatabase.getDatabase(this)
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult

        lifecycleScope.launch {
            val limit = if (currentCategory == "식권") 2 else 25
            val existingCount = viewModel.countByDateAndCategory(currentSelectedDate.toString(), currentCategory)
            if (existingCount + uris.size > limit) {
                Toast.makeText(this@MainActivity, "최대 $limit 장 저장할 수 있습니다.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            uris.forEach { uri ->
                val inputStream = contentResolver.openInputStream(uri) ?: return@forEach
                val fileName = "${currentSelectedDate}_${System.currentTimeMillis()}.jpg"
                val file = File(filesDir, fileName)
                FileOutputStream(file).use { output -> inputStream.copyTo(output) }

                val entity = PhotoEntity(
                    date = currentSelectedDate.toString(),
                    path = file.absolutePath,
                    category = currentCategory
                )
                viewModel.insert(entity)
            }
            viewModel.loadPhotos(currentSelectedDate.toString(), currentCategory)
        }
    }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        dividerHandle = findViewById(R.id.divider_drag_handle)
        viewPager = findViewById(R.id.main_view_pager)
        gridView = findViewById(R.id.main_grid_view)
        addButton = findViewById(R.id.btn_add_photo)
        defaultViewBtn = findViewById(R.id.btn_default_view)
        gridViewBtn = findViewById(R.id.btn_grid_view)
        tabLayout = findViewById(R.id.tab_layout)
        btnMonthPrev = findViewById(R.id.btn_month_prev)
        btnMonthNext = findViewById(R.id.btn_month_next)
        textCurrentMonth = findViewById(R.id.text_current_month)

        tabLayout.addTab(tabLayout.newTab().setText("식권"))
        tabLayout.addTab(tabLayout.newTab().setText("사진"))
        tabLayout.addOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentCategory = tab.text.toString()
                viewModel.loadPhotos(currentSelectedDate.toString(), currentCategory)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        adapter = ImagePagerAdapter(emptyList(), this, this as ImagePagerAdapter.OnPhotoLongClickListener)
        viewPager.adapter = adapter

        gridAdapter = GridImageAdapter(
            emptyList(), this,
            onDeleteRequest = { uri -> showDeleteDialog(uri) },
            onMoveRequest = { uri -> showMoveDialog(uri) }
        )
        gridView.adapter = gridAdapter
        gridView.layoutManager = GridLayoutManager(this, 5)


        addButton.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        defaultViewBtn.setOnClickListener {
            updateViewMode(isGrid = false)
        }

        gridViewBtn.setOnClickListener {
            updateViewMode(isGrid = true)
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPage = position
            }
        })

        viewPager.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> startX = event.x
                MotionEvent.ACTION_UP -> {
                    val deltaX = event.x - startX
                    val isSwipeRight = deltaX > 50
                    val isSwipeLeft = deltaX < -50
                    val lastPage = adapter.itemCount - 1

                    if (currentPage == 0 && isSwipeRight) {
                        bounce(viewPager)
                    } else if (currentPage == lastPage && isSwipeLeft) {
                        bounce(viewPager)
                    }
                }
            }
            false
        }

        calendarViewModel = ViewModelProvider(this)[CalendarViewModel::class.java]
        calendarRecyclerView = findViewById(R.id.recycler_calendar)
        calendarRecyclerView.layoutManager = GridLayoutManager(this, 7) // 7 columns for days

        // 관찰자 등록
        calendarViewModel.days.observe(this) { days ->
            val selected = calendarViewModel.selectedDate.value
            val holidays = calendarViewModel.holidays.value ?: emptySet()
            val labelMap = calendarViewModel.holidayLabels.value ?: emptyMap()

            calendarAdapter = CalendarAdapter(
                days = days,
                holidays = holidays,
                holidayLabels = labelMap,
                today = LocalDate.now(),
                selectedDate = calendarViewModel.selectedDate.value ?: LocalDate.now()
            ) { clickedDate ->
                clickedDate?.let {
                    calendarViewModel.setSelectedDate(it)
                    currentSelectedDate = clickedDate
                    viewModel.loadPhotos(it.toString(), currentCategory)
                }
            }
            calendarAdapter.updateDays(days, selected, holidays, labelMap)
            calendarRecyclerView.adapter = calendarAdapter
        }

        calendarViewModel.selectedDate.observe(this) { selected ->
            val days = calendarViewModel.days.value ?: return@observe
            val holidays = calendarViewModel.holidays.value ?: emptySet()
            val labels = calendarViewModel.holidayLabels.value ?: emptyMap()
            calendarAdapter.updateDays(days, selected, holidays, labels)
        }

        calendarViewModel.days.observe(this@MainActivity) { days ->
            val holidaySet = calendarViewModel.holidays.value ?: emptySet()
            val labelMap = calendarViewModel.holidayLabels.value ?: emptyMap()
            val selectedDate = calendarViewModel.selectedDate.value ?: LocalDate.now()

            calendarAdapter.updateDays(days, selectedDate, holidaySet, labelMap)
        }

        val now = LocalDate.now()
        val days = generateDaysInMonth(now.year, now.monthValue)
        val currentMonth = YearMonth.of(now.year, now.monthValue)
        textCurrentMonth.text="${now.year}년 ${now.monthValue}월"
        calendarViewModel.setDaysForMonth(currentMonth)

        calendarViewModel.holidays.observe(this) { holidaySet ->
            val selectedDate = calendarViewModel.selectedDate.value ?: LocalDate.now()
            val labelMap = calendarViewModel.holidayLabels.value ?: emptyMap()
            calendarAdapter.updateDays(days, selectedDate, holidaySet, labelMap)
        }

        calendarViewModel.holidayLabels.observe(this) { labelMap ->
            val selectedDate = calendarViewModel.selectedDate.value ?: LocalDate.now()
            val days = calendarViewModel.days.value ?: return@observe
            val holidays = calendarViewModel.holidays.value ?: emptySet()

            calendarAdapter.updateDays(days, selectedDate, holidays, labelMap)
        }

        btnMonthPrev.setOnClickListener {
            currentYearMonth = currentYearMonth.minusMonths(1)
            updateCalendar()
        }

        btnMonthNext.setOnClickListener {
            currentYearMonth = currentYearMonth.plusMonths(1)
            updateCalendar()
        }

        viewModel.photoUris.observe(this) { files ->
            val uris = files.map { it.toUri() }
            adapter.updateImages(uris)
            gridAdapter.updateImages(uris)
            viewPager.setCurrentItem(0, false)
        }

        setupDraggableDivider()
        onDateChanged(currentSelectedDate)

        viewModel.loadPhotos(currentSelectedDate.toString(), currentCategory)
    }

    fun generateDaysInMonth(year: Int, month: Int): List<LocalDate?> {
        val firstDayOfMonth = LocalDate.of(year, month, 1)
        val daysInMonth = firstDayOfMonth.lengthOfMonth()
        val dayOfWeekOffset = firstDayOfMonth.dayOfWeek.value % 7 // 일요일=0

        val totalCells = dayOfWeekOffset + daysInMonth
        val weeks = ((totalCells + 6) / 7) * 7 // 7의 배수로 채움

        val daysList = MutableList<LocalDate?>(weeks) { null }

        for (i in 0 until daysInMonth) {
            daysList[dayOfWeekOffset + i] = firstDayOfMonth.plusDays(i.toLong())
        }

        return daysList
    }

    fun isCurrentCategoryMealTicket(): Boolean {
        return currentCategory == "식권"
    }

    private fun updateViewMode(isGrid: Boolean) {
        viewPager.visibility = if (isGrid) View.GONE else View.VISIBLE
        gridView.visibility = if (isGrid) View.VISIBLE else View.GONE
    }

    private fun bounce(view: View) {
        view.animate()
            .translationXBy(30f)
            .setDuration(100)
            .withEndAction {
                view.animate().translationXBy(-30f).duration = 100
            }
    }

    private fun showMoveDialog(uri: Uri) {
        val options = arrayOf("식권으로 이동", "사진으로 이동")
        AlertDialog.Builder(this)
            .setTitle("카테고리 선택")
            .setItems(options) { _, which ->
                val targetCategory = if (which == 0) "식권" else "사진"
                //moveImageToCategory(uri, targetCategory)
                showDatePickerForMove(uri, targetCategory)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showDatePickerForMove(uri: Uri, targetCategory: String) {
        val today = LocalDate.now()
        val datePicker = android.app.DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                moveImageToCategoryWithDate(uri, targetCategory, selectedDate)
            },
            today.year, today.monthValue - 1, today.dayOfMonth
        )
        datePicker.show()
    }

    private fun moveImageToCategoryWithDate(uri: Uri, targetCategory: String, targetDate: LocalDate) {
        lifecycleScope.launch {
            val limit = if (targetCategory == "식권") 2 else 25
            val count = viewModel.countByDateAndCategory(targetDate.toString(), targetCategory)

            if (count >= limit) {
                Toast.makeText(this@MainActivity, "해당 카테고리에 최대 $limit 장 저장할 수 있습니다.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val oldFile = File(uri.path ?: return@launch)
            val newFile = File(filesDir, "${targetDate}_${System.currentTimeMillis()}.jpg")
            oldFile.copyTo(newFile, overwrite = true)
            oldFile.delete()

            viewModel.delete(oldFile.absolutePath)
            val entity = PhotoEntity(
                date = targetDate.toString(),
                path = newFile.absolutePath,
                category = targetCategory
            )
            viewModel.insert(entity)

            // 현재 화면이 대상 날짜인 경우에만 갱신
            if (targetDate == currentSelectedDate && targetCategory == currentCategory) {
                viewModel.loadPhotos(targetDate.toString(), targetCategory)
            }
        }
    }

    private fun showDeleteDialog(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle("사진 삭제")
            .setMessage("이 사진을 삭제할까요?")
            .setPositiveButton("삭제") { _, _ ->
                val file = File(uri.path ?: return@setPositiveButton)
                file.delete()
                viewModel.delete(file.absolutePath)
                viewModel.loadPhotos(currentSelectedDate.toString(), currentCategory)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    fun showUseConfirmDialog(uri: Uri) {
        val file = File(uri.path ?: return)
        val alreadyMarked = file.nameWithoutExtension.endsWith("_used")

        val title = if (alreadyMarked) "식권 미사용 처리" else "식권 사용 처리"
        val message = if (alreadyMarked) "해당 식권을 미사용 처리 하시겠습니까?" else "해당 식권을 사용 처리 하시겠습니까?"

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("확인") { _, _ ->
                if (alreadyMarked) {
                    restoreOriginalImage(uri)
                } else {
                    markImageAsUsed(uri)
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun restoreOriginalImage(uri: Uri) {
        val usedFile = File(uri.path ?: return)
        if (!usedFile.nameWithoutExtension.endsWith("_used")) return

        val originalName = usedFile.nameWithoutExtension.removeSuffix("_used") + ".jpg"
        val originalFile = File(usedFile.parent, originalName)

        if (originalFile.exists()) {
            usedFile.delete()
            // 데이터 무결성 확인을 위한 안전 로딩
            lifecycleScope.launch {
                database.photoDao().updatePhotoPath(
                    date = currentSelectedDate.toString(),
                    category = currentCategory,
                    oldPath = usedFile.absolutePath,
                    newPath = originalFile.absolutePath
                )
                viewModel.loadPhotos(currentSelectedDate.toString(), currentCategory)
            }
        } else {
            Toast.makeText(this, "미사용 원본 이미지를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun markImageAsUsed(uri: Uri) {
        val file = File(uri.path ?: return)
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutable)

        // ✅ 1. 스탬프 이미지 그리기
        val stamp = BitmapFactory.decodeResource(resources, R.drawable.used_stamp_red)
        val scale = 0.6f
        val stampSize = (bitmap.width * scale).toInt()
        val scaledStamp = Bitmap.createScaledBitmap(stamp, stampSize, stampSize, true)

        val left = (bitmap.width - scaledStamp.width) / 2f
        val top = (bitmap.height - scaledStamp.height) / 2f
        canvas.drawBitmap(scaledStamp, left, top, null)

        // ✅ 2. 현재 시간 가져오기
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val now = LocalDateTime.now().format(formatter)
        val message = "사용완료 : $now"

        // ✅ 3. 텍스트 페인트 설정
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = bitmap.width * 0.06f  // 이미지 너비 기준으로 글자 크기 설정
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(5f, 3f, 3f, Color.WHITE) // 글자 가독성 향상용 그림자
            isAntiAlias = true
        }

        // ✅ 4. 텍스트 위치 계산 (우측 하단 기준 padding)
        val padding = 20f
        val textWidth = paint.measureText(message)
        val x = bitmap.width - textWidth - padding
        val y = bitmap.height - padding

        // ✅ 5. 텍스트 그리기
        canvas.drawText(message, x, y, paint)

        // ✅ 6. 파일 저장
        val newFile = File(file.parent, file.nameWithoutExtension + "_used.jpg")
        FileOutputStream(newFile).use { out ->
            mutable.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }

        // ✅ 7. DB 갱신 및 리프레시
        lifecycleScope.launch {
            database.photoDao().updatePhotoPath(
                date = currentSelectedDate.toString(),
                category = currentCategory,
                oldPath = file.absolutePath,
                newPath = newFile.absolutePath
            )
            viewModel.loadPhotos(currentSelectedDate.toString(), currentCategory)
        }
    }

    override fun onPhotoLongClicked(uri: Uri) {
        if (isCurrentCategoryMealTicket()) {
            showUseConfirmDialog(uri)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDraggableDivider() {
        val screenHeight = resources.displayMetrics.heightPixels
        val minCalendarHeight = (screenHeight * minCalendarHeightPercent).toInt()

        dividerHandle.setOnTouchListener(object : View.OnTouchListener {
            var initialY = 0f
            var initialHeight = 0

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val layoutParams = calendarRecyclerView.layoutParams
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialY = event.rawY
                        initialHeight = layoutParams.height
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dy = (event.rawY - initialY).toInt()
                        val newHeight = (initialHeight + dy).coerceAtLeast(minCalendarHeight)
                        layoutParams.height = newHeight
                        calendarRecyclerView.layoutParams = layoutParams

                        val heightPercent = newHeight.toFloat() / screenHeight
                        if (!isCustomDateMode && heightPercent <= minCalendarHeightPercent + 0.01f) {
                            enterCustomDateMode()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun enterCustomDateMode() {
        isCustomDateMode = true
        calendarRecyclerView.visibility = View.GONE
        customDateBar.visibility = View.VISIBLE
        updateCustomDateText()

        btnDateLeft.setOnClickListener {
            currentSelectedDate = currentSelectedDate.minusDays(1)
            updateCustomDateText()
            onDateChanged(currentSelectedDate)
        }

        btnDateRight.setOnClickListener {
            currentSelectedDate = currentSelectedDate.plusDays(1)
            updateCustomDateText()
            onDateChanged(currentSelectedDate)
        }
    }

    private fun updateCustomDateText() {
        val formatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREA)
        customDateText.text = currentSelectedDate.format(formatter)
    }

    private fun onDateChanged(date: LocalDate) {
        lifecycleScope.launch {
            viewModel.loadPhotos(date.toString(), currentCategory)
        }
    }

    private fun updateCalendar() {
        textCurrentMonth.text = currentYearMonth.format(DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREA))
        calendarViewModel.setDaysForMonth(currentYearMonth)
    }
}