package com.example.pleos_endingcredit

import android.animation.ValueAnimator
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.car.Car
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.time.LocalDate

class MainActivity : AppCompatActivity() {
    private lateinit var store: TripStore
    private lateinit var views: WireframeViews
    private lateinit var stage: FrameLayout
    private lateinit var sheetMotion: PopupMotion
    private val pageBackground = Color.rgb(245, 245, 245)
    private var currentPageView: FrameLayout? = null
    private var outgoingPageView: FrameLayout? = null
    private var renderedScreen: ScreenKey? = null
    private val handler = Handler(Looper.getMainLooper())
    private val advancePreparation = Runnable { if (page == Page.B2_PREPARING) show(Page.B3_CREDIT) }
    private var started = false

    private var page = Page.A0_CONSENT
    private var sheet: Sheet? = null
    private var mode = ResultMode.BOTH
    private var tripId: String? = null
    private var creditFrame = 0
    private var creditElapsedMillis = 0L
    private var creditMuted = false
    private var resumed = false
    private var creditPaused = false
    private val soundtrack by lazy { CreditSoundtrack(this) }
    private val creditVideoFiles by lazy { CreditVideoFiles(this) }
    private var creditUpload: CreditVideoUpload? = null
    private var creditVideoReceipt: CreditVideoReceipt? = null
    private var restoringVideoPreview = false
    private val localVideoPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (page == Page.B1_CREDIT_VIDEO) render()
    }
    private var yearFrame = 0
    private val catalog = CatalogState()
    private var catalogDay = 0
    private var resultDay = 0
    private var photoTripId: String? = null
    private var replayFromCatalog = false
    private var returnToArchive = false
    private var replaying = false
    private var draftCrewName: String? = null
    private var draftCrewSaved = false
    private var draftName = ""
    private var draftPeople = ""
    private var draftDate = ""
    private var uxAllowed = false
    private var uxGuard: TripUxGuard? = null
    private var requestingConsentPermissions = false
    private var completingConsent = false
    private var consentReturnPage: Page? = null
    private var consentReturnSheet: Sheet? = null
    private val catalogPhotoPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val id = photoTripId
        photoTripId = null
        if (uri == null || id == null || store.trips.none { it.id == id }) return@registerForActivityResult
        CatalogPhotos.save(this, id, uri) { saved ->
            if (isDestroyed || isFinishing) return@save
            if (!saved) {
                Toast.makeText(this, "이 사진을 열 수 없어요. 다른 사진을 선택해 주세요.", Toast.LENGTH_SHORT).show()
            } else if (page == Page.F1_CATALOG) {
                rememberCatalogScroll()
                render()
            }
        }
    }
    private val recordingPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        requestingConsentPermissions = false
        if (page == Page.A4_RECORDING && grants[Car.PERMISSION_ENERGY] == true && uxAllowed && recordingActive()) {
            TripRecordingService.start(this)
        }
        if (page != Page.A0_CONSENT) return@registerForActivityResult
        if (TripRecordingService.hasRecordingPermissions(this)) {
            completingConsent = true
            completeConsent()
        } else {
            completingConsent = false
            Toast.makeText(this, "위치·주행 기록 권한을 허용한 뒤 시작해 주세요", Toast.LENGTH_LONG).show()
        }
    }

    /** A0 requests recording permissions after the user accepts. */
    private fun acceptConsent() {
        if (page != Page.A0_CONSENT || !uxAllowed || requestingConsentPermissions) return
        val missing = TripRecordingService.missingPermissions(this)
        if (missing.isEmpty()) {
            completingConsent = true
            completeConsent()
        } else {
            requestingConsentPermissions = true
            recordingPermissions.launch(missing.toTypedArray())
        }
    }

    private fun completeConsent() {
        if (!completingConsent || page != Page.A0_CONSENT || !uxAllowed) return
        completingConsent = false
        if (!TripRecordingService.hasRecordingPermissions(this)) return
        store.hasConsent = true
        if (recordingActive()) TripRecordingService.start(this)
        show(Page.A2_HOME)
    }

    private fun continueAfterPayoutSetup() {
        if (page != Page.A1_PAYOUT_SETUP || !uxAllowed) return
        if (consentReturnPage != Page.A3_START && consentReturnSheet != Sheet.A3_TRIP_START) {
            consentReturnPage = null
            consentReturnSheet = null
        }
        show(Page.A2_HOME)
    }

    private fun openConsentSetup() {
        if (!uxAllowed || page == Page.A0_CONSENT) return
        consentReturnPage = page
        consentReturnSheet = sheet
        completingConsent = false
        // Keep the A3 draft, but never start a trip from a permission callback.
        show(Page.A0_CONSENT)
    }

    private fun recordingActive(): Boolean = store.activeTrip?.let {
        TripRecordingRepository.get(this).load(it.id)?.active != false
    } == true

    private fun checkRecordingSetup() {
        if (!uxAllowed) return
        if (!TripRecordingService.hasRecordingPermissions(this)) { openConsentSetup(); return }
        if (packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_AUTOMOTIVE) &&
            checkSelfPermission(Car.PERMISSION_ENERGY) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            recordingPermissions.launch(arrayOf(Car.PERMISSION_ENERGY))
            return
        }
        val locationManager = getSystemService(android.location.LocationManager::class.java)
        if (!locationManager.isLocationEnabled) { startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)); return }
        TripRecordingService.stop(this)
        TripRecordingService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = TripStore(this)
        views = WireframeViews(this)
        TripRecordingRepository.get(this).pruneRawPaths()
        stage = FrameLayout(this).apply {
            setBackgroundColor(pageBackground)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) isForceDarkAllowed = false
        }
        sheetMotion = PopupMotion(stage)
        setContentView(stage)
        ViewCompat.setOnApplyWindowInsetsListener(stage) { view, insets ->
            val visible = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(visible.left, visible.top, visible.right, visible.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(stage)

        restore(savedInstanceState)
        creditVideoFiles.pruneDrafts()
        if (isDrivingPreview(intent)) {
            page = Page.A5_DRIVING_PREVIEW
            sheet = null
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goBack()
        })
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isDrivingPreview(intent)) {
            show(Page.A5_DRIVING_PREVIEW)
        } else {
            // A launcher tap can reuse this singleTask Activity.
            consentReturnPage = null
            consentReturnSheet = null
            show(when {
                recordingActive() -> Page.A4_RECORDING
                store.activeTrip != null -> Page.LEGACY_RESULT_CHOICE
                store.hasConsent && TripRecordingService.hasRecordingPermissions(this) -> Page.A2_HOME
                else -> Page.A0_CONSENT
            })
        }
    }

    override fun onStart() {
        super.onStart()
        started = true
        uxGuard = TripUxGuard(this) { allowed ->
            if (uxAllowed != allowed) {
                uxAllowed = allowed
                if (!allowed) sheet = null
                render()
                if (allowed) completeConsent()
            }
        }.also { it.start() }
        if (recordingActive()) TripRecordingService.start(this)
        currentPageView?.findViewWithTag<A4RecordingView>("a4.recording")?.onHostStarted()
        currentPageView?.findViewWithTag<C1TripResultView>("c1.result")?.onHostStarted()
        currentPageView?.findViewWithTag<F4CatalogDetailView>("f4.detail")?.onHostStarted()
        schedulePreparation()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        soundtrack.allowResume()
        if (page == Page.B1_CREDIT_VIDEO) render()
        updateCreditPlayback()
    }

    override fun onPause() {
        resumed = false
        stopCreditUpload()
        currentPageView?.findViewWithTag<A4RecordingView>("a4.recording")?.dismissStartNotice()
        creditView()?.setPlaybackAllowed(false)
        saveCreditCheckpoint()
        soundtrack.setEnabled(false)
        super.onPause()
    }

    override fun onDestroy() { soundtrack.release(); super.onDestroy() }

    override fun onStop() {
        started = false
        uxGuard?.stop(); uxGuard = null
        currentPageView?.findViewWithTag<A4RecordingView>("a4.recording")?.onHostStopped()
        stage.findViewWithTag<A3TripStartView>("a3.scroll")?.onHostStopped()
        currentPageView?.findViewWithTag<C1TripResultView>("c1.result")?.onHostStopped()
        currentPageView?.findViewWithTag<F4CatalogDetailView>("f4.detail")?.onHostStopped()
        handler.removeCallbacks(advancePreparation)
        finishCrossfade()
        sheetMotion.settle()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        rememberCatalogScroll()
        super.onSaveInstanceState(outState)
        outState.putString("page", page.savedName)
        outState.putString("sheet", sheet?.savedName)
        outState.putString("mode", mode.name)
        outState.putString("trip_id", tripId)
        outState.putInt("credit_frame", creditFrame)
        if (page == Page.B1_CREDIT_VIDEO) creditVideoFiles.save(outState, creditVideoReceipt)
        outState.putLong("credit_elapsed", creditElapsedMillis)
        outState.putBoolean("credit_muted", creditMuted)
        outState.putBoolean("credit_paused", creditPaused)
        outState.putInt("year_frame", yearFrame)
        outState.putString("archive_from", catalog.dates?.start?.toString())
        outState.putString("archive_to", catalog.dates?.end?.toString())
        outState.putString("catalog_query", catalog.query)
        outState.putInt("catalog_position", catalog.firstVisible)
        outState.putInt("catalog_offset", catalog.firstOffset)
        outState.putInt("catalog_day", catalogDay)
        outState.putInt("result_day", resultDay)
        outState.putString("catalog_photo_trip", photoTripId)
        outState.putBoolean("catalog_replay", replayFromCatalog)
        outState.putBoolean("from_archive", returnToArchive)
        outState.putBoolean("replaying", replaying)
        outState.putString("draft_crew", draftCrewName)
        outState.putBoolean("draft_crew_saved", draftCrewSaved)
        outState.putString("draft_name", draftName)
        outState.putString("draft_people", draftPeople)
        outState.putString("draft_date", draftDate)
        outState.putBoolean("requesting_consent_permissions", requestingConsentPermissions)
        outState.putBoolean("completing_consent", completingConsent)
        outState.putString("consent_return_page", consentReturnPage?.savedName)
        outState.putString("consent_return_sheet", consentReturnSheet?.savedName)
    }

    private fun restore(state: Bundle?) {
        // An active recording takes precedence over the normal launch introduction.
        // Recreating an existing Activity still restores its current screen.
        page = Page.fromSavedName(state?.getString("page"))
            ?: if (store.hasConsent && TripRecordingService.hasRecordingPermissions(this)) Page.A2_HOME else Page.A0_CONSENT
        sheet = Sheet.fromSavedName(state?.getString("sheet"))
        mode = enumOrNull<ResultMode>(state?.getString("mode")) ?: ResultMode.BOTH
        tripId = state?.getString("trip_id")
        if (page == Page.B1_CREDIT_VIDEO) creditVideoReceipt = creditVideoFiles.restore(state)
        // Older builds stored A1 as HOME with a separate A2 visibility flag.
        if (page == Page.A2_HOME && sheet == null && state?.containsKey("show_a2_home") == true &&
            !state.getBoolean("show_a2_home")) {
            page = Page.A1_PAYOUT_SETUP
        }
        creditFrame = (state?.getInt("credit_frame") ?: 0).coerceIn(0, CREDIT_FRAME_COUNT - 1)
        creditElapsedMillis = (state?.getLong("credit_elapsed") ?: 0).coerceIn(0, CreditPlayback.duration(creditFrame))
        creditMuted = state?.getBoolean("credit_muted") ?: store.creditMuted
        creditPaused = state?.getBoolean("credit_paused") ?: false
        yearFrame = (state?.getInt("year_frame") ?: 0).coerceIn(0, YEAR_FRAME_COUNT - 1)
        val archiveFrom = state?.getString("archive_from")?.let(::parseTripDate)
        val archiveTo = state?.getString("archive_to")?.let(::parseTripDate)
        catalog.dates = if (archiveFrom == null || archiveTo == null) null else ArchiveDateRange(archiveFrom, archiveTo)
        catalog.query = state?.getString("catalog_query").orEmpty()
        catalog.firstVisible = state?.getInt("catalog_position") ?: 0
        catalog.firstOffset = state?.getInt("catalog_offset") ?: 0
        catalogDay = state?.getInt("catalog_day") ?: 0
        resultDay = state?.getInt("result_day") ?: 0
        photoTripId = state?.getString("catalog_photo_trip")
        replayFromCatalog = state?.getBoolean("catalog_replay") ?: false
        returnToArchive = state?.getBoolean("from_archive") ?: false
        replaying = state?.getBoolean("replaying") ?: false
        draftCrewName = state?.getString("draft_crew")
        draftCrewSaved = state?.getBoolean("draft_crew_saved") == true
        draftName = state?.getString("draft_name").orEmpty()
        draftPeople = state?.getString("draft_people").orEmpty()
        draftDate = state?.getString("draft_date").orEmpty()
        requestingConsentPermissions = state?.getBoolean("requesting_consent_permissions") ?: false
        completingConsent = state?.getBoolean("completing_consent") ?: false
        consentReturnPage = Page.fromSavedName(state?.getString("consent_return_page"))
        consentReturnSheet = Sheet.fromSavedName(state?.getString("consent_return_sheet"))
        if (store.activeTrip != null) {
            store.activeTrip?.id?.let { TripRecordingRepository.get(this).ensure(it) }
            val completingSetup = consentReturnPage != null &&
                (page == Page.A0_CONSENT || page == Page.A1_PAYOUT_SETUP)
            if (!completingSetup) {
                page = if (recordingActive()) Page.A4_RECORDING else Page.LEGACY_RESULT_CHOICE
                sheet = null
            }
        }
    }

    private fun render() = render(keepClosingSheet = false)

    private fun render(keepClosingSheet: Boolean) {
        if (!keepClosingSheet) sheetMotion.dismiss(animate = false)
        creditView()?.setPlaybackAllowed(false)
        if (!uxAllowed) saveCreditCheckpoint()
        val trips = store.trips
        val active = store.activeTrip
        val selectedTrip = trips.firstOrNull { it.id == tripId }
        if ((page == Page.A4_RECORDING || page == Page.LEGACY_RESULT_CHOICE) && active == null ||
            page in setOf(Page.B1_CREDIT_VIDEO, Page.B2_PREPARING, Page.B3_CREDIT, Page.C1_RESULT, Page.F4_CATALOG_DETAIL) && selectedTrip == null) {
            page = Page.A2_HOME
            sheet = null
        }
        if (page != Page.B1_CREDIT_VIDEO || !uxAllowed) stopCreditUpload()
        if (page != Page.B1_CREDIT_VIDEO && creditVideoReceipt != null) discardCreditVideoReceipt()
        val visiblePage = if (uxAllowed || page == Page.A4_RECORDING) page else Page.A5_DRIVING_PREVIEW
        val view = when (visiblePage) {
            Page.A0_CONSENT -> views.consent(::acceptConsent)
            Page.A1_PAYOUT_SETUP -> A1PayoutSetupView(this, ::continueAfterPayoutSetup)
            Page.A2_HOME -> views.home(start = ::prepareTravel, archive = ::openArchive)
            Page.A3_START -> views.start(
                draftName, draftPeople, draftDate,
                nameChanged = { draftName = it },
                peopleChanged = { draftPeople = it },
                dateChanged = { draftDate = it },
                begin = ::beginTrip,
            )
            Page.A4_RECORDING -> A4RecordingView(this, requireNotNull(active), { uxAllowed },
                cancel = { showSheet(Sheet.CANCEL_TRIP) }, end = ::finishRecording,
                permissions = ::checkRecordingSetup)
            Page.A5_DRIVING_PREVIEW -> views.drivingPreview()
            Page.LEGACY_RESULT_CHOICE -> views.choice(
                requireNotNull(active),
                selected = mode,
                select = { mode = it },
                close = { show(if (recordingActive()) Page.A4_RECORDING else Page.A2_HOME) },
                next = ::completeTrip,
            )
            Page.B1_CREDIT_VIDEO -> creditVideoPrompt(requireNotNull(selectedTrip))
            Page.B2_PREPARING -> views.preparing(requireNotNull(selectedTrip), ::finishCredit)
            Page.B3_CREDIT -> {
                val trip = requireNotNull(selectedTrip)
                if (creditFrame != CreditPlayback.SCREENING_FRAME) {
                    creditFrame = CreditPlayback.SCREENING_FRAME
                    creditElapsedMillis = 0
                }
                val chapter = creditFrame
                val screeningTrip = if (trip.creditVideo == CreditVideoChoice.UPLOADED) trip else trip.copy(creditVideo = CreditVideoChoice.NONE)
                views.credit(screeningTrip, trips, chapter, creditElapsedMillis, creditMuted, creditPaused,
                    onTime = { if (page == Page.B3_CREDIT && chapter == creditFrame) creditElapsedMillis = it },
                    onChapter = { setCreditFrame(it) },
                    onComplete = {
                        if (page == Page.B3_CREDIT && chapter == creditFrame && resumed && uxAllowed) {
                            val next = CreditPlayback.nextFrame(chapter)
                            if (next == null) finishCredit() else setCreditFrame(next)
                        }
                    },
                    onMute = {
                        creditMuted = it; store.creditMuted = it
                        if (!it) soundtrack.allowResume()
                        updateCreditAudio()
                    },
                    onPause = { creditPaused = it; updateCreditAudio() },
                )
            }
            Page.C1_RESULT -> views.result(
                requireNotNull(selectedTrip),
                close = { show(Page.A2_HOME) },
                costs = { showSheet(Sheet.D1_1_COSTS) },
                card = { showSheet(Sheet.CARD_PREVIEW) },
                replay = { replayDisplayedCredit(fromCatalog = false) },
                editPlaces = { showSheet(Sheet.C1_EDIT_RESULT_PLACES) },
                removePlace = { placeId -> removeResultPlace(selectedTrip.id, placeId) },
                selectedDay = resultDay,
                onDay = { resultDay = it },
            )
            Page.F1_CATALOG -> catalogView(trips)
            Page.F4_CATALOG_DETAIL -> catalogDetailView(requireNotNull(selectedTrip))
            Page.F3_YEAR_REVIEW -> {
                val year = catalog.dates?.end?.year ?: trips.firstOrNull()?.createdOn?.take(4)?.toIntOrNull()
                    ?: LocalDate.now().year
                views.yearReview(
                    year, trips.filter { it.createdOn.startsWith("$year-") }, yearFrame,
                    close = { show(Page.F1_CATALOG) },
                    previous = { yearFrame = (yearFrame - 1).coerceAtLeast(0); render() },
                    next = {
                        if (yearFrame == YEAR_FRAME_COUNT - 1) show(Page.F1_CATALOG)
                        else { yearFrame++; render() }
                    },
                )
            }
        }

        if (view !is B1CreditVideoPromptView) AppTypography.applyTo(view)
        val screen = ScreenKey(visiblePage, when (visiblePage) {
            Page.B3_CREDIT -> creditFrame
            Page.F3_YEAR_REVIEW -> yearFrame
            else -> 0
        })
        showContent(view, screen)
        renderSheet(selectedTrip, animate = false)
        updateCreditPlayback()
    }

    private fun renderSheet(selectedTrip: Trip?, animate: Boolean) {
        val panel = if (!uxAllowed) null else when (sheet) {
            Sheet.A2_CREW -> A2CrewPanel(this, TravelCrewStore(this).list(), ::dismissSheet) { name, count ->
                draftCrewName = name
                draftCrewSaved = count != null
                if (count != null) draftPeople = count.toString()
                // Replace only the sheet so the popup blurs the A2 view already on screen.
                showSheet(Sheet.A3_TRIP_START)
            }
            Sheet.A3_TRIP_START -> A3TripStartView(this,
                draftName, draftPeople, draftDate,
                nameChanged = { draftName = it },
                peopleChanged = { draftPeople = it },
                dateChanged = { draftDate = it },
                begin = ::beginTrip,
                presentation = A3TripStartView.Presentation.POPUP,
                titleOnly = draftCrewSaved,
            )
            Sheet.CANCEL_TRIP -> views.confirm(
                "여행을 취소할까요?", "지금까지의 여행 기록은 남지 않아요.",
                "계속 기록", "여행 취소", ::dismissSheet,
            ) {
                val cancelledId = store.activeTrip?.id
                store.activeTrip = null
                TripRecordingService.stop(this)
                cancelledId?.let { TripRecordingRepository.get(this).delete(it) }
                show(Page.A2_HOME)
            }
            Sheet.END_TRIP -> views.confirm(
                "여행이 끝났나요?", "기록을 마치고 여행의 결과를 볼 수 있어요.",
                "아직이에요", "여행 끝", ::dismissSheet,
            ) {
                store.activeTrip?.id?.let { TripRecordingRepository.get(this).finish(it) }
                TripRecordingService.stop(this)
                mode = ResultMode.BOTH
                show(Page.LEGACY_RESULT_CHOICE)
            }
            Sheet.COSTS -> selectedTrip?.let { trip ->
                views.costs(trip, ::dismissSheet) { people -> store.update(trip.copy(splitPeople = people)) }
            }
            Sheet.D1_1_COSTS -> selectedTrip?.let { trip ->
                val recording = TripRecordingRepository.get(this).load(trip.id)
                D1_1CostsView(this, TripResultContent(trip, recording), ::dismissSheet,
                    home = { show(Page.A2_HOME) }) { people ->
                    store.update(trip.copy(splitPeople = people))
                }
            }
            Sheet.C1_EDIT_RESULT_PLACES -> selectedTrip?.let { trip ->
                val repository = TripRecordingRepository.get(this)
                val recording = repository.load(trip.id)
                val resultPlaces = C1Places.entries(TripResultContent(trip, recording))
                ResultPlacesView(this, resultPlaces, ::dismissSheet, save = { places ->
                    store.update(trip.copy(resultPlaces = places))
                    val retained = places.associateBy { it.id }
                    val initial = resultPlaces.associateBy { it.id }
                    recording?.stops?.forEach { stop ->
                        repository.editStop(trip.id, stop.id) { original ->
                            retained[stop.id]?.let { value -> original.copy(
                                userLabel = if (!value.needsReview && value.name != initial[stop.id]?.name)
                                    value.name else original.userLabel, excluded = false)
                            } ?: original.copy(excluded = true)
                        }
                    }
                    dismissSheet(refreshPage = true)
                }, record = recording)
            }
            Sheet.CARD_PREVIEW -> selectedTrip?.let { trip ->
                views.cardPreview(trip, ::dismissSheet, share = { showSheet(Sheet.SHARE_PREVIEW) },
                    replay = { playFromResult(trip) })
            }
            Sheet.SHARE_PREVIEW -> selectedTrip?.let { views.sharePreview(it, ::dismissSheet) }
            Sheet.DELETE_CATALOG_TRIP -> selectedTrip?.let { trip ->
                views.confirm("여행 지우기", "‘${trip.name}’의 카드와 기록을 지울까요? 지운 여행은 복구할 수 없어요.",
                    "취소", "지우기", ::dismissSheet) { deleteCatalogTrip(trip.id) }
            }
            Sheet.RECORDED_STOPS -> selectedTrip?.let { trip ->
                A4RecordingView(this, trip.copy(mode = ResultMode.CREDIT), { uxAllowed },
                    cancel = {}, end = ::dismissSheet, permissions = {}, review = true)
            }
            null -> null
        }

        if (panel == null) return
        AppTypography.applyTo(panel)
        val overlay = when (sheet) {
            Sheet.A2_CREW -> TravelFlowOverlay(this, panel, 1100f, 824f, dim = true,
                backdrop = currentPageView, onClose = ::dismissSheet)
            Sheet.A3_TRIP_START -> A3TripStartPopupOverlay(this, panel, currentPageView, draftCrewSaved, ::dismissSheet)
            Sheet.C1_EDIT_RESULT_PLACES -> C1PlacesOverlay(this, panel, currentPageView, ::dismissSheet)
            Sheet.COSTS -> ResultDialogOverlay(this, panel, 702.3f, ::dismissSheet)
            Sheet.D1_1_COSTS -> ResultDialogOverlay(this, panel, D1_1CostsView.HEIGHT, ::dismissSheet)
            else -> views.overlay(panel, ::dismissSheet)
        }
        val movingPanel = when (sheet) {
            Sheet.A2_CREW, Sheet.A3_TRIP_START, Sheet.C1_EDIT_RESULT_PLACES, Sheet.COSTS, Sheet.D1_1_COSTS -> null
            else -> panel
        }
        overlay.setTag(R.id.ending_credit_screen_id, sheet?.screenId)
        overlay.isFocusableInTouchMode = true
        setPageModal(true)
        sheetMotion.show(overlay, matchStage(), movingPanel, animate)
        overlay.requestFocus()
    }

    private fun setPageModal(modal: Boolean) {
        currentPageView?.apply {
            importantForAccessibility = if (modal) View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                else View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            descendantFocusability = if (modal) ViewGroup.FOCUS_BLOCK_DESCENDANTS
                else ViewGroup.FOCUS_BEFORE_DESCENDANTS
        }
    }

    private fun showContent(view: View, screen: ScreenKey) {
        val lastScreen = renderedScreen
        val animate = started && uxAllowed && stage.isLaidOut && lastScreen != null &&
            lastScreen != screen && lastScreen.page != Page.A5_DRIVING_PREVIEW &&
            screen.page != Page.A5_DRIVING_PREVIEW && ValueAnimator.areAnimatorsEnabled()
        val sceneChange = lastScreen?.page == screen.page
        finishCrossfade()
        val previous = currentPageView
        val next = FrameLayout(this).apply {
            setBackgroundColor(pageBackground)
            // Consume touches outside controls so they cannot reach the old page.
            isClickable = true
            val screenId = when (screen.page) {
                Page.B1_CREDIT_VIDEO -> (view as B1CreditVideoPromptView).screenId
                Page.B3_CREDIT -> CreditPlayback.screenId(screen.frame)
                else -> screen.page.screenId
            }
            setTag(R.id.ending_credit_screen_id, screenId)
            addView(view, matchStage())
        }
        currentPageView = next
        renderedScreen = screen
        if (!animate || previous == null) {
            previous?.let { stage.removeView(it) }
            stage.addView(next, matchStage())
            return
        }
        previous.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        previous.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        previous.clearFocus()
        outgoingPageView = previous
        next.alpha = 0f
        if (!sceneChange) {
            next.scaleX = PAGE_ENTER_SCALE
            next.scaleY = PAGE_ENTER_SCALE
        }
        stage.addView(next, matchStage())
        // The outgoing page stays opaque so dark credit scenes blend without a flash.
        next.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setInterpolator(if (sceneChange) SceneEase else EnterEase)
            .setDuration(when {
                sceneChange -> SCENE_CROSSFADE_MS
                lastScreen?.page == Page.B3_CREDIT || screen.page == Page.B3_CREDIT -> CREDIT_PAGE_CROSSFADE_MS
                else -> PAGE_AND_POPUP_TRANSITION_MS
            })
            .withEndAction { if (currentPageView === next) finishCrossfade() }
            .start()
    }

    private fun finishCrossfade() {
        currentPageView?.let {
            it.animate().cancel()
            it.alpha = 1f
            it.scaleX = 1f
            it.scaleY = 1f
        }
        outgoingPageView?.let { stage.removeView(it) }
        outgoingPageView = null
    }

    private fun show(next: Page) {
        if (recordingActive() && next != Page.A4_RECORDING && next != Page.A0_CONSENT && next != Page.A1_PAYOUT_SETUP) {
            sheet = null; page = Page.A4_RECORDING; render(); return
        }
        if ((page == Page.A3_START && next != Page.A3_START) || (page == Page.F1_CATALOG && next != Page.F1_CATALOG) ||
            sheet == Sheet.A3_TRIP_START) {
            WindowCompat.getInsetsController(window, stage).hide(WindowInsetsCompat.Type.ime())
        }
        sheet = null
        page = next
        render()
    }

    private fun removeResultPlace(resultTripId: String, placeId: String) {
        if (!uxAllowed || page != Page.C1_RESULT || sheet != null || tripId != resultTripId) return
        val trip = store.trips.firstOrNull { it.id == resultTripId } ?: return
        val repository = TripRecordingRepository.get(this)
        val recording = repository.load(resultTripId)
        val places = C1Places.entries(TripResultContent(trip, recording))
        if (places.none { it.id == placeId }) return

        val scrollY = currentPageView?.findViewWithTag<C1Timeline>("c1.places.timeline")?.scrollY ?: 0
        if (recording != null) {
            repository.editStop(resultTripId, placeId) { stop -> stop.copy(excluded = true) }
        }
        if (recording == null || trip.resultPlaces != null) {
            store.update(trip.copy(resultPlaces = places.filterNot { it.id == placeId }))
        }
        render()
        currentPageView?.findViewWithTag<C1Timeline>("c1.places.timeline")?.let { timeline ->
            timeline.post { timeline.scrollTo(0, scrollY) }
        }
    }

    private fun showSheet(next: Sheet) {
        if (!uxAllowed || sheet == next) return
        sheet = next
        renderSheet(store.trips.firstOrNull { it.id == tripId }, animate = started)
        updateCreditPlayback()
    }

    private fun dismissSheet() = dismissSheet(refreshPage = false)

    private fun dismissSheet(refreshPage: Boolean) {
        if (sheet == null) return
        if (sheet == Sheet.A3_TRIP_START) {
            WindowCompat.getInsetsController(window, stage).hide(WindowInsetsCompat.Type.ime())
        }
        sheet = null
        sheetMotion.dismiss(animate = started && uxAllowed)
        setPageModal(false)
        if (refreshPage) render(keepClosingSheet = true) else updateCreditPlayback()
    }

    private fun prepareTravel() {
        if (!uxAllowed) return
        if (store.activeTrip != null) {
            show(if (recordingActive()) Page.A4_RECORDING else Page.LEGACY_RESULT_CHOICE)
            return
        }
        if (restorePreparation()) return
        returnToArchive = false
        draftCrewName = null
        draftCrewSaved = false
        resetTripDraft()
        showSheet(Sheet.A2_CREW)
    }

    private fun openStart(fromArchive: Boolean = false) {
        if (!uxAllowed) return
        if (store.activeTrip != null) {
            show(if (recordingActive()) Page.A4_RECORDING else Page.LEGACY_RESULT_CHOICE)
            return
        }
        consentReturnPage = null
        consentReturnSheet = null
        returnToArchive = fromArchive
        draftCrewName = null
        draftCrewSaved = false
        resetTripDraft()
        show(Page.A3_START)
    }

    private fun restorePreparation(): Boolean {
        val storedPage = consentReturnPage
        val storedSheet = consentReturnSheet
        consentReturnPage = null
        consentReturnSheet = null
        if (storedSheet == Sheet.A3_TRIP_START) {
            showSheet(storedSheet)
            return true
        }
        if (storedPage == Page.A3_START) {
            show(storedPage)
            return true
        }
        return false
    }

    private fun resetTripDraft() {
        draftName = ""
        draftPeople = ""
        draftDate = ""
    }
    private fun beginTrip() {
        val people = if (draftPeople.isBlank()) 4 else draftPeople.toIntOrNull()
        if (people == null || people !in 1..99) {
            Toast.makeText(this, "인원은 1명에서 99명 사이로 입력해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!uxAllowed) return
        if (!TripRecordingService.hasRecordingPermissions(this)) {
            openConsentSetup()
            return
        }
        store.activeTrip = Trip(
            name = draftName.trim().ifEmpty { "우리의 여행" },
            people = people,
            plannedEnd = if (sheet == Sheet.A3_TRIP_START) "" else draftDate,
            crewName = draftCrewName,
        )
        store.activeTrip?.id?.let { TripRecordingRepository.get(this).ensure(it) }
        TripRecordingService.start(this)
        draftCrewName?.let { TravelCrewStore(this).save(it, people) }
        draftCrewName = null
        draftCrewSaved = false
        returnToArchive = false
        show(Page.A4_RECORDING)
        currentPageView?.findViewWithTag<A4RecordingView>("a4.recording")?.showStartNotice()
    }

    private fun completeTrip() {
        val trip = store.activeTrip?.copy(mode = mode,
            creditVideo = CreditVideoChoice.PENDING.takeIf { mode.showsCredit }) ?: return
        store.complete(trip)
        tripId = trip.id
        resultDay = 0
        replaying = false
        creditFrame = 0
        creditElapsedMillis = 0
        creditPaused = false
        show(if (trip.mode.showsCredit) Page.B1_CREDIT_VIDEO else Page.C1_RESULT)
    }

    private fun finishRecording() {
        if(!uxAllowed)return
        store.activeTrip?.id?.let{TripRecordingRepository.get(this).finish(it)}
        TripRecordingService.stop(this)
        mode=ResultMode.BOTH
        completeTrip()
    }

    private fun creditVideoPrompt(trip: Trip): View {
        if (resumed && creditUpload == null && localVideoAllowed()) {
            creditUpload = CreditVideoUpload.open(this, creditVideoFiles) { source, receipt ->
                handler.post {
                    if (creditUpload !== source || page != Page.B1_CREDIT_VIDEO || !uxAllowed || !resumed) {
                        receipt.file?.delete()
                    } else {
                        discardCreditVideoReceipt()
                        creditVideoReceipt = receipt
                        render()
                    }
                }
            }
        }
        restoreCreditVideoPreview()
        val receipt = creditVideoReceipt
        return B1CreditVideoPromptView(this, trip, creditUpload?.qr, receipt,
            if (localVideoAllowed()) "QR 다시 불러오기" else "로컬 네트워크 허용",
            sampleQr = false,
            onQrAction = {
                if (localVideoAllowed()) {
                    render()
                    if (creditUpload == null) Toast.makeText(this, "네트워크에 연결한 뒤 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
                } else {
                    localVideoPermission.launch(CreditVideoUpload.LOCAL_NETWORK_PERMISSION)
                }
            },
            onSkip = { makeCredit(trip.id, includeVideo = false) },
            onMake = if (receipt == null) null else { { makeCredit(trip.id, includeVideo = true) } },
        )
    }

    private fun restoreCreditVideoPreview() {
        val receipt = creditVideoReceipt ?: return
        val file = receipt.file ?: return
        if (receipt.thumbnail != null || restoringVideoPreview) return
        restoringVideoPreview = true
        kotlin.concurrent.thread(name = "credit-video-preview", isDaemon = true) {
            val inspected = creditVideoFiles.inspect(file)
            handler.post {
                restoringVideoPreview = false
                if (!isDestroyed && page == Page.B1_CREDIT_VIDEO && creditVideoReceipt === receipt) {
                    if (inspected == null) discardCreditVideoReceipt() else creditVideoReceipt = inspected
                    render()
                }
            }
        }
    }

    private fun localVideoAllowed() = Build.VERSION.SDK_INT < 37 ||
        checkSelfPermission(CreditVideoUpload.LOCAL_NETWORK_PERMISSION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun stopCreditUpload() {
        val upload = creditUpload
        creditUpload = null
        upload?.close()
    }

    private fun discardCreditVideoReceipt() {
        creditVideoReceipt?.file?.delete()
        creditVideoReceipt = null
    }

    private fun makeCredit(id: String, includeVideo: Boolean) {
        if (page != Page.B1_CREDIT_VIDEO || tripId != id || !uxAllowed || !resumed) return
        val trip = store.trips.firstOrNull { it.id == id } ?: return
        val receipt = creditVideoReceipt
        if (includeVideo && receipt == null) return
        if (includeVideo && !creditVideoFiles.keep(id, requireNotNull(receipt))) {
            Toast.makeText(this, "영상을 저장하지 못했어요. 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!includeVideo) creditVideoFiles.committed(id).delete()
        store.update(trip.copy(creditVideo = if (includeVideo) CreditVideoChoice.UPLOADED else CreditVideoChoice.NONE))
        discardCreditVideoReceipt()
        show(Page.B2_PREPARING)
    }

    private fun resumeCredit(frame: Int?, fromArchive: Boolean = false) {
        val resume = store.resume ?: return
        val trip = store.trips.firstOrNull { it.id == resume.tripId } ?: return
        tripId = resume.tripId
        resultDay = 0
        returnToArchive = fromArchive
        replaying = false
        if (trip.creditVideo == CreditVideoChoice.PENDING) {
            show(Page.B1_CREDIT_VIDEO)
            return
        }
        setCreditFrame(frame ?: resume.frame, redraw = false)
        creditElapsedMillis = if (frame == null) resume.elapsedMillis else 0
        show(Page.B3_CREDIT)
    }

    private fun setCreditFrame(index: Int, redraw: Boolean = true) {
        creditView()?.setPlaybackAllowed(false)
        creditFrame = index.coerceIn(0, CREDIT_FRAME_COUNT - 1)
        creditElapsedMillis = 0
        creditPaused = false
        if (!replaying) tripId?.let { store.resume = CreditResume(it, creditFrame) }
        if (redraw) render()
    }

    private fun finishCredit() {
        if (!replaying && store.resume?.tripId == tripId) store.resume = null
        val destination = if (replayFromCatalog) Page.F4_CATALOG_DETAIL else Page.C1_RESULT
        replayFromCatalog = false
        replaying = false
        show(destination)
    }

    private fun leaveCredit() {
        creditView()?.setPlaybackAllowed(false)
        saveCreditCheckpoint()
        val destination = when {
            replayFromCatalog -> Page.F4_CATALOG_DETAIL
            replaying -> Page.C1_RESULT
            else -> Page.F1_CATALOG
        }
        replayFromCatalog = false
        replaying = false
        show(destination)
    }

    private fun playFromResult(trip: Trip) {
        replayFromCatalog = false
        replaying = true
        creditFrame = 0
        creditElapsedMillis = 0
        creditPaused = false
        if (trip.mode == ResultMode.SPLIT) {
            store.update(trip.copy(mode = ResultMode.BOTH, creditVideo = CreditVideoChoice.PENDING))
            show(Page.B1_CREDIT_VIDEO)
        } else {
            show(if (trip.creditVideo == CreditVideoChoice.PENDING) Page.B1_CREDIT_VIDEO else Page.B3_CREDIT)
        }
    }

    private fun rememberCatalogScroll() {
        currentPageView?.findViewWithTag<F1CatalogView>("f1.catalog")?.rememberScroll()
    }

    private fun catalogView(trips: List<Trip>): View {
        val resumeId = store.resume?.tripId
        return F1CatalogView(this, trips, catalog,
            onBack = ::goBack,
            onStart = { rememberCatalogScroll(); openStart(fromArchive = true) },
            onOpen = { trip -> openCatalogDetail(trip) },
            onPlay = { trip ->
                openCatalogDetail(trip)
                replayCatalogCredit()
            },
            onPhoto = ::pickCatalogPhoto,
            onReview = { rememberCatalogScroll(); yearFrame = 0; show(Page.F3_YEAR_REVIEW) },
            onResume = if (trips.any { trip -> trip.id == resumeId }) ::resumeCatalogCredit else null,
        )
    }

    private fun resumeCatalogCredit(frame: Int?) {
        rememberCatalogScroll()
        resumeCredit(frame, fromArchive = true)
    }

    private fun catalogDetailView(trip: Trip): View {
        val recording = TripRecordingRepository.get(this).load(trip.id)
        val tripEnd = recording?.endedAt?.let { time ->
            java.time.Instant.ofEpochMilli(time).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        } ?: runCatching { LocalDate.parse(trip.createdOn) }.getOrNull()
        val today = LocalDate.now()
        val canShare = tripEnd != null && tripEnd in today.minusDays(7)..today
        return F4CatalogDetailView(this, TripResultContent(trip, recording), catalogDay,
            onDay = { day -> catalogDay = day },
            onClose = ::returnToList,
            onEdit = { showSheet(Sheet.C1_EDIT_RESULT_PLACES) },
            onReplay = { replayDisplayedCredit(fromCatalog = true) },
            onCosts = { showSheet(Sheet.D1_1_COSTS) },
            onDelete = { showSheet(Sheet.DELETE_CATALOG_TRIP) },
            onRename = { name -> renameCatalogTrip(trip.id, name) },
            onShare = if (canShare) ::shareCatalogTrip else null,
        )
    }

    private fun shareCatalogTrip() = showSheet(Sheet.SHARE_PREVIEW)

    private fun replayDisplayedCredit(fromCatalog: Boolean) {
        val origin = if (fromCatalog) Page.F4_CATALOG_DETAIL else Page.C1_RESULT
        if (!uxAllowed || page != origin || store.trips.none { it.id == tripId }) return
        replayFromCatalog = fromCatalog
        replaying = true
        setCreditFrame(0, redraw = false)
        show(Page.B3_CREDIT)
    }

    private fun renameCatalogTrip(id: String, name: String) {
        if (!uxAllowed || page != Page.F4_CATALOG_DETAIL || tripId != id) return
        val trip = store.trips.firstOrNull { it.id == id } ?: return
        store.update(trip.copy(name = name))
        render()
    }

    private fun openCatalogDetail(trip: Trip, fromArchive: Boolean = true) {
        if (!uxAllowed) return
        if (fromArchive) rememberCatalogScroll()
        tripId = trip.id
        catalogDay = 0
        returnToArchive = fromArchive
        replaying = false
        replayFromCatalog = false
        show(Page.F4_CATALOG_DETAIL)
    }

    private fun pickCatalogPhoto(trip: Trip) {
        if (page != Page.F1_CATALOG || !uxAllowed || photoTripId != null) return
        rememberCatalogScroll()
        photoTripId = trip.id
        runCatching { catalogPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }.onFailure {
            photoTripId = null
            Toast.makeText(this, "사진 선택기를 열 수 없어요.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteCatalogTrip(id: String) {
        if (page != Page.F4_CATALOG_DETAIL || sheet != Sheet.DELETE_CATALOG_TRIP || tripId != id || !uxAllowed) return
        store.deleteCompleted(id)
        TripRecordingRepository.get(this).delete(id)
        CatalogPhotos.remove(this, id)
        creditVideoFiles.committed(id).delete()
        tripId = null
        returnToList()
    }

    private fun replayCatalogCredit() {
        if (page != Page.F4_CATALOG_DETAIL || !uxAllowed) return
        replayFromCatalog = true
        replaying = true
        val trip = store.trips.firstOrNull { it.id == tripId } ?: return
        if (trip.creditVideo == CreditVideoChoice.PENDING) {
            show(Page.B1_CREDIT_VIDEO)
        } else {
            setCreditFrame(0, redraw = false)
            show(Page.B3_CREDIT)
        }
    }

    private fun openArchive() {
        catalog.dates = null
        catalog.query = ""
        catalog.firstVisible = 0
        catalog.firstOffset = 0
        show(Page.F1_CATALOG)
    }

    private fun returnToList() = show(if (returnToArchive) Page.F1_CATALOG else Page.A2_HOME)

    private fun goBack() {
        if (currentPageView?.findViewWithTag<F1CatalogView>("f1.catalog")?.closeCalendar() == true) return
        if(currentPageView?.findViewWithTag<A4RecordingView>("a4.recording")?.handleBack()==true)return
        if (sheet != null) {
            dismissSheet()
            return
        }
        when (page) {
            Page.A0_CONSENT, Page.A1_PAYOUT_SETUP, Page.A2_HOME, Page.A5_DRIVING_PREVIEW -> finish()
            Page.A3_START -> returnToList()
            Page.C1_RESULT -> openArchive()
            Page.A4_RECORDING -> Unit
            Page.F1_CATALOG -> show(Page.A2_HOME)
            Page.F4_CATALOG_DETAIL -> returnToList()
            Page.LEGACY_RESULT_CHOICE -> show(if (recordingActive()) Page.A4_RECORDING else Page.A2_HOME)
            Page.B1_CREDIT_VIDEO -> if (replaying) leaveCredit() else show(Page.C1_RESULT)
            Page.B2_PREPARING, Page.B3_CREDIT -> leaveCredit()
            Page.F3_YEAR_REVIEW -> show(Page.F1_CATALOG)
        }
    }

    private fun schedulePreparation() {
        handler.removeCallbacks(advancePreparation)
        if (started && resumed && uxAllowed && page == Page.B2_PREPARING) handler.postDelayed(advancePreparation, B2CreditPreparingView.DURATION_MILLIS)
    }

    private fun creditView() = currentPageView?.findViewWithTag<B3CreditView>("b3.credit")

    private fun saveCreditCheckpoint() {
        if (page == Page.B3_CREDIT && !replaying) tripId?.let {
            store.resume = CreditResume(it, creditFrame, creditElapsedMillis)
        }
    }

    private fun updateCreditPlayback() {
        creditView()?.setPlaybackAllowed(resumed && uxAllowed && page == Page.B3_CREDIT && sheet == null)
        updateCreditAudio()
        schedulePreparation()
    }

    private fun updateCreditAudio() {
        soundtrack.setEnabled(resumed && uxAllowed && page == Page.B3_CREDIT && sheet == null && !creditMuted && !creditPaused)
    }

    private fun isDrivingPreview(intent: Intent): Boolean =
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 &&
            intent.getBooleanExtra("preview_driving", false)

    private data class ScreenKey(val page: Page, val frame: Int)

    private companion object {
        const val CREDIT_PAGE_CROSSFADE_MS = 260L
        const val SCENE_CROSSFADE_MS = 400L
        const val PAGE_ENTER_SCALE = 1.012f
    }

    private fun matchStage() = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT,
    )
}
